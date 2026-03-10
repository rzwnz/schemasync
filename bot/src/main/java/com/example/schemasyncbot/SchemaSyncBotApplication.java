package com.example.schemasyncbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import io.github.cdimascio.dotenv.Dotenv;

@SpringBootApplication
public class SchemaSyncBotApplication {
    public static void main(String[] args) {
        // Set global uncaught exception handler
        Thread.setDefaultUncaughtExceptionHandler(new com.example.schemasyncbot.bot.GlobalExceptionHandler());
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        dotenv.entries().forEach(e -> System.setProperty(e.getKey(), e.getValue()));
        SpringApplication.run(SchemaSyncBotApplication.class, args);
    }
}
