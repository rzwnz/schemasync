package com.example.schemasync.dto;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class CreateDiffRequestTest {

    @Test
    void gettersAndSetters_allFields() {
        CreateDiffRequest req = new CreateDiffRequest();

        req.setAuthor("testAuthor");
        req.setDescription("testDesc");
        req.setIncludeSchemas(List.of("public", "sales"));
        req.setExcludeSchemas(List.of("temp"));

        assertThat(req.getAuthor()).isEqualTo("testAuthor");
        assertThat(req.getDescription()).isEqualTo("testDesc");
        assertThat(req.getIncludeSchemas()).containsExactly("public", "sales");
        assertThat(req.getExcludeSchemas()).containsExactly("temp");
    }

    @Test
    void defaults_areNull() {
        CreateDiffRequest req = new CreateDiffRequest();
        assertThat(req.getAuthor()).isNull();
        assertThat(req.getDescription()).isNull();
        assertThat(req.getIncludeSchemas()).isNull();
        assertThat(req.getExcludeSchemas()).isNull();
    }

    @Test
    void setters_overwriteValues() {
        CreateDiffRequest req = new CreateDiffRequest();
        req.setAuthor("a");
        req.setAuthor("b");
        assertThat(req.getAuthor()).isEqualTo("b");
    }
}
