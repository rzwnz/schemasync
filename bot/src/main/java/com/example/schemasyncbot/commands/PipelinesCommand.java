package com.example.schemasyncbot.commands;

import com.example.schemasyncbot.model.BotState;
import com.example.schemasyncbot.model.SessionData;
import com.example.schemasyncbot.service.JenkinsService;
import com.example.schemasyncbot.service.RedisSessionService;
import com.example.schemasyncbot.utils.MessageTemplates;
import com.fasterxml.jackson.databind.JsonNode;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.SendMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lists available Jenkins pipelines as inline-keyboard buttons.
 * Transitions session to {@link BotState#SELECTING_PIPELINE}.
 */
public class PipelinesCommand implements BotCommand {

    private static final Logger log = LoggerFactory.getLogger(PipelinesCommand.class);

    private final TelegramBot bot;
    private final JenkinsService jenkins;
    private final RedisSessionService redisSessionService;

    public PipelinesCommand(TelegramBot bot, JenkinsService jenkins, RedisSessionService redisSessionService) {
        this.bot = bot;
        this.jenkins = jenkins;
        this.redisSessionService = redisSessionService;
    }

    @Override
    public boolean handle(Update update) {
        if (update.message() != null && "/pipelines".equals(update.message().text())) {
            Long chatId = update.message().chat().id();

            SessionData session = redisSessionService.getSession(chatId);
            session.setState(BotState.SELECTING_PIPELINE);
            redisSessionService.saveSession(chatId, session);

            jenkins.listJobs().subscribe(jobsNode -> {
                if (jobsNode.has("jobs")) {
                    InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
                    for (JsonNode job : jobsNode.get("jobs")) {
                        String name = job.get("name").asText();
                        String displayName = job.has("displayName") ? job.get("displayName").asText() : name;
                        keyboard.addRow(new InlineKeyboardButton("📌 " + displayName)
                                .callbackData("select_pipeline:" + name));
                    }
                    keyboard.addRow(new InlineKeyboardButton("❌ Cancel").callbackData("cancel_pipeline_select"));
                    bot.execute(new SendMessage(chatId, MessageTemplates.pipelineListHeader())
                            .parseMode(ParseMode.HTML)
                            .replyMarkup(keyboard));
                } else {
                    bot.execute(new SendMessage(chatId, MessageTemplates.noPipelinesFound())
                            .parseMode(ParseMode.HTML));
                }
            }, err -> {
                log.error("Failed to list Jenkins jobs for chat {}", chatId, err);
                bot.execute(new SendMessage(chatId, MessageTemplates.error("Failed to fetch Jenkins jobs: " + MessageTemplates.safeErrorMessage(err)))
                        .parseMode(ParseMode.HTML));
            });
            return true;
        }
        return false;
    }

    @Override
    public String getCommand() {
        return "/pipelines";
    }
}
