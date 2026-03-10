package com.example.schemasync.service.interfaces;

import java.util.List;
import java.util.Map;

import com.example.schemasync.dto.ChangeSetDto;
import com.example.schemasync.dto.CreateDiffRequest;
import com.example.schemasync.dto.SchemaDiffDto;

public interface SchemaDiffService {
    SchemaDiffDto createAndGenerateDiff(CreateDiffRequest request);
    List<SchemaDiffDto> listDiffs(String status);
    byte[] getDiffContent(Long id);
    String getValidationLog(Long id);
    void approveDiff(Long id);
    void rejectDiff(Long id, String reason);
    SchemaDiffDto getDiffById(Long id);
    List<ChangeSetDto> listChangeSets(Long diffId);
    String applyFilteredChangeSets(Long diffId, List<String> selectedChangeSetIds, String dbType);
    Map<String, Object> getParsedDiff(Long diffId);

    /** Generate SQL preview (dry-run) for selected changesets without applying. */
    String generateUpdateSQL(Long diffId, List<String> selectedChangeSetIds, String dbType);

    /** Roll back specific previously-applied changesets from the target database. */
    String rollbackChangeSets(Long diffId, List<String> changeSetIds);

    /** Generate rollback SQL preview without executing. */
    String generateRollbackSQL(Long diffId, List<String> changeSetIds);
}