package com.example.schemasyncbot.commands;

import com.pengrad.telegrambot.model.Update;

public interface BotCommand {
    boolean handle(Update update);
    String getCommand();
} 