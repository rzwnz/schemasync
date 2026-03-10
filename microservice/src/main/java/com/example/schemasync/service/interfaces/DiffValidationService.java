package com.example.schemasync.service.interfaces;

import com.example.schemasync.service.ValidationResult;
import java.nio.file.Path;

public interface DiffValidationService {
    ValidationResult validateDiff(Path changelogPath);
}