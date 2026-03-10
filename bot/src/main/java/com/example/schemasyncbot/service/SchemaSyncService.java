package com.example.schemasyncbot.service;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;


@SuppressWarnings("unchecked")
@Service
public class SchemaSyncService {

    private static final Logger logger = LoggerFactory.getLogger(SchemaSyncService.class);

    private final WebClient client;

    public SchemaSyncService(
            @Value("${schemasync.url}") String baseUrl,
            @Value("${schemasync.api-key}") String apiKey) {
        this.client = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("X-API-KEY", apiKey)
                .build();
    }

    /**
     * Generate a diff via the microservice, storing the resulting file locally.
     * 
     * @param params JSON body parameters for diff generation
     * @return a Mono<File> pointing to the downloaded diff file
     */
    private static final String DIFF_TEMP_SUBDIR = "schemasync-diffs";

    /**
     * Returns (and creates if needed) a dedicated temp subdirectory for diff files.
     */
    private File getDiffTempDir() {
        File dir = Path.of(System.getProperty("java.io.tmpdir"), DIFF_TEMP_SUBDIR).toFile();
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    public Mono<File> generateDiff(Map<String, String> params) {
        return client.post()
                .uri("/api/diffs")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(params)
                .retrieve()
                .bodyToMono(Map.class)
                .flatMap(resp -> {
                    Object id = resp.get("id");
                    return client.get()
                            .uri("/api/diffs/{id}/content", id)
                            .accept(MediaType.APPLICATION_OCTET_STREAM)
                            .retrieve()
                            .bodyToMono(byte[].class)
                            .map(bytes -> {
                                try {
                                    String filename = "schema_diff_" + Instant.now().toEpochMilli() + ".xml";
                                    File target = new File(getDiffTempDir(), filename);
                                    FileUtils.writeByteArrayToFile(target, bytes);
                                    return target;
                                } catch (Exception e) {
                                    throw new RuntimeException("Failed to write diff file", e);
                                }
                            });
                });
    }

    /**
     * Cleanup temporary diff files from the dedicated schemasync-diffs subdirectory.
     * Only removes files created by this service — never touches the system temp root.
     */
    public void cleanupTemp() {
        File dir = getDiffTempDir();
        try {
            if (dir.exists()) {
                FileUtils.cleanDirectory(dir);
            }
        } catch (IOException e) {
            logger.error("Failed to clean up temp directory: {}", getDiffTempDir().getAbsolutePath(), e);
        }
    }

    public Mono<Map<String, Object>> fetchParsedDiff(Object diffId) {
        return client.get()
                .uri("/api/diffs/{id}/parsed", diffId)
                .retrieve()
                .bodyToMono(Map.class)
                .map(obj -> (Map<String, Object>) obj);
    }

    public Mono<Long> getLatestDiffId() {
        return client.get()
                .uri("/api/diffs")
                .retrieve()
                .bodyToMono(List.class)
                .map(diffs -> {
                    if (diffs.isEmpty()) {
                        return null;
                    }
                    Map<String, Object> latestDiff = (Map<String, Object>) diffs.get(diffs.size() - 1);
                    return Long.valueOf(latestDiff.get("id").toString());
                });
    }
}