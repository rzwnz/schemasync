package com.example.schemasync.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SchemaDiffControllerIT {

    @SuppressWarnings("resource")
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    private static final String API_KEY = System.getenv().getOrDefault("API_KEY", "dummy-key-for-local");

    @DynamicPropertySource
    static void setApiKey(DynamicPropertyRegistry registry) {
        registry.add("security.apiKey", () -> API_KEY);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private HttpHeaders headersWithApiKey() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-KEY", API_KEY);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    @BeforeEach
    void resetRateLimit() throws InterruptedException {
        // Optionally sleep or reset state if rate limiting is strict
        Thread.sleep(100);
    }

    @Test
    void healthEndpointWorks() {
        ResponseEntity<String> response = restTemplate.getForEntity("http://localhost:" + port + "/actuator/health", String.class);
        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getBody()).contains("UP");
    }

    @Test
    void apiDiffsHealthEndpointWorks() {
        ResponseEntity<String> response = restTemplate.getForEntity("http://localhost:" + port + "/api/diffs/health", String.class);
        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getBody()).contains("status").contains("UP");
    }

    @Test
    void listDiffsReturnsArray() {
        HttpEntity<Void> entity = new HttpEntity<>(headersWithApiKey());
        ResponseEntity<String> response = restTemplate.exchange("http://localhost:" + port + "/api/diffs", HttpMethod.GET, entity, String.class);
        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getBody()).startsWith("[");
    }

    @Test
    void createDiffReturnsOk() {
        String body = "{\"sourceDatabase\":{\"jdbcUrl\":\"jdbc:postgresql://localhost:5432/test\",\"username\":\"test\",\"password\":\"test\"},\"targetDatabases\":[],\"changeLogFile\":\"file\"}";
        HttpEntity<String> entity = new HttpEntity<>(body, headersWithApiKey());
        ResponseEntity<String> response = restTemplate.postForEntity("http://localhost:" + port + "/api/diffs", entity, String.class);
        assertThat(response.getStatusCode()).isIn(200, 201);
    }

    @Test
    void getDiffByIdReturns404Or200() {
        HttpEntity<Void> entity = new HttpEntity<>(headersWithApiKey());
        ResponseEntity<String> response = restTemplate.exchange("http://localhost:" + port + "/api/diffs/1", HttpMethod.GET, entity, String.class);
        assertThat(response.getStatusCode()).isIn(200, 404);
    }

    @Test
    void approveDiffReturns204Or404() {
        HttpEntity<Void> entity = new HttpEntity<>(headersWithApiKey());
        ResponseEntity<Void> response = restTemplate.exchange("http://localhost:" + port + "/api/diffs/1/approve", HttpMethod.POST, entity, Void.class);
        assertThat(response.getStatusCode()).isIn(204, 404);
    }

    @Test
    void rejectDiffReturns204Or404() {
        HttpEntity<String> entity = new HttpEntity<>("reason", headersWithApiKey());
        ResponseEntity<Void> response = restTemplate.exchange("http://localhost:" + port + "/api/diffs/1/reject", HttpMethod.POST, entity, Void.class);
        assertThat(response.getStatusCode()).isIn(204, 404);
    }

    @Test
    void getDiffContentReturns200Or404() {
        HttpEntity<Void> entity = new HttpEntity<>(headersWithApiKey());
        ResponseEntity<byte[]> response = restTemplate.exchange("http://localhost:" + port + "/api/diffs/1/content", HttpMethod.GET, entity, byte[].class);
        assertThat(response.getStatusCode()).isIn(200, 404);
    }

    @Test
    void apiKeyRequiredReturns401() {
        ResponseEntity<String> response = restTemplate.getForEntity("http://localhost:" + port + "/api/diffs", String.class);
        assertThat(response.getStatusCode()).isEqualTo(401);
    }

    @Test
    void rateLimitingReturns429() {
        HttpEntity<Void> entity = new HttpEntity<>(headersWithApiKey());
        // Exceed rate limit (capacity=2 in test config)
        restTemplate.exchange("http://localhost:" + port + "/api/diffs", HttpMethod.GET, entity, String.class);
        restTemplate.exchange("http://localhost:" + port + "/api/diffs", HttpMethod.GET, entity, String.class);
        ResponseEntity<String> response = restTemplate.exchange("http://localhost:" + port + "/api/diffs", HttpMethod.GET, entity, String.class);
        assertThat(response.getStatusCode()).isEqualTo(429);
    }

    @Test
    void validateMergeConfigWorks() {
        String validBody = "{\"changeLogFile\":\"file\",\"sourceDatabase\":{\"jdbcUrl\":\"url\",\"username\":\"user\",\"password\":\"pass\"},\"targetDatabases\":[{\"jdbcUrl\":\"url\",\"username\":\"user\",\"password\":\"pass\",\"name\":\"db\"}]}";
        HttpEntity<String> entity = new HttpEntity<>(validBody, headersWithApiKey());
        ResponseEntity<String> response = restTemplate.postForEntity("http://localhost:" + port + "/api/diffs/api/merge/validate-config", entity, String.class);
        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getBody()).contains("OK");
    }

    @Test
    void validateMergeConfigFailsOnMissingFields() {
        String invalidBody = "{\"changeLogFile\":null,\"sourceDatabase\":null,\"targetDatabases\":[]}";
        HttpEntity<String> entity = new HttpEntity<>(invalidBody, headersWithApiKey());
        ResponseEntity<String> response = restTemplate.postForEntity("http://localhost:" + port + "/api/diffs/api/merge/validate-config", entity, String.class);
        assertThat(response.getStatusCode()).isEqualTo(400);
        assertThat(response.getBody()).contains("Missing required fields");
    }

    @Test
    void validateChangelogReturnsOkOr400() {
        String body = "{\"changeLogFile\":\"file\"}";
        HttpEntity<String> entity = new HttpEntity<>(body, headersWithApiKey());
        ResponseEntity<String> response = restTemplate.postForEntity("http://localhost:" + port + "/api/diffs/api/merge/validate-changelog", entity, String.class);
        assertThat(response.getStatusCode()).isIn(200, 400);
    }

    @Test
    void validateSourceReturnsOkOr400() {
        String body = "{\"sourceDatabase\":{\"jdbcUrl\":\"url\",\"username\":\"user\",\"password\":\"pass\"},\"targetDatabases\":[],\"changeLogFile\":\"file\"}";
        HttpEntity<String> entity = new HttpEntity<>(body, headersWithApiKey());
        ResponseEntity<String> response = restTemplate.postForEntity("http://localhost:" + port + "/api/diffs/api/merge/validate-source", entity, String.class);
        assertThat(response.getStatusCode()).isIn(200, 400);
    }

    @Test
    void validateTargetReturnsOkOr400() {
        String body = "{\"sourceDatabase\":{\"jdbcUrl\":\"url\",\"username\":\"user\",\"password\":\"pass\"},\"targetDatabases\":[{\"jdbcUrl\":\"url\",\"username\":\"user\",\"password\":\"pass\",\"name\":\"db\"}],\"changeLogFile\":\"file\"}";
        HttpEntity<String> entity = new HttpEntity<>(body, headersWithApiKey());
        ResponseEntity<String> response = restTemplate.postForEntity("http://localhost:" + port + "/api/diffs/api/merge/validate-target", entity, String.class);
        assertThat(response.getStatusCode()).isIn(200, 400);
    }

    @Test
    void transferDataReturnsOkOr500() {
        String body = "{\"sourceDatabase\":{\"jdbcUrl\":\"url\",\"username\":\"user\",\"password\":\"pass\"},\"targetDatabases\":[{\"jdbcUrl\":\"url\",\"username\":\"user\",\"password\":\"pass\",\"name\":\"db\"}],\"changeLogFile\":\"file\"}";
        HttpEntity<String> entity = new HttpEntity<>(body, headersWithApiKey());
        ResponseEntity<String> response = restTemplate.postForEntity("http://localhost:" + port + "/api/diffs/api/merge/transfer-data", entity, String.class);
        assertThat(response.getStatusCode()).isIn(200, 500);
    }

    @Test
    void changelogToJsonReturnsOkOr400() {
        String body = "{\"changeLogFile\":\"file\"}";
        HttpEntity<String> entity = new HttpEntity<>(body, headersWithApiKey());
        ResponseEntity<String> response = restTemplate.postForEntity("http://localhost:" + port + "/api/diffs/api/merge/changelog-to-json", entity, String.class);
        assertThat(response.getStatusCode()).isIn(200, 400);
    }
} 