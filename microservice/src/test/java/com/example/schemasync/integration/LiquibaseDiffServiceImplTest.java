package com.example.schemasync.integration;

import com.example.schemasync.dto.ChangeSetDto;
import com.example.schemasync.service.LiquibaseDiffServiceImpl;
import liquibase.exception.LiquibaseException;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URL;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class LiquibaseDiffServiceImplTest {
    @Test
    @Disabled("LiquibaseDiffServiceImplTest is not supported in this test context")
    void testListChangeSets() throws LiquibaseException {
        LiquibaseDiffServiceImpl svc = new LiquibaseDiffServiceImpl();
        URL url = getClass().getClassLoader().getResource("test-changelog.xml");
        assertNotNull(url, "Test changelog file should exist");
        File changelog = new File(url.getFile());
        List<ChangeSetDto> changesets = svc.listChangeSets(changelog);
        assertFalse(changesets.isEmpty());
        assertEquals("1", changesets.get(0).getId());
        assertEquals("author", changesets.get(0).getAuthor());
    }
} 