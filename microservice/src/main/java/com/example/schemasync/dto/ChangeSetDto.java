package com.example.schemasync.dto;

public class ChangeSetDto {
    private String id;
    private String author;
    private String schema;
    private String table;
    private String type;
    private String description;

    public ChangeSetDto() {}

    public ChangeSetDto(String id, String author, String schema, String table, String type, String description) {
        this.id = id;
        this.author = author;
        this.schema = schema;
        this.table = table;
        this.type = type;
        this.description = description;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
    public String getSchema() { return schema; }
    public void setSchema(String schema) { this.schema = schema; }
    public String getTable() { return table; }
    public void setTable(String table) { this.table = table; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
} 