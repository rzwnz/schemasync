package com.example.schemasync.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;

public class CreateDiffRequest {
    @NotBlank(message = "Author is required")
    private String author;
    private String description;
    private List<String> includeSchemas;
    private List<String> excludeSchemas;

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    } 

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<String> getIncludeSchemas() {
        return includeSchemas;
    }

    public void setIncludeSchemas(List<String> includeSchemas) {
        this.includeSchemas = includeSchemas;
    }

    public List<String> getExcludeSchemas() {
        return excludeSchemas;
    }

    public void setExcludeSchemas(List<String> excludeSchemas) {
        this.excludeSchemas = excludeSchemas;
    }
}
