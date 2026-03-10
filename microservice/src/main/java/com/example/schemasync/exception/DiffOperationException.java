package com.example.schemasync.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a diff operation (apply, rollback, SQL generation, etc.) fails.
 */
public class DiffOperationException extends SchemaSyncException {

    public DiffOperationException(String message, Throwable cause) {
        super(message, cause, HttpStatus.INTERNAL_SERVER_ERROR, "DIFF_OPERATION_FAILED");
    }

    public DiffOperationException(String message) {
        super(message, HttpStatus.INTERNAL_SERVER_ERROR, "DIFF_OPERATION_FAILED");
    }
}
