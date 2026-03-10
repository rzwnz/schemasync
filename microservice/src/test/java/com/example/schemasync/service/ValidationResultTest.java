package com.example.schemasync.service;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class ValidationResultTest {

    @Test
    void success_returnsSuccessTrue() {
        ValidationResult result = ValidationResult.success("all good");
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getLog()).isEqualTo("all good");
    }

    @Test
    void success_getErrorMessage_returnsNull() {
        ValidationResult result = ValidationResult.success("log output");
        assertThat(result.getErrorMessage()).isNull();
    }

    @Test
    void failure_returnsSuccessFalse() {
        ValidationResult result = ValidationResult.failure("something broke");
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getLog()).isEqualTo("something broke");
    }

    @Test
    void failure_getErrorMessage_returnsLog() {
        ValidationResult result = ValidationResult.failure("err msg");
        assertThat(result.getErrorMessage()).isEqualTo("err msg");
    }

    @Test
    void success_withEmptyLog() {
        ValidationResult result = ValidationResult.success("");
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getLog()).isEmpty();
        assertThat(result.getErrorMessage()).isNull();
    }

    @Test
    void failure_withNullLog() {
        ValidationResult result = ValidationResult.failure(null);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getLog()).isNull();
        assertThat(result.getErrorMessage()).isNull(); // log is null
    }
}
