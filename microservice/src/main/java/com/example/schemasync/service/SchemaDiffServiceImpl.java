package com.example.schemasync.service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.schemasync.dto.ChangeSetDto;
import com.example.schemasync.dto.CreateDiffRequest;
import com.example.schemasync.dto.SchemaDiffDto;
import com.example.schemasync.entity.SchemaDiff;
import com.example.schemasync.exception.DiffNotFoundException;
import com.example.schemasync.exception.DiffOperationException;
import com.example.schemasync.exception.InvalidDiffStateException;
import com.example.schemasync.repository.SchemaDiffRepository;
import com.example.schemasync.service.interfaces.DiffValidationService;
import com.example.schemasync.service.interfaces.IDiffProcessingService;
import com.example.schemasync.service.interfaces.JenkinsIntegrationService;
import com.example.schemasync.service.interfaces.LiquibaseDiffService;
import com.example.schemasync.service.interfaces.SchemaDiffService;
import com.example.schemasync.utils.DiffXmlParser;
import com.example.schemasync.utils.JsonUtil;

@Service
public class SchemaDiffServiceImpl implements SchemaDiffService {

    private final SchemaDiffRepository repository;
    private final LiquibaseDiffService diffService;
    private final DiffValidationService validationService;
    private final JenkinsIntegrationService jenkinsService;
    private final IDiffProcessingService processingService;

    /** Target (production) database credentials — injected from application.yml / env vars. */
    @Value("${proddb.url}") private String targetDbUrl;
    @Value("${proddb.username}") private String targetDbUser;
    @Value("${proddb.password}") private String targetDbPass;

    public SchemaDiffServiceImpl(SchemaDiffRepository repository,
                                 LiquibaseDiffService diffService,
                                 DiffValidationService validationService,
                                 JenkinsIntegrationService jenkinsService,
                                 IDiffProcessingService processingService) {
        this.repository = repository;
        this.diffService = diffService;
        this.validationService = validationService;
        this.jenkinsService = jenkinsService;
        this.processingService = processingService;
    }

    @Override
    @Transactional
    public SchemaDiffDto createAndGenerateDiff(CreateDiffRequest request) {
        SchemaDiff diff = new SchemaDiff();
        diff.setAuthor(request.getAuthor());
        diff.setDescription(request.getDescription());
        diff.setCreatedAt(Instant.now());
        diff.setStatus(SchemaDiff.Status.PENDING);
        diff.setFilterJson(JsonUtil.toJson(request));
        repository.save(diff);

        // Delegate to separate bean so @Async proxy works correctly
        processingService.processAsync(diff.getId(), request);
        return new SchemaDiffDto(diff);
    }


    @Override
    public List<SchemaDiffDto> listDiffs(String status) {
        List<SchemaDiff> list;
        if (status != null) {
            try {
                SchemaDiff.Status st = SchemaDiff.Status.valueOf(status.toUpperCase());
                list = repository.findByStatus(st);
            } catch (IllegalArgumentException e) {
                list = repository.findAll();
            }
        } else {
            list = repository.findAll();
        }
        return list.stream().map(SchemaDiffDto::new).collect(Collectors.toList());
    }

    @Override
    public byte[] getDiffContent(Long id) {
        SchemaDiff diff = repository.findById(id)
                .orElseThrow(() -> new DiffNotFoundException(id));
        try {
            Path path = Path.of(diff.getDiffFilePath());
            return Files.readAllBytes(path);
        } catch (Exception e) {
            throw new DiffOperationException("Cannot read diff file for diff " + id, e);
        }
    }

    @Override
    public String getValidationLog(Long id) {
        SchemaDiff diff = repository.findById(id)
                .orElseThrow(() -> new DiffNotFoundException(id));
        return diff.getValidationLog();
    }

    @Override
    @Transactional
    public void approveDiff(Long id) {
        SchemaDiff diff = repository.findById(id)
                .orElseThrow(() -> new DiffNotFoundException(id));
        if (diff.getStatus() != SchemaDiff.Status.VALID) {
            throw new InvalidDiffStateException("Only VALID diffs can be approved; current status: " + diff.getStatus());
        }
        diff.setStatus(SchemaDiff.Status.APPROVED);
        diff.setApprovedAt(Instant.now());
        repository.save(diff);
        jenkinsService.triggerApplyJob(id);
    }

    @Override
    @Transactional
    public void rejectDiff(Long id, String reason) {
        SchemaDiff diff = repository.findById(id)
                .orElseThrow(() -> new DiffNotFoundException(id));
        diff.setStatus(SchemaDiff.Status.REJECTED);
        diff.setErrorMessage(reason);
        repository.save(diff);
    }

    @Override
    public SchemaDiffDto getDiffById(Long id) {
        SchemaDiff diff = repository.findById(id)
                .orElseThrow(() -> new DiffNotFoundException(id));
        return new SchemaDiffDto(diff);
    }

    @Override
    public List<ChangeSetDto> listChangeSets(Long diffId) {
        SchemaDiff diff = repository.findById(diffId)
                .orElseThrow(() -> new DiffNotFoundException(diffId));
        File changelogFile = new File(diff.getDiffFilePath());
        try {
            return diffService.listChangeSets(changelogFile);
        } catch (Exception e) {
            throw new DiffOperationException("Failed to list changesets for diff " + diffId, e);
        }
    }

    @Override
    public String applyFilteredChangeSets(Long diffId, List<String> selectedChangeSetIds, String dbType) {
        SchemaDiff diff = repository.findById(diffId)
                .orElseThrow(() -> new DiffNotFoundException(diffId));
        File changelogFile = new File(diff.getDiffFilePath());
        try {
            return diffService.applyFilteredChangeSets(changelogFile, selectedChangeSetIds, dbType, targetDbUrl, targetDbUser, targetDbPass);
        } catch (Exception e) {
            throw new DiffOperationException("Failed to apply filtered changesets for diff " + diffId, e);
        }
    }

    @Override
    public Map<String, Object> getParsedDiff(Long diffId) {
        SchemaDiff diff = repository.findById(diffId)
                .orElseThrow(() -> new DiffNotFoundException(diffId));
        File changelogFile = new File(diff.getDiffFilePath());
        try {
            return DiffXmlParser.parseDiff(changelogFile);
        } catch (Exception e) {
            throw new DiffOperationException("Failed to parse diff XML for diff " + diffId, e);
        }
    }

    @Override
    public String generateUpdateSQL(Long diffId, List<String> selectedChangeSetIds, String dbType) {
        SchemaDiff diff = repository.findById(diffId)
                .orElseThrow(() -> new DiffNotFoundException(diffId));
        File changelogFile = new File(diff.getDiffFilePath());
        try {
            return diffService.generateUpdateSQL(changelogFile, selectedChangeSetIds, dbType, targetDbUrl, targetDbUser, targetDbPass);
        } catch (Exception e) {
            throw new DiffOperationException("Failed to generate update SQL for diff " + diffId, e);
        }
    }

    @Override
    public String rollbackChangeSets(Long diffId, List<String> changeSetIds) {
        SchemaDiff diff = repository.findById(diffId)
                .orElseThrow(() -> new DiffNotFoundException(diffId));
        File changelogFile = new File(diff.getDiffFilePath());
        try {
            return diffService.rollbackChangeSets(changelogFile, changeSetIds, targetDbUrl, targetDbUser, targetDbPass);
        } catch (Exception e) {
            throw new DiffOperationException("Failed to rollback changesets for diff " + diffId, e);
        }
    }

    @Override
    public String generateRollbackSQL(Long diffId, List<String> changeSetIds) {
        SchemaDiff diff = repository.findById(diffId)
                .orElseThrow(() -> new DiffNotFoundException(diffId));
        File changelogFile = new File(diff.getDiffFilePath());
        try {
            return diffService.generateRollbackSQL(changelogFile, changeSetIds, targetDbUrl, targetDbUser, targetDbPass);
        } catch (Exception e) {
            throw new DiffOperationException("Failed to generate rollback SQL for diff " + diffId, e);
        }
    }
}