package com.example.schemasync.service.interfaces;

import liquibase.exception.LiquibaseException;

public interface IChangelogValidationService {
    String validateChangelog(String changelogPath) throws LiquibaseException;
}