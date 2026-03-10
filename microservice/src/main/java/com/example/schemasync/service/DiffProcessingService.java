package com.example.schemasync.service;

import java.io.File;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.example.schemasync.dto.CreateDiffRequest;
import com.example.schemasync.entity.SchemaDiff;
import com.example.schemasync.repository.SchemaDiffRepository;
import com.example.schemasync.service.interfaces.DiffValidationService;
import com.example.schemasync.service.interfaces.IDiffProcessingService;
import com.example.schemasync.service.interfaces.LiquibaseDiffService;

import liquibase.exception.LiquibaseException;

/**
 * Extracted async processing into a separate bean so that Spring's @Async proxy
 * works correctly (calling @Async on the same class bypasses the proxy).
 */
@Service
public class DiffProcessingService implements IDiffProcessingService {

    private static final Logger logger = LoggerFactory.getLogger(DiffProcessingService.class);

    private final SchemaDiffRepository repository;
    private final LiquibaseDiffService diffService;
    private final DiffValidationService validationService;

    private final String testDbUrl;
    private final String testDbUser;
    private final String testDbPass;
    private final String prodDbUrl;
    private final String prodDbUser;
    private final String prodDbPass;

    public DiffProcessingService(SchemaDiffRepository repository,
                                 LiquibaseDiffService diffService,
                                 DiffValidationService validationService,
                                 @org.springframework.beans.factory.annotation.Value("${testdb.url}") String testDbUrl,
                                 @org.springframework.beans.factory.annotation.Value("${testdb.username}") String testDbUser,
                                 @org.springframework.beans.factory.annotation.Value("${testdb.password}") String testDbPass,
                                 @org.springframework.beans.factory.annotation.Value("${proddb.url}") String prodDbUrl,
                                 @org.springframework.beans.factory.annotation.Value("${proddb.username}") String prodDbUser,
                                 @org.springframework.beans.factory.annotation.Value("${proddb.password}") String prodDbPass) {
        this.repository = repository;
        this.diffService = diffService;
        this.validationService = validationService;
        this.testDbUrl = testDbUrl;
        this.testDbUser = testDbUser;
        this.testDbPass = testDbPass;
        this.prodDbUrl = prodDbUrl;
        this.prodDbUser = prodDbUser;
        this.prodDbPass = prodDbPass;
    }

    @Async("taskExecutor")
    @Override
    public void processAsync(Long diffId, CreateDiffRequest request) {
        Optional<SchemaDiff> opt = repository.findById(diffId);
        if (opt.isEmpty()) {
            logger.warn("processAsync called for non-existent diffId={}, skipping", diffId);
            return;
        }
        SchemaDiff diff = opt.get();
        diff.setStatus(SchemaDiff.Status.VALIDATING);
        repository.save(diff);

        try {
            File changelog = diffService.generateDiff(
                    diffId,
                    prodDbUrl, prodDbUser, prodDbPass,
                    testDbUrl, testDbUser, testDbPass,
                    request.getIncludeSchemas(),
                    request.getExcludeSchemas()
            );
            diff.setDiffFilePath(changelog.getAbsolutePath());

            ValidationResult vr = validationService.validateDiff(changelog.toPath());
            diff.setValidationLog(vr.getLog());
            if (vr.isSuccess()) {
                diff.setStatus(SchemaDiff.Status.VALID);
            } else {
                diff.setStatus(SchemaDiff.Status.INVALID);
                diff.setErrorMessage(vr.getErrorMessage());
            }
        } catch (LiquibaseException e) {
            logger.error("Diff generation failed for diffId={}: {}", diffId, e.getMessage(), e);
            diff.setStatus(SchemaDiff.Status.INVALID);
            diff.setErrorMessage(e.getMessage());
        }
        repository.save(diff);
    }
}
