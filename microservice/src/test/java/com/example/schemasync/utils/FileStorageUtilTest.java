package com.example.schemasync.utils;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.BeforeAll;
import java.nio.file.Files;
import java.nio.file.Path;

class FileStorageUtilTest {
    @BeforeAll
    static void setUp() throws Exception {
        Path tempDir = Files.createTempDirectory("test-diff-store");
        System.setProperty("DIFF_STORE_PATH", tempDir.toString());
    }
    @Test
    void getDiffFile_returnsFile() throws java.io.IOException {
        java.io.File file = FileStorageUtil.getDiffFile(123L);
        assertThat(file).isNotNull();
        file.createNewFile();
        assertThat(file.exists()).isTrue();
        assertThat(file.getPath()).contains("123");
    }
}