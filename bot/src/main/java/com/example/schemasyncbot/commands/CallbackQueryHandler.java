package com.example.schemasyncbot.commands;

import com.example.schemasyncbot.model.BotState;
import com.example.schemasyncbot.model.SessionData;
import com.example.schemasyncbot.service.JenkinsService;
import com.example.schemasyncbot.service.RedisSessionService;
import com.example.schemasyncbot.utils.MessageTemplates;
import com.fasterxml.jackson.databind.JsonNode;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.CallbackQuery;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.AnswerCallbackQuery;
import com.pengrad.telegrambot.request.SendMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Handles all inline-keyboard callback queries.
 * <p>
 * Now a Spring-managed {@code @Component} (previously instantiated via {@code new}).
 * Every session mutation is followed by a {@code saveSession} call.
 */
@Component
public class CallbackQueryHandler {

    private static final Logger log = LoggerFactory.getLogger(CallbackQueryHandler.class);
    private static final Set<String> EXCLUDED_PARAMS = Set.of("APPLY_DIFF", "DIFF_ID", "BOT_USER_ID", "PIPELINE_NAME");

    private final TelegramBot bot;
    private final JenkinsService jenkins;
    private final RedisSessionService redisSessionService;

    public CallbackQueryHandler(TelegramBot bot,
                                JenkinsService jenkins,
                                RedisSessionService redisSessionService) {
        this.bot = bot;
        this.jenkins = jenkins;
        this.redisSessionService = redisSessionService;
    }

    public void handle(CallbackQuery callback) {
        Long chatId = callback.maybeInaccessibleMessage().chat().id();
        String data = callback.data();

        SessionData session = redisSessionService.getSession(chatId);

        // ── Route by callback data prefix ───────────────────────────────
        if (data.startsWith("select_pipeline:")) {
            handleSelectPipeline(chatId, data.substring("select_pipeline:".length()), callback);
            return;
        }
        if (data.startsWith("param:")) {
            handleParamDetail(chatId, data.substring("param:".length()), session, callback);
            return;
        }
        if (data.startsWith("editval:")) {
            handleEditValue(chatId, data.substring("editval:".length()), session, callback);
            return;
        }
        if (data.startsWith("confirmval:")) {
            handleConfirmValue(chatId, data.substring("confirmval:".length()), session, callback);
            return;
        }
        if (data.startsWith("show_table:")) {
            handleShowTable(chatId, data.substring("show_table:".length()), session, callback);
            return;
        }
        if (data.startsWith("help_")) {
            handleHelpNavigation(chatId, data, callback);
            return;
        }

        // ── Route by exact callback data ────────────────────────────────
        switch (data) {
            case "pipelines" -> handleListPipelines(chatId, callback);
            case "backtoparams" -> handleBackToParams(chatId, session, callback);
            case "confirm_all_params" -> handleConfirmAllParams(chatId, session, callback);
            case "cancel_params" -> handleCancelParams(chatId, callback);
            case "confirm_params" -> handleConfirmParams(chatId, session, callback);
            case "edit_params" -> handleEditParams(chatId, callback);
            case "approve_diff" -> handleApproveDiff(chatId, session, callback);
            case "cancel_diff" -> handleCancelDiff(chatId, callback);
            case "back_to_start" -> handleBackToStart(chatId, callback);
            case "cancel_pipeline_select" -> handleCancelPipelineSelect(chatId, callback);
            default -> {
                log.debug("Unknown callback data '{}' from chat {}", data, chatId);
                ack(callback);
            }
        }
    }

    // ─── PIPELINE SELECTION ────────────────────────────────────────────

    private void handleListPipelines(Long chatId, CallbackQuery callback) {
        SessionData session = redisSessionService.getSession(chatId);
        session.setState(BotState.SELECTING_PIPELINE);
        redisSessionService.saveSession(chatId, session);

        jenkins.listJobs().subscribe(jobsNode -> {
            if (jobsNode.has("jobs")) {
                InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
                for (JsonNode job : jobsNode.get("jobs")) {
                    String name = job.get("name").asText();
                    String display = job.has("displayName") ? job.get("displayName").asText() : name;
                    keyboard.addRow(new InlineKeyboardButton("📌 " + display).callbackData("select_pipeline:" + name));
                }
                keyboard.addRow(new InlineKeyboardButton("❌ Cancel").callbackData("cancel_pipeline_select"));
                send(chatId, MessageTemplates.pipelineListHeader(), keyboard);
            } else {
                send(chatId, MessageTemplates.noPipelinesFound(), null);
            }
        }, err -> {
            log.error("Failed to list Jenkins jobs for chat {}", chatId, err);
            send(chatId, MessageTemplates.error("Failed to fetch Jenkins jobs: " + MessageTemplates.safeErrorMessage(err)), null);
        });
        ack(callback);
    }

    private void handleSelectPipeline(Long chatId, String jobName, CallbackQuery callback) {
        jenkins.getJobDetails(jobName).subscribe(jobNode -> {
            Set<String> paramSet = new LinkedHashSet<>();
            Map<String, String> defaults = new HashMap<>();
            boolean foundParams = extractParameters(jobNode, paramSet, defaults);

            List<String> paramNames = new ArrayList<>(paramSet);
            if (!foundParams || paramNames.isEmpty()) {
                send(chatId, MessageTemplates.noParametersFound(), null);
                return;
            }

            SessionData session = redisSessionService.getSession(chatId);
            session.setSelectedPipeline(jobName);
            session.setPendingParams(paramNames);
            session.setState(BotState.CONFIGURING_PARAMS);
            if (session.getEnv() == null) session.setEnv(new HashMap<>());
            defaults.forEach((k, v) -> session.getEnv().putIfAbsent(k, v));
            session.setConfirmedParams(new HashSet<>());
            session.setEditingParam(null);
            redisSessionService.saveSession(chatId, session);

            sendParameterButtons(chatId, session);
        }, err -> {
            log.error("Failed to fetch job details for '{}' chat {}", jobName, chatId, err);
            send(chatId, MessageTemplates.error("Failed to fetch job details: " + MessageTemplates.safeErrorMessage(err)), null);
        });
        ack(callback);
    }

    // ─── PARAMETER MANAGEMENT ──────────────────────────────────────────

    private void handleParamDetail(Long chatId, String paramName, SessionData session, CallbackQuery callback) {
        session.setEditingParam(paramName);
        redisSessionService.saveSession(chatId, session);

        String value = session.getEnv().getOrDefault(paramName, "");
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup(
                new InlineKeyboardButton[] { new InlineKeyboardButton("✏️ Edit Value").callbackData("editval:" + paramName) },
                new InlineKeyboardButton[] { new InlineKeyboardButton("✅ Confirm Value").callbackData("confirmval:" + paramName) },
                new InlineKeyboardButton[] { new InlineKeyboardButton("⬅️ Back to Parameters").callbackData("backtoparams") }
        );
        send(chatId, MessageTemplates.parameterDetail(paramName, value), keyboard);
        ack(callback);
    }

    private void handleEditValue(Long chatId, String paramName, SessionData session, CallbackQuery callback) {
        session.setEditingParam(paramName);
        redisSessionService.saveSession(chatId, session);
        send(chatId, MessageTemplates.parameterValuePrompt(paramName), null);
        ack(callback);
    }

    private void handleConfirmValue(Long chatId, String paramName, SessionData session, CallbackQuery callback) {
        if (session.getEnv().getOrDefault(paramName, "").isEmpty()) {
            send(chatId, "⚠️ Please set a value for <b>" + MessageTemplates.escapeHtml(paramName) + "</b> before confirming.", null);
            ack(callback);
            return;
        }
        session.getConfirmedParams().add(paramName);
        session.setEditingParam(null);
        redisSessionService.saveSession(chatId, session);

        if (session.areAllParamsConfirmed()) {
            send(chatId, MessageTemplates.allParametersConfirmed(), null);
        } else {
            sendParameterButtons(chatId, session);
        }
        ack(callback);
    }

    private void handleBackToParams(Long chatId, SessionData session, CallbackQuery callback) {
        session.setEditingParam(null);
        redisSessionService.saveSession(chatId, session);
        sendParameterButtons(chatId, session);
        ack(callback);
    }

    private void handleConfirmAllParams(Long chatId, SessionData session, CallbackQuery callback) {
        if (session.areAllParamsConfirmed()) {
            send(chatId, MessageTemplates.allParametersConfirmed(), null);
        } else {
            send(chatId, "⚠️ Please confirm all parameters before proceeding.", null);
        }
        ack(callback);
    }

    private void handleConfirmParams(Long chatId, SessionData session, CallbackQuery callback) {
        if (session.areAllParamsConfirmed()) {
            send(chatId, MessageTemplates.allParametersConfirmed(), null);
        } else {
            send(chatId, "⚠️ Confirm all parameters first, then use /diff.", null);
        }
        ack(callback);
    }

    private void handleEditParams(Long chatId, CallbackQuery callback) {
        send(chatId, "✏️ Send parameters in <code>KEY=value</code> format.", null);
        ack(callback);
    }

    private void handleCancelParams(Long chatId, CallbackQuery callback) {
        redisSessionService.deleteSession(chatId);
        send(chatId, MessageTemplates.cancelled(), startKeyboard());
        ack(callback);
    }

    // ─── DIFF REVIEW ──────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void handleApproveDiff(Long chatId, SessionData session, CallbackQuery callback) {
        String pipeline = session.getSelectedPipeline();
        if (pipeline == null) {
            send(chatId, MessageTemplates.noPipelineSelected(), null);
            ack(callback);
            return;
        }

        Map<String, String> params = session.getLastBuildParams() != null
                ? new HashMap<>(session.getLastBuildParams())
                : new HashMap<>(session.getEnv());
        params.put("APPLY_DIFF", "true");
        params.put("BOT_USER_ID", chatId.toString());
        params.put("PIPELINE_NAME", pipeline);

        String schema = params.getOrDefault("SCHEMA_NAME", null);
        session.setState(BotState.APPLYING);
        redisSessionService.saveSession(chatId, session);

        send(chatId, MessageTemplates.applyTriggered(pipeline, schema), null);

        jenkins.triggerBuild(pipeline, params).subscribe(
                res -> {
                    redisSessionService.deleteSession(chatId);
                    send(chatId, MessageTemplates.applySuccess(), startKeyboard());
                },
                err -> {
                    log.error("Failed to trigger apply for chat {}", chatId, err);
                    session.setState(BotState.REVIEWING_DIFF);
                    redisSessionService.saveSession(chatId, session);
                    send(chatId, MessageTemplates.error("Failed to trigger apply: " + MessageTemplates.safeErrorMessage(err)), null);
                }
        );
        ack(callback);
    }

    private void handleCancelDiff(Long chatId, CallbackQuery callback) {
        redisSessionService.deleteSession(chatId);
        send(chatId, MessageTemplates.cancelled(), startKeyboard());
        ack(callback);
    }

    @SuppressWarnings("unchecked")
    private void handleShowTable(Long chatId, String tableName, SessionData session, CallbackQuery callback) {
        Map<String, Object> parsed = session.getParsedDiff();
        if (parsed == null) {
            send(chatId, "⚠️ No parsed diff data in session.", null);
            ack(callback);
            return;
        }
        List<Map<String, Object>> allColumns = (List<Map<String, Object>>) parsed.get("columns");
        if (allColumns == null) {
            send(chatId, "⚠️ No column data available.", null);
            ack(callback);
            return;
        }
        List<Map<String, Object>> tableColumns = allColumns.stream()
                .filter(col -> tableName.equals(col.get("tableName")))
                .collect(Collectors.toList());
        send(chatId, MessageTemplates.tableColumns(tableName, tableColumns), null);
        ack(callback);
    }

    // ─── HELP NAVIGATION ──────────────────────────────────────────────

    private void handleHelpNavigation(Long chatId, String data, CallbackQuery callback) {
        String text = switch (data) {
            case "help_workflow" -> MessageTemplates.helpWorkflow();
            case "help_params" -> MessageTemplates.helpParameters();
            case "help_overview" -> MessageTemplates.helpOverview();
            default -> MessageTemplates.helpOverview();
        };
        InlineKeyboardMarkup kb = helpKeyboard(data);
        send(chatId, text, kb);
        ack(callback);
    }

    private InlineKeyboardMarkup helpKeyboard(String current) {
        InlineKeyboardMarkup kb = new InlineKeyboardMarkup();
        if (!"help_overview".equals(current)) {
            kb.addRow(new InlineKeyboardButton("📖 Overview").callbackData("help_overview"));
        }
        if (!"help_workflow".equals(current)) {
            kb.addRow(new InlineKeyboardButton("🔄 Workflow Guide").callbackData("help_workflow"));
        }
        if (!"help_params".equals(current)) {
            kb.addRow(new InlineKeyboardButton("⚙️ Parameter Reference").callbackData("help_params"));
        }
        return kb;
    }

    // ─── NAVIGATION ───────────────────────────────────────────────────

    private void handleBackToStart(Long chatId, CallbackQuery callback) {
        send(chatId, MessageTemplates.welcome(null), startKeyboard());
        ack(callback);
    }

    private void handleCancelPipelineSelect(Long chatId, CallbackQuery callback) {
        SessionData session = redisSessionService.getSession(chatId);
        session.setState(BotState.IDLE);
        redisSessionService.saveSession(chatId, session);
        send(chatId, MessageTemplates.cancelled(), startKeyboard());
        ack(callback);
    }

    // ─── PARAMETER BUTTON RENDERING ────────────────────────────────────

    private void sendParameterButtons(Long chatId, SessionData session) {
        List<String> params = session.getPendingParams();
        Map<String, String> env = session.getEnv();
        Set<String> confirmed = session.getConfirmedParams();

        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        for (String param : params) {
            String value = env.getOrDefault(param, "");
            String icon = confirmed.contains(param) ? "✅ " : (value.isEmpty() ? "⬜ " : "✏️ ");
            String display = icon + param;
            if (!value.isEmpty()) {
                String truncated = value.length() > 25 ? value.substring(0, 25) + "…" : value;
                display += ": " + truncated;
            }
            keyboard.addRow(new InlineKeyboardButton(display).callbackData("param:" + param));
        }

        boolean allConfirmed = params.stream().allMatch(confirmed::contains);
        if (allConfirmed) {
            keyboard.addRow(new InlineKeyboardButton("✅ All Confirmed — Use /diff").callbackData("confirm_all_params"));
        }
        keyboard.addRow(new InlineKeyboardButton("❌ Cancel").callbackData("cancel_params"));

        String text = MessageTemplates.parameterList(
                session.getSelectedPipeline(), params, env, confirmed);
        send(chatId, text, keyboard);
    }

    // ─── HELPERS ──────────────────────────────────────────────────────

    private boolean extractParameters(JsonNode jobNode, Set<String> paramSet, Map<String, String> defaults) {
        boolean found = false;

        if (jobNode.has("actions")) {
            for (JsonNode action : jobNode.get("actions")) {
                if (action.has("parameterDefinitions")) {
                    found = true;
                    collectParams(action.get("parameterDefinitions"), paramSet, defaults);
                }
            }
        }

        if (!found || paramSet.isEmpty()) {
            JsonNode propsNode = jobNode.has("property") ? jobNode.get("property")
                    : jobNode.has("properties") ? jobNode.get("properties") : null;
            if (propsNode != null) {
                for (JsonNode prop : propsNode) {
                    if (prop.has("parameterDefinitions")) {
                        found = true;
                        collectParams(prop.get("parameterDefinitions"), paramSet, defaults);
                    }
                }
            }
        }
        return found;
    }

    private void collectParams(JsonNode paramDefs, Set<String> paramSet, Map<String, String> defaults) {
        for (JsonNode param : paramDefs) {
            String name = param.get("name").asText();
            if (EXCLUDED_PARAMS.contains(name)) continue;
            paramSet.add(name);
            if (param.has("defaultParameterValue")
                    && param.get("defaultParameterValue").has("value")) {
                defaults.put(name, param.get("defaultParameterValue").get("value").asText(""));
            }
        }
    }

    private InlineKeyboardMarkup startKeyboard() {
        return new InlineKeyboardMarkup(
                new InlineKeyboardButton("🔍 Select Pipeline").callbackData("pipelines"),
                new InlineKeyboardButton("📖 Help").callbackData("help_overview")
        );
    }

    private void send(Long chatId, String text, InlineKeyboardMarkup keyboard) {
        SendMessage msg = new SendMessage(chatId, text).parseMode(ParseMode.HTML);
        if (keyboard != null) msg.replyMarkup(keyboard);
        bot.execute(msg);
    }

    private void ack(CallbackQuery callback) {
        bot.execute(new AnswerCallbackQuery(callback.id()));
    }
}
