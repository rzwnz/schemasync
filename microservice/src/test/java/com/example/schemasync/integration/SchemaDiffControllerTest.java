package com.example.schemasync.integration;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.schemasync.dto.ChangeSetDto;
import com.example.schemasync.dto.SchemaDiffDto;
import com.example.schemasync.entity.SchemaDiff;
import com.example.schemasync.service.interfaces.SchemaDiffService;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class SchemaDiffControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SchemaDiffService schemaDiffService;

    private static final String API_KEY = System.getenv().getOrDefault("API_KEY", "dummy-key-for-local");

    @DynamicPropertySource
    static void setApiKey(DynamicPropertyRegistry registry) {
        registry.add("security.apiKey", () -> API_KEY);
    }

    private MockHttpServletRequestBuilder authorized(MockHttpServletRequestBuilder builder) {
        return builder.header("X-API-KEY", API_KEY);
    }

    @Test
    void testListChangeSets() throws Exception {
        List<ChangeSetDto> changesets = Arrays.asList(
                new ChangeSetDto("1", "author", "schema", "table", "CreateTableChange", "desc1"),
                new ChangeSetDto("2", "author", "schema", "table", "AddColumnChange", "desc2")
        );
        Mockito.when(schemaDiffService.listChangeSets(eq(1L))).thenReturn(changesets);

        mockMvc.perform(authorized(get("/api/diffs/1/changesets")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("1"))
                .andExpect(jsonPath("$[1].type").value("AddColumnChange"));
    }

    @Test
    void testApplyFilteredChangeSets_Postgres() throws Exception {
        Mockito.when(schemaDiffService.applyFilteredChangeSets(eq(1L), anyList(), eq("POSTGRES")))
                .thenReturn("Filtered changesets applied successfully.");

        String body = "{\n" +
                "  \"selectedChangeSetIds\": [\"1:author\"],\n" +
                "  \"dbType\": \"POSTGRES\",\n" +
                "  \"jdbcUrl\": \"jdbc:postgresql://localhost:5432/test\",\n" +
                "  \"username\": \"user\",\n" +
                "  \"password\": \"pass\"\n" +
                "}";

        mockMvc.perform(authorized(post("/api/diffs/1/apply-filtered")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)))
                .andExpect(status().isOk())
                .andExpect(content().string("Filtered changesets applied successfully."));
    }

    @Test
    void testApplyFilteredChangeSets_OracleStub() throws Exception {
        Mockito.when(schemaDiffService.applyFilteredChangeSets(eq(1L), anyList(), eq("ORACLE")))
                .thenReturn("Oracle support is not implemented yet.");

        String body = "{\n" +
                "  \"selectedChangeSetIds\": [\"1:author\"],\n" +
                "  \"dbType\": \"ORACLE\",\n" +
                "  \"jdbcUrl\": \"jdbc:oracle:thin:@localhost:1521:xe\",\n" +
                "  \"username\": \"user\",\n" +
                "  \"password\": \"pass\"\n" +
                "}";

        mockMvc.perform(authorized(post("/api/diffs/1/apply-filtered")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)))
                .andExpect(status().isOk())
                .andExpect(content().string("Oracle support is not implemented yet."));
    }

    @Test
    void testCreateDiff() throws Exception {
        SchemaDiff entity = new SchemaDiff();
        entity.setId(1L);
        entity.setAuthor("author");
        entity.setCreatedAt(java.time.Instant.now());
        entity.setStatus(SchemaDiff.Status.PENDING);
        entity.setDescription("desc");
        SchemaDiffDto dto = new SchemaDiffDto(entity);
        Mockito.when(schemaDiffService.createAndGenerateDiff(any())).thenReturn(dto);
        String body = "{\"sourceDatabase\":{\"jdbcUrl\":\"url\",\"username\":\"user\",\"password\":\"pass\"},\"targetDatabases\":[],\"changeLogFile\":\"file\"}";
        mockMvc.perform(authorized(post("/api/diffs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)))
                .andExpect(status().isOk());
    }

    @Test
    void testListDiffs() throws Exception {
        Mockito.when(schemaDiffService.listDiffs(any())).thenReturn(Collections.emptyList());
        mockMvc.perform(authorized(get("/api/diffs")))
                .andExpect(status().isOk());
    }

    @Test
    void testGetDiffContent() throws Exception {
        byte[] data = "test".getBytes(StandardCharsets.UTF_8);
        Mockito.when(schemaDiffService.getDiffContent(anyLong())).thenReturn(data);
        mockMvc.perform(authorized(get("/api/diffs/1/content")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("schema-diff-1.xml")));
    }

    @Test
    void testGetValidationLog() throws Exception {
        Mockito.when(schemaDiffService.getValidationLog(anyLong())).thenReturn("log");
        mockMvc.perform(authorized(get("/api/diffs/1/validation-log")))
                .andExpect(status().isOk())
                .andExpect(content().string("log"));
    }

    @Test
    void testApproveDiff() throws Exception {
        mockMvc.perform(authorized(post("/api/diffs/1/approve")))
                .andExpect(status().isNoContent());
    }

    @Test
    void testGetDiffById() throws Exception {
        SchemaDiff entity = new SchemaDiff();
        entity.setId(1L);
        entity.setAuthor("author");
        entity.setCreatedAt(java.time.Instant.now());
        entity.setStatus(SchemaDiff.Status.PENDING);
        entity.setDescription("desc");
        SchemaDiffDto dto = new SchemaDiffDto(entity);
        Mockito.when(schemaDiffService.getDiffById(anyLong())).thenReturn(dto);
        mockMvc.perform(authorized(get("/api/diffs/1")))
                .andExpect(status().isOk());
    }

    @Test
    void testRejectDiff() throws Exception {
        mockMvc.perform(authorized(post("/api/diffs/1/reject")
                .contentType(MediaType.TEXT_PLAIN)
                .content("reason")))
                .andExpect(status().isNoContent());
    }

    @Test
    void testValidateMergeConfig() throws Exception {
        String body = "{\"changeLogFile\":\"file\",\"sourceDatabase\":{},\"targetDatabases\":[{}]}";
        mockMvc.perform(authorized(post("/api/merge/validate-config")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)))
                .andExpect(status().isOk());
    }

    @Test
    void testValidateChangelog() throws Exception {
        Mockito.when(schemaDiffService.getValidationLog(anyLong())).thenReturn("OK");
        mockMvc.perform(authorized(post("/api/merge/validate-changelog")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"changeLogFile\":\"file\"}")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testValidateSource() throws Exception {
        String body = "{\"sourceDatabase\":{\"jdbcUrl\":\"url\",\"username\":\"user\",\"password\":\"pass\"},\"targetDatabases\":[],\"changeLogFile\":\"file\"}";
        mockMvc.perform(authorized(post("/api/merge/validate-source")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testValidateTarget() throws Exception {
        String body = "{\"sourceDatabase\":{\"jdbcUrl\":\"url\",\"username\":\"user\",\"password\":\"pass\"},\"targetDatabases\":[{\"jdbcUrl\":\"url\",\"username\":\"user\",\"password\":\"pass\",\"name\":\"db\"}],\"changeLogFile\":\"file\"}";
        mockMvc.perform(authorized(post("/api/merge/validate-target")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)))
                .andExpect(status().isOk());
    }

    @Test
    void testTransferData() throws Exception {
        String body = "{\"sourceDatabase\":{\"jdbcUrl\":\"url\",\"username\":\"user\",\"password\":\"pass\"},\"targetDatabases\":[{\"jdbcUrl\":\"url\",\"username\":\"user\",\"password\":\"pass\",\"name\":\"db\"}],\"changeLogFile\":\"file\"}";
        mockMvc.perform(authorized(post("/api/merge/transfer-data")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void testChangelogToJson() throws Exception {
        Mockito.when(schemaDiffService.getValidationLog(anyLong())).thenReturn("json");
        mockMvc.perform(authorized(post("/api/merge/changelog-to-json")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"changeLogFile\":\"file\"}")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testGetParsedDiff() throws Exception {
        Mockito.when(schemaDiffService.getParsedDiff(anyLong())).thenReturn(Collections.emptyMap());
        mockMvc.perform(authorized(get("/api/diffs/1/parsed")))
                .andExpect(status().isOk());
    }

    @Test
    void testMissingApiKeyReturns401() throws Exception {
        mockMvc.perform(get("/api/diffs")).andExpect(status().isUnauthorized());
    }

    @Test
    void testInvalidApiKeyReturns401() throws Exception {
        mockMvc.perform(get("/api/diffs").header("X-API-KEY", "wrong-key")).andExpect(status().isUnauthorized());
    }

    @Test
    void testValidApiKeyAllowsAccess() throws Exception {
        Mockito.when(schemaDiffService.listDiffs(any())).thenReturn(Collections.emptyList());
        mockMvc.perform(authorized(get("/api/diffs"))).andExpect(status().isOk());
    }

    @Test
    @Disabled("Rate limiting filter is not reliably testable in this context")
    void testRateLimitingReturns429() throws Exception {
        Mockito.when(schemaDiffService.listDiffs(any())).thenReturn(Collections.emptyList());
        // First two requests should pass
        mockMvc.perform(authorized(get("/api/diffs"))).andExpect(status().isOk());
        mockMvc.perform(authorized(get("/api/diffs"))).andExpect(status().isOk());
        // Third request should be rate limited
        mockMvc.perform(authorized(get("/api/diffs"))).andExpect(status().isTooManyRequests());
    }

    @Test
    void testInvalidPayloadReturns400() throws Exception {
        mockMvc.perform(authorized(post("/api/diffs")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{invalid-json}"))).andExpect(status().isBadRequest());
    }
} 