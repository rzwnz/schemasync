package com.example.schemasync.utils;

import org.w3c.dom.*;
import javax.xml.parsers.*;
import java.io.File;
import java.util.*;

public class DiffXmlParser {
    public static Map<String, Object> parseDiff(File xmlFile) throws Exception {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> tables = new ArrayList<>();
        List<Map<String, Object>> columns = new ArrayList<>();
        List<Map<String, Object>> constraints = new ArrayList<>();
        List<Map<String, Object>> indexes = new ArrayList<>();
        List<Map<String, Object>> views = new ArrayList<>();
        List<Map<String, Object>> schemas = new ArrayList<>();

        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(xmlFile);
        doc.getDocumentElement().normalize();

        // Parse table operations
        parseTableOperations(doc, tables);
        
        // Parse column operations
        parseColumnOperations(doc, columns);
        
        // Parse constraint operations
        parseConstraintOperations(doc, constraints);
        
        // Parse index operations
        parseIndexOperations(doc, indexes);
        
        // Parse view operations
        parseViewOperations(doc, views);
        
        // Parse schema operations
        parseSchemaOperations(doc, schemas);

        result.put("tables", tables);
        result.put("columns", columns);
        result.put("constraints", constraints);
        result.put("indexes", indexes);
        result.put("views", views);
        result.put("schemas", schemas);
        
        return result;
    }

    private static void parseTableOperations(Document doc, List<Map<String, Object>> tables) {
        // CREATE TABLE
        NodeList createTableList = doc.getElementsByTagName("createTable");
        for (int i = 0; i < createTableList.getLength(); i++) {
            Element tableElem = (Element) createTableList.item(i);
            String tableName = tableElem.getAttribute("tableName");
            String schemaName = tableElem.getAttribute("schemaName");
            List<String> columns = new ArrayList<>();
            NodeList columnList = tableElem.getElementsByTagName("column");
            for (int j = 0; j < columnList.getLength(); j++) {
                Element colElem = (Element) columnList.item(j);
                columns.add(colElem.getAttribute("name"));
            }
            Map<String, Object> table = new HashMap<>();
            table.put("action", "create");
            table.put("tableName", tableName);
            table.put("schemaName", schemaName);
            table.put("columns", columns);
            tables.add(table);
        }

        // DROP TABLE
        NodeList dropTableList = doc.getElementsByTagName("dropTable");
        for (int i = 0; i < dropTableList.getLength(); i++) {
            Element tableElem = (Element) dropTableList.item(i);
            String tableName = tableElem.getAttribute("tableName");
            String schemaName = tableElem.getAttribute("schemaName");
            Map<String, Object> table = new HashMap<>();
            table.put("action", "drop");
            table.put("tableName", tableName);
            table.put("schemaName", schemaName);
            tables.add(table);
        }
    }

    private static void parseColumnOperations(Document doc, List<Map<String, Object>> columns) {
        // ADD COLUMN
        NodeList addColumnList = doc.getElementsByTagName("addColumn");
        for (int i = 0; i < addColumnList.getLength(); i++) {
            Element colElem = (Element) addColumnList.item(i);
            String tableName = colElem.getAttribute("tableName");
            String schemaName = colElem.getAttribute("schemaName");
            NodeList columnList = colElem.getElementsByTagName("column");
            for (int j = 0; j < columnList.getLength(); j++) {
                Element column = (Element) columnList.item(j);
                Map<String, Object> col = new HashMap<>();
                col.put("action", "add");
                col.put("tableName", tableName);
                col.put("schemaName", schemaName);
                col.put("columnName", column.getAttribute("name"));
                col.put("type", column.getAttribute("type"));
                columns.add(col);
            }
        }

        // DROP COLUMN
        NodeList dropColumnList = doc.getElementsByTagName("dropColumn");
        for (int i = 0; i < dropColumnList.getLength(); i++) {
            Element colElem = (Element) dropColumnList.item(i);
            Map<String, Object> col = new HashMap<>();
            col.put("action", "drop");
            col.put("tableName", colElem.getAttribute("tableName"));
            col.put("schemaName", colElem.getAttribute("schemaName"));
            col.put("columnName", colElem.getAttribute("columnName"));
            columns.add(col);
        }

        // MODIFY COLUMN
        NodeList modifyColumnList = doc.getElementsByTagName("modifyDataType");
        for (int i = 0; i < modifyColumnList.getLength(); i++) {
            Element colElem = (Element) modifyColumnList.item(i);
            Map<String, Object> col = new HashMap<>();
            col.put("action", "modify");
            col.put("tableName", colElem.getAttribute("tableName"));
            col.put("schemaName", colElem.getAttribute("schemaName"));
            col.put("columnName", colElem.getAttribute("columnName"));
            col.put("newType", colElem.getAttribute("newDataType"));
            columns.add(col);
        }

        // RENAME COLUMN
        NodeList renameColumnList = doc.getElementsByTagName("renameColumn");
        for (int i = 0; i < renameColumnList.getLength(); i++) {
            Element colElem = (Element) renameColumnList.item(i);
            Map<String, Object> col = new HashMap<>();
            col.put("action", "rename");
            col.put("tableName", colElem.getAttribute("tableName"));
            col.put("schemaName", colElem.getAttribute("schemaName"));
            col.put("oldColumnName", colElem.getAttribute("oldColumnName"));
            col.put("newColumnName", colElem.getAttribute("newColumnName"));
            columns.add(col);
        }
    }

    private static void parseConstraintOperations(Document doc, List<Map<String, Object>> constraints) {
        // PRIMARY KEY operations
        NodeList addPkList = doc.getElementsByTagName("addPrimaryKey");
        for (int i = 0; i < addPkList.getLength(); i++) {
            Element pkElem = (Element) addPkList.item(i);
            Map<String, Object> constraint = new HashMap<>();
            constraint.put("action", "add");
            constraint.put("type", "primaryKey");
            constraint.put("tableName", pkElem.getAttribute("tableName"));
            constraint.put("schemaName", pkElem.getAttribute("schemaName"));
            constraint.put("constraintName", pkElem.getAttribute("constraintName"));
            constraint.put("columnNames", pkElem.getAttribute("columnNames"));
            constraints.add(constraint);
        }

        NodeList dropPkList = doc.getElementsByTagName("dropPrimaryKey");
        for (int i = 0; i < dropPkList.getLength(); i++) {
            Element pkElem = (Element) dropPkList.item(i);
            Map<String, Object> constraint = new HashMap<>();
            constraint.put("action", "drop");
            constraint.put("type", "primaryKey");
            constraint.put("tableName", pkElem.getAttribute("tableName"));
            constraint.put("schemaName", pkElem.getAttribute("schemaName"));
            constraints.add(constraint);
        }

        // UNIQUE CONSTRAINT operations
        NodeList addUniqueList = doc.getElementsByTagName("addUniqueConstraint");
        for (int i = 0; i < addUniqueList.getLength(); i++) {
            Element uniqueElem = (Element) addUniqueList.item(i);
            Map<String, Object> constraint = new HashMap<>();
            constraint.put("action", "add");
            constraint.put("type", "unique");
            constraint.put("tableName", uniqueElem.getAttribute("tableName"));
            constraint.put("schemaName", uniqueElem.getAttribute("schemaName"));
            constraint.put("constraintName", uniqueElem.getAttribute("constraintName"));
            constraint.put("columnNames", uniqueElem.getAttribute("columnNames"));
            constraints.add(constraint);
        }

        NodeList dropUniqueList = doc.getElementsByTagName("dropUniqueConstraint");
        for (int i = 0; i < dropUniqueList.getLength(); i++) {
            Element uniqueElem = (Element) dropUniqueList.item(i);
            Map<String, Object> constraint = new HashMap<>();
            constraint.put("action", "drop");
            constraint.put("type", "unique");
            constraint.put("tableName", uniqueElem.getAttribute("tableName"));
            constraint.put("schemaName", uniqueElem.getAttribute("schemaName"));
            constraint.put("constraintName", uniqueElem.getAttribute("constraintName"));
            constraints.add(constraint);
        }

        // FOREIGN KEY operations
        NodeList addFkList = doc.getElementsByTagName("addForeignKeyConstraint");
        for (int i = 0; i < addFkList.getLength(); i++) {
            Element fkElem = (Element) addFkList.item(i);
            Map<String, Object> constraint = new HashMap<>();
            constraint.put("action", "add");
            constraint.put("type", "foreignKey");
            constraint.put("tableName", fkElem.getAttribute("baseTableName"));
            constraint.put("schemaName", fkElem.getAttribute("baseTableSchemaName"));
            constraint.put("constraintName", fkElem.getAttribute("constraintName"));
            constraint.put("columnNames", fkElem.getAttribute("baseColumnNames"));
            constraint.put("referencedTable", fkElem.getAttribute("referencedTableName"));
            constraint.put("referencedColumns", fkElem.getAttribute("referencedColumnNames"));
            constraints.add(constraint);
        }

        NodeList dropFkList = doc.getElementsByTagName("dropForeignKeyConstraint");
        for (int i = 0; i < dropFkList.getLength(); i++) {
            Element fkElem = (Element) dropFkList.item(i);
            Map<String, Object> constraint = new HashMap<>();
            constraint.put("action", "drop");
            constraint.put("type", "foreignKey");
            constraint.put("tableName", fkElem.getAttribute("baseTableName"));
            constraint.put("schemaName", fkElem.getAttribute("baseTableSchemaName"));
            constraint.put("constraintName", fkElem.getAttribute("constraintName"));
            constraints.add(constraint);
        }

        // CHECK CONSTRAINT operations
        NodeList addCheckList = doc.getElementsByTagName("addCheckConstraint");
        for (int i = 0; i < addCheckList.getLength(); i++) {
            Element checkElem = (Element) addCheckList.item(i);
            Map<String, Object> constraint = new HashMap<>();
            constraint.put("action", "add");
            constraint.put("type", "check");
            constraint.put("tableName", checkElem.getAttribute("tableName"));
            constraint.put("schemaName", checkElem.getAttribute("schemaName"));
            constraint.put("constraintName", checkElem.getAttribute("constraintName"));
            constraint.put("constraintText", checkElem.getAttribute("constraintText"));
            constraints.add(constraint);
        }

        NodeList dropCheckList = doc.getElementsByTagName("dropCheckConstraint");
        for (int i = 0; i < dropCheckList.getLength(); i++) {
            Element checkElem = (Element) dropCheckList.item(i);
            Map<String, Object> constraint = new HashMap<>();
            constraint.put("action", "drop");
            constraint.put("type", "check");
            constraint.put("tableName", checkElem.getAttribute("tableName"));
            constraint.put("schemaName", checkElem.getAttribute("schemaName"));
            constraint.put("constraintName", checkElem.getAttribute("constraintName"));
            constraints.add(constraint);
        }
    }

    private static void parseIndexOperations(Document doc, List<Map<String, Object>> indexes) {
        // CREATE INDEX
        NodeList createIndexList = doc.getElementsByTagName("createIndex");
        for (int i = 0; i < createIndexList.getLength(); i++) {
            Element indexElem = (Element) createIndexList.item(i);
            Map<String, Object> index = new HashMap<>();
            index.put("action", "create");
            index.put("indexName", indexElem.getAttribute("indexName"));
            index.put("tableName", indexElem.getAttribute("tableName"));
            index.put("schemaName", indexElem.getAttribute("schemaName"));
            index.put("columnNames", indexElem.getAttribute("columnNames"));
            indexes.add(index);
        }

        // DROP INDEX
        NodeList dropIndexList = doc.getElementsByTagName("dropIndex");
        for (int i = 0; i < dropIndexList.getLength(); i++) {
            Element indexElem = (Element) dropIndexList.item(i);
            Map<String, Object> index = new HashMap<>();
            index.put("action", "drop");
            index.put("indexName", indexElem.getAttribute("indexName"));
            index.put("tableName", indexElem.getAttribute("tableName"));
            index.put("schemaName", indexElem.getAttribute("schemaName"));
            indexes.add(index);
        }
    }

    private static void parseViewOperations(Document doc, List<Map<String, Object>> views) {
        // CREATE VIEW
        NodeList createViewList = doc.getElementsByTagName("createView");
        for (int i = 0; i < createViewList.getLength(); i++) {
            Element viewElem = (Element) createViewList.item(i);
            Map<String, Object> view = new HashMap<>();
            view.put("action", "create");
            view.put("viewName", viewElem.getAttribute("viewName"));
            view.put("schemaName", viewElem.getAttribute("schemaName"));
            view.put("selectQuery", viewElem.getTextContent());
            views.add(view);
        }

        // DROP VIEW
        NodeList dropViewList = doc.getElementsByTagName("dropView");
        for (int i = 0; i < dropViewList.getLength(); i++) {
            Element viewElem = (Element) dropViewList.item(i);
            Map<String, Object> view = new HashMap<>();
            view.put("action", "drop");
            view.put("viewName", viewElem.getAttribute("viewName"));
            view.put("schemaName", viewElem.getAttribute("schemaName"));
            views.add(view);
        }
    }

    private static void parseSchemaOperations(Document doc, List<Map<String, Object>> schemas) {
        // CREATE SCHEMA
        NodeList createSchemaList = doc.getElementsByTagName("createSchema");
        for (int i = 0; i < createSchemaList.getLength(); i++) {
            Element schemaElem = (Element) createSchemaList.item(i);
            Map<String, Object> schema = new HashMap<>();
            schema.put("action", "create");
            schema.put("schemaName", schemaElem.getAttribute("schemaName"));
            schemas.add(schema);
        }

        // DROP SCHEMA
        NodeList dropSchemaList = doc.getElementsByTagName("dropSchema");
        for (int i = 0; i < dropSchemaList.getLength(); i++) {
            Element schemaElem = (Element) dropSchemaList.item(i);
            Map<String, Object> schema = new HashMap<>();
            schema.put("action", "drop");
            schema.put("schemaName", schemaElem.getAttribute("schemaName"));
            schemas.add(schema);
        }
    }
} 