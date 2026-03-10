package com.example.schemasyncbot.controller;

import com.example.schemasyncbot.bot.TelegramCommandHandler;
import com.pengrad.telegrambot.model.Update;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class WebhookController {
    private final TelegramCommandHandler handler;

    /**
     * Secret token set when registering the webhook with Telegram
     * (setWebhook?secret_token=...). Telegram sends it back in every
     * webhook request via the X-Telegram-Bot-Api-Secret-Token header.
     * If blank, validation is skipped (NOT recommended in production).
     */
    @Value("${bot.webhook-secret:}")
    private String webhookSecret;

    public WebhookController(TelegramCommandHandler handler) {
        this.handler = handler;
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> onUpdate(
            @RequestHeader(value = "X-Telegram-Bot-Api-Secret-Token", required = false) String secretToken,
            @RequestBody Update update) {

        // Validate the secret token if one is configured
        if (webhookSecret != null && !webhookSecret.isBlank()) {
            if (secretToken == null || !webhookSecret.equals(secretToken)) {
                return ResponseEntity.status(401).build();
            }
        }

        handler.handle(update);
        return ResponseEntity.ok().build();
    }
} 