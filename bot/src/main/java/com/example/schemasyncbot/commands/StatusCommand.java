package com.example.schemasyncbot.commands;

import com.example.schemasyncbot.model.SessionData;
import com.example.schemasyncbot.service.RedisSessionService;
import com.example.schemasyncbot.utils.MessageTemplates;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.SendMessage;

/**
 * Shows the current session status — state, pipeline, parameter progress.
 */
public class StatusCommand implements BotCommand {

    private final TelegramBot bot;
    private final RedisSessionService redisSessionService;

    public StatusCommand(TelegramBot bot, RedisSessionService redisSessionService) {
        this.bot = bot;
        this.redisSessionService = redisSessionService;
    }

    @Override
    public boolean handle(Update update) {
        if (update.message() != null && "/status".equals(update.message().text())) {
            Long chatId = update.message().chat().id();
            SessionData session = redisSessionService.getSession(chatId);
            bot.execute(new SendMessage(chatId, MessageTemplates.status(session))
                    .parseMode(ParseMode.HTML));
            return true;
        }
        return false;
    }

    @Override
    public String getCommand() {
        return "/status";
    }
}
