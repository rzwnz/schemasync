package com.example.schemasync.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class DbMergeConfig {
    private String description;
    @NotBlank(message = "changeLogFile is required")
    private String changeLogFile;
    private boolean deleteTargetData = true;
    private boolean compareData = false;

    @NotNull(message = "sourceDatabase is required")
    @Valid
    private DatabaseConfig sourceDatabase;
    @NotNull(message = "targetDatabases is required")
    @Size(min = 1, message = "At least one target database is required")
    private List<@Valid DatabaseConfig> targetDatabases;

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getChangeLogFile() {
        return changeLogFile;
    }

    public void setChangeLogFile(String changeLogFile) {
        this.changeLogFile = changeLogFile;
    }

    public boolean isDeleteTargetData() {
        return deleteTargetData;
    }

    public void setDeleteTargetData(boolean deleteTargetData) {
        this.deleteTargetData = deleteTargetData;
    }

    public boolean isCompareData() {
        return compareData;
    }

    public void setCompareData(boolean compareData) {
        this.compareData = compareData;
    }

    public DatabaseConfig getSourceDatabase() {
        return sourceDatabase;
    }

    public void setSourceDatabase(DatabaseConfig sourceDatabase) {
        this.sourceDatabase = sourceDatabase;
    }

    public List<DatabaseConfig> getTargetDatabases() {
        return targetDatabases;
    }

    public void setTargetDatabases(List<DatabaseConfig> targetDatabases) {
        this.targetDatabases = targetDatabases;
    }

    public static class DatabaseConfig {
        @NotBlank(message = "Database name is required")
        private String name;
        private String dbmsType;
        @NotBlank(message = "JDBC URL is required")
        private String jdbcUrl;
        @NotBlank(message = "Username is required")
        private String username;
        private String password;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDbmsType() { return dbmsType; }
        public void setDbmsType(String dbmsType) { this.dbmsType = dbmsType; }
        public String getJdbcUrl() { return jdbcUrl; }
        public void setJdbcUrl(String jdbcUrl) { this.jdbcUrl = jdbcUrl; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }
}
