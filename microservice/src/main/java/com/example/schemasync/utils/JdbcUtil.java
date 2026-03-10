package com.example.schemasync.utils;

import java.sql.Connection;
import java.sql.DriverManager;

public class JdbcUtil {
  public static void validateConnection(String url, String user, String pass) throws Exception {
    try (Connection c = DriverManager.getConnection(url, user, pass)) { /* no-op */ }
  }
}
