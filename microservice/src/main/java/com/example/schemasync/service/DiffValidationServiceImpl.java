package com.example.schemasync.service;

import com.example.schemasync.service.interfaces.DiffValidationService;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.DirectoryResourceAccessor;
import liquibase.resource.ResourceAccessor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.StringWriter;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;

@Service
public class DiffValidationServiceImpl implements DiffValidationService {

    @Value("${validation.url}")
    private String validationDbUrl;
    @Value("${validation.username}")
    private String validationDbUser;
    @Value("${validation.password}")
    private String validationDbPass;

    @Override
    public ValidationResult validateDiff(Path changelogPath) {
        // The directory containing the changelog
        String changelogDir = changelogPath.getParent().toString();
        // Just the filename, e.g. "schema-diff-12.xml"
        String changelogFile = changelogPath.getFileName().toString();
    
        ResourceAccessor fsAccessor;
        try {
            fsAccessor = new DirectoryResourceAccessor(Path.of(changelogDir));
        } catch (java.io.IOException e) {
            return ValidationResult.failure("Resource access error: "+e.getMessage());
        }
        try (Connection conn = DriverManager.getConnection(validationDbUrl, validationDbUser, validationDbPass)) {
            Database validationDb = DatabaseFactory.getInstance()
                .findCorrectDatabaseImplementation(new JdbcConnection(conn));
        
            try (// Liquibase will search the changelogFile *inside* changelogDir
            Liquibase liquibase = new Liquibase(changelogFile, fsAccessor, validationDb)) {
                StringWriter writer = new StringWriter();
                liquibase.update(new Contexts(), new LabelExpression(), writer);
                return ValidationResult.success(writer.toString());
            }
        } catch (Exception e) {
            return ValidationResult.failure(e.getMessage());
        }
    }

}