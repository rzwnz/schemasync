package com.example.schemasync.service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.schemasync.dto.DbMergeConfig;

class DataTransferServiceTest {
    private final DataTransferService service = new DataTransferService();

    private String srcUrl;
    private String tgtUrl;

    @BeforeEach
    void setUp() {
        String uid = UUID.randomUUID().toString().replace("-", "");
        // DB_CLOSE_DELAY=-1 keeps the DB alive when no connections are open
        srcUrl = "jdbc:h2:mem:src_" + uid + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1";
        tgtUrl = "jdbc:h2:mem:tgt_" + uid + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1";
    }

    private void initDb(String url, String... sqls) throws Exception {
        try (Connection c = DriverManager.getConnection(url, "sa", "")) {
            for (String sql : sqls) {
                c.createStatement().execute(sql);
            }
        }
    }

    private DbMergeConfig buildConfig(boolean deleteTarget, boolean compare, String... targetUrls) {
        DbMergeConfig.DatabaseConfig src = new DbMergeConfig.DatabaseConfig();
        src.setJdbcUrl(srcUrl);
        src.setUsername("sa");
        src.setPassword("");
        src.setName("source");

        DbMergeConfig cfg = new DbMergeConfig();
        cfg.setSourceDatabase(src);
        cfg.setDeleteTargetData(deleteTarget);
        cfg.setCompareData(compare);

        List<DbMergeConfig.DatabaseConfig> targets = new java.util.ArrayList<>();
        for (int i = 0; i < targetUrls.length; i++) {
            DbMergeConfig.DatabaseConfig t = new DbMergeConfig.DatabaseConfig();
            t.setJdbcUrl(targetUrls[i]);
            t.setUsername("sa");
            t.setPassword("");
            t.setName("target" + i);
            targets.add(t);
        }
        cfg.setTargetDatabases(targets);
        return cfg;
    }

    private int countRows(String url, String table) throws Exception {
        try (Connection c = DriverManager.getConnection(url, "sa", "");
             ResultSet rs = c.createStatement().executeQuery("SELECT count(*) FROM public.\"" + table + "\"")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    // ── Happy path ───────────────────────────────────────────────────

    @Test
    void transfer_copiesDataToTarget_withRowVerification() throws Exception {
        initDb(srcUrl,
                "CREATE SCHEMA IF NOT EXISTS public",
                "CREATE TABLE public.items (id INT, name VARCHAR(100))",
                "INSERT INTO public.items VALUES (1, 'Alice')",
                "INSERT INTO public.items VALUES (2, 'Bob')");
        initDb(tgtUrl,
                "CREATE SCHEMA IF NOT EXISTS public",
                "CREATE TABLE public.items (id INT, name VARCHAR(100))");

        DbMergeConfig cfg = buildConfig(false, true, tgtUrl);
        service.transfer(cfg);

        assertThat(countRows(tgtUrl, "items")).isEqualTo(2);
    }

    @Test
    void transfer_emptyTable_copiesZeroRows() throws Exception {
        initDb(srcUrl,
                "CREATE SCHEMA IF NOT EXISTS public",
                "CREATE TABLE public.empty_tbl (id INT)");
        initDb(tgtUrl,
                "CREATE SCHEMA IF NOT EXISTS public",
                "CREATE TABLE public.empty_tbl (id INT)");

        DbMergeConfig cfg = buildConfig(false, true, tgtUrl);
        service.transfer(cfg);

        assertThat(countRows(tgtUrl, "empty_tbl")).isEqualTo(0);
    }

    @Test
    void transfer_largeBatch_copiesAllRows() throws Exception {
        initDb(srcUrl,
                "CREATE SCHEMA IF NOT EXISTS public",
                "CREATE TABLE public.big_table (id INT)");
        // Insert > 1000 rows to exercise batch boundary
        try (Connection c = DriverManager.getConnection(srcUrl, "sa", "")) {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO public.big_table VALUES (?)")) {
                for (int i = 0; i < 1050; i++) {
                    ps.setInt(1, i);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        }
        initDb(tgtUrl,
                "CREATE SCHEMA IF NOT EXISTS public",
                "CREATE TABLE public.big_table (id INT)");

        DbMergeConfig cfg = buildConfig(false, true, tgtUrl);
        service.transfer(cfg);

        assertThat(countRows(tgtUrl, "big_table")).isEqualTo(1050);
    }

    @Test
    void transfer_multipleTargets_copiesDataToAll() throws Exception {
        String uid2 = UUID.randomUUID().toString().replace("-", "");
        String tgtUrl2 = "jdbc:h2:mem:tgt2_" + uid2 + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1";

        initDb(srcUrl,
                "CREATE SCHEMA IF NOT EXISTS public",
                "CREATE TABLE public.data (id INT, val VARCHAR(50))",
                "INSERT INTO public.data VALUES (1, 'x')");
        initDb(tgtUrl,
                "CREATE SCHEMA IF NOT EXISTS public",
                "CREATE TABLE public.data (id INT, val VARCHAR(50))");
        initDb(tgtUrl2,
                "CREATE SCHEMA IF NOT EXISTS public",
                "CREATE TABLE public.data (id INT, val VARCHAR(50))");

        DbMergeConfig cfg = buildConfig(false, false, tgtUrl, tgtUrl2);
        service.transfer(cfg);

        assertThat(countRows(tgtUrl, "data")).isEqualTo(1);
        assertThat(countRows(tgtUrl2, "data")).isEqualTo(1);
    }

    // ── No tables in source ──────────────────────────────────────────

    @Test
    void transfer_noTablesInSource_returnsEarly() throws Exception {
        initDb(srcUrl, "CREATE SCHEMA IF NOT EXISTS public");

        DbMergeConfig cfg = buildConfig(false, false, tgtUrl);
        assertThatCode(() -> service.transfer(cfg)).doesNotThrowAnyException();
    }

    // ── Error paths ──────────────────────────────────────────────────

    @Test
    void transfer_invalidSourceConnection_throwsException() {
        DbMergeConfig.DatabaseConfig src = new DbMergeConfig.DatabaseConfig();
        src.setJdbcUrl("jdbc:invalid");
        src.setUsername("bad");
        src.setPassword("bad");
        DbMergeConfig cfg = new DbMergeConfig();
        cfg.setSourceDatabase(src);
        cfg.setTargetDatabases(Collections.emptyList());
        assertThatThrownBy(() -> service.transfer(cfg)).isInstanceOf(Exception.class);
    }

    @Test
    void transfer_invalidTargetConnection_throwsException() throws Exception {
        initDb(srcUrl,
                "CREATE SCHEMA IF NOT EXISTS public",
                "CREATE TABLE public.tbl (id INT)",
                "INSERT INTO public.tbl VALUES (1)");

        DbMergeConfig.DatabaseConfig src = new DbMergeConfig.DatabaseConfig();
        src.setJdbcUrl(srcUrl);
        src.setUsername("sa");
        src.setPassword("");

        DbMergeConfig.DatabaseConfig tgt = new DbMergeConfig.DatabaseConfig();
        tgt.setJdbcUrl("jdbc:h2:mem:nonexistent_" + UUID.randomUUID() + ";IFEXISTS=TRUE");
        tgt.setUsername("sa");
        tgt.setPassword("");
        tgt.setName("badTarget");

        DbMergeConfig cfg = new DbMergeConfig();
        cfg.setSourceDatabase(src);
        cfg.setDeleteTargetData(false);
        cfg.setCompareData(false);
        cfg.setTargetDatabases(List.of(tgt));

        assertThatThrownBy(() -> service.transfer(cfg)).isInstanceOf(Exception.class);
    }

    @Test
    void transfer_rowCountMismatch_throwsRuntimeException() throws Exception {
        initDb(srcUrl,
                "CREATE SCHEMA IF NOT EXISTS public",
                "CREATE TABLE public.mismatch (id INT)",
                "INSERT INTO public.mismatch VALUES (1)",
                "INSERT INTO public.mismatch VALUES (2)");
        initDb(tgtUrl,
                "CREATE SCHEMA IF NOT EXISTS public",
                "CREATE TABLE public.mismatch (id INT)",
                "INSERT INTO public.mismatch VALUES (99)");

        // compareData=true → source=2, target=2+1=3 → mismatch → rollback
        DbMergeConfig cfg = buildConfig(false, true, tgtUrl);
        assertThatThrownBy(() -> service.transfer(cfg))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Row count mismatch");
    }

    @Test
    void transfer_deleteTargetData_truncateCascade_exercisesRollback() throws Exception {
        initDb(srcUrl,
                "CREATE SCHEMA IF NOT EXISTS public",
                "CREATE TABLE public.trunctest (id INT)",
                "INSERT INTO public.trunctest VALUES (1)");
        initDb(tgtUrl,
                "CREATE SCHEMA IF NOT EXISTS public",
                "CREATE TABLE public.trunctest (id INT)");

        // H2 doesn't support TRUNCATE ... CASCADE, so rollback path is exercised
        DbMergeConfig cfg = buildConfig(true, false, tgtUrl);
        assertThatThrownBy(() -> service.transfer(cfg))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("rolled back");
    }
} 