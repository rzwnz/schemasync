package com.example.schemasync.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.schemasync.dto.DbMergeConfig;
import com.example.schemasync.service.interfaces.IChangelogJsonService;
import com.example.schemasync.service.interfaces.IChangelogValidationService;
import com.example.schemasync.service.interfaces.IDataTransferService;
import com.example.schemasync.utils.JdbcUtil;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import liquibase.exception.LiquibaseException;

@RestController
@RequestMapping("/api/merge")
@Tag(name = "Merge & Validation", description = "Validate merge configs, changelog files, database connections, and transfer data")
public class MergeController {

    private final IChangelogValidationService changelogValidator;
    private final IDataTransferService transferService;
    private final IChangelogJsonService jsonService;

    public MergeController(IChangelogValidationService changelogValidator,
                           IDataTransferService transferService,
                           IChangelogJsonService jsonService) {
        this.changelogValidator = changelogValidator;
        this.transferService = transferService;
        this.jsonService = jsonService;
    }

    @PostMapping("/validate-config")
    @Operation(summary = "Validate merge config", description = "Checks that required fields (changeLogFile, sourceDatabase, targetDatabases) are present")
    public ResponseEntity<String> validateMergeConfig(@Valid @RequestBody DbMergeConfig config) {
        if (config.getChangeLogFile() == null || config.getSourceDatabase() == null
                || config.getTargetDatabases() == null || config.getTargetDatabases().isEmpty()) {
            return ResponseEntity.badRequest().body("Missing required fields");
        }
        return ResponseEntity.ok("OK");
    }

    @PostMapping("/validate-changelog")
    @Operation(summary = "Validate changelog file", description = "Runs offline Liquibase validation on the specified changelog XML file")
    public ResponseEntity<String> validateChangelog(@RequestBody Map<String, String> body) {
        String path = body.get("changeLogFile");
        try {
            String result = changelogValidator.validateChangelog(path);
            return ResponseEntity.ok(result);
        } catch (LiquibaseException e) {
            return ResponseEntity.status(400).body("Invalid changelog: " + e.getMessage());
        }
    }

    @PostMapping("/validate-source")
    @Operation(summary = "Validate source DB", description = "Tests JDBC connectivity to the source database")
    public ResponseEntity<String> validateSource(@Valid @RequestBody DbMergeConfig config) {
        try {
            JdbcUtil.validateConnection(
                    config.getSourceDatabase().getJdbcUrl(),
                    config.getSourceDatabase().getUsername(),
                    config.getSourceDatabase().getPassword()
            );
            return ResponseEntity.ok("Source OK");
        } catch (Exception e) {
            return ResponseEntity.status(400).body("Source failed: " + e.getMessage());
        }
    }

    @PostMapping("/validate-target")
    @Operation(summary = "Validate target DBs", description = "Tests JDBC connectivity to all configured target databases")
    public ResponseEntity<String> validateTarget(@Valid @RequestBody DbMergeConfig config) {
        List<String> results = new ArrayList<>();
        for (var t : config.getTargetDatabases()) {
            try {
                JdbcUtil.validateConnection(t.getJdbcUrl(), t.getUsername(), t.getPassword());
                results.add(t.getName() + ": OK");
            } catch (Exception e) {
                results.add(t.getName() + ": FAILED - " + e.getMessage());
            }
        }
        return ResponseEntity.ok(String.join("\n", results));
    }

    @PostMapping("/transfer-data")
    @Operation(summary = "Transfer data", description = "Copies data from source database to all target databases via JDBC")
    public ResponseEntity<String> transferData(@Valid @RequestBody DbMergeConfig cfg) {
        try {
            transferService.transfer(cfg);
            return ResponseEntity.ok("Data transferred");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Transfer failed: " + e.getMessage());
        }
    }

    @PostMapping("/changelog-to-json")
    @Operation(summary = "Convert changelog to JSON", description = "Converts a Liquibase changelog XML file to a JSON representation")
    public ResponseEntity<String> changelogToJson(@RequestBody Map<String, String> body) {
        try {
            return ResponseEntity.ok(jsonService.changelogToJson(body.get("changeLogFile")));
        } catch (Exception e) {
            return ResponseEntity.status(400).body("Error: " + e.getMessage());
        }
    }
}
