package com.example.schemasyncbot.bot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class GlobalExceptionHandler implements Thread.UncaughtExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        logger.error("Uncaught exception in thread {}: {}", t.getName(), e.getMessage(), e);
        // Optionally: notify admin via Telegram here
    }
} 