package com.example.schemasync.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.schemasync.dto.ChangeSetDto;
import com.example.schemasync.dto.CreateDiffRequest;
import com.example.schemasync.dto.SchemaDiffDto;
import com.example.schemasync.entity.SchemaDiff;
import com.example.schemasync.exception.DiffNotFoundException;
import com.example.schemasync.exception.DiffOperationException;
import com.example.schemasync.exception.InvalidDiffStateException;
import com.example.schemasync.repository.SchemaDiffRepository;
import com.example.schemasync.service.interfaces.DiffValidationService;
import com.example.schemasync.service.interfaces.JenkinsIntegrationService;
import com.example.schemasync.service.interfaces.LiquibaseDiffService;

import liquibase.exception.LiquibaseException;

@ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
class SchemaDiffServiceImplTest {
    @Mock private SchemaDiffRepository repository;
    @Mock private LiquibaseDiffService diffService;
    @Mock private DiffValidationService validationService;
    @Mock private JenkinsIntegrationService jenkinsService;
    @Mock private DiffProcessingService processingService;
    @InjectMocks private SchemaDiffServiceImpl service;

    @BeforeEach
    void setup() {
        // Set @Value fields that are used by the service for target DB credentials
        ReflectionTestUtils.setField(service, "targetDbUrl", "jdbc:postgresql://localhost/test");
        ReflectionTestUtils.setField(service, "targetDbUser", "test_user");
        ReflectionTestUtils.setField(service, "targetDbPass", "test_pass");
    }

    @Test
    void createAndGenerateDiff_savesAndReturnsDto() {
        CreateDiffRequest request = mock(CreateDiffRequest.class);
        when(request.getAuthor()).thenReturn("author");
        when(request.getDescription()).thenReturn("desc");
        SchemaDiff diff = new SchemaDiff();
        diff.setId(1L);
        when(repository.save(any())).thenReturn(diff);
        SchemaDiffDto dto = service.createAndGenerateDiff(request);
        assertThat(dto).isNotNull();
        verify(repository, atLeastOnce()).save(any());
    }

    @Test
    void listDiffs_withStatus() {
        SchemaDiff diff = new SchemaDiff();
        diff.setStatus(SchemaDiff.Status.PENDING);
        when(repository.findByStatus(SchemaDiff.Status.PENDING)).thenReturn(List.of(diff));
        List<SchemaDiffDto> result = service.listDiffs("PENDING");
        assertThat(result).hasSize(1);
    }

    @Test
    void listDiffs_invalidStatus_returnsAll() {
        SchemaDiff diff = new SchemaDiff();
        when(repository.findAll()).thenReturn(List.of(diff));
        List<SchemaDiffDto> result = service.listDiffs("INVALID_STATUS");
        assertThat(result).hasSize(1);
    }

    @Test
    void getDiffContent_readsFile() throws Exception {
        SchemaDiff diff = new SchemaDiff();
        diff.setDiffFilePath("/tmp/test.xml");
        when(repository.findById(1L)).thenReturn(Optional.of(diff));
        Path path = Path.of("/tmp/test.xml");
        Files.writeString(path, "test");
        byte[] content = service.getDiffContent(1L);
        assertThat(content).isNotEmpty();
        Files.deleteIfExists(path);
    }

    @Test
    void getDiffContent_fileMissing_throws() {
        SchemaDiff diff = new SchemaDiff();
        diff.setDiffFilePath("/tmp/missing.xml");
        when(repository.findById(1L)).thenReturn(Optional.of(diff));
        assertThatThrownBy(() -> service.getDiffContent(1L)).isInstanceOf(DiffOperationException.class);
    }

    @Test
    void getValidationLog_returnsLog() {
        SchemaDiff diff = new SchemaDiff();
        diff.setValidationLog("log");
        when(repository.findById(1L)).thenReturn(Optional.of(diff));
        assertThat(service.getValidationLog(1L)).isEqualTo("log");
    }

    @Test
    void approveDiff_validStatus() {
        SchemaDiff diff = new SchemaDiff();
        diff.setStatus(SchemaDiff.Status.VALID);
        when(repository.findById(1L)).thenReturn(Optional.of(diff));
        doNothing().when(jenkinsService).triggerApplyJob(1L);
        service.approveDiff(1L);
        assertThat(diff.getStatus()).isEqualTo(SchemaDiff.Status.APPROVED);
        assertThat(diff.getApprovedAt()).isNotNull();
    }

    @Test
    void approveDiff_invalidStatus_throws() {
        SchemaDiff diff = new SchemaDiff();
        diff.setStatus(SchemaDiff.Status.PENDING);
        when(repository.findById(1L)).thenReturn(Optional.of(diff));
        assertThatThrownBy(() -> service.approveDiff(1L)).isInstanceOf(InvalidDiffStateException.class);
    }

    @Test
    void rejectDiff_setsRejected() {
        SchemaDiff diff = new SchemaDiff();
        when(repository.findById(1L)).thenReturn(Optional.of(diff));
        service.rejectDiff(1L, "reason");
        assertThat(diff.getStatus()).isEqualTo(SchemaDiff.Status.REJECTED);
        assertThat(diff.getErrorMessage()).isEqualTo("reason");
    }

    @Test
    void getDiffById_found() {
        SchemaDiff diff = new SchemaDiff();
        diff.setId(1L);
        when(repository.findById(1L)).thenReturn(Optional.of(diff));
        assertThat(service.getDiffById(1L)).isNotNull();
    }

    @Test
    void getDiffById_notFound_throws() {
        when(repository.findById(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getDiffById(1L)).isInstanceOf(DiffNotFoundException.class);
    }

    @Test
    void listChangeSets_success() {
        SchemaDiff diff = new SchemaDiff();
        diff.setDiffFilePath("/tmp/test.xml");
        when(repository.findById(1L)).thenReturn(Optional.of(diff));
        try {
            when(diffService.listChangeSets(any())).thenReturn(List.of(mock(ChangeSetDto.class)));
        } catch (LiquibaseException e) {
            e.printStackTrace();
        }
        List<ChangeSetDto> result = service.listChangeSets(1L);
        assertThat(result).hasSize(1);
    }

    @Test
    void listChangeSets_error_throws() {
        SchemaDiff diff = new SchemaDiff();
        diff.setDiffFilePath("/tmp/test.xml");
        when(repository.findById(1L)).thenReturn(Optional.of(diff));
        try {
            when(diffService.listChangeSets(any())).thenThrow(new RuntimeException("fail"));
        } catch (LiquibaseException e) {
            e.printStackTrace();
        }
        assertThatThrownBy(() -> service.listChangeSets(1L)).isInstanceOf(DiffOperationException.class);
    }

    @Test
    void applyFilteredChangeSets_success() {
        SchemaDiff diff = new SchemaDiff();
        diff.setDiffFilePath("/tmp/test.xml");
        when(repository.findById(1L)).thenReturn(Optional.of(diff));
        try {
            when(diffService.applyFilteredChangeSets(any(), anyList(), anyString(), anyString(), anyString(), anyString())).thenReturn("ok");
        } catch (LiquibaseException e) {
            e.printStackTrace();
        }
        String result = service.applyFilteredChangeSets(1L, List.of("id"), "POSTGRES");
        assertThat(result).isEqualTo("ok");
    }

    @Test
    void applyFilteredChangeSets_error_throws() {
        SchemaDiff diff = new SchemaDiff();
        diff.setDiffFilePath("/tmp/test.xml");
        when(repository.findById(1L)).thenReturn(Optional.of(diff));
        try {
            when(diffService.applyFilteredChangeSets(any(), anyList(), anyString(), anyString(), anyString(), anyString())).thenThrow(new RuntimeException("fail"));
        } catch (LiquibaseException e) {
            e.printStackTrace();
        }
        assertThatThrownBy(() -> service.applyFilteredChangeSets(1L, List.of("id"), "POSTGRES")).isInstanceOf(DiffOperationException.class);
    }

    @Test
    void getParsedDiff_success() {
        SchemaDiff diff = new SchemaDiff();
        diff.setDiffFilePath("/tmp/test.xml");
        when(repository.findById(1L)).thenReturn(Optional.of(diff));
        Map<String, Object> parsed = Map.of("key", "value");
        try (MockedStatic<com.example.schemasync.utils.DiffXmlParser> parser = mockStatic(com.example.schemasync.utils.DiffXmlParser.class)) {
            parser.when(() -> com.example.schemasync.utils.DiffXmlParser.parseDiff(any())).thenReturn(parsed);
            Map<String, Object> result = service.getParsedDiff(1L);
            assertThat(result).containsEntry("key", "value");
        }
    }

    @Test
    void getParsedDiff_notFound_throws() {
        when(repository.findById(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getParsedDiff(1L)).isInstanceOf(DiffNotFoundException.class);
    }

    @Test
    void getParsedDiff_parseError_throws() {
        SchemaDiff diff = new SchemaDiff();
        diff.setDiffFilePath("/tmp/test.xml");
        when(repository.findById(1L)).thenReturn(Optional.of(diff));
        try (MockedStatic<com.example.schemasync.utils.DiffXmlParser> parser = mockStatic(com.example.schemasync.utils.DiffXmlParser.class)) {
            parser.when(() -> com.example.schemasync.utils.DiffXmlParser.parseDiff(any())).thenThrow(new RuntimeException("fail"));
            assertThatThrownBy(() -> service.getParsedDiff(1L)).isInstanceOf(DiffOperationException.class);
        }
    }
} 