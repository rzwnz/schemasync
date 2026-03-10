package com.example.schemasyncbot.commands;

import com.example.schemasyncbot.model.SessionData;
import com.example.schemasyncbot.service.JenkinsService;
import com.example.schemasyncbot.service.RedisSessionService;
import com.example.schemasyncbot.utils.MessageTemplates;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.SendMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fetches and displays the last Jenkins build logs for the selected pipeline.
 * Truncates output to 4000 characters to stay within Telegram message limits.
 */
public class LogsCommand implements BotCommand {

    private static final Logger log = LoggerFactory.getLogger(LogsCommand.class);
    private static final int MAX_LOG_LENGTH = 4000;

    private final TelegramBot bot;
    private final JenkinsService jenkins;
    private final RedisSessionService redisSessionService;

    public LogsCommand(TelegramBot bot, JenkinsService jenkins, RedisSessionService redisSessionService) {
        this.bot = bot;
        this.jenkins = jenkins;
        this.redisSessionService = redisSessionService;
    }

    @Override
    public boolean handle(Update update) {
        if (update.message() != null && "/logs".equals(update.message().text())) {
            Long chatId = update.message().chat().id();
            SessionData session = redisSessionService.getSession(chatId);

            if (session.getSelectedPipeline() == null) {
                bot.execute(new SendMessage(chatId, MessageTemplates.noPipelineSelected())
                        .parseMode(ParseMode.HTML));
                return true;
            }

            String pipeline = session.getSelectedPipeline();
            jenkins.getLastBuildLogs(pipeline).subscribe(
                    logs -> {
                        String truncated = logs.length() > MAX_LOG_LENGTH
                                ? logs.substring(0, MAX_LOG_LENGTH) + "\n… [TRUNCATED]"
                                : logs;
                        String message = MessageTemplates.logsHeader(pipeline)
                                + "\n<pre>" + MessageTemplates.escapeHtml(truncated) + "</pre>";
                        bot.execute(new SendMessage(chatId, message).parseMode(ParseMode.HTML));
                    },
                    error -> {
                        log.error("Failed to fetch logs for pipeline '{}' chat {}", pipeline, chatId, error);
                        bot.execute(new SendMessage(chatId, MessageTemplates.error("Failed to fetch logs: " + error.getMessage()))
                                .parseMode(ParseMode.HTML));
                    }
            );
            return true;
        }
        return false;
    }

    @Override
    public String getCommand() {
        return "/logs";
    }
}
