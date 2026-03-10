package com.example.schemasync.service.interfaces;

import liquibase.exception.LiquibaseException;
import java.io.File;
import java.util.List;
import com.example.schemasync.dto.ChangeSetDto;

public interface LiquibaseDiffService {
    File generateDiff(Long diffId,
                      String prodUrl, String prodUser, String prodPass,
                      String testUrl, String testUser, String testPass,
                      List<String> includeSchemas,
                      List<String> excludeSchemas) throws LiquibaseException;

    /**
     * Parse the changelog XML and return a list of changesets (with metadata).
     */
    List<ChangeSetDto> listChangeSets(File changelogFile) throws LiquibaseException;

    /**
     * Apply only the selected changesets (by id/author) from the changelog to the target DB.
     */
    String applyFilteredChangeSets(File changelogFile, List<String> selectedChangeSetIds, String dbType, String jdbcUrl, String username, String password) throws LiquibaseException;

    /**
     * Generate the SQL that would be executed for selected changesets without actually applying them (dry-run).
     */
    String generateUpdateSQL(File changelogFile, List<String> selectedChangeSetIds, String dbType, String jdbcUrl, String username, String password) throws LiquibaseException;

    /**
     * Roll back specific changesets that were previously applied to the target database.
     * Generates and executes rollback SQL for each changeset in reverse order.
     *
     * @param changelogFile The changelog XML file
     * @param changeSetIds List of changeset ids to rollback (id:author format)
     * @param jdbcUrl Target database JDBC URL
     * @param username DB username
     * @param password DB password
     * @return Rollback result log
     */
    String rollbackChangeSets(File changelogFile, List<String> changeSetIds, String jdbcUrl, String username, String password) throws LiquibaseException;

    /**
     * Generate rollback SQL for specific changesets without executing it (dry-run rollback).
     */
    String generateRollbackSQL(File changelogFile, List<String> changeSetIds, String jdbcUrl, String username, String password) throws LiquibaseException;
}