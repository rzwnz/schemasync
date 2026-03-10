package com.example.schemasyncbot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SchemaSyncServiceTest {

    private MockWebServer server;
    private SchemaSyncService service;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        String baseUrl = server.url("/").toString();
        service = new SchemaSyncService(baseUrl, "test-api-key");
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
        service.cleanupTemp();
    }

    @Test
    void generateDiff_createsFile() throws Exception {
        // 1st request: POST /api/diffs -> returns {id: 42}
        server.enqueue(new MockResponse()
                .setBody("{\"id\": 42}")
                .addHeader("Content-Type", "application/json"));

        // 2nd request: GET /api/diffs/42/content -> returns bytes
        server.enqueue(new MockResponse()
                .setBody("<?xml version=\"1.0\"?><diff/>")
                .addHeader("Content-Type", "application/octet-stream"));

        File result = service.generateDiff(Map.of("schema", "public")).block();

        assertNotNull(result);
        assertTrue(result.exists());
        assertTrue(result.getName().startsWith("schema_diff_"));
        assertTrue(result.getName().endsWith(".xml"));
        assertTrue(result.length() > 0);

        // Verify API key header is sent
        var req1 = server.takeRequest();
        assertEquals("test-api-key", req1.getHeader("X-API-KEY"));
        assertEquals("/api/diffs", req1.getPath());

        var req2 = server.takeRequest();
        assertEquals("/api/diffs/42/content", req2.getPath());
    }

    @Test
    void fetchParsedDiff_returnsMap() {
        String body = "{\"tables\":[\"users\"],\"columns\":[{\"tableName\":\"users\",\"columnName\":\"id\"}]}";
        server.enqueue(new MockResponse()
                .setBody(body)
                .addHeader("Content-Type", "application/json"));

        Map<String, Object> result = service.fetchParsedDiff(42L).block();

        assertNotNull(result);
        assertTrue(result.containsKey("tables"));
        assertTrue(result.containsKey("columns"));
    }

    @Test
    void getLatestDiffId_returnsMostRecentId() {
        String body = "[{\"id\":1},{\"id\":2},{\"id\":5}]";
        server.enqueue(new MockResponse()
                .setBody(body)
                .addHeader("Content-Type", "application/json"));

        Long id = service.getLatestDiffId().block();

        assertEquals(5L, id);
    }

    @Test
    void getLatestDiffId_emptyList_errors() {
        server.enqueue(new MockResponse()
                .setBody("[]")
                .addHeader("Content-Type", "application/json"));

        // Reactor map() cannot return null — this is a known edge case in the source
        assertThrows(NullPointerException.class, () -> service.getLatestDiffId().block());
    }

    @Test
    void cleanupTemp_doesNotThrow_whenDirEmpty() {
        // Should not throw even if nothing to clean
        assertDoesNotThrow(() -> service.cleanupTemp());
    }

    @Test
    void cleanupTemp_removesFiles() throws Exception {
        // First generate a diff file
        server.enqueue(new MockResponse()
                .setBody("{\"id\": 1}")
                .addHeader("Content-Type", "application/json"));
        server.enqueue(new MockResponse()
                .setBody("<diff/>")
                .addHeader("Content-Type", "application/octet-stream"));

        File f = service.generateDiff(Map.of("s", "p")).block();
        assertNotNull(f);
        assertTrue(f.exists());

        service.cleanupTemp();

        // File should be deleted (its parent dir may still exist)
        assertFalse(f.exists());
    }
}
