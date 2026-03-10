package com.example.schemasyncbot.commands;

import com.example.schemasyncbot.model.BotState;
import com.example.schemasyncbot.model.SessionData;
import com.example.schemasyncbot.service.IJenkinsPollingService;
import com.example.schemasyncbot.service.JenkinsService;
import com.example.schemasyncbot.service.RedisSessionService;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.SendMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class DiffCommandTest {
    private TelegramBot bot;
    private JenkinsService jenkins;
    private RedisSessionService redisSessionService;
    private IJenkinsPollingService jenkinsPollingService;
    private DiffCommand command;
    private Update update;
    private Message message;
    private Chat chat;
    private final Long chatId = 123L;

    @BeforeEach
    void setUp() {
        bot = mock(TelegramBot.class);
        jenkins = mock(JenkinsService.class);
        redisSessionService = mock(RedisSessionService.class);
        jenkinsPollingService = mock(IJenkinsPollingService.class);
        command = new DiffCommand(bot, jenkins, redisSessionService, jenkinsPollingService);
        update = mock(Update.class);
        message = mock(Message.class);
        chat = mock(Chat.class);
        when(update.message()).thenReturn(message);
        when(message.text()).thenReturn("/diff");
        when(message.chat()).thenReturn(chat);
        when(chat.id()).thenReturn(chatId);
    }

    @Test
    void testHandle_NoPipelineSelected() {
        SessionData session = new SessionData();
        session.setSelectedPipeline(null);
        when(redisSessionService.getSession(chatId)).thenReturn(session);
        boolean handled = command.handle(update);
        assertTrue(handled);
        verify(bot).execute(argThat((SendMessage m) ->
                m.getParameters().get("text").toString().contains("No Pipeline Selected")));
    }

    @Test
    void testHandle_MissingParameters() {
        SessionData session = new SessionData();
        session.setSelectedPipeline("pipe");
        session.setPendingParams(new ArrayList<>(List.of("A", "B")));
        Map<String, String> env = new HashMap<>();
        env.put("A", "");
        env.put("B", "valB");
        session.setEnv(env);
        when(redisSessionService.getSession(chatId)).thenReturn(session);
        boolean handled = command.handle(update);
        assertTrue(handled);
        verify(bot).execute(argThat((SendMessage m) ->
                m.getParameters().get("text").toString().contains("Missing Parameters")));
    }

    @Test
    void testHandle_SuccessfulTrigger() {
        SessionData session = new SessionData();
        session.setPendingParams(List.of());
        session.setSelectedPipeline("pipe");
        Map<String, String> env = new HashMap<>();
        env.put("A", "valA");
        session.setEnv(env);
        when(redisSessionService.getSession(chatId)).thenReturn(session);
        when(jenkins.triggerBuild(any(), any())).thenReturn(Mono.just("ok"));
        doNothing().when(jenkinsPollingService).pollJenkinsForDiffArtifact(any(), any(), any());

        boolean handled = command.handle(update);

        assertTrue(handled);
        verify(bot).execute(argThat((SendMessage m) ->
                m.getParameters().get("text").toString().contains("Diff Job Triggered")));
        verify(jenkinsPollingService).pollJenkinsForDiffArtifact(eq(chatId), eq("pipe"), any(SessionData.class));
        verify(redisSessionService).saveSession(eq(chatId), argThat(s ->
                s.getState() == BotState.AWAITING_DIFF));
    }

    @Test
    void testHandle_JenkinsError() {
        SessionData session = new SessionData();
        session.setPendingParams(List.of());
        session.setSelectedPipeline("pipe");
        Map<String, String> env = new HashMap<>();
        env.put("A", "valA");
        session.setEnv(env);
        when(redisSessionService.getSession(chatId)).thenReturn(session);
        when(jenkins.triggerBuild(any(), any())).thenReturn(Mono.error(new RuntimeException("fail!")));

        boolean handled = command.handle(update);

        assertTrue(handled);
        verify(bot).execute(argThat((SendMessage m) ->
                m.getParameters().get("text").toString().contains("Diff Job Triggered")));
        verify(bot, atLeastOnce()).execute(argThat((SendMessage m) ->
                m.getParameters().get("text").toString().contains("Failed to trigger diff job")));
    }

    @Test
    void testHandle_NotDiffCommand() {
        when(message.text()).thenReturn("/other");
        boolean handled = command.handle(update);
        assertFalse(handled);
        verifyNoInteractions(redisSessionService);
    }

    @Test
    void testGetCommand() {
        assertEquals("/diff", command.getCommand());
    }
}
