package com.example.schemasync.controller;

import java.util.List;
import java.util.Map;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.schemasync.dto.ChangeSetDto;
import com.example.schemasync.dto.CreateDiffRequest;
import com.example.schemasync.dto.SchemaDiffDto;
import com.example.schemasync.service.interfaces.JenkinsIntegrationService;
import com.example.schemasync.service.interfaces.SchemaDiffService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

@RestController
@RequestMapping("/api/diffs")
@Tag(name = "Schema Diffs", description = "Generate, validate, approve, and apply database schema diffs")
public class SchemaDiffController {

    private final SchemaDiffService diffService;
    private final JenkinsIntegrationService jenkinsService;

    public SchemaDiffController(SchemaDiffService diffService, JenkinsIntegrationService jenkinsService) {
        this.diffService = diffService;
        this.jenkinsService = jenkinsService;
    }

    @PostMapping
    @Operation(summary = "Create a new diff", description = "Triggers async diff generation between test and prod databases, followed by automatic validation")
    public ResponseEntity<SchemaDiffDto> createDiff(@Valid @RequestBody CreateDiffRequest req) {
        SchemaDiffDto dto = diffService.createAndGenerateDiff(req);
        return ResponseEntity.ok(dto);
    }

    @GetMapping
    @Operation(summary = "List all diffs", description = "Returns all diffs, optionally filtered by status (PENDING, VALIDATING, VALID, INVALID, APPROVED, APPLYING, APPLIED, REJECTED)")
    public ResponseEntity<List<SchemaDiffDto>> listDiffs(
            @Parameter(description = "Filter by status") @RequestParam(required = false) String status) {
        return ResponseEntity.ok(diffService.listDiffs(status));
    }

    @GetMapping("/{id}/content")
    @Operation(summary = "Download diff XML", description = "Downloads the raw Liquibase changelog XML file for this diff")
    public ResponseEntity<ByteArrayResource> getDiffContent(@PathVariable Long id) {
        byte[] data = diffService.getDiffContent(id);
        ByteArrayResource resource = new ByteArrayResource(data);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"schema-diff-" + id + ".xml\"")
                .body(resource);
    }

    @GetMapping("/{id}/validation-log")
    @Operation(summary = "Get validation log", description = "Returns the validation log text produced during diff validation")
    public ResponseEntity<String> getValidationLog(@PathVariable Long id) {
        return ResponseEntity.ok(diffService.getValidationLog(id));
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve a diff", description = "Approves a VALID diff and triggers the configured Jenkins apply job")
    public ResponseEntity<Void> approve(@PathVariable Long id) {
        diffService.approveDiff(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get diff by ID", description = "Returns the diff metadata for a specific diff ID")
    public ResponseEntity<SchemaDiffDto> getDiffById(@PathVariable Long id) {
        SchemaDiffDto dto = diffService.getDiffById(id);
        return ResponseEntity.ok(dto);
    }


    @PostMapping("/{id}/reject")
    @Operation(summary = "Reject a diff", description = "Rejects a diff with a reason. Status changes to REJECTED")
    public ResponseEntity<Void> reject(@PathVariable Long id, @RequestBody String reason) {
        diffService.rejectDiff(id, reason);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/changesets")
    @Operation(summary = "List changesets", description = "Parses the diff XML and returns individual changesets with metadata")
    public ResponseEntity<List<ChangeSetDto>> listChangeSets(@PathVariable Long id) {
        return ResponseEntity.ok(diffService.listChangeSets(id));
    }

    @GetMapping("/{id}/parsed")
    @Operation(summary = "Get parsed diff", description = "Returns a structured representation of the diff (tables, columns, constraints, indexes, views)")
    public ResponseEntity<Map<String, Object>> getParsedDiff(@PathVariable Long id) {
        Map<String, Object> parsed = diffService.getParsedDiff(id);
        return ResponseEntity.ok(parsed);
    }

    public static class ApplyFilteredRequest {
        @NotEmpty(message = "selectedChangeSetIds must not be empty")
        public java.util.List<String> selectedChangeSetIds;
        @NotBlank(message = "dbType is required")
        public String dbType;
    }

    public static class RollbackRequest {
        @NotEmpty(message = "changeSetIds must not be empty")
        public java.util.List<String> changeSetIds;
    }

    @PostMapping("/{id}/apply-filtered")
    @Operation(summary = "Apply selected changesets", description = "Applies only the selected changesets (by id:author) to the configured target database")
    public ResponseEntity<String> applyFilteredChangeSets(@PathVariable Long id, @Valid @RequestBody ApplyFilteredRequest req) {
        String result = diffService.applyFilteredChangeSets(id, req.selectedChangeSetIds, req.dbType);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{id}/update-sql")
    @Operation(summary = "Preview SQL (dry-run)", description = "Generates the SQL that would be executed for selected changesets without actually applying them")
    public ResponseEntity<String> generateUpdateSQL(@PathVariable Long id, @Valid @RequestBody ApplyFilteredRequest req) {
        String sql = diffService.generateUpdateSQL(id, req.selectedChangeSetIds, req.dbType);
        return ResponseEntity.ok(sql);
    }

    @PostMapping("/{id}/rollback")
    @Operation(summary = "Rollback changesets", description = "Rolls back specific previously-applied changesets from the target database in reverse order")
    public ResponseEntity<String> rollbackChangeSets(@PathVariable Long id, @Valid @RequestBody RollbackRequest req) {
        String result = diffService.rollbackChangeSets(id, req.changeSetIds);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{id}/rollback-sql")
    @Operation(summary = "Preview rollback SQL", description = "Generates rollback SQL for specific changesets without executing it (dry-run)")
    public ResponseEntity<String> generateRollbackSQL(@PathVariable Long id, @Valid @RequestBody RollbackRequest req) {
        String sql = diffService.generateRollbackSQL(id, req.changeSetIds);
        return ResponseEntity.ok(sql);
    }

    @GetMapping("/jenkins/status")
    @Operation(summary = "Jenkins build status", description = "Returns the latest Jenkins build status for the configured schema-apply job")
    public ResponseEntity<String> getJenkinsBuildStatus() {
        return ResponseEntity.ok(jenkinsService.getLastBuildStatus());
    }

    @GetMapping("/health")
    @Operation(summary = "Health check", description = "Returns UP if the service is running")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP"));
    }
}