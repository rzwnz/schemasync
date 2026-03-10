package com.example.schemasync.utils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;

class JsonUtilTest {

    @Test
    void toJson_and_fromJson_roundTrip() {
        Map<String, Object> map = Map.of("key", "value");
        String json = JsonUtil.toJson(map);
        assertThat(json).contains("key").contains("value");
        Map<?, ?> result = JsonUtil.fromJson(json, Map.class);
        assertThat(result)
                .isInstanceOf(Map.class)
                .extracting(m -> ((Map<?, ?>) m).get("key"))
                .isEqualTo("value");
    }

    @Test
    void fromJson_invalidJson_throws() {
        assertThatThrownBy(() -> JsonUtil.fromJson("not-json", Map.class))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("deserialization");
    }

    @Test
    void toJson_simpleString() {
        String json = JsonUtil.toJson("hello");
        assertThat(json).isEqualTo("\"hello\"");
    }

    @Test
    void toJson_list() {
        String json = JsonUtil.toJson(List.of(1, 2, 3));
        assertThat(json).isEqualTo("[1,2,3]");
    }

    @Test
    void fromJson_toList() {
        @SuppressWarnings("unchecked")
        List<Integer> list = JsonUtil.fromJson("[1,2,3]", List.class);
        assertThat(list).containsExactly(1, 2, 3);
    }

    @Test
    void toJson_null_returnsNullString() {
        String json = JsonUtil.toJson(null);
        assertThat(json).isEqualTo("null");
    }

    @Test
    void fromJson_emptyString_throws() {
        assertThatThrownBy(() -> JsonUtil.fromJson("", Map.class))
                .isInstanceOf(RuntimeException.class);
    }
} 