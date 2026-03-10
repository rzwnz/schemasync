package com.example.schemasyncbot.commands;

import com.example.schemasyncbot.model.SessionData;
import com.example.schemasyncbot.service.JenkinsService;
import com.example.schemasyncbot.service.RedisSessionService;
import com.example.schemasyncbot.utils.MessageTemplates;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.SendMessage;

import java.util.List;

/**
 * Validates all parameters are present and triggers the Jenkins build.
 * Unlike /diff, this triggers the job directly without polling for an artifact.
 */
public class ConfirmCommand implements BotCommand {

    private final TelegramBot bot;
    private final JenkinsService jenkins;
    private final RedisSessionService redisSessionService;

    public ConfirmCommand(TelegramBot bot, JenkinsService jenkins, RedisSessionService redisSessionService) {
        this.bot = bot;
        this.jenkins = jenkins;
        this.redisSessionService = redisSessionService;
    }

    @Override
    public boolean handle(Update update) {
        if (update.message() != null && "/confirm".equals(update.message().text())) {
            Long chatId = update.message().chat().id();
            SessionData session = redisSessionService.getSession(chatId);

            if (session.getSelectedPipeline() == null) {
                bot.execute(new SendMessage(chatId, MessageTemplates.noPipelineSelected())
                        .parseMode(ParseMode.HTML));
                return true;
            }

            // Validate all required params
            List<String> missing = session.getMissingParams();
            if (!missing.isEmpty()) {
                bot.execute(new SendMessage(chatId, MessageTemplates.parametersIncomplete(missing))
                        .parseMode(ParseMode.HTML));
                return true;
            }

            if (!session.areAllParamsConfirmed()) {
                bot.execute(new SendMessage(chatId,
                        "⚠️ Please confirm all parameters before proceeding.\n"
                                + "Tap each parameter → ✅ Confirm Value.")
                        .parseMode(ParseMode.HTML));
                return true;
            }

            bot.execute(new SendMessage(chatId, MessageTemplates.allParametersConfirmed())
                    .parseMode(ParseMode.HTML));

            jenkins.triggerBuild(session.getSelectedPipeline(), session.getEnv()).subscribe(
                    msg -> bot.execute(new SendMessage(chatId,
                            "✅ Job triggered for <b>" + MessageTemplates.escapeHtml(session.getSelectedPipeline()) + "</b>")
                            .parseMode(ParseMode.HTML)),
                    err -> bot.execute(new SendMessage(chatId,
                            MessageTemplates.error("Failed to trigger job: " + err.getMessage()))
                            .parseMode(ParseMode.HTML))
            );
            return true;
        }
        return false;
    }

    @Override
    public String getCommand() {
        return "/confirm";
    }
}
