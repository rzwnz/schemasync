package com.example.schemasync.service;

import com.example.schemasync.service.interfaces.JenkinsIntegrationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

@Service
public class JenkinsIntegrationServiceImpl implements JenkinsIntegrationService {

    private static final Logger logger = LoggerFactory.getLogger(JenkinsIntegrationServiceImpl.class);

    @Value("${jenkins.url}")
    private String jenkinsUrl;
    @Value("${jenkins.user}")
    private String jenkinsUser;
    @Value("${jenkins.token}")
    private String jenkinsToken;
    @Value("${jenkins.jobName}")
    private String jobName;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Override
    public void triggerApplyJob(Long diffId) {
        triggerJobWithParams(diffId, Map.of());
    }

    @Override
    public void triggerJobWithParams(Long diffId, Map<String, String> extraParams) {
        try {
            var builder = UriComponentsBuilder
                    .fromHttpUrl(String.format("%s/job/%s/buildWithParameters", jenkinsUrl, jobName))
                    .queryParam("DIFF_ID", diffId);
            extraParams.forEach(builder::queryParam);
            URI uri = builder.build().toUri();

            String auth = jenkinsUser + ":" + jenkinsToken;
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));

            HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .header("Authorization", "Basic " + encodedAuth)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                logger.info("Jenkins job triggered successfully for diffId={}, params={}", diffId, extraParams.keySet());
            } else {
                logger.error("Failed to trigger Jenkins job for diffId={}: status={}, body={}", diffId, response.statusCode(), response.body());
            }
        } catch (Exception e) {
            logger.error("Exception triggering Jenkins job for diffId={}: {}", diffId, e.getMessage(), e);
        }
    }

    @Override
    public String getLastBuildStatus() {
        try {
            URI uri = URI.create(String.format("%s/job/%s/lastBuild/api/json", jenkinsUrl, jobName));
            String auth = jenkinsUser + ":" + jenkinsToken;
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .header("Authorization", "Basic " + encodedAuth)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return response.body();
            } else {
                logger.warn("Failed to get Jenkins build status: {}", response.statusCode());
                return "{\"error\": \"HTTP " + response.statusCode() + "\"}";
            }
        } catch (Exception e) {
            logger.error("Exception getting Jenkins build status: {}", e.getMessage(), e);
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }
}