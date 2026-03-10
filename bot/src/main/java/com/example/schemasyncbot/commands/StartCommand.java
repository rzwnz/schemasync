package com.example.schemasyncbot.commands;

import com.example.schemasyncbot.model.BotState;
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
 * Resets the session and shows the welcome message with quick-start keyboard.
 */
public class StartCommand implements BotCommand {

    private final TelegramBot bot;
    private final RedisSessionService redisSessionService;

    public StartCommand(TelegramBot bot, RedisSessionService redisSessionService) {
        this.bot = bot;
        this.redisSessionService = redisSessionService;
    }

    @Override
    public boolean handle(Update update) {
        if (update.message() != null && "/start".equals(update.message().text())) {
            Long chatId = update.message().chat().id();
            String username = update.message().from() != null
                    ? update.message().from().firstName()
                    : null;

            // Reset session to clean IDLE state
            SessionData session = new SessionData();
            session.setState(BotState.IDLE);
            redisSessionService.saveSession(chatId, session);

            InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup(
                    new InlineKeyboardButton[] { new InlineKeyboardButton("🔍 Select Pipeline").callbackData("pipelines") },
                    new InlineKeyboardButton[] { new InlineKeyboardButton("📖 Help").callbackData("help_overview") }
            );
            bot.execute(new SendMessage(chatId, MessageTemplates.welcome(username))
                    .parseMode(ParseMode.HTML)
                    .replyMarkup(keyboard));
            return true;
        }
        return false;
    }

    @Override
    public String getCommand() {
        return "/start";
    }
}
