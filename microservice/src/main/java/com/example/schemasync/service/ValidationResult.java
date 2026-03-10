package com.example.schemasync.service;

public class ValidationResult {
    private final boolean success;
    private final String log;

    private ValidationResult(boolean success, String log) {
        this.success = success;
        this.log = log;
    }

    public static ValidationResult success(String log) {
        return new ValidationResult(true, log);
    }
    public static ValidationResult failure(String log) {
        return new ValidationResult(false, log);
    }
    public boolean isSuccess() { return success; }
    public String getLog() { return log; }
    public String getErrorMessage() { return success ? null : log; }
}
