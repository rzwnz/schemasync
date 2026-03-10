package com.example.schemasyncbot.service;

import com.fasterxml.jackson.databind.JsonNode;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link JenkinsService} using MockWebServer.
 */
class JenkinsServiceTest {

    private MockWebServer server;
    private JenkinsService jenkinsService;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();

        String baseUrl = server.url("/").toString();
        // Remove trailing slash
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        jenkinsService = new JenkinsService(baseUrl, "test-user", "test-token", "trigger-token");
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    // ── listJobs ──────────────────────────────────────────────

    @Test
    void listJobs_returnsJsonNode() {
        server.enqueue(new MockResponse()
                .setBody("{\"jobs\":[{\"name\":\"my-job\",\"displayName\":\"My Job\",\"url\":\"http://x\"}]}")
                .addHeader("Content-Type", "application/json"));

        StepVerifier.create(jenkinsService.listJobs())
                .assertNext(node -> {
                    assertThat(node.path("jobs").isArray()).isTrue();
                    assertThat(node.path("jobs").get(0).path("name").asText()).isEqualTo("my-job");
                })
                .verifyComplete();
    }

    // ── getJobDetails ─────────────────────────────────────────

    @Test
    void getJobDetails_returnsJobJson() {
        server.enqueue(new MockResponse()
                .setBody("{\"name\":\"schema-apply\",\"buildable\":true,\"lastBuild\":{\"number\":42}}")
                .addHeader("Content-Type", "application/json"));

        StepVerifier.create(jenkinsService.getJobDetails("schema-apply"))
                .assertNext(node -> {
                    assertThat(node.path("name").asText()).isEqualTo("schema-apply");
                    assertThat(node.path("lastBuild").path("number").asInt()).isEqualTo(42);
                })
                .verifyComplete();
    }

    // ── getLastBuildNumber ────────────────────────────────────

    @Test
    void getLastBuildNumber_returnsNumber() {
        server.enqueue(new MockResponse()
                .setBody("{\"lastBuild\":{\"number\":7}}")
                .addHeader("Content-Type", "application/json"));

        StepVerifier.create(jenkinsService.getLastBuildNumber("my-job"))
                .expectNext(7)
                .verifyComplete();
    }

    // ── getLastBuildLogs ──────────────────────────────────────

    @Test
    void getLastBuildLogs_returnsConsoleText() {
        // First request: crumb
        server.enqueue(new MockResponse()
                .setBody("{\"crumb\":\"test-crumb-123\",\"crumbRequestField\":\"Jenkins-Crumb\"}")
                .addHeader("Content-Type", "application/json"));
        // Second request: logs
        server.enqueue(new MockResponse()
                .setBody("Started by user...\nBuild SUCCESS")
                .addHeader("Content-Type", "text/plain"));

        StepVerifier.create(jenkinsService.getLastBuildLogs("my-job"))
                .assertNext(logs -> {
                    assertThat(logs).contains("Build SUCCESS");
                })
                .verifyComplete();
    }

    // ── triggerBuild ──────────────────────────────────────────

    @Test
    void triggerBuild_success_returnsTriggered() {
        // First: crumb request
        server.enqueue(new MockResponse()
                .setBody("{\"crumb\":\"trigger-crumb\",\"crumbRequestField\":\"Jenkins-Crumb\"}")
                .addHeader("Content-Type", "application/json"));
        // Second: build trigger
        server.enqueue(new MockResponse().setResponseCode(201));

        Map<String, String> params = new HashMap<>();
        params.put("SCHEMA_NAME", "public");

        StepVerifier.create(jenkinsService.triggerBuild("schema-apply", params))
                .expectNext("Triggered")
                .verifyComplete();
    }

    @Test
    void triggerBuild_failure_throwsError() {
        // Crumb
        server.enqueue(new MockResponse()
                .setBody("{\"crumb\":\"crumb-val\",\"crumbRequestField\":\"Jenkins-Crumb\"}")
                .addHeader("Content-Type", "application/json"));
        // Build trigger fails
        server.enqueue(new MockResponse().setResponseCode(500).setBody("Internal Server Error"));
        // Fallback also fails (403 path not taken since it's 500)

        Map<String, String> params = new HashMap<>();
        params.put("SCHEMA_NAME", "public");

        StepVerifier.create(jenkinsService.triggerBuild("schema-apply", params))
                .expectError(RuntimeException.class)
                .verify();
    }

    // ── downloadArtifact ──────────────────────────────────────

    @Test
    void downloadArtifact_matchingPattern_downloadsFile() {
        // First: artifact list
        server.enqueue(new MockResponse()
                .setBody("{\"artifacts\":[{\"fileName\":\"schema-diff-1.xml\",\"relativePath\":\"schema-diff-1.xml\"}]}")
                .addHeader("Content-Type", "application/json"));
        // Second: artifact content
        server.enqueue(new MockResponse()
                .setBody("<changelog/>")
                .addHeader("Content-Type", "application/xml"));

        StepVerifier.create(jenkinsService.downloadArtifact("schema-apply", 5, "schema-diff-*.xml"))
                .assertNext(file -> {
                    assertThat(file).isNotNull();
                    assertThat(file.getName()).contains("schema-diff-1.xml");
                    // Cleanup
                    file.delete();
                })
                .verifyComplete();
    }

    @Test
    void downloadArtifact_noMatch_returnsEmpty() {
        server.enqueue(new MockResponse()
                .setBody("{\"artifacts\":[{\"fileName\":\"other-file.txt\",\"relativePath\":\"other-file.txt\"}]}")
                .addHeader("Content-Type", "application/json"));

        StepVerifier.create(jenkinsService.downloadArtifact("schema-apply", 5, "schema-diff-*.xml"))
                .verifyComplete(); // empty Mono
    }
}
