package com.example.schemasync.dto;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class ChangeSetDtoTest {

    @Test
    void allArgsConstructor_setsAllFields() {
        ChangeSetDto dto = new ChangeSetDto("1", "author", "public", "users", "createTable", "description");
        assertThat(dto.getId()).isEqualTo("1");
        assertThat(dto.getAuthor()).isEqualTo("author");
        assertThat(dto.getSchema()).isEqualTo("public");
        assertThat(dto.getTable()).isEqualTo("users");
        assertThat(dto.getType()).isEqualTo("createTable");
        assertThat(dto.getDescription()).isEqualTo("description");
    }

    @Test
    void noArgsConstructor_allFieldsNull() {
        ChangeSetDto dto = new ChangeSetDto();
        assertThat(dto.getId()).isNull();
        assertThat(dto.getAuthor()).isNull();
        assertThat(dto.getSchema()).isNull();
        assertThat(dto.getTable()).isNull();
        assertThat(dto.getType()).isNull();
        assertThat(dto.getDescription()).isNull();
    }

    @Test
    void setters_overrideValues() {
        ChangeSetDto dto = new ChangeSetDto();
        dto.setId("id1");
        dto.setAuthor("auth1");
        dto.setSchema("sch1");
        dto.setTable("tbl1");
        dto.setType("type1");
        dto.setDescription("desc1");

        assertThat(dto.getId()).isEqualTo("id1");
        assertThat(dto.getAuthor()).isEqualTo("auth1");
        assertThat(dto.getSchema()).isEqualTo("sch1");
        assertThat(dto.getTable()).isEqualTo("tbl1");
        assertThat(dto.getType()).isEqualTo("type1");
        assertThat(dto.getDescription()).isEqualTo("desc1");
    }

    @Test
    void constructor_withNullFields() {
        ChangeSetDto dto = new ChangeSetDto("1", "a", null, null, null, null);
        assertThat(dto.getId()).isEqualTo("1");
        assertThat(dto.getTable()).isNull();
        assertThat(dto.getType()).isNull();
        assertThat(dto.getDescription()).isNull();
    }
}
