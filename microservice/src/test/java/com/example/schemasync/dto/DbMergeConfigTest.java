package com.example.schemasync.dto;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class DbMergeConfigTest {

    @Test
    void gettersSetters_allFieldsCovered() {
        DbMergeConfig cfg = new DbMergeConfig();
        cfg.setDescription("test merge");
        cfg.setChangeLogFile("/path/changelog.xml");
        cfg.setDeleteTargetData(false);
        cfg.setCompareData(true);

        assertThat(cfg.getDescription()).isEqualTo("test merge");
        assertThat(cfg.getChangeLogFile()).isEqualTo("/path/changelog.xml");
        assertThat(cfg.isDeleteTargetData()).isFalse();
        assertThat(cfg.isCompareData()).isTrue();
    }

    @Test
    void defaults_deleteTargetTrue_compareDataFalse() {
        DbMergeConfig cfg = new DbMergeConfig();
        assertThat(cfg.isDeleteTargetData()).isTrue();
        assertThat(cfg.isCompareData()).isFalse();
    }

    @Test
    void sourceDatabase_getterSetter() {
        DbMergeConfig cfg = new DbMergeConfig();
        DbMergeConfig.DatabaseConfig src = new DbMergeConfig.DatabaseConfig();
        src.setName("prod");
        cfg.setSourceDatabase(src);
        assertThat(cfg.getSourceDatabase().getName()).isEqualTo("prod");
    }

    @Test
    void targetDatabases_getterSetter() {
        DbMergeConfig cfg = new DbMergeConfig();
        DbMergeConfig.DatabaseConfig t1 = new DbMergeConfig.DatabaseConfig();
        t1.setName("dev1");
        DbMergeConfig.DatabaseConfig t2 = new DbMergeConfig.DatabaseConfig();
        t2.setName("dev2");
        cfg.setTargetDatabases(List.of(t1, t2));
        assertThat(cfg.getTargetDatabases()).hasSize(2);
        assertThat(cfg.getTargetDatabases().get(0).getName()).isEqualTo("dev1");
        assertThat(cfg.getTargetDatabases().get(1).getName()).isEqualTo("dev2");
    }

    // ── DatabaseConfig nested class ──────────────────────────────────

    @Test
    void databaseConfig_allGettersSetters() {
        DbMergeConfig.DatabaseConfig dc = new DbMergeConfig.DatabaseConfig();
        dc.setName("myDb");
        dc.setDbmsType("POSTGRES");
        dc.setJdbcUrl("jdbc:postgresql://localhost/mydb");
        dc.setUsername("admin");
        dc.setPassword("secret");

        assertThat(dc.getName()).isEqualTo("myDb");
        assertThat(dc.getDbmsType()).isEqualTo("POSTGRES");
        assertThat(dc.getJdbcUrl()).isEqualTo("jdbc:postgresql://localhost/mydb");
        assertThat(dc.getUsername()).isEqualTo("admin");
        assertThat(dc.getPassword()).isEqualTo("secret");
    }

    @Test
    void databaseConfig_defaults_areNull() {
        DbMergeConfig.DatabaseConfig dc = new DbMergeConfig.DatabaseConfig();
        assertThat(dc.getName()).isNull();
        assertThat(dc.getDbmsType()).isNull();
        assertThat(dc.getJdbcUrl()).isNull();
        assertThat(dc.getUsername()).isNull();
        assertThat(dc.getPassword()).isNull();
    }
}
