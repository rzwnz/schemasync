package com.example.schemasyncbot.commands;

import com.example.schemasyncbot.utils.MessageTemplates;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.SendMessage;

/**
 * Displays the help overview with navigation buttons for
 * Workflow Guide and Parameter Reference sections.
 */
public class HelpCommand implements BotCommand {

    private final TelegramBot bot;

    public HelpCommand(TelegramBot bot) {
        this.bot = bot;
    }

    @Override
    public boolean handle(Update update) {
        if (update.message() != null && "/help".equals(update.message().text())) {
            Long chatId = update.message().chat().id();
            InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup(
                    new InlineKeyboardButton[] { new InlineKeyboardButton("🔄 Workflow Guide").callbackData("help_workflow") },
                    new InlineKeyboardButton[] { new InlineKeyboardButton("⚙️ Parameter Reference").callbackData("help_params") }
            );
            bot.execute(new SendMessage(chatId, MessageTemplates.helpOverview())
                    .parseMode(ParseMode.HTML)
                    .replyMarkup(keyboard));
            return true;
        }
        return false;
    }

    @Override
    public String getCommand() {
        return "/help";
    }
}
