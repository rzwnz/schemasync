package com.example.schemasync.service;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.schemasync.dto.CreateDiffRequest;
import com.example.schemasync.entity.SchemaDiff;
import com.example.schemasync.repository.SchemaDiffRepository;
import com.example.schemasync.service.interfaces.DiffValidationService;
import com.example.schemasync.service.interfaces.LiquibaseDiffService;

import liquibase.exception.LiquibaseException;

/**
 * Unit tests for {@link DiffProcessingService}.
 */
@ExtendWith(MockitoExtension.class)
class DiffProcessingServiceTest {

    @Mock
    private SchemaDiffRepository repository;

    @Mock
    private LiquibaseDiffService diffService;

    @Mock
    private DiffValidationService validationService;

    private DiffProcessingService service;

    private SchemaDiff diff;
    private CreateDiffRequest request;

    @BeforeEach
    void setUp() {
        service = new DiffProcessingService(
                repository, diffService, validationService,
                "jdbc:postgresql://test:5432/test", "testUser", "testPass",
                "jdbc:postgresql://prod:5432/prod", "prodUser", "prodPass"
        );

        diff = new SchemaDiff();
        diff.setId(1L);
        diff.setStatus(SchemaDiff.Status.PENDING);

        request = new CreateDiffRequest();
        request.setIncludeSchemas(List.of("public"));
        request.setExcludeSchemas(List.of());
    }

    @Test
    void processAsync_diffNotFound_skips() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        service.processAsync(99L, request);

        verify(repository, never()).save(any());
    }

    @Test
    void processAsync_validDiff_setsStatusValid() throws LiquibaseException {
        when(repository.findById(1L)).thenReturn(Optional.of(diff));

        File changelogFile = new File("/tmp/diff-1.xml");
        when(diffService.generateDiff(
                eq(1L), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(),
                eq(List.of("public")), eq(List.of())
        )).thenReturn(changelogFile);

        when(validationService.validateDiff(changelogFile.toPath()))
                .thenReturn(ValidationResult.success("All good"));

        // Capture statuses at the exact moment save() is called (same mutable object)
        List<SchemaDiff.Status> savedStatuses = new java.util.ArrayList<>();
        when(repository.save(any(SchemaDiff.class))).thenAnswer(inv -> {
            SchemaDiff d = inv.getArgument(0);
            savedStatuses.add(d.getStatus());
            return d;
        });

        service.processAsync(1L, request);

        assertThat(savedStatuses).hasSize(2);
        assertThat(savedStatuses.get(0)).isEqualTo(SchemaDiff.Status.VALIDATING);
        assertThat(savedStatuses.get(1)).isEqualTo(SchemaDiff.Status.VALID);
        assertThat(diff.getDiffFilePath()).isEqualTo(changelogFile.getAbsolutePath());
        assertThat(diff.getValidationLog()).isEqualTo("All good");
    }

    @Test
    void processAsync_invalidDiff_setsStatusInvalid() throws LiquibaseException {
        when(repository.findById(1L)).thenReturn(Optional.of(diff));

        File changelogFile = new File("/tmp/diff-1.xml");
        when(diffService.generateDiff(
                eq(1L), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(),
                any(), any()
        )).thenReturn(changelogFile);

        when(validationService.validateDiff(any(Path.class)))
                .thenReturn(ValidationResult.failure("Incompatible changeset"));

        service.processAsync(1L, request);

        ArgumentCaptor<SchemaDiff> captor = ArgumentCaptor.forClass(SchemaDiff.class);
        verify(repository, times(2)).save(captor.capture());

        SchemaDiff secondSave = captor.getAllValues().get(1);
        assertThat(secondSave.getStatus()).isEqualTo(SchemaDiff.Status.INVALID);
        assertThat(secondSave.getErrorMessage()).contains("Incompatible");
    }

    @Test
    void processAsync_liquibaseException_setsInvalidWithMessage() throws LiquibaseException {
        when(repository.findById(1L)).thenReturn(Optional.of(diff));

        when(diffService.generateDiff(
                eq(1L), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(),
                any(), any()
        )).thenThrow(new LiquibaseException("Connection refused"));

        service.processAsync(1L, request);

        ArgumentCaptor<SchemaDiff> captor = ArgumentCaptor.forClass(SchemaDiff.class);
        verify(repository, times(2)).save(captor.capture());

        SchemaDiff secondSave = captor.getAllValues().get(1);
        assertThat(secondSave.getStatus()).isEqualTo(SchemaDiff.Status.INVALID);
        assertThat(secondSave.getErrorMessage()).isEqualTo("Connection refused");
    }
}
