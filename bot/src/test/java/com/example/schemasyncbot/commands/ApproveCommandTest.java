package com.example.schemasyncbot.commands;

import com.example.schemasyncbot.model.SessionData;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ApproveCommandTest {
    private TelegramBot bot;
    private JenkinsService jenkins;
    private RedisSessionService redisSessionService;
    private ApproveCommand command;
    private Update update;
    private Message message;
    private Chat chat;
    private final Long chatId = 123L;

    @BeforeEach
    void setUp() {
        bot = mock(TelegramBot.class);
        jenkins = mock(JenkinsService.class);
        redisSessionService = mock(RedisSessionService.class);
        command = new ApproveCommand(bot, jenkins, redisSessionService);
        update = mock(Update.class);
        message = mock(Message.class);
        chat = mock(Chat.class);
        when(update.message()).thenReturn(message);
        when(message.text()).thenReturn("/approve");
        when(message.chat()).thenReturn(chat);
        when(chat.id()).thenReturn(chatId);
    }

    @Test
    void testHandle_NoPipeline() {
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
        session.setPendingParams(List.of("A", "B"));
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
        session.setSelectedPipeline("pipe");
        session.setPendingParams(List.of());
        Map<String, String> env = new HashMap<>();
        env.put("A", "valA");
        session.setEnv(env);
        when(redisSessionService.getSession(chatId)).thenReturn(session);
        when(jenkins.triggerBuild(any(), any())).thenReturn(Mono.just("ok"));

        boolean handled = command.handle(update);

        assertTrue(handled);
        verify(bot).execute(argThat((SendMessage m) ->
                m.getParameters().get("text").toString().contains("Apply Job Triggered")));
        verify(bot, atLeastOnce()).execute(argThat((SendMessage m) ->
                m.getParameters().get("text").toString().contains("Changes Applied Successfully")));
        verify(redisSessionService).deleteSession(chatId);
    }

    @Test
    void testHandle_JenkinsError() {
        SessionData session = new SessionData();
        session.setSelectedPipeline("pipe");
        session.setPendingParams(List.of());
        Map<String, String> env = new HashMap<>();
        env.put("A", "valA");
        session.setEnv(env);
        when(redisSessionService.getSession(chatId)).thenReturn(session);
        when(jenkins.triggerBuild(any(), any())).thenReturn(Mono.error(new RuntimeException("fail!")));

        boolean handled = command.handle(update);

        assertTrue(handled);
        verify(bot).execute(argThat((SendMessage m) ->
                m.getParameters().get("text").toString().contains("Apply Job Triggered")));
        verify(bot, atLeastOnce()).execute(argThat((SendMessage m) ->
                m.getParameters().get("text").toString().contains("Failed to trigger apply job")));
    }

    @Test
    void testHandle_NotApproveCommand() {
        when(message.text()).thenReturn("/other");
        boolean handled = command.handle(update);
        assertFalse(handled);
        verifyNoInteractions(redisSessionService);
    }

    @Test
    void testGetCommand() {
        assertEquals("/approve", command.getCommand());
    }
}
