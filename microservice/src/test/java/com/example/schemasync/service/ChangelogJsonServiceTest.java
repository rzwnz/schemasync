package com.example.schemasync.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ChangelogJsonServiceTest {
    private final ChangelogJsonService service = new ChangelogJsonService();

    @Test
    void changelogToJson_invalidPath_throwsException() {
        String invalidPath = "/not/a/real/file.xml";
        assertThatThrownBy(() -> service.changelogToJson(invalidPath)).isInstanceOf(Exception.class);
    }
} 