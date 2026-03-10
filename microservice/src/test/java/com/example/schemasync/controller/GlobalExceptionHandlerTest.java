package com.example.schemasync.controller;

import com.example.schemasync.exception.DiffNotFoundException;
import com.example.schemasync.exception.DiffOperationException;
import com.example.schemasync.exception.InvalidDiffStateException;
import com.example.schemasync.exception.SchemaSyncException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleSchemaSyncException_diffNotFound_returns404() {
        DiffNotFoundException ex = new DiffNotFoundException(42L);
        ResponseEntity<Map<String, Object>> response = handler.handleSchemaSyncException(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("error", "DIFF_NOT_FOUND");
        assertThat(response.getBody()).containsEntry("status", 404);
        assertThat(response.getBody().get("message").toString()).contains("42");
    }

    @Test
    void handleSchemaSyncException_diffOperation_returns500() {
        DiffOperationException ex = new DiffOperationException("operation failed");
        ResponseEntity<Map<String, Object>> response = handler.handleSchemaSyncException(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).containsEntry("error", "DIFF_OPERATION_FAILED");
    }

    @Test
    void handleSchemaSyncException_invalidDiffState_returns409() {
        InvalidDiffStateException ex = new InvalidDiffStateException("wrong state");
        ResponseEntity<Map<String, Object>> response = handler.handleSchemaSyncException(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("error", "INVALID_DIFF_STATE");
    }

    @Test
    void handleResponseStatusException_returnsMatchingStatus() {
        ResponseStatusException ex = new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        ResponseEntity<Map<String, Object>> response = handler.handleResponseStatusException(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).containsEntry("status", 403);
        assertThat(response.getBody()).containsEntry("error", "FORBIDDEN");
    }

    @Test
    void handleHttpMessageNotReadable_returnsBadRequest() {
        HttpMessageNotReadableException ex = mock(HttpMessageNotReadableException.class);
        when(ex.getMessage()).thenReturn("Invalid JSON");
        ResponseEntity<Map<String, Object>> response = handler.handleHttpMessageNotReadable(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("error", "BAD_REQUEST");
        assertThat(response.getBody()).containsEntry("message", "Malformed JSON request");
    }

    @Test
    void handleValidationErrors_returnsBadRequestWithFieldDetails() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);
        FieldError fe1 = new FieldError("obj", "author", "must not be empty");
        FieldError fe2 = new FieldError("obj", "description", "too long");
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fe1, fe2));

        ResponseEntity<Map<String, Object>> response = handler.handleValidationErrors(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("error", "VALIDATION_ERROR");
        assertThat(response.getBody().get("message").toString())
                .contains("author: must not be empty")
                .contains("description: too long");
    }

    @Test
    void handleIllegalState_returnsConflict() {
        IllegalStateException ex = new IllegalStateException("illegal state");
        ResponseEntity<Map<String, Object>> response = handler.handleIllegalState(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("error", "ILLEGAL_STATE");
        assertThat(response.getBody()).containsEntry("message", "illegal state");
    }

    @Test
    void handleIllegalArgument_returnsBadRequest() {
        IllegalArgumentException ex = new IllegalArgumentException("bad argument");
        ResponseEntity<Map<String, Object>> response = handler.handleIllegalArgument(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("error", "BAD_REQUEST");
        assertThat(response.getBody()).containsEntry("message", "bad argument");
    }

    @Test
    void handleGenericException_returnsInternalServerError() {
        Exception ex = new RuntimeException("unexpected");
        ResponseEntity<Map<String, Object>> response = handler.handleGenericException(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).containsEntry("error", "INTERNAL_ERROR");
        assertThat(response.getBody()).containsEntry("message", "An unexpected error occurred");
    }

    @Test
    void errorBody_containsTimestamp() {
        DiffNotFoundException ex = new DiffNotFoundException(1L);
        ResponseEntity<Map<String, Object>> response = handler.handleSchemaSyncException(ex);
        assertThat(response.getBody()).containsKey("timestamp");
    }

    @Test
    void handleSchemaSyncException_nullMessage_usesDefault() {
        SchemaSyncException ex = new SchemaSyncException(null, HttpStatus.BAD_REQUEST, "TEST_ERROR");
        ResponseEntity<Map<String, Object>> response = handler.handleSchemaSyncException(ex);
        assertThat(response.getBody()).containsEntry("message", "No details available");
    }
}
