package com.example.schemasync.dto;

import com.example.schemasync.entity.SchemaDiff;
import java.time.Instant;

public class SchemaDiffDto {
    private Long id;
    private String author;
    private Instant createdAt;
    private SchemaDiff.Status status;
    private String description;

    private String filterJson;
    private Instant approvedAt;
    private Instant appliedAt;

    public SchemaDiffDto(SchemaDiff entity) {
        this.id = entity.getId();
        this.author = entity.getAuthor();
        this.createdAt = entity.getCreatedAt();
        this.status = entity.getStatus();
        this.description = entity.getDescription();
        this.filterJson = entity.getFilterJson();
        this.approvedAt = entity.getApprovedAt();
        this.appliedAt = entity.getAppliedAt();
    }

    public Long getId() {
        return id;
    }

    public String getAuthor() {
        return author;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public SchemaDiff.Status getStatus() {
        return status;
    }

    public String getDescription() {
        return description;
    }

    public String getFilterJson() {
        return filterJson;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public Instant getAppliedAt() {
        return appliedAt;
    }
}
