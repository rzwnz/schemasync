package com.example.schemasync.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a diff is not in the required state for the requested operation.
 */
public class InvalidDiffStateException extends SchemaSyncException {

    public InvalidDiffStateException(String message) {
        super(message, HttpStatus.CONFLICT, "INVALID_DIFF_STATE");
    }
}
