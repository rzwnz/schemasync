package com.example.schemasyncbot.commands;

import com.example.schemasyncbot.model.BotState;
import com.example.schemasyncbot.model.SessionData;
import com.example.schemasyncbot.service.IJenkinsPollingService;
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
 * Triggers a Jenkins diff job (APPLY_DIFF=false) and starts polling for the diff artifact.
 * Validates parameters, transitions to {@link BotState#AWAITING_DIFF}.
 */
public class DiffCommand implements BotCommand {

    private static final Logger log = LoggerFactory.getLogger(DiffCommand.class);

    private final TelegramBot bot;
    private final JenkinsService jenkins;
    private final RedisSessionService redisSessionService;
    private final IJenkinsPollingService jenkinsPollingService;

    public DiffCommand(TelegramBot bot,
                       JenkinsService jenkins,
                       RedisSessionService redisSessionService,
                       IJenkinsPollingService jenkinsPollingService) {
        this.bot = bot;
        this.jenkins = jenkins;
        this.redisSessionService = redisSessionService;
        this.jenkinsPollingService = jenkinsPollingService;
    }

    @Override
    public boolean handle(Update update) {
        if (update.message() != null && "/diff".equals(update.message().text())) {
            Long chatId = update.message().chat().id();
            SessionData session = redisSessionService.getSession(chatId);

            // Must have a pipeline selected
            if (session.getSelectedPipeline() == null) {
                bot.execute(new SendMessage(chatId, MessageTemplates.noPipelineSelected())
                        .parseMode(ParseMode.HTML));
                return true;
            }

            // Validate that all required parameters are set
            List<String> missing = session.getMissingParams();
            if (!missing.isEmpty()) {
                bot.execute(new SendMessage(chatId, MessageTemplates.parametersIncomplete(missing))
                        .parseMode(ParseMode.HTML));
                return true;
            }

            // Build parameters
            Map<String, String> params = new HashMap<>(session.getEnv());
            params.put("APPLY_DIFF", "false");
            params.put("BOT_USER_ID", chatId.toString());
            params.put("PIPELINE_NAME", session.getSelectedPipeline());

            String schema = session.getEnv().getOrDefault("SCHEMA_NAME", null);

            // Transition to AWAITING_DIFF
            session.setState(BotState.AWAITING_DIFF);
            session.setLastBuildParams(params);
            redisSessionService.saveSession(chatId, session);

            bot.execute(new SendMessage(chatId, MessageTemplates.diffTriggered(session.getSelectedPipeline(), schema))
                    .parseMode(ParseMode.HTML));

            jenkins.triggerBuild(session.getSelectedPipeline(), params).subscribe(
                    msg -> {
                        log.info("Diff job triggered for chat {} pipeline '{}'", chatId, session.getSelectedPipeline());
                        jenkinsPollingService.pollJenkinsForDiffArtifact(chatId, session.getSelectedPipeline(), session);
                    },
                    error -> {
                        log.error("Failed to trigger diff for chat {}", chatId, error);
                        session.setState(BotState.CONFIGURING_PARAMS);
                        redisSessionService.saveSession(chatId, session);
                        bot.execute(new SendMessage(chatId, MessageTemplates.error("Failed to trigger diff job: " + MessageTemplates.safeErrorMessage(error)))
                                .parseMode(ParseMode.HTML));
                    }
            );
            return true;
        }
        return false;
    }

    @Override
    public String getCommand() {
        return "/diff";
    }
}
