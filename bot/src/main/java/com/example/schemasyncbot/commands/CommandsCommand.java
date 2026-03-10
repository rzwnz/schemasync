package com.example.schemasyncbot.commands;

import com.example.schemasyncbot.utils.MessageTemplates;
import com.example.schemasyncbot.utils.localization.Strings;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.SendMessage;

import java.util.Locale;
import java.util.Map;

/**
 * Shows a compact list of all available commands with descriptions.
 */
public class CommandsCommand implements BotCommand {

    private final TelegramBot bot;

    public CommandsCommand(TelegramBot bot) {
        this.bot = bot;
    }

    @Override
    public boolean handle(Update update) {
        if (update.message() != null && "/commands".equals(update.message().text())) {
            Long chatId = update.message().chat().id();

            StringBuilder sb = new StringBuilder();
            sb.append("📋 <b>Available Commands</b>\n");
            sb.append("───────────────────\n\n");

            Map<String, String> commands = Strings.getCommandDescriptions(Locale.ENGLISH);
            commands.forEach((cmd, desc) ->
                    sb.append("• <code>").append(MessageTemplates.escapeHtml(cmd)).append("</code>")
                            .append(" — ").append(MessageTemplates.escapeHtml(desc)).append("\n")
            );

            sb.append("\n<b>Quick Workflow:</b>\n");
            sb.append("  /pipelines → configure → /diff → /approve");

            bot.execute(new SendMessage(chatId, sb.toString()).parseMode(ParseMode.HTML));
            return true;
        }
        return false;
    }

    @Override
    public String getCommand() {
        return "/commands";
    }
}
