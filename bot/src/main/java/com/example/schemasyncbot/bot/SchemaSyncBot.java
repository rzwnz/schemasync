package com.example.schemasyncbot.bot;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Update;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class SchemaSyncBot {

    private final TelegramCommandHandler handler;
    private final TelegramBot bot;

    @Value("${telegram.bot.username:}")
    private String botUsername;

    public SchemaSyncBot(TelegramCommandHandler handler, TelegramBot bot) {
        this.handler = handler;
        this.bot = bot;
    }

    @PostConstruct
    public void start() {
        bot.setUpdatesListener(updates -> {
            for (Update update : updates) {
                handler.handle(update);
            }
            return UpdatesListener.CONFIRMED_UPDATES_ALL;
        });
    }

    public String getBotUsername() {
        return botUsername;
    }
}