package com.example.schemasync.controller;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.example.schemasync.dto.DbMergeConfig;
import com.example.schemasync.service.interfaces.IChangelogJsonService;
import com.example.schemasync.service.interfaces.IChangelogValidationService;
import com.example.schemasync.service.interfaces.IDataTransferService;

import liquibase.exception.LiquibaseException;

/**
 * Unit tests for {@link MergeController}.
 * Validates all 6 endpoints with mocked service dependencies.
 */
@ExtendWith(MockitoExtension.class)
class MergeControllerTest {

    @Mock
    private IChangelogValidationService changelogValidator;

    @Mock
    private IDataTransferService transferService;

    @Mock
    private IChangelogJsonService jsonService;

    @InjectMocks
    private MergeController controller;

    private DbMergeConfig validConfig;

    @BeforeEach
    void setUp() {
        var source = new DbMergeConfig.DatabaseConfig();
        source.setName("source");
        source.setJdbcUrl("jdbc:postgresql://localhost/src");
        source.setUsername("user");
        source.setPassword("pass");

        var target = new DbMergeConfig.DatabaseConfig();
        target.setName("target");
        target.setJdbcUrl("jdbc:postgresql://localhost/tgt");
        target.setUsername("user");
        target.setPassword("pass");

        validConfig = new DbMergeConfig();
        validConfig.setChangeLogFile("/path/to/changelog.xml");
        validConfig.setSourceDatabase(source);
        validConfig.setTargetDatabases(List.of(target));
    }

    // ── /validate-config ──────────────────────────────────────

    @Test
    void validateMergeConfig_validConfig_returnsOk() {
        ResponseEntity<String> response = controller.validateMergeConfig(validConfig);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("OK");
    }

    @Test
    void validateMergeConfig_missingChangeLogFile_returnsBadRequest() {
        validConfig.setChangeLogFile(null);

        ResponseEntity<String> response = controller.validateMergeConfig(validConfig);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("Missing required fields");
    }

    @Test
    void validateMergeConfig_missingSourceDatabase_returnsBadRequest() {
        validConfig.setSourceDatabase(null);

        ResponseEntity<String> response = controller.validateMergeConfig(validConfig);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void validateMergeConfig_emptyTargetDatabases_returnsBadRequest() {
        validConfig.setTargetDatabases(List.of());

        ResponseEntity<String> response = controller.validateMergeConfig(validConfig);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ── /validate-changelog ───────────────────────────────────

    @Test
    void validateChangelog_valid_returnsOk() throws LiquibaseException {
        when(changelogValidator.validateChangelog("/changelog.xml")).thenReturn("Valid");

        ResponseEntity<String> response = controller.validateChangelog(
                Map.of("changeLogFile", "/changelog.xml"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("Valid");
    }

    @Test
    void validateChangelog_invalid_returnsBadRequest() throws LiquibaseException {
        when(changelogValidator.validateChangelog("/bad.xml"))
                .thenThrow(new LiquibaseException("Parse error"));

        ResponseEntity<String> response = controller.validateChangelog(
                Map.of("changeLogFile", "/bad.xml"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("Invalid changelog");
    }

    // ── /transfer-data ────────────────────────────────────────

    @Test
    void transferData_success_returnsOk() throws Exception {
        doNothing().when(transferService).transfer(any(DbMergeConfig.class));

        ResponseEntity<String> response = controller.transferData(validConfig);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("Data transferred");
        verify(transferService).transfer(validConfig);
    }

    @Test
    void transferData_failure_returns500() throws Exception {
        doThrow(new RuntimeException("Connection refused"))
                .when(transferService).transfer(any(DbMergeConfig.class));

        ResponseEntity<String> response = controller.transferData(validConfig);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).contains("Transfer failed");
    }

    // ── /changelog-to-json ────────────────────────────────────

    @Test
    void changelogToJson_success_returnsJsonString() throws Exception {
        when(jsonService.changelogToJson("/path.xml")).thenReturn("{\"changeSets\":[]}");

        ResponseEntity<String> response = controller.changelogToJson(
                Map.of("changeLogFile", "/path.xml"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("changeSets");
    }

    @Test
    void changelogToJson_error_returnsBadRequest() throws Exception {
        when(jsonService.changelogToJson("/bad.xml"))
                .thenThrow(new RuntimeException("File not found"));

        ResponseEntity<String> response = controller.changelogToJson(
                Map.of("changeLogFile", "/bad.xml"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("Error");
    }
}
