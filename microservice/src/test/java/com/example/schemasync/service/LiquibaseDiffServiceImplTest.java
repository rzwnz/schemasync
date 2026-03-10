package com.example.schemasync.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.schemasync.dto.ChangeSetDto;

import liquibase.exception.LiquibaseException;

class LiquibaseDiffServiceImplTest {
    private final LiquibaseDiffServiceImpl service = new LiquibaseDiffServiceImpl();

    @Test
    void generateDiff_invalidConnection_throwsLiquibaseException() {
        assertThatThrownBy(() -> service.generateDiff(1L, "bad", "bad", "bad", "bad", "bad", "bad", null, null))
                .isInstanceOf(LiquibaseException.class);
    }

    @Test
    void generateDiff_invalidWithSchemas_throwsLiquibaseException() {
        assertThatThrownBy(() -> service.generateDiff(2L, "bad", "u", "p", "bad", "u", "p",
                List.of("public"), null))
                .isInstanceOf(LiquibaseException.class);
    }

    // ── listChangeSets ────────────────────────────────────────────────

    @Test
    void listChangeSets_validChangelog_returnsChangeSets(@TempDir Path tempDir) throws Exception {
        Path changelog = tempDir.resolve("changelog.xml");
        Files.writeString(changelog, """
                <?xml version="1.0" encoding="UTF-8"?>
                <databaseChangeLog xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
                    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
                    http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd">
                  <changeSet id="cs-1" author="tester">
                    <createTable tableName="t1">
                      <column name="id" type="int"/>
                    </createTable>
                  </changeSet>
                  <changeSet id="cs-2" author="dev">
                    <addColumn tableName="t1">
                      <column name="name" type="varchar(255)"/>
                    </addColumn>
                  </changeSet>
                </databaseChangeLog>
                """);

        List<ChangeSetDto> changeSets = service.listChangeSets(changelog.toFile());
        assertThat(changeSets).hasSize(2);
        assertThat(changeSets.get(0).getId()).isEqualTo("cs-1");
        assertThat(changeSets.get(0).getAuthor()).isEqualTo("tester");
        assertThat(changeSets.get(0).getType()).isNotNull();
        assertThat(changeSets.get(1).getId()).isEqualTo("cs-2");
        assertThat(changeSets.get(1).getAuthor()).isEqualTo("dev");
    }

    @Test
    void listChangeSets_emptyChangelog_returnsEmptyList(@TempDir Path tempDir) throws Exception {
        Path changelog = tempDir.resolve("empty.xml");
        Files.writeString(changelog, """
                <?xml version="1.0" encoding="UTF-8"?>
                <databaseChangeLog xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
                    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
                    http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd">
                </databaseChangeLog>
                """);
        List<ChangeSetDto> changeSets = service.listChangeSets(changelog.toFile());
        assertThat(changeSets).isEmpty();
    }

    @Test
    void listChangeSets_nonExistentFile_throwsLiquibaseException() {
        File file = new File("/tmp/doesnotexist-" + System.nanoTime() + ".xml");
        assertThatThrownBy(() -> service.listChangeSets(file))
                .isInstanceOf(LiquibaseException.class);
    }

    @Test
    void listChangeSets_malformedXml_throwsLiquibaseException(@TempDir Path tempDir) throws IOException {
        Path bad = tempDir.resolve("bad.xml");
        Files.writeString(bad, "NOT VALID XML");
        assertThatThrownBy(() -> service.listChangeSets(bad.toFile()))
                .isInstanceOf(LiquibaseException.class);
    }

    @Test
    void listChangeSets_multipleChangeTypes(@TempDir Path tempDir) throws Exception {
        Path changelog = tempDir.resolve("multi.xml");
        Files.writeString(changelog, """
                <?xml version="1.0" encoding="UTF-8"?>
                <databaseChangeLog xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
                    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
                    http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd">
                  <changeSet id="1" author="a">
                    <createTable tableName="t"><column name="id" type="int"/></createTable>
                  </changeSet>
                  <changeSet id="2" author="a">
                    <dropTable tableName="t"/>
                  </changeSet>
                  <changeSet id="3" author="a">
                    <addColumn tableName="t2"><column name="c" type="int"/></addColumn>
                  </changeSet>
                </databaseChangeLog>
                """);
        List<ChangeSetDto> changeSets = service.listChangeSets(changelog.toFile());
        assertThat(changeSets).hasSize(3);
        // Each ChangeSet should have a type from its first change
        assertThat(changeSets.stream().map(ChangeSetDto::getType)).doesNotContainNull();
    }

    // ── applyFilteredChangeSets ───────────────────────────────────────

    @Test
    void applyFilteredChangeSets_oracleStub() throws Exception {
        File file = File.createTempFile("changelog", ".xml");
        file.deleteOnExit();
        String result = service.applyFilteredChangeSets(file, List.of(), "ORACLE", "", "", "");
        assertThat(result).containsIgnoringCase("not implemented");
    }

    @Test
    void applyFilteredChangeSets_oracle_caseInsensitive() throws Exception {
        File file = File.createTempFile("changelog", ".xml");
        file.deleteOnExit();
        String result = service.applyFilteredChangeSets(file, List.of(), "oracle", "", "", "");
        assertThat(result).containsIgnoringCase("not implemented");
    }

    @Test
    void applyFilteredChangeSets_invalidConnection_throwsLiquibaseException() throws Exception {
        File file = File.createTempFile("changelog", ".xml");
        file.deleteOnExit();
        assertThatThrownBy(() -> service.applyFilteredChangeSets(file, List.of(), "POSTGRES", "bad", "bad", "bad"))
                .isInstanceOf(LiquibaseException.class);
    }
} 