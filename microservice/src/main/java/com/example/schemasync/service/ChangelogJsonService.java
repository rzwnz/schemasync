package com.example.schemasync.service;

import java.nio.file.Path;

import org.springframework.stereotype.Service;

import com.example.schemasync.service.interfaces.IChangelogJsonService;
import com.fasterxml.jackson.databind.ObjectMapper;

import liquibase.changelog.DatabaseChangeLog;
import liquibase.parser.ChangeLogParserFactory;
import liquibase.resource.DirectoryResourceAccessor;

@Service
public class ChangelogJsonService implements IChangelogJsonService {
  private final ObjectMapper mapper = new ObjectMapper();

  @Override
  public String changelogToJson(String changelogPath) throws Exception {
    Path path = Path.of(changelogPath).toAbsolutePath();
    DirectoryResourceAccessor accessor = new DirectoryResourceAccessor(path.getParent());
    var parser = ChangeLogParserFactory.getInstance()
                   .getParser(path.getFileName().toString(), accessor);
    DatabaseChangeLog dbch = parser.parse(path.getFileName().toString(), null, accessor);
    return mapper.writeValueAsString(dbch);
  }
}
