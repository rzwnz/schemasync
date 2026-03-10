package com.example.schemasync.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "schema_diff")
public class SchemaDiff {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String author;

    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    private Status status;

    private String description;

    /**
     * JSON string encoding filter parameters, e.g.:
     * {
     *   "includeSchemas": ["public"],
     *   "excludeSchemas": ["audit"]
     * }
     */
    @Column(columnDefinition = "TEXT")
    private String filterJson;

    /**
     * Path to stored diff file within mounted volume, e.g. "/diff-store/{id}/schema-diff.xml"
     */
    private String diffFilePath;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column(columnDefinition = "TEXT")
    private String validationLog;

    private Instant approvedAt;
    private Instant appliedAt;

    public enum Status {
        PENDING,        // created but not yet processed
        VALIDATING,     // diff generation/validation in progress
        VALID,          // diff generated and validated OK
        INVALID,        // diff generation or validation failed
        APPROVED,       // approved for apply
        APPLYING,       // application in progress
        APPLIED,        // applied successfully
        REJECTED        // explicitly rejected
    }

    // Getters and setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getFilterJson() {
        return filterJson;
    }

    public void setFilterJson(String filterJson) {
        this.filterJson = filterJson;
    }

    public String getDiffFilePath() {
        return diffFilePath;
    }

    public void setDiffFilePath(String diffFilePath) {
        this.diffFilePath = diffFilePath;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getValidationLog() {
        return validationLog;
    }

    public void setValidationLog(String validationLog) {
        this.validationLog = validationLog;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(Instant approvedAt) {
        this.approvedAt = approvedAt;
    }

    public Instant getAppliedAt() {
        return appliedAt;
    }

    public void setAppliedAt(Instant appliedAt) {
        this.appliedAt = appliedAt;
    }
}
