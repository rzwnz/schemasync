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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LogsCommandTest {
    private TelegramBot bot;
    private JenkinsService jenkins;
    private RedisSessionService redisSessionService;
    private LogsCommand command;
    private Update update;
    private Message message;
    private Chat chat;
    private final Long chatId = 123L;

    @BeforeEach
    void setUp() {
        bot = mock(TelegramBot.class);
        jenkins = mock(JenkinsService.class);
        redisSessionService = mock(RedisSessionService.class);
        command = new LogsCommand(bot, jenkins, redisSessionService);
        update = mock(Update.class);
        message = mock(Message.class);
        chat = mock(Chat.class);
        when(update.message()).thenReturn(message);
        when(message.text()).thenReturn("/logs");
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
    void testHandle_SuccessfulLogFetch_ShortLog() {
        SessionData session = new SessionData();
        session.setSelectedPipeline("pipe");
        when(redisSessionService.getSession(chatId)).thenReturn(session);
        when(jenkins.getLastBuildLogs("pipe")).thenReturn(Mono.just("short log"));
        boolean handled = command.handle(update);
        assertTrue(handled);
        verify(bot).execute(argThat((SendMessage m) ->
                m.getParameters().get("text").toString().contains("Jenkins Logs")));
    }

    @Test
    void testHandle_SuccessfulLogFetch_LongLog() {
        SessionData session = new SessionData();
        session.setSelectedPipeline("pipe");
        when(redisSessionService.getSession(chatId)).thenReturn(session);
        String longLog = "a".repeat(5000);
        when(jenkins.getLastBuildLogs("pipe")).thenReturn(Mono.just(longLog));
        boolean handled = command.handle(update);
        assertTrue(handled);
        verify(bot).execute(argThat((SendMessage m) ->
                m.getParameters().get("text").toString().contains("[TRUNCATED]")));
    }

    @Test
    void testHandle_JenkinsError() {
        SessionData session = new SessionData();
        session.setSelectedPipeline("pipe");
        when(redisSessionService.getSession(chatId)).thenReturn(session);
        when(jenkins.getLastBuildLogs("pipe")).thenReturn(Mono.error(new RuntimeException("fail!")));
        boolean handled = command.handle(update);
        assertTrue(handled);
        verify(bot, atLeastOnce()).execute(argThat((SendMessage m) ->
                m.getParameters().get("text").toString().contains("Failed to fetch logs")));
    }

    @Test
    void testHandle_NotLogsCommand() {
        when(message.text()).thenReturn("/other");
        boolean handled = command.handle(update);
        assertFalse(handled);
        verifyNoInteractions(redisSessionService);
    }

    @Test
    void testGetCommand() {
        assertEquals("/logs", command.getCommand());
    }
}
