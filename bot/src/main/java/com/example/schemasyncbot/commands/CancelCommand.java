package com.example.schemasyncbot.commands;

import com.example.schemasyncbot.model.SessionData;
import com.example.schemasyncbot.service.RedisSessionService;
import com.example.schemasyncbot.utils.MessageTemplates;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.SendMessage;

/**
 * Cancels the current workflow, resets the session, and returns to IDLE.
 */
public class CancelCommand implements BotCommand {

    private final TelegramBot bot;
    private final RedisSessionService redisSessionService;

    public CancelCommand(TelegramBot bot, RedisSessionService redisSessionService) {
        this.bot = bot;
        this.redisSessionService = redisSessionService;
    }

    @Override
    public boolean handle(Update update) {
        if (update.message() != null && "/cancel".equals(update.message().text())) {
            Long chatId = update.message().chat().id();

            // Reset session instead of deleting — preserves the object in Redis
            SessionData session = redisSessionService.getSession(chatId);
            session.reset();
            redisSessionService.saveSession(chatId, session);

            InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup(
                    new InlineKeyboardButton[] { new InlineKeyboardButton("🔍 Select Pipeline").callbackData("pipelines") },
                    new InlineKeyboardButton[] { new InlineKeyboardButton("📖 Help").callbackData("help_overview") }
            );
            bot.execute(new SendMessage(chatId, MessageTemplates.cancelled())
                    .parseMode(ParseMode.HTML)
                    .replyMarkup(keyboard));
            return true;
        }
        return false;
    }

    @Override
    public String getCommand() {
        return "/cancel";
    }
}
