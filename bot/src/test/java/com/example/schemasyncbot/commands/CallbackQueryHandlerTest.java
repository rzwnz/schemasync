package com.example.schemasyncbot.commands;

import com.example.schemasyncbot.model.BotState;
import com.example.schemasyncbot.model.SessionData;
import com.example.schemasyncbot.service.JenkinsService;
import com.example.schemasyncbot.service.RedisSessionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.CallbackQuery;
import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.request.AnswerCallbackQuery;
import com.pengrad.telegrambot.request.BaseRequest;
import com.pengrad.telegrambot.request.SendMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CallbackQueryHandlerTest {

    private TelegramBot bot;
    private JenkinsService jenkins;
    private RedisSessionService redisSessionService;
    private CallbackQueryHandler handler;

    private CallbackQuery callback;
    private Message cbMsg;
    private Chat cbChat;
    private final Long chatId = 100L;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        bot = mock(TelegramBot.class);
        jenkins = mock(JenkinsService.class);
        redisSessionService = mock(RedisSessionService.class);
        handler = new CallbackQueryHandler(bot, jenkins, redisSessionService);

        callback = mock(CallbackQuery.class);
        cbMsg = mock(Message.class);
        cbChat = mock(Chat.class);

        when(callback.maybeInaccessibleMessage()).thenReturn(cbMsg);
        when(cbMsg.chat()).thenReturn(cbChat);
        when(cbChat.id()).thenReturn(chatId);
        when(callback.id()).thenReturn("cb-1");
    }

    private void setCallbackData(String data) {
        when(callback.data()).thenReturn(data);
    }

    private SessionData newSession() {
        SessionData s = new SessionData();
        s.setEnv(new HashMap<>());
        s.setPendingParams(new ArrayList<>());
        s.setConfirmedParams(new HashSet<>());
        return s;
    }

    @SuppressWarnings("unchecked")
    private void assertSentContains(String substring) {
        ArgumentCaptor<BaseRequest> captor = ArgumentCaptor.forClass(BaseRequest.class);
        verify(bot, atLeast(1)).execute(captor.capture());
        boolean found = captor.getAllValues().stream()
                .filter(a -> a instanceof SendMessage)
                .map(a -> (SendMessage) a)
                .anyMatch(m -> m.getParameters().get("text").toString().contains(substring));
        assertTrue(found, "Expected sent message containing '" + substring + "'");
    }

    @SuppressWarnings("unchecked")
    private void assertSentHasReplyMarkup() {
        ArgumentCaptor<BaseRequest> captor = ArgumentCaptor.forClass(BaseRequest.class);
        verify(bot, atLeast(1)).execute(captor.capture());
        boolean found = captor.getAllValues().stream()
                .filter(a -> a instanceof SendMessage)
                .map(a -> (SendMessage) a)
                .anyMatch(m -> m.getParameters().get("reply_markup") != null);
        assertTrue(found, "Expected a sent message with reply_markup");
    }

    // ─── Pipeline selection ──────────────────────────────────────────

    @Test
    void handle_pipelines_listsJobs() {
        setCallbackData("pipelines");
        SessionData session = newSession();
        when(redisSessionService.getSession(chatId)).thenReturn(session);

        ObjectNode root = mapper.createObjectNode();
        ArrayNode jobs = root.putArray("jobs");
        ObjectNode job = jobs.addObject();
        job.put("name", "build-job");
        job.put("displayName", "Build Job");
        when(jenkins.listJobs()).thenReturn(Mono.just(root));

        handler.handle(callback);

        verify(redisSessionService).saveSession(eq(chatId), argThat(s ->
                s.getState() == BotState.SELECTING_PIPELINE));
        verify(bot).execute(any(AnswerCallbackQuery.class));
        assertSentContains("Available Pipelines");
    }

    @Test
    void handle_pipelines_noPipelinesFound() {
        setCallbackData("pipelines");
        SessionData session = newSession();
        when(redisSessionService.getSession(chatId)).thenReturn(session);

        ObjectNode root = mapper.createObjectNode();
        when(jenkins.listJobs()).thenReturn(Mono.just(root));

        handler.handle(callback);
        assertSentContains("No Pipelines Found");
    }

    @Test
    void handle_pipelines_error() {
        setCallbackData("pipelines");
        SessionData session = newSession();
        when(redisSessionService.getSession(chatId)).thenReturn(session);
        when(jenkins.listJobs()).thenReturn(Mono.error(new RuntimeException("network error")));

        handler.handle(callback);
        assertSentContains("Failed to fetch Jenkins jobs");
    }

    @Test
    void handle_selectPipeline_withParams() {
        setCallbackData("select_pipeline:my-pipeline");
        SessionData session = newSession();
        when(redisSessionService.getSession(chatId)).thenReturn(session);

        ObjectNode jobNode = mapper.createObjectNode();
        ArrayNode actions = jobNode.putArray("actions");
        ObjectNode action = actions.addObject();
        ArrayNode paramDefs = action.putArray("parameterDefinitions");

        ObjectNode param1 = paramDefs.addObject();
        param1.put("name", "SCHEMA_NAME");
        ObjectNode def1 = param1.putObject("defaultParameterValue");
        def1.put("value", "public");

        ObjectNode param2 = paramDefs.addObject();
        param2.put("name", "DB_HOST");

        ObjectNode excluded = paramDefs.addObject();
        excluded.put("name", "APPLY_DIFF");

        when(jenkins.getJobDetails("my-pipeline")).thenReturn(Mono.just(jobNode));

        handler.handle(callback);

        verify(redisSessionService).saveSession(eq(chatId), argThat(s ->
                "my-pipeline".equals(s.getSelectedPipeline())
                        && s.getState() == BotState.CONFIGURING_PARAMS
                        && s.getPendingParams().contains("SCHEMA_NAME")
                        && s.getPendingParams().contains("DB_HOST")
                        && !s.getPendingParams().contains("APPLY_DIFF")));
    }

    @Test
    void handle_selectPipeline_noParams() {
        setCallbackData("select_pipeline:empty-job");
        SessionData session = newSession();
        when(redisSessionService.getSession(chatId)).thenReturn(session);

        ObjectNode jobNode = mapper.createObjectNode();
        when(jenkins.getJobDetails("empty-job")).thenReturn(Mono.just(jobNode));

        handler.handle(callback);
        assertSentContains("No Parameters Found");
    }

    @Test
    void handle_selectPipeline_error() {
        setCallbackData("select_pipeline:bad-job");
        when(jenkins.getJobDetails("bad-job")).thenReturn(Mono.error(new RuntimeException("err")));

        handler.handle(callback);
        assertSentContains("Failed to fetch job details");
    }

    @Test
    void handle_selectPipeline_propertiesFallback() {
        setCallbackData("select_pipeline:prop-job");
        SessionData session = newSession();
        when(redisSessionService.getSession(chatId)).thenReturn(session);

        ObjectNode jobNode = mapper.createObjectNode();
        ArrayNode props = jobNode.putArray("property");
        ObjectNode prop = props.addObject();
        ArrayNode paramDefs = prop.putArray("parameterDefinitions");
        ObjectNode p = paramDefs.addObject();
        p.put("name", "DB_PORT");

        when(jenkins.getJobDetails("prop-job")).thenReturn(Mono.just(jobNode));

        handler.handle(callback);

        verify(redisSessionService).saveSession(eq(chatId), argThat(s ->
                s.getPendingParams().contains("DB_PORT")));
    }

    // ─── Parameter management ────────────────────────────────────────

    @Test
    void handle_paramDetail_showsDetailKeyboard() {
        setCallbackData("param:SCHEMA_NAME");
        SessionData session = newSession();
        session.getEnv().put("SCHEMA_NAME", "public");
        when(redisSessionService.getSession(chatId)).thenReturn(session);

        handler.handle(callback);

        verify(redisSessionService).saveSession(eq(chatId), argThat(s ->
                "SCHEMA_NAME".equals(s.getEditingParam())));
        assertSentContains("SCHEMA_NAME");
    }

    @Test
    void handle_editValue_promptsForInput() {
        setCallbackData("editval:DB_HOST");
        SessionData session = newSession();
        when(redisSessionService.getSession(chatId)).thenReturn(session);

        handler.handle(callback);

        verify(redisSessionService).saveSession(eq(chatId), argThat(s ->
                "DB_HOST".equals(s.getEditingParam())));
        assertSentContains("DB_HOST");
    }

    @Test
    void handle_confirmValue_emptyValue_warns() {
        setCallbackData("confirmval:SCHEMA_NAME");
        SessionData session = newSession();
        session.getEnv().put("SCHEMA_NAME", "");
        when(redisSessionService.getSession(chatId)).thenReturn(session);

        handler.handle(callback);
        assertSentContains("set a value");
    }

    @Test
    void handle_confirmValue_confirmsAndShowsButtons() {
        setCallbackData("confirmval:SCHEMA_NAME");
        SessionData session = newSession();
        session.getEnv().put("SCHEMA_NAME", "public");
        session.setPendingParams(new ArrayList<>(List.of("SCHEMA_NAME", "DB_HOST")));
        when(redisSessionService.getSession(chatId)).thenReturn(session);

        handler.handle(callback);

        verify(redisSessionService).saveSession(eq(chatId), argThat(s ->
                s.getConfirmedParams().contains("SCHEMA_NAME")
                        && s.getEditingParam() == null));
    }

    @Test
    void handle_confirmValue_allConfirmed_showsMessage() {
        setCallbackData("confirmval:SCHEMA_NAME");
        SessionData session = newSession();
        session.getEnv().put("SCHEMA_NAME", "public");
        session.setPendingParams(new ArrayList<>(List.of("SCHEMA_NAME")));
        session.setConfirmedParams(new HashSet<>());
        when(redisSessionService.getSession(chatId)).thenReturn(session);

        handler.handle(callback);
        assertSentContains("All Parameters Confirmed");
    }

    @Test
    void handle_backtoparams_resetsEditing() {
        setCallbackData("backtoparams");
        SessionData session = newSession();
        session.setEditingParam("SCHEMA_NAME");
        session.setSelectedPipeline("pipe");
        session.setPendingParams(new ArrayList<>(List.of("SCHEMA_NAME")));
        session.getEnv().put("SCHEMA_NAME", "val");
        when(redisSessionService.getSession(chatId)).thenReturn(session);

        handler.handle(callback);

        verify(redisSessionService).saveSession(eq(chatId), argThat(s ->
                s.getEditingParam() == null));
    }

    @Test
    void handle_confirmAllParams_allConfirmed() {
        setCallbackData("confirm_all_params");
        SessionData session = newSession();
        session.setPendingParams(new ArrayList<>(List.of("A")));
        session.setConfirmedParams(new HashSet<>(Set.of("A")));
        session.getEnv().put("A", "val");
        when(redisSessionService.getSession(chatId)).thenReturn(session);

        handler.handle(callback);
        assertSentContains("All Parameters Confirmed");
    }

    @Test
    void handle_confirmAllParams_notAllConfirmed() {
        setCallbackData("confirm_all_params");
        SessionData session = newSession();
        session.setPendingParams(new ArrayList<>(List.of("A", "B")));
        session.setConfirmedParams(new HashSet<>(Set.of("A")));
        session.getEnv().put("A", "val");
        when(redisSessionService.getSession(chatId)).thenReturn(session);

        handler.handle(callback);
        assertSentContains("confirm all parameters");
    }

    @Test
    void handle_confirmParams_allConfirmed() {
        setCallbackData("confirm_params");
        SessionData session = newSession();
        session.setPendingParams(new ArrayList<>(List.of("A")));
        session.setConfirmedParams(new HashSet<>(Set.of("A")));
        session.getEnv().put("A", "val");
        when(redisSessionService.getSession(chatId)).thenReturn(session);

        handler.handle(callback);
        assertSentContains("All Parameters Confirmed");
    }

    @Test
    void handle_confirmParams_notAllConfirmed() {
        setCallbackData("confirm_params");
        SessionData session = newSession();
        session.setPendingParams(new ArrayList<>(List.of("A", "B")));
        session.setConfirmedParams(new HashSet<>(Set.of("A")));
        session.getEnv().put("A", "val");
        when(redisSessionService.getSession(chatId)).thenReturn(session);

        handler.handle(callback);
        assertSentContains("Confirm all parameters");
    }

    @Test
    void handle_editParams_sendsInstructions() {
        setCallbackData("edit_params");
        SessionData session = newSession();
        when(redisSessionService.getSession(chatId)).thenReturn(session);

        handler.handle(callback);
        assertSentContains("KEY=value");
    }

    @Test
    void handle_cancelParams_deletesSession() {
        setCallbackData("cancel_params");
        SessionData session = newSession();
        when(redisSessionService.getSession(chatId)).thenReturn(session);

        handler.handle(callback);
        verify(redisSessionService).deleteSession(chatId);
    }

    // ─── Diff review ─────────────────────────────────────────────────

    @Test
    void handle_approveDiff_noPipeline() {
        setCallbackData("approve_diff");
        SessionData session = newSession();
        session.setSelectedPipeline(null);
        when(redisSessionService.getSession(chatId)).thenReturn(session);

        handler.handle(callback);
        assertSentContains("No Pipeline Selected");
    }

    @Test
    void handle_approveDiff_success() {
        setCallbackData("approve_diff");
        SessionData session = newSession();
        session.setSelectedPipeline("my-pipe");
        session.getEnv().put("SCHEMA_NAME", "public");
        when(redisSessionService.getSession(chatId)).thenReturn(session);
        when(jenkins.triggerBuild(eq("my-pipe"), any())).thenReturn(Mono.just("ok"));

        handler.handle(callback);

        verify(redisSessionService).saveSession(eq(chatId), argThat(s ->
                s.getState() == BotState.APPLYING));
        verify(redisSessionService).deleteSession(chatId);
    }

    @Test
    void handle_approveDiff_jenkinsError() {
        setCallbackData("approve_diff");
        SessionData session = newSession();
        session.setSelectedPipeline("my-pipe");
        when(redisSessionService.getSession(chatId)).thenReturn(session);
        when(jenkins.triggerBuild(eq("my-pipe"), any()))
                .thenReturn(Mono.error(new RuntimeException("fail")));

        handler.handle(callback);

        verify(redisSessionService, atLeast(1)).saveSession(eq(chatId), argThat(s ->
                s.getState() == BotState.REVIEWING_DIFF));
        assertSentContains("Failed to trigger apply");
    }

    @Test
    void handle_approveDiff_usesLastBuildParams() {
        setCallbackData("approve_diff");
        SessionData session = newSession();
        session.setSelectedPipeline("my-pipe");
        session.setLastBuildParams(Map.of("KEY", "val"));
        when(redisSessionService.getSession(chatId)).thenReturn(session);
        when(jenkins.triggerBuild(eq("my-pipe"), any())).thenReturn(Mono.just("ok"));

        handler.handle(callback);

        verify(jenkins).triggerBuild(eq("my-pipe"), argThat(params ->
                "val".equals(params.get("KEY"))
                        && "true".equals(params.get("APPLY_DIFF"))));
    }

    @Test
    void handle_cancelDiff_deletesSession() {
        setCallbackData("cancel_diff");
        SessionData session = newSession();
        when(redisSessionService.getSession(chatId)).thenReturn(session);

        handler.handle(callback);
        verify(redisSessionService).deleteSession(chatId);
    }

    // ─── Show table ──────────────────────────────────────────────────

    @Test
    void handle_showTable_success() {
        setCallbackData("show_table:users");
        SessionData session = newSession();
        Map<String, Object> parsed = new HashMap<>();
        List<Map<String, Object>> columns = new ArrayList<>();
        Map<String, Object> col = new HashMap<>();
        col.put("tableName", "users");
        col.put("columnName", "id");
        columns.add(col);
        parsed.put("columns", columns);
        session.setParsedDiff(parsed);
        when(redisSessionService.getSession(chatId)).thenReturn(session);

        handler.handle(callback);
        assertSentContains("users");
    }

    @Test
    void handle_showTable_noParsedDiff() {
        setCallbackData("show_table:users");
        SessionData session = newSession();
        session.setParsedDiff(null);
        when(redisSessionService.getSession(chatId)).thenReturn(session);

        handler.handle(callback);
        assertSentContains("No parsed diff");
    }

    @Test
    void handle_showTable_noColumns() {
        setCallbackData("show_table:users");
        SessionData session = newSession();
        session.setParsedDiff(new HashMap<>());
        when(redisSessionService.getSession(chatId)).thenReturn(session);

        handler.handle(callback);
        assertSentContains("No column data");
    }

    // ─── Help navigation ─────────────────────────────────────────────

    @Test
    void handle_helpOverview() {
        setCallbackData("help_overview");
        SessionData session = newSession();
        when(redisSessionService.getSession(chatId)).thenReturn(session);

        handler.handle(callback);
        verify(bot).execute(any(AnswerCallbackQuery.class));
        verify(bot).execute(any(SendMessage.class));
    }

    @Test
    void handle_helpWorkflow() {
        setCallbackData("help_workflow");
        SessionData session = newSession();
        when(redisSessionService.getSession(chatId)).thenReturn(session);

        handler.handle(callback);
        verify(bot).execute(any(AnswerCallbackQuery.class));
        verify(bot).execute(any(SendMessage.class));
    }

    @Test
    void handle_helpParams() {
        setCallbackData("help_params");
        SessionData session = newSession();
        when(redisSessionService.getSession(chatId)).thenReturn(session);

        handler.handle(callback);
        verify(bot).execute(any(AnswerCallbackQuery.class));
        verify(bot).execute(any(SendMessage.class));
    }

    // ─── Navigation ──────────────────────────────────────────────────

    @Test
    void handle_backToStart() {
        setCallbackData("back_to_start");
        SessionData session = newSession();
        when(redisSessionService.getSession(chatId)).thenReturn(session);

        handler.handle(callback);
        verify(bot).execute(any(AnswerCallbackQuery.class));
        verify(bot).execute(any(SendMessage.class));
    }

    @Test
    void handle_cancelPipelineSelect() {
        setCallbackData("cancel_pipeline_select");
        SessionData session = newSession();
        when(redisSessionService.getSession(chatId)).thenReturn(session);

        handler.handle(callback);
        verify(redisSessionService).saveSession(eq(chatId), argThat(s ->
                s.getState() == BotState.IDLE));
    }

    @Test
    void handle_unknownCallbackData_acks() {
        setCallbackData("something_unknown");
        SessionData session = newSession();
        when(redisSessionService.getSession(chatId)).thenReturn(session);

        handler.handle(callback);
        verify(bot).execute(any(AnswerCallbackQuery.class));
        verify(bot, never()).execute(any(SendMessage.class));
    }

    // ─── sendParameterButtons (tested indirectly) ────────────────────

    @Test
    void handle_backtoparams_rendersParamButtons() {
        setCallbackData("backtoparams");
        SessionData session = newSession();
        session.setSelectedPipeline("pipe");
        session.setEditingParam("A");
        session.setPendingParams(new ArrayList<>(List.of("A", "B")));
        session.getEnv().put("A", "a_very_long_value_that_exceeds_twenty_five_characters_limit");
        session.getEnv().put("B", "");
        session.setConfirmedParams(new HashSet<>(Set.of("A")));
        when(redisSessionService.getSession(chatId)).thenReturn(session);

        handler.handle(callback);
        assertSentHasReplyMarkup();
    }
}
