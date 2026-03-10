package com.example.schemasync.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a requested schema diff is not found.
 */
public class DiffNotFoundException extends SchemaSyncException {

    public DiffNotFoundException(Long diffId) {
        super("Diff not found with ID: " + diffId, HttpStatus.NOT_FOUND, "DIFF_NOT_FOUND");
    }
}
