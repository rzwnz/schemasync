package com.example.schemasync.service;

import liquibase.exception.LiquibaseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ChangelogValidationServiceTest {
    private final ChangelogValidationService service = new ChangelogValidationService();

    @Test
    void validateChangelog_invalidPath_throwsLiquibaseException() {
        String invalidPath = "/not/a/real/file.xml";
        assertThatThrownBy(() -> service.validateChangelog(invalidPath)).isInstanceOf(LiquibaseException.class);
    }
} 