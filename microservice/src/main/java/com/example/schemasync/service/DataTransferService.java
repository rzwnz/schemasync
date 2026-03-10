package com.example.schemasync.service;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.schemasync.dto.DbMergeConfig;
import com.example.schemasync.service.interfaces.IDataTransferService;

@Service
public class DataTransferService implements IDataTransferService {

    private static final Logger logger = LoggerFactory.getLogger(DataTransferService.class);

    /**
     * Transfer data from source database to target databases per the merge config.
     * Dynamically discovers tables in the public schema and copies rows via batch INSERT.
     */
    @Override
    public void transfer(DbMergeConfig cfg) throws Exception {
        DbMergeConfig.DatabaseConfig src = cfg.getSourceDatabase();

        try (Connection sourceConn = DriverManager.getConnection(
                src.getJdbcUrl(), src.getUsername(), src.getPassword())) {

            List<String> tables = discoverTables(sourceConn, "public");
            if (tables.isEmpty()) {
                logger.warn("No tables found in source schema 'public'");
                return;
            }

            for (DbMergeConfig.DatabaseConfig tgt : cfg.getTargetDatabases()) {
                try (Connection targetConn = DriverManager.getConnection(
                        tgt.getJdbcUrl(), tgt.getUsername(), tgt.getPassword())) {

                    targetConn.setAutoCommit(false);
                    try {
                        for (String table : tables) {
                            if (cfg.isDeleteTargetData()) {
                                truncateTable(targetConn, table);
                            }
                            copyTable(sourceConn, targetConn, table);
                        }

                        if (cfg.isCompareData()) {
                            for (String table : tables) {
                                verifyRowCount(sourceConn, targetConn, table);
                            }
                        }

                        targetConn.commit();
                        logger.info("Data transfer to target '{}' completed successfully", tgt.getName());
                    } catch (Exception e) {
                        targetConn.rollback();
                        throw new RuntimeException(
                                "Transfer to target '" + tgt.getName() + "' failed, rolled back: " + e.getMessage(), e);
                    }
                }
            }
        }
    }

    private List<String> discoverTables(Connection conn, String schema) throws SQLException {
        List<String> tables = new ArrayList<>();
        DatabaseMetaData meta = conn.getMetaData();
        try (ResultSet rs = meta.getTables(null, schema, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                tables.add(rs.getString("TABLE_NAME"));
            }
        }
        return tables;
    }

    private void truncateTable(Connection conn, String table) throws SQLException {
        String sql = "TRUNCATE TABLE public." + quoteIdentifier(table) + " CASCADE";
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
        logger.debug("Truncated table: {}", table);
    }

    private void copyTable(Connection sourceConn, Connection targetConn, String table) throws SQLException {
        String quotedTable = "public." + quoteIdentifier(table);

        List<String> columns = new ArrayList<>();
        try (ResultSet rs = sourceConn.getMetaData().getColumns(null, "public", table, "%")) {
            while (rs.next()) {
                columns.add(rs.getString("COLUMN_NAME"));
            }
        }
        if (columns.isEmpty()) {
            return;
        }

        String columnList = String.join(", ", columns.stream()
                .map(this::quoteIdentifier).toList());
        String placeholders = String.join(", ", columns.stream().map(c -> "?").toList());

        String selectSql = "SELECT " + columnList + " FROM " + quotedTable;
        String insertSql = "INSERT INTO " + quotedTable + " (" + columnList + ") VALUES (" + placeholders + ")";

        int batchSize = 1000;
        int count = 0;

        try (Statement selectStmt = sourceConn.createStatement();
             ResultSet rs = selectStmt.executeQuery(selectSql);
             PreparedStatement insertStmt = targetConn.prepareStatement(insertSql)) {

            int colCount = columns.size();
            while (rs.next()) {
                for (int i = 1; i <= colCount; i++) {
                    insertStmt.setObject(i, rs.getObject(i));
                }
                insertStmt.addBatch();
                count++;
                if (count % batchSize == 0) {
                    insertStmt.executeBatch();
                }
            }
            if (count % batchSize != 0) {
                insertStmt.executeBatch();
            }
        }
        logger.debug("Copied {} rows to table: {}", count, table);
    }

    private void verifyRowCount(Connection sourceConn, Connection targetConn, String table) throws SQLException {
        String quotedTable = "public." + quoteIdentifier(table);
        String countSql = "SELECT count(*) FROM " + quotedTable;

        int srcCount, tgtCount;
        try (Statement stmt = sourceConn.createStatement();
             ResultSet rs = stmt.executeQuery(countSql)) {
            rs.next();
            srcCount = rs.getInt(1);
        }
        try (Statement stmt = targetConn.createStatement();
             ResultSet rs = stmt.executeQuery(countSql)) {
            rs.next();
            tgtCount = rs.getInt(1);
        }

        if (srcCount != tgtCount) {
            throw new RuntimeException(
                    "Row count mismatch for " + table + ": source=" + srcCount + " target=" + tgtCount);
        }
    }

    private String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}
