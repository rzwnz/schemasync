package com.example.schemasync.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DiffXmlParserTest {

    private static File comprehensiveXml;

    @BeforeAll
    static void setup() {
        comprehensiveXml = new File(
                DiffXmlParserTest.class.getClassLoader().getResource("test-diff-comprehensive.xml").getFile());
    }

    // ── Table operations ─────────────────────────────────────────────

    @Test
    void parseDiff_createTable_parsesTablesWithColumns() throws Exception {
        Map<String, Object> result = DiffXmlParser.parseDiff(comprehensiveXml);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tables = (List<Map<String, Object>>) result.get("tables");

        long createCount = tables.stream().filter(t -> "create".equals(t.get("action"))).count();
        assertThat(createCount).isEqualTo(2);

        Map<String, Object> usersTable = tables.stream()
                .filter(t -> "users".equals(t.get("tableName")) && "create".equals(t.get("action")))
                .findFirst().orElseThrow();
        assertThat(usersTable.get("schemaName")).isEqualTo("public");
        @SuppressWarnings("unchecked")
        List<String> cols = (List<String>) usersTable.get("columns");
        assertThat(cols).containsExactly("id", "name", "email");

        Map<String, Object> ordersTable = tables.stream()
                .filter(t -> "orders".equals(t.get("tableName")) && "create".equals(t.get("action")))
                .findFirst().orElseThrow();
        assertThat(ordersTable.get("schemaName")).isEqualTo("sales");
        @SuppressWarnings("unchecked")
        List<String> orderCols = (List<String>) ordersTable.get("columns");
        assertThat(orderCols).containsExactly("order_id", "user_id");
    }

    @Test
    void parseDiff_dropTable_parsed() throws Exception {
        Map<String, Object> result = DiffXmlParser.parseDiff(comprehensiveXml);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tables = (List<Map<String, Object>>) result.get("tables");

        Map<String, Object> drop = tables.stream()
                .filter(t -> "drop".equals(t.get("action")))
                .findFirst().orElseThrow();
        assertThat(drop.get("tableName")).isEqualTo("legacy_table");
        assertThat(drop.get("schemaName")).isEqualTo("public");
    }

    // ── Column operations ────────────────────────────────────────────

    @Test
    void parseDiff_addColumn_parsesMultipleColumns() throws Exception {
        Map<String, Object> result = DiffXmlParser.parseDiff(comprehensiveXml);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> columns = (List<Map<String, Object>>) result.get("columns");

        List<Map<String, Object>> adds = columns.stream()
                .filter(c -> "add".equals(c.get("action"))).toList();
        assertThat(adds).hasSize(2);

        Map<String, Object> phone = adds.stream()
                .filter(c -> "phone".equals(c.get("columnName")))
                .findFirst().orElseThrow();
        assertThat(phone.get("tableName")).isEqualTo("users");
        assertThat(phone.get("type")).isEqualTo("varchar(20)");

        Map<String, Object> address = adds.stream()
                .filter(c -> "address".equals(c.get("columnName")))
                .findFirst().orElseThrow();
        assertThat(address.get("type")).isEqualTo("text");
    }

    @Test
    void parseDiff_dropColumn_parsed() throws Exception {
        Map<String, Object> result = DiffXmlParser.parseDiff(comprehensiveXml);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> columns = (List<Map<String, Object>>) result.get("columns");

        Map<String, Object> drop = columns.stream()
                .filter(c -> "drop".equals(c.get("action")))
                .findFirst().orElseThrow();
        assertThat(drop.get("columnName")).isEqualTo("address");
        assertThat(drop.get("tableName")).isEqualTo("users");
        assertThat(drop.get("schemaName")).isEqualTo("public");
    }

    @Test
    void parseDiff_modifyDataType_parsed() throws Exception {
        Map<String, Object> result = DiffXmlParser.parseDiff(comprehensiveXml);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> columns = (List<Map<String, Object>>) result.get("columns");

        Map<String, Object> modify = columns.stream()
                .filter(c -> "modify".equals(c.get("action")))
                .findFirst().orElseThrow();
        assertThat(modify.get("columnName")).isEqualTo("name");
        assertThat(modify.get("newType")).isEqualTo("varchar(500)");
        assertThat(modify.get("tableName")).isEqualTo("users");
    }

    @Test
    void parseDiff_renameColumn_parsed() throws Exception {
        Map<String, Object> result = DiffXmlParser.parseDiff(comprehensiveXml);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> columns = (List<Map<String, Object>>) result.get("columns");

        Map<String, Object> rename = columns.stream()
                .filter(c -> "rename".equals(c.get("action")))
                .findFirst().orElseThrow();
        assertThat(rename.get("oldColumnName")).isEqualTo("phone");
        assertThat(rename.get("newColumnName")).isEqualTo("mobile");
        assertThat(rename.get("tableName")).isEqualTo("users");
    }

    // ── Primary key ──────────────────────────────────────────────────

    @Test
    void parseDiff_addPrimaryKey_parsed() throws Exception {
        Map<String, Object> result = DiffXmlParser.parseDiff(comprehensiveXml);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> constraints = (List<Map<String, Object>>) result.get("constraints");

        Map<String, Object> addPk = constraints.stream()
                .filter(c -> "primaryKey".equals(c.get("type")) && "add".equals(c.get("action")))
                .findFirst().orElseThrow();
        assertThat(addPk.get("constraintName")).isEqualTo("pk_users");
        assertThat(addPk.get("columnNames")).isEqualTo("id");
        assertThat(addPk.get("tableName")).isEqualTo("users");
    }

    @Test
    void parseDiff_dropPrimaryKey_parsed() throws Exception {
        Map<String, Object> result = DiffXmlParser.parseDiff(comprehensiveXml);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> constraints = (List<Map<String, Object>>) result.get("constraints");

        Map<String, Object> dropPk = constraints.stream()
                .filter(c -> "primaryKey".equals(c.get("type")) && "drop".equals(c.get("action")))
                .findFirst().orElseThrow();
        assertThat(dropPk.get("tableName")).isEqualTo("old_table");
        assertThat(dropPk.get("schemaName")).isEqualTo("public");
    }

    // ── Unique constraint ────────────────────────────────────────────

    @Test
    void parseDiff_addUniqueConstraint_parsed() throws Exception {
        Map<String, Object> result = DiffXmlParser.parseDiff(comprehensiveXml);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> constraints = (List<Map<String, Object>>) result.get("constraints");

        Map<String, Object> addUnique = constraints.stream()
                .filter(c -> "unique".equals(c.get("type")) && "add".equals(c.get("action")))
                .findFirst().orElseThrow();
        assertThat(addUnique.get("constraintName")).isEqualTo("uq_email");
        assertThat(addUnique.get("columnNames")).isEqualTo("email");
    }

    @Test
    void parseDiff_dropUniqueConstraint_parsed() throws Exception {
        Map<String, Object> result = DiffXmlParser.parseDiff(comprehensiveXml);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> constraints = (List<Map<String, Object>>) result.get("constraints");

        Map<String, Object> dropUnique = constraints.stream()
                .filter(c -> "unique".equals(c.get("type")) && "drop".equals(c.get("action")))
                .findFirst().orElseThrow();
        assertThat(dropUnique.get("constraintName")).isEqualTo("uq_old");
    }

    // ── Foreign key ──────────────────────────────────────────────────

    @Test
    void parseDiff_addForeignKey_parsed() throws Exception {
        Map<String, Object> result = DiffXmlParser.parseDiff(comprehensiveXml);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> constraints = (List<Map<String, Object>>) result.get("constraints");

        Map<String, Object> addFk = constraints.stream()
                .filter(c -> "foreignKey".equals(c.get("type")) && "add".equals(c.get("action")))
                .findFirst().orElseThrow();
        assertThat(addFk.get("constraintName")).isEqualTo("fk_orders_user");
        assertThat(addFk.get("tableName")).isEqualTo("orders");
        assertThat(addFk.get("schemaName")).isEqualTo("sales");
        assertThat(addFk.get("columnNames")).isEqualTo("user_id");
        assertThat(addFk.get("referencedTable")).isEqualTo("users");
        assertThat(addFk.get("referencedColumns")).isEqualTo("id");
    }

    @Test
    void parseDiff_dropForeignKey_parsed() throws Exception {
        Map<String, Object> result = DiffXmlParser.parseDiff(comprehensiveXml);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> constraints = (List<Map<String, Object>>) result.get("constraints");

        Map<String, Object> dropFk = constraints.stream()
                .filter(c -> "foreignKey".equals(c.get("type")) && "drop".equals(c.get("action")))
                .findFirst().orElseThrow();
        assertThat(dropFk.get("constraintName")).isEqualTo("fk_old");
        assertThat(dropFk.get("tableName")).isEqualTo("orders");
    }

    // ── Check constraint ─────────────────────────────────────────────

    @Test
    void parseDiff_addCheckConstraint_parsed() throws Exception {
        Map<String, Object> result = DiffXmlParser.parseDiff(comprehensiveXml);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> constraints = (List<Map<String, Object>>) result.get("constraints");

        Map<String, Object> addCheck = constraints.stream()
                .filter(c -> "check".equals(c.get("type")) && "add".equals(c.get("action")))
                .findFirst().orElseThrow();
        assertThat(addCheck.get("constraintName")).isEqualTo("chk_name");
        assertThat(addCheck.get("constraintText")).isEqualTo("length(name) > 0");
    }

    @Test
    void parseDiff_dropCheckConstraint_parsed() throws Exception {
        Map<String, Object> result = DiffXmlParser.parseDiff(comprehensiveXml);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> constraints = (List<Map<String, Object>>) result.get("constraints");

        Map<String, Object> dropCheck = constraints.stream()
                .filter(c -> "check".equals(c.get("type")) && "drop".equals(c.get("action")))
                .findFirst().orElseThrow();
        assertThat(dropCheck.get("constraintName")).isEqualTo("chk_old");
    }

    // ── Index operations ─────────────────────────────────────────────

    @Test
    void parseDiff_createIndex_parsed() throws Exception {
        Map<String, Object> result = DiffXmlParser.parseDiff(comprehensiveXml);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> indexes = (List<Map<String, Object>>) result.get("indexes");

        Map<String, Object> create = indexes.stream()
                .filter(i -> "create".equals(i.get("action")))
                .findFirst().orElseThrow();
        assertThat(create.get("indexName")).isEqualTo("idx_email");
        assertThat(create.get("tableName")).isEqualTo("users");
        assertThat(create.get("schemaName")).isEqualTo("public");
    }

    @Test
    void parseDiff_dropIndex_parsed() throws Exception {
        Map<String, Object> result = DiffXmlParser.parseDiff(comprehensiveXml);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> indexes = (List<Map<String, Object>>) result.get("indexes");

        Map<String, Object> drop = indexes.stream()
                .filter(i -> "drop".equals(i.get("action")))
                .findFirst().orElseThrow();
        assertThat(drop.get("indexName")).isEqualTo("idx_old");
    }

    // ── View operations ──────────────────────────────────────────────

    @Test
    void parseDiff_createView_parsed() throws Exception {
        Map<String, Object> result = DiffXmlParser.parseDiff(comprehensiveXml);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> views = (List<Map<String, Object>>) result.get("views");

        Map<String, Object> create = views.stream()
                .filter(v -> "create".equals(v.get("action")))
                .findFirst().orElseThrow();
        assertThat(create.get("viewName")).isEqualTo("active_users");
        assertThat(create.get("schemaName")).isEqualTo("public");
        assertThat((String) create.get("selectQuery")).contains("SELECT * FROM users");
    }

    @Test
    void parseDiff_dropView_parsed() throws Exception {
        Map<String, Object> result = DiffXmlParser.parseDiff(comprehensiveXml);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> views = (List<Map<String, Object>>) result.get("views");

        Map<String, Object> drop = views.stream()
                .filter(v -> "drop".equals(v.get("action")))
                .findFirst().orElseThrow();
        assertThat(drop.get("viewName")).isEqualTo("old_view");
    }

    // ── Schema operations ────────────────────────────────────────────

    @Test
    void parseDiff_createSchema_parsed() throws Exception {
        Map<String, Object> result = DiffXmlParser.parseDiff(comprehensiveXml);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> schemas = (List<Map<String, Object>>) result.get("schemas");

        Map<String, Object> create = schemas.stream()
                .filter(s -> "create".equals(s.get("action")))
                .findFirst().orElseThrow();
        assertThat(create.get("schemaName")).isEqualTo("analytics");
    }

    @Test
    void parseDiff_dropSchema_parsed() throws Exception {
        Map<String, Object> result = DiffXmlParser.parseDiff(comprehensiveXml);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> schemas = (List<Map<String, Object>>) result.get("schemas");

        Map<String, Object> drop = schemas.stream()
                .filter(s -> "drop".equals(s.get("action")))
                .findFirst().orElseThrow();
        assertThat(drop.get("schemaName")).isEqualTo("legacy");
    }

    // ── Result structure ─────────────────────────────────────────────

    @Test
    void parseDiff_comprehensive_allKeysPresent() throws Exception {
        Map<String, Object> result = DiffXmlParser.parseDiff(comprehensiveXml);
        assertThat(result).containsKeys("tables", "columns", "constraints", "indexes", "views", "schemas");
    }

    @Test
    void parseDiff_comprehensive_totalCounts() throws Exception {
        Map<String, Object> result = DiffXmlParser.parseDiff(comprehensiveXml);
        @SuppressWarnings("unchecked")
        List<?> tables = (List<?>) result.get("tables");
        @SuppressWarnings("unchecked")
        List<?> columns = (List<?>) result.get("columns");
        @SuppressWarnings("unchecked")
        List<?> constraints = (List<?>) result.get("constraints");
        @SuppressWarnings("unchecked")
        List<?> indexes = (List<?>) result.get("indexes");
        @SuppressWarnings("unchecked")
        List<?> views = (List<?>) result.get("views");
        @SuppressWarnings("unchecked")
        List<?> schemas = (List<?>) result.get("schemas");

        assertThat(tables).hasSize(3);      // 2 create + 1 drop
        assertThat(columns).hasSize(5);     // 2 add + 1 drop + 1 modify + 1 rename
        assertThat(constraints).hasSize(8); // add/drop × (pk + unique + fk + check)
        assertThat(indexes).hasSize(2);     // 1 create + 1 drop
        assertThat(views).hasSize(2);       // 1 create + 1 drop
        assertThat(schemas).hasSize(2);     // 1 create + 1 drop
    }

    // ── Empty changelog ──────────────────────────────────────────────

    @Test
    void parseDiff_emptyChangelog_emptySections(@TempDir Path tempDir) throws Exception {
        Path empty = tempDir.resolve("empty.xml");
        Files.writeString(empty, """
                <?xml version="1.0" encoding="UTF-8"?>
                <databaseChangeLog xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
                    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
                    http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd">
                </databaseChangeLog>
                """);
        Map<String, Object> result = DiffXmlParser.parseDiff(empty.toFile());
        assertThat((List<?>) result.get("tables")).isEmpty();
        assertThat((List<?>) result.get("columns")).isEmpty();
        assertThat((List<?>) result.get("constraints")).isEmpty();
        assertThat((List<?>) result.get("indexes")).isEmpty();
        assertThat((List<?>) result.get("views")).isEmpty();
        assertThat((List<?>) result.get("schemas")).isEmpty();
    }

    // ── Error cases ──────────────────────────────────────────────────

    @Test
    void parseDiff_invalidFile_throws() {
        File file = new File("/not/a/real/file.xml");
        assertThatThrownBy(() -> DiffXmlParser.parseDiff(file)).isInstanceOf(Exception.class);
    }

    @Test
    void parseDiff_malformedXml_throws(@TempDir Path tempDir) throws IOException {
        Path bad = tempDir.resolve("bad.xml");
        Files.writeString(bad, "<<not xml at all!!");
        assertThatThrownBy(() -> DiffXmlParser.parseDiff(bad.toFile())).isInstanceOf(Exception.class);
    }

    // ── Per-operation isolation tests (single-changeset XMLs) ────────

    @Test
    void parseDiff_singleCreateTable(@TempDir Path tempDir) throws Exception {
        Path f = tempDir.resolve("single.xml");
        Files.writeString(f, """
                <?xml version="1.0" encoding="UTF-8"?>
                <databaseChangeLog xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
                    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
                    http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd">
                  <changeSet id="1" author="a">
                    <createTable tableName="t1" schemaName="s1">
                      <column name="c1" type="int"/>
                    </createTable>
                  </changeSet>
                </databaseChangeLog>
                """);
        Map<String, Object> r = DiffXmlParser.parseDiff(f.toFile());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tables = (List<Map<String, Object>>) r.get("tables");
        assertThat(tables).hasSize(1);
        assertThat(tables.get(0).get("action")).isEqualTo("create");
        assertThat(tables.get(0).get("tableName")).isEqualTo("t1");
        @SuppressWarnings("unchecked")
        List<String> cols = (List<String>) tables.get(0).get("columns");
        assertThat(cols).containsExactly("c1");
    }

    @Test
    void parseDiff_singleDropTable(@TempDir Path tempDir) throws Exception {
        Path f = tempDir.resolve("drop.xml");
        Files.writeString(f, """
                <?xml version="1.0" encoding="UTF-8"?>
                <databaseChangeLog xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
                    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
                    http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd">
                  <changeSet id="1" author="a">
                    <dropTable tableName="gone" schemaName="pub"/>
                  </changeSet>
                </databaseChangeLog>
                """);
        Map<String, Object> r = DiffXmlParser.parseDiff(f.toFile());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tables = (List<Map<String, Object>>) r.get("tables");
        assertThat(tables).hasSize(1);
        assertThat(tables.get(0).get("action")).isEqualTo("drop");
        assertThat(tables.get(0).get("tableName")).isEqualTo("gone");
    }

    @Test
    void parseDiff_singleAddColumn(@TempDir Path tempDir) throws Exception {
        Path f = tempDir.resolve("addcol.xml");
        Files.writeString(f, """
                <?xml version="1.0" encoding="UTF-8"?>
                <databaseChangeLog xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
                    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
                    http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd">
                  <changeSet id="1" author="a">
                    <addColumn tableName="t" schemaName="s">
                      <column name="newcol" type="bigint"/>
                    </addColumn>
                  </changeSet>
                </databaseChangeLog>
                """);
        Map<String, Object> r = DiffXmlParser.parseDiff(f.toFile());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> columns = (List<Map<String, Object>>) r.get("columns");
        assertThat(columns).hasSize(1);
        assertThat(columns.get(0).get("action")).isEqualTo("add");
        assertThat(columns.get(0).get("columnName")).isEqualTo("newcol");
        assertThat(columns.get(0).get("type")).isEqualTo("bigint");
    }

    @Test
    void parseDiff_singleAddForeignKey(@TempDir Path tempDir) throws Exception {
        Path f = tempDir.resolve("fk.xml");
        Files.writeString(f, """
                <?xml version="1.0" encoding="UTF-8"?>
                <databaseChangeLog xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
                    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
                    http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd">
                  <changeSet id="1" author="a">
                    <addForeignKeyConstraint baseTableName="child" baseTableSchemaName="pub"
                        constraintName="fk_test" baseColumnNames="parent_id"
                        referencedTableName="parent" referencedColumnNames="id"/>
                  </changeSet>
                </databaseChangeLog>
                """);
        Map<String, Object> r = DiffXmlParser.parseDiff(f.toFile());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> constraints = (List<Map<String, Object>>) r.get("constraints");
        assertThat(constraints).hasSize(1);
        assertThat(constraints.get(0).get("type")).isEqualTo("foreignKey");
        assertThat(constraints.get(0).get("referencedTable")).isEqualTo("parent");
        assertThat(constraints.get(0).get("referencedColumns")).isEqualTo("id");
    }

    @Test
    void parseDiff_singleCreateView(@TempDir Path tempDir) throws Exception {
        Path f = tempDir.resolve("view.xml");
        Files.writeString(f, """
                <?xml version="1.0" encoding="UTF-8"?>
                <databaseChangeLog xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
                    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
                    http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd">
                  <changeSet id="1" author="a">
                    <createView viewName="v1" schemaName="pub">SELECT 1</createView>
                  </changeSet>
                </databaseChangeLog>
                """);
        Map<String, Object> r = DiffXmlParser.parseDiff(f.toFile());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> views = (List<Map<String, Object>>) r.get("views");
        assertThat(views).hasSize(1);
        assertThat(views.get(0).get("viewName")).isEqualTo("v1");
        assertThat((String) views.get(0).get("selectQuery")).isEqualTo("SELECT 1");
    }
}
