package com.example.schemasyncbot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class JenkinsService {

    private static final Logger log = LoggerFactory.getLogger(JenkinsService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final WebClient webClient;
    private final String jenkinsUrl;
    private final String jenkinsUser;
    private final String jenkinsToken;
    private final String jenkinsTriggerToken;

    private final ConcurrentHashMap<String, String> sessionCookies = new ConcurrentHashMap<>();
    private String cachedCrumb = null;
    private long crumbExpiry = 0;
    private final HttpClient httpClient;

    @Autowired
    public JenkinsService(
            @Value("${JENKINS_URL}") String jenkinsUrl,
            @Value("${JENKINS_USER}") String jenkinsUser,
            @Value("${JENKINS_TOKEN}") String jenkinsToken,
            @Value("${JENKINS_TRIGGER_TOKEN}") String jenkinsTriggerToken) {

        this.jenkinsUrl = jenkinsUrl;
        this.jenkinsUser = jenkinsUser;
        this.jenkinsToken = jenkinsToken;
        this.jenkinsTriggerToken = jenkinsTriggerToken;

        this.httpClient = HttpClient.newBuilder().build();

        this.webClient = WebClient.builder()
                .baseUrl(jenkinsUrl)
                .defaultHeaders(headers -> headers.setBasicAuth(jenkinsUser, jenkinsToken))
                .filter(sessionCookieFilter())
                .filter(errorHandler())
                .codecs(config -> config.defaultCodecs().maxInMemorySize(5 * 1024 * 1024))
                .build();
    }

    // ─── Filters ───────────────────────────────────────────────────────

    private ExchangeFilterFunction sessionCookieFilter() {
        return (request, next) -> {
            try {
                ClientRequest actualRequest = request;
                if (!sessionCookies.isEmpty()) {
                    String cookieHeader = sessionCookies.entrySet().stream()
                            .map(e -> e.getKey() + "=" + e.getValue())
                            .reduce((a, b) -> a + "; " + b)
                            .orElse("");
                    actualRequest = ClientRequest.from(request)
                            .header("Cookie", cookieHeader)
                            .build();
                    log.debug("Added cookie header to request");
                }

                return next.exchange(actualRequest)
                        .flatMap(response -> {
                            try {
                                response.cookies().values().stream()
                                        .flatMap(List::stream)
                                        .filter(cookie -> cookie.getName().startsWith("JSESSIONID"))
                                        .findFirst()
                                        .ifPresent(cookie -> {
                                            sessionCookies.put("JSESSIONID", cookie.getValue());
                                            log.debug("Captured session cookie");
                                        });
                            } catch (Exception e) {
                                log.warn("Error capturing session cookies: {}", e.getMessage());
                            }
                            return Mono.just(response);
                        });
            } catch (Exception e) {
                log.error("Error in session cookie filter: {}", e.getMessage(), e);
                return next.exchange(request);
            }
        };
    }

    private ExchangeFilterFunction errorHandler() {
        return ExchangeFilterFunction.ofResponseProcessor(clientResponse -> {
            log.debug("Jenkins response status: {}", clientResponse.statusCode().value());
            if (clientResponse.statusCode().is4xxClientError() || clientResponse.statusCode().is5xxServerError()) {
                return clientResponse.bodyToMono(String.class)
                        .flatMap(errorBody -> {
                            String errorMessage = String.format("HTTP %d: %s",
                                    clientResponse.statusCode().value(), errorBody);
                            log.warn("Jenkins error response: {}", errorMessage);
                            return Mono.error(new RuntimeException(errorMessage));
                        });
            }
            return Mono.just(clientResponse);
        });
    }

    // ─── CSRF Crumb ────────────────────────────────────────────────────

    private Mono<String> getCsrfCrumb() {
        long now = System.currentTimeMillis();
        if (cachedCrumb != null && now < crumbExpiry) {
            log.debug("Using cached CSRF crumb");
            return Mono.just(cachedCrumb);
        }

        log.debug("Fetching new CSRF crumb");

        return Mono.fromCallable(() -> {
            try {
                String crumbUrl = jenkinsUrl.endsWith("/")
                        ? jenkinsUrl.substring(0, jenkinsUrl.length() - 1)
                        : jenkinsUrl;
                crumbUrl += "/crumbIssuer/api/json";

                String encodedAuth = Base64.getEncoder()
                        .encodeToString((jenkinsUser + ":" + jenkinsToken).getBytes(StandardCharsets.UTF_8));

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(crumbUrl))
                        .header("Authorization", "Basic " + encodedAuth)
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    String jsonResponse = response.body();
                    log.debug("Crumb response received (status 200)");

                    int crumbStart = jsonResponse.indexOf("\"crumb\":\"") + 9;
                    int crumbEnd = jsonResponse.indexOf("\"", crumbStart);
                    String crumb = jsonResponse.substring(crumbStart, crumbEnd);

                    // Capture session cookies from response
                    List<String> setCookies = response.headers().allValues("Set-Cookie");
                    log.debug("Set-Cookie headers found: {}", setCookies.size());

                    setCookies.stream()
                            .filter(cookie -> cookie.startsWith("JSESSIONID"))
                            .findFirst()
                            .ifPresent(cookie -> {
                                String[] parts = cookie.split(";")[0].split("=", 2);
                                if (parts.length == 2) {
                                    sessionCookies.put(parts[0], parts[1]);
                                    log.debug("Captured JSESSIONID cookie from crumb response");
                                }
                            });

                    setCookies.stream()
                            .filter(cookie -> !cookie.startsWith("JSESSIONID"))
                            .forEach(cookie -> {
                                if (cookie.contains("=")) {
                                    String[] parts = cookie.split(";")[0].split("=", 2);
                                    if (parts.length == 2) {
                                        sessionCookies.put(parts[0], parts[1]);
                                        log.debug("Captured additional cookie: {}", parts[0]);
                                    }
                                }
                            });

                    cachedCrumb = crumb;
                    crumbExpiry = now + (10 * 60 * 1000); // 10 minutes
                    log.debug("New CSRF crumb cached (expires in 10 minutes)");
                    return crumb;
                } else {
                    log.warn("Failed to fetch crumb: HTTP {}", response.statusCode());
                    return "";
                }
            } catch (Exception e) {
                log.warn("Exception fetching CSRF crumb: {}", e.getMessage());
                return "";
            }
        });
    }

    // ─── Build Trigger ─────────────────────────────────────────────────

    public Mono<String> triggerBuild(String pipelineName, Map<String, String> params) {
        log.info("Triggering build for pipeline '{}' with {} parameters", pipelineName, params.size());

        params.put("token", jenkinsTriggerToken);

        return getCsrfCrumb().flatMap(crumb -> {
            String path = "/job/" + pipelineName + "/buildWithParameters";

            StringBuilder urlBuilder = new StringBuilder(jenkinsUrl.endsWith("/")
                    ? jenkinsUrl.substring(0, jenkinsUrl.length() - 1)
                    : jenkinsUrl);
            urlBuilder.append(path).append("?");
            params.forEach((key, value) -> {
                if (urlBuilder.charAt(urlBuilder.length() - 1) != '?') {
                    urlBuilder.append("&");
                }
                urlBuilder.append(key).append("=").append(value);
            });
            String fullUrl = urlBuilder.toString();
            log.debug("Build URL constructed for pipeline '{}'", pipelineName);

            return Mono.fromCallable(() -> {
                try {
                    String encodedAuth = Base64.getEncoder()
                            .encodeToString((jenkinsUser + ":" + jenkinsToken).getBytes(StandardCharsets.UTF_8));

                    HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                            .uri(URI.create(fullUrl))
                            .header("Authorization", "Basic " + encodedAuth)
                            .POST(HttpRequest.BodyPublishers.noBody());

                    if (crumb != null && !crumb.isEmpty()) {
                        requestBuilder.header("Jenkins-Crumb", crumb);
                    }

                    if (!sessionCookies.isEmpty()) {
                        String cookieHeader = sessionCookies.entrySet().stream()
                                .map(e -> e.getKey() + "=" + e.getValue())
                                .reduce((a, b) -> a + "; " + b)
                                .orElse("");
                        requestBuilder.header("Cookie", cookieHeader);
                    }

                    HttpResponse<String> response = httpClient.send(requestBuilder.build(),
                            HttpResponse.BodyHandlers.ofString());

                    log.debug("Jenkins trigger response: HTTP {}", response.statusCode());

                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        log.info("Build triggered successfully for pipeline '{}'", pipelineName);
                        return "Triggered";
                    } else if (response.statusCode() == 403 && !crumb.isEmpty()) {
                        log.debug("Got 403, retrying without CSRF crumb");
                        HttpRequest fallbackRequest = HttpRequest.newBuilder()
                                .uri(URI.create(fullUrl))
                                .POST(HttpRequest.BodyPublishers.noBody())
                                .build();
                        HttpResponse<String> fallbackResponse = httpClient.send(fallbackRequest,
                                HttpResponse.BodyHandlers.ofString());

                        if (fallbackResponse.statusCode() >= 200 && fallbackResponse.statusCode() < 300) {
                            log.info("Build triggered (fallback) for pipeline '{}'", pipelineName);
                            return "Triggered";
                        } else {
                            String errorMsg = "Jenkins trigger failed (fallback): HTTP " + fallbackResponse.statusCode();
                            log.error("{}: {}", errorMsg, fallbackResponse.body());
                            throw new RuntimeException(errorMsg);
                        }
                    } else {
                        String errorMsg = "Jenkins trigger failed: HTTP " + response.statusCode();
                        log.error("{}: {}", errorMsg, response.body());
                        throw new RuntimeException(errorMsg);
                    }
                } catch (RuntimeException e) {
                    throw e;
                } catch (Exception e) {
                    log.error("Exception triggering Jenkins build: {}", e.getMessage(), e);
                    throw new RuntimeException("Jenkins trigger failed: " + e.getMessage(), e);
                }
            });
        });
    }

    // ─── Read Operations ───────────────────────────────────────────────

    public Mono<String> getLastBuildLogs(String pipelineName) {
        return getCsrfCrumb().flatMap(crumb -> {
            String url = "/job/" + pipelineName + "/lastBuild/consoleText";
            return webClient.get()
                    .uri(url)
                    .header("Jenkins-Crumb", crumb)
                    .retrieve()
                    .bodyToMono(String.class);
        });
    }

    public Mono<JsonNode> listJobs() {
        return webClient.get()
                .uri("/api/json?tree=jobs[name,displayName,url]")
                .retrieve()
                .bodyToMono(JsonNode.class);
    }

    public Mono<JsonNode> getJobDetails(String jobName) {
        return webClient.get()
                .uri("/job/" + jobName + "/api/json?depth=2")
                .retrieve()
                .bodyToMono(String.class)
                .map(body -> {
                    try {
                        return OBJECT_MAPPER.readTree(body);
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to parse Jenkins job JSON", e);
                    }
                });
    }

    public Mono<Integer> getLastBuildNumber(String jobName) {
        return webClient.get()
                .uri("/job/" + jobName + "/api/json?tree=lastBuild[number]")
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(node -> node.path("lastBuild").path("number").asInt());
    }

    public Mono<File> downloadArtifact(String jobName, int buildNumber, String artifactPattern) {
        return webClient.get()
                .uri("/job/" + jobName + "/" + buildNumber + "/api/json?tree=artifacts[fileName,relativePath]")
                .retrieve()
                .bodyToMono(JsonNode.class)
                .flatMap(node -> {
                    for (JsonNode artifact : node.path("artifacts")) {
                        String fileName = artifact.path("fileName").asText();
                        String relPath = artifact.path("relativePath").asText();
                        if (fileName.matches(artifactPattern.replace("*", ".*"))) {
                            return webClient.get()
                                    .uri("/job/" + jobName + "/" + buildNumber + "/artifact/" + relPath)
                                    .retrieve()
                                    .bodyToMono(byte[].class)
                                    .map(bytes -> {
                                        try {
                                            Path temp = Files.createTempFile("jenkins-artifact-", fileName);
                                            Files.write(temp, bytes);
                                            return temp.toFile();
                                        } catch (Exception e) {
                                            throw new RuntimeException("Failed to download artifact", e);
                                        }
                                    });
                        }
                    }
                    return Mono.empty();
                });
    }
}
