package com.example.schemasync.service;

import java.nio.file.Path;

import org.springframework.stereotype.Service;

import com.example.schemasync.service.interfaces.IChangelogValidationService;

import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.exception.LiquibaseException;
import liquibase.resource.DirectoryResourceAccessor;

@Service
public class ChangelogValidationService implements IChangelogValidationService {

    @Override
    public String validateChangelog(String changelogPath) throws LiquibaseException {
        Database db = DatabaseFactory.getInstance()
            .openDatabase("offline:validate", null, null, null, null);

        try {
            Path path = Path.of(changelogPath).toAbsolutePath();
            DirectoryResourceAccessor accessor = new DirectoryResourceAccessor(path.getParent());
            try (Liquibase liquibase = new Liquibase(path.getFileName().toString(), accessor, db)) {
                liquibase.validate();
            }
        } catch (java.io.IOException e) {
            throw new LiquibaseException("Resource access error", e);
        }

        return "OK";
    }
}
