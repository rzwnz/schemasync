package com.example.schemasync.service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringWriter;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.Set;

import javax.xml.parsers.ParserConfigurationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.schemasync.dto.ChangeSetDto;
import com.example.schemasync.service.interfaces.LiquibaseDiffService;
import com.example.schemasync.utils.FileStorageUtil;

import liquibase.CatalogAndSchema;
import liquibase.Liquibase;
import liquibase.changelog.ChangeSet;
import liquibase.changelog.DatabaseChangeLog;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.diff.DiffResult;
import liquibase.diff.compare.CompareControl;
import liquibase.diff.output.DiffOutputControl;
import liquibase.diff.output.changelog.DiffToChangeLog;
import liquibase.exception.LiquibaseException;
import liquibase.resource.DirectoryResourceAccessor;
import liquibase.serializer.ChangeLogSerializerFactory;
import liquibase.serializer.core.xml.XMLChangeLogSerializer;
import liquibase.snapshot.SnapshotControl;
import liquibase.snapshot.SnapshotGeneratorFactory;
import liquibase.structure.DatabaseObject;

@Service
public class LiquibaseDiffServiceImpl implements LiquibaseDiffService {

    private static final Logger logger = LoggerFactory.getLogger(LiquibaseDiffServiceImpl.class);

    @Override
    public File generateDiff(Long diffId,
                             String prodUrl, String prodUser, String prodPass,
                             String testUrl, String testUser, String testPass,
                             List<String> includeSchemas,
                             List<String> excludeSchemas) throws LiquibaseException {
        Database prodDb = null;
        Database testDb = null;
        try {
            File diffFile = FileStorageUtil.getDiffFile(diffId);

            prodDb = DatabaseFactory.getInstance()
                .openDatabase(prodUrl, prodUser, prodPass, null, null);
            testDb = DatabaseFactory.getInstance()
                .openDatabase(testUrl, testUser, testPass, null, null);

            // Determine schema names
            String prodSchemaName = (includeSchemas != null && !includeSchemas.isEmpty())
                ? includeSchemas.get(0)
                : prodDb.getDefaultSchemaName();
            String testSchemaName = (includeSchemas != null && !includeSchemas.isEmpty())
                ? includeSchemas.get(0)
                : testDb.getDefaultSchemaName();
            CatalogAndSchema prodSchema = new CatalogAndSchema(null, prodSchemaName);
            CatalogAndSchema testSchema = new CatalogAndSchema(null, testSchemaName);

            // Snapshots
            SnapshotControl prodSnapshotControl = new SnapshotControl(prodDb);
            SnapshotControl testSnapshotControl = new SnapshotControl(testDb);
            var prodSnapshot = SnapshotGeneratorFactory.getInstance()
                .createSnapshot(prodSchema, prodDb, prodSnapshotControl);
            var testSnapshot = SnapshotGeneratorFactory.getInstance()
                .createSnapshot(testSchema, testDb, testSnapshotControl);

            // CompareControl: schema comparison
            CompareControl.SchemaComparison schemaComp =
                new CompareControl.SchemaComparison(prodSchema, testSchema);
            CompareControl compareControl = new CompareControl(
                new CompareControl.SchemaComparison[]{ schemaComp },
                (Set<Class<? extends DatabaseObject>>) null
            );

            // Generate diff
            DiffResult diffResult = liquibase.diff.DiffGeneratorFactory.getInstance()
                .compare(testSnapshot, prodSnapshot, compareControl);

            DiffOutputControl outputControl = new DiffOutputControl();

            DiffToChangeLog diffToChangeLog = new DiffToChangeLog(diffResult, outputControl);

            // Write changelog, catching ParserConfigurationException
            try (FileOutputStream fos = new FileOutputStream(diffFile);
                 PrintStream ps = new PrintStream(fos)) {
                try {
                    diffToChangeLog.print(ps);
                } catch (ParserConfigurationException e) {
                    throw new LiquibaseException("Error generating changelog XML: " + e.getMessage(), e);
                }
            }

            return diffFile;
        } catch (IOException e) {
            throw new LiquibaseException("Error generating diff file: " + e.getMessage(), e);
        } finally {
            closeQuietly(prodDb);
            closeQuietly(testDb);
        }
    }

    @Override
    public List<ChangeSetDto> listChangeSets(File changelogFile) throws LiquibaseException {
        try {
            DirectoryResourceAccessor accessor;
            try {
                accessor = new DirectoryResourceAccessor(changelogFile.getParentFile().toPath());
            } catch (java.io.IOException ioe) {
                throw new LiquibaseException("Resource accessor error", ioe);
            }
            // Parse the changelog XML without a database connection using the ChangeLogParser
            var parser = liquibase.parser.ChangeLogParserFactory.getInstance()
                    .getParser(changelogFile.getName(), accessor);
            DatabaseChangeLog dbChangeLog = parser.parse(changelogFile.getName(), null, accessor);

            List<ChangeSetDto> result = new java.util.ArrayList<>();
            for (ChangeSet cs : dbChangeLog.getChangeSets()) {
                String id = cs.getId();
                String author = cs.getAuthor();
                String schema = cs.getFilePath();
                String table = null;
                String type = cs.getChanges().isEmpty() ? null : cs.getChanges().get(0).getClass().getSimpleName();
                String description = cs.getComments();
                result.add(new ChangeSetDto(id, author, schema, table, type, description));
            }
            return result;
        } catch (Exception e) {
            throw new LiquibaseException("Failed to parse changelog: " + e.getMessage(), e);
        }
    }

    @Override
    public String applyFilteredChangeSets(File changelogFile, List<String> selectedChangeSetIds, String dbType, String jdbcUrl, String username, String password) throws LiquibaseException {
        if ("ORACLE".equalsIgnoreCase(dbType)) {
            // Stub for Oracle support
            return "Oracle support is not implemented yet.";
        }
        // For Postgres: filter changelog and apply only selected changesets
        try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password)) {
            DirectoryResourceAccessor accessor;
            try {
                accessor = new DirectoryResourceAccessor(Path.of("").toAbsolutePath());
            } catch (java.io.IOException ioe) {
                throw new LiquibaseException("Resource accessor error", ioe);
            }
            // Parse the original changelog
            Database origDb = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(conn));
            try (Liquibase liquibase = new Liquibase(changelogFile.getAbsolutePath(), accessor, origDb)) {
            List<ChangeSet> allChangeSets = liquibase.getDatabaseChangeLog().getChangeSets();
            // Filter change sets
            List<ChangeSet> filteredChangeSets = new java.util.ArrayList<>();
            for (ChangeSet cs : allChangeSets) {
                String key = cs.getId() + ":" + cs.getAuthor();
                if (selectedChangeSetIds.contains(key)) {
                    filteredChangeSets.add(cs);
                }
            }
            // Create a temporary changelog file with only the selected change sets
            File tempChangelog = File.createTempFile("filtered-changelog", ".xml");
            DatabaseChangeLog tempChangeLog = new DatabaseChangeLog();
            for (ChangeSet cs : filteredChangeSets) {
                tempChangeLog.addChangeSet(cs);
            }
            // Write the filtered changelog to the temp file
            try (FileOutputStream fos = new FileOutputStream(tempChangelog)) {
                XMLChangeLogSerializer serializer = (XMLChangeLogSerializer) ChangeLogSerializerFactory.getInstance().getSerializer("xml");
                serializer.write(tempChangeLog.getChangeSets(), fos);
            }
            // Apply the filtered changelog
            try {
                Database db = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(conn));
                try (Liquibase filteredLiquibase = new Liquibase(tempChangelog.getAbsolutePath(), accessor, db)) {
                    filteredLiquibase.update("");
                }
            } finally {
                deleteTempFile(tempChangelog);
            }
            }
            return "Filtered changesets applied successfully.";
        } catch (Exception e) {
            throw new LiquibaseException("Failed to apply filtered changesets: " + e.getMessage(), e);
        }
    }

    @Override
    public String generateUpdateSQL(File changelogFile, List<String> selectedChangeSetIds,
                                    String dbType, String jdbcUrl, String username, String password)
            throws LiquibaseException {
        if ("ORACLE".equalsIgnoreCase(dbType)) {
            return "Oracle support is not implemented yet.";
        }
        try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password)) {
            DirectoryResourceAccessor accessor = createAccessor();
            Database origDb = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(conn));

            try (Liquibase liquibase = new Liquibase(changelogFile.getAbsolutePath(), accessor, origDb)) {
                File tempChangelog = buildFilteredChangelog(liquibase, selectedChangeSetIds);
                Database db = DatabaseFactory.getInstance()
                        .findCorrectDatabaseImplementation(new JdbcConnection(conn));

                try (Liquibase filteredLiquibase = new Liquibase(tempChangelog.getAbsolutePath(), accessor, db);
                     StringWriter sw = new StringWriter()) {
                    filteredLiquibase.update("", sw);
                    String sql = sw.toString();
                    logger.info("Generated updateSQL for {} changesets ({} chars)", selectedChangeSetIds.size(), sql.length());
                    return sql.isEmpty() ? "-- No SQL changes to apply" : sql;
                }
            }
        } catch (Exception e) {
            throw new LiquibaseException("Failed to generate update SQL: " + e.getMessage(), e);
        }
    }

    @Override
    public String rollbackChangeSets(File changelogFile, List<String> changeSetIds,
                                     String jdbcUrl, String username, String password)
            throws LiquibaseException {
        int count = resolveRollbackCount(changelogFile, changeSetIds);
        try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password)) {
            DirectoryResourceAccessor accessor = createAccessor();
            Database db = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(conn));

            try (Liquibase liquibase = new Liquibase(changelogFile.getAbsolutePath(), accessor, db)) {
                // Liquibase rollback by count; count is validated against requested changesets.
                liquibase.rollback(count, "");
                logger.info("Successfully rolled back {} changesets", count);
                return "Successfully rolled back " + count + " changeset(s).";
            }
        } catch (Exception e) {
            throw new LiquibaseException("Failed to rollback changesets: " + e.getMessage(), e);
        }
    }

    @Override
    public String generateRollbackSQL(File changelogFile, List<String> changeSetIds,
                                      String jdbcUrl, String username, String password)
            throws LiquibaseException {
        int count = resolveRollbackCount(changelogFile, changeSetIds);
        try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password)) {
            DirectoryResourceAccessor accessor = createAccessor();
            Database db = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(conn));

            try (Liquibase liquibase = new Liquibase(changelogFile.getAbsolutePath(), accessor, db);
                 StringWriter sw = new StringWriter()) {
                liquibase.rollback(count, "", sw);
                String sql = sw.toString();
                logger.info("Generated rollback SQL for {} changesets ({} chars)", count, sql.length());
                return sql.isEmpty() ? "-- No rollback SQL generated" : sql;
            }
        } catch (Exception e) {
            throw new LiquibaseException("Failed to generate rollback SQL: " + e.getMessage(), e);
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private DirectoryResourceAccessor createAccessor() throws LiquibaseException {
        try {
            return new DirectoryResourceAccessor(Path.of("").toAbsolutePath());
        } catch (IOException ioe) {
            throw new LiquibaseException("Resource accessor error", ioe);
        }
    }

    private File buildFilteredChangelog(Liquibase liquibase, List<String> selectedChangeSetIds)
            throws LiquibaseException, IOException {
        List<ChangeSet> allChangeSets = liquibase.getDatabaseChangeLog().getChangeSets();
        List<ChangeSet> filtered = new java.util.ArrayList<>();
        for (ChangeSet cs : allChangeSets) {
            String key = cs.getId() + ":" + cs.getAuthor();
            if (selectedChangeSetIds.contains(key)) {
                filtered.add(cs);
            }
        }
        File tempChangelog = File.createTempFile("filtered-changelog", ".xml");
        try {
            DatabaseChangeLog tempChangeLog = new DatabaseChangeLog();
            for (ChangeSet cs : filtered) {
                tempChangeLog.addChangeSet(cs);
            }
            try (FileOutputStream fos = new FileOutputStream(tempChangelog)) {
                XMLChangeLogSerializer serializer = (XMLChangeLogSerializer)
                        ChangeLogSerializerFactory.getInstance().getSerializer("xml");
                serializer.write(tempChangeLog.getChangeSets(), fos);
            }
            return tempChangelog;
        } catch (Exception e) {
            deleteTempFile(tempChangelog);
            throw e;
        }
    }

    private int resolveRollbackCount(File changelogFile, List<String> changeSetIds) throws LiquibaseException {
        if (changeSetIds == null || changeSetIds.isEmpty()) {
            throw new LiquibaseException("No changeSet IDs provided for rollback");
        }

        List<ChangeSetDto> all = listChangeSets(changelogFile);
        int count = changeSetIds.size();
        if (count > all.size()) {
            throw new LiquibaseException("Requested rollback count exceeds changelog size");
        }

        List<ChangeSetDto> tail = all.subList(all.size() - count, all.size());
        java.util.Set<String> tailKeys = tail.stream()
                .flatMap(cs -> java.util.stream.Stream.of(
                        cs.getId(),
                        cs.getId() + ":" + cs.getAuthor()))
                .collect(java.util.stream.Collectors.toSet());

        for (String id : changeSetIds) {
            if (!tailKeys.contains(id)) {
                throw new LiquibaseException(
                        "Rollback supports only latest contiguous changesets; unknown or non-tail ID: " + id);
            }
        }

        return count;
    }

    private void closeQuietly(Database db) {
        if (db != null) {
            try {
                db.close();
            } catch (Exception e) {
                logger.warn("Failed to close database connection: {}", e.getMessage());
            }
        }
    }

    private void deleteTempFile(File file) {
        if (file != null && file.exists()) {
            if (!file.delete()) {
                logger.warn("Failed to delete temp file: {}", file.getAbsolutePath());
            }
        }
    }
}
