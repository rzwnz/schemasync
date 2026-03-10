package com.example.schemasyncbot.commands;

import com.example.schemasyncbot.model.SessionData;
import com.example.schemasyncbot.service.RedisSessionService;
import com.example.schemasyncbot.utils.MessageTemplates;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.SendMessage;

/**
 * Deletes a parameter from the current session.
 * Usage: /delete &lt;KEY&gt;
 */
public class DeleteCommand implements BotCommand {

    private final TelegramBot bot;
    private final RedisSessionService redisSessionService;

    public DeleteCommand(TelegramBot bot, RedisSessionService redisSessionService) {
        this.bot = bot;
        this.redisSessionService = redisSessionService;
    }

    @Override
    public boolean handle(Update update) {
        if (update.message() != null && update.message().text() != null
                && update.message().text().startsWith("/delete ")) {
            Long chatId = update.message().chat().id();
            String key = update.message().text().substring("/delete ".length()).trim();

            SessionData session = redisSessionService.getSession(chatId);
            if (session.getEnv().containsKey(key)) {
                session.getEnv().remove(key);
                session.getConfirmedParams().remove(key);
                redisSessionService.saveSession(chatId, session);
                bot.execute(new SendMessage(chatId, MessageTemplates.paramDeleted(key))
                        .parseMode(ParseMode.HTML));
            } else {
                bot.execute(new SendMessage(chatId, MessageTemplates.paramNotFound(key))
                        .parseMode(ParseMode.HTML));
            }
            return true;
        }
        return false;
    }

    @Override
    public String getCommand() {
        return "/delete";
    }
}
