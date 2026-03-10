package com.example.schemasyncbot.commands;

import com.example.schemasyncbot.model.BotState;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Applies the reviewed diff by triggering a Jenkins build with APPLY_DIFF=true.
 * Transitions to {@link BotState#APPLYING} and clears the session on success.
 */
public class ApproveCommand implements BotCommand {

    private static final Logger log = LoggerFactory.getLogger(ApproveCommand.class);

    private final TelegramBot bot;
    private final JenkinsService jenkins;
    private final RedisSessionService redisSessionService;

    public ApproveCommand(TelegramBot bot, JenkinsService jenkins, RedisSessionService redisSessionService) {
        this.bot = bot;
        this.jenkins = jenkins;
        this.redisSessionService = redisSessionService;
    }

    @Override
    public boolean handle(Update update) {
        if (update.message() != null && "/approve".equals(update.message().text())) {
            Long chatId = update.message().chat().id();
            SessionData session = redisSessionService.getSession(chatId);

            if (session.getSelectedPipeline() == null) {
                bot.execute(new SendMessage(chatId, MessageTemplates.noPipelineSelected())
                        .parseMode(ParseMode.HTML));
                return true;
            }

            // Check missing params
            List<String> missing = session.getMissingParams();
            if (!missing.isEmpty()) {
                bot.execute(new SendMessage(chatId, MessageTemplates.parametersIncomplete(missing))
                        .parseMode(ParseMode.HTML));
                return true;
            }

            String pipeline = session.getSelectedPipeline();
            Map<String, String> params = session.getLastBuildParams() != null
                    ? new HashMap<>(session.getLastBuildParams())
                    : new HashMap<>(session.getEnv());
            params.put("APPLY_DIFF", "true");
            params.put("BOT_USER_ID", chatId.toString());
            params.put("PIPELINE_NAME", pipeline);

            String schema = params.getOrDefault("SCHEMA_NAME", null);

            session.setState(BotState.APPLYING);
            redisSessionService.saveSession(chatId, session);

            bot.execute(new SendMessage(chatId, MessageTemplates.applyTriggered(pipeline, schema))
                    .parseMode(ParseMode.HTML));

            jenkins.triggerBuild(pipeline, params).subscribe(
                    msg -> {
                        log.info("Apply job triggered for chat {} pipeline '{}'", chatId, pipeline);
                        redisSessionService.deleteSession(chatId);
                        bot.execute(new SendMessage(chatId, MessageTemplates.applySuccess())
                                .parseMode(ParseMode.HTML));
                    },
                    error -> {
                        log.error("Failed to trigger apply for chat {}", chatId, error);
                        session.setState(BotState.REVIEWING_DIFF);
                        redisSessionService.saveSession(chatId, session);
                        bot.execute(new SendMessage(chatId, MessageTemplates.error("Failed to trigger apply job: " + MessageTemplates.safeErrorMessage(error)))
                                .parseMode(ParseMode.HTML));
                    }
            );
            return true;
        }
        return false;
    }

    @Override
    public String getCommand() {
        return "/approve";
    }
}
