package com.example.schemasync.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FileStorageUtil {
    private static String getBaseDir() {
        return System.getProperty("DIFF_STORE_PATH",
            System.getenv().getOrDefault("DIFF_STORE_PATH", "/diff-store"));
    }

    public static Path getDiffDir(Long diffId) throws IOException {
        Path dir = Paths.get(getBaseDir(), String.valueOf(diffId));
        Files.createDirectories(dir);
        return dir;
    }

    public static File getDiffFile(Long diffId) throws IOException {
        Path dir = getDiffDir(diffId);
        return dir.resolve("schema-diff.xml").toFile();
    }
}