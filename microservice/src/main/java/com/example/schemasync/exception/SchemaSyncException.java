package com.example.schemasync.exception;

import org.springframework.http.HttpStatus;

/**
 * Base exception for all domain-specific errors in the SchemaSync microservice.
 * Carries an HTTP status code and an error code string for structured API responses.
 */
public class SchemaSyncException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    public SchemaSyncException(String message, HttpStatus status, String errorCode) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public SchemaSyncException(String message, Throwable cause, HttpStatus status, String errorCode) {
        super(message, cause);
        this.status = status;
        this.errorCode = errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
