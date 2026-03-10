package com.example.schemasyncbot.commands;

import com.example.schemasyncbot.model.BotState;
import com.example.schemasyncbot.model.SessionData;
import com.example.schemasyncbot.service.JenkinsService;
import com.example.schemasyncbot.service.RedisSessionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
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

class PipelinesCommandTest {
    private TelegramBot bot;
    private JenkinsService jenkins;
    private RedisSessionService redisSessionService;
    private PipelinesCommand command;
    private Update update;
    private Message message;
    private Chat chat;
    private SessionData session;
    private final Long chatId = 123L;

    @BeforeEach
    void setUp() {
        bot = mock(TelegramBot.class);
        jenkins = mock(JenkinsService.class);
        redisSessionService = mock(RedisSessionService.class);
        session = new SessionData();
        when(redisSessionService.getSession(chatId)).thenReturn(session);
        command = new PipelinesCommand(bot, jenkins, redisSessionService);
        update = mock(Update.class);
        message = mock(Message.class);
        chat = mock(Chat.class);
        when(update.message()).thenReturn(message);
        when(message.text()).thenReturn("/pipelines");
        when(message.chat()).thenReturn(chat);
        when(chat.id()).thenReturn(chatId);
    }

    @Test
    void testHandle_JobsPresent() {
        ArrayNode jobs = JsonNodeFactory.instance.arrayNode();
        jobs.add(JsonNodeFactory.instance.objectNode().put("name", "job1").put("displayName", "Job One"));
        jobs.add(JsonNodeFactory.instance.objectNode().put("name", "job2"));
        JsonNode jobsNode = JsonNodeFactory.instance.objectNode().set("jobs", jobs);
        when(jenkins.listJobs()).thenReturn(Mono.just(jobsNode));
        boolean handled = command.handle(update);
        assertTrue(handled);
        verify(bot).execute(argThat((SendMessage m) ->
                m.getParameters().get("text").toString().contains("Available Pipelines")));
        assertEquals(BotState.SELECTING_PIPELINE, session.getState());
        verify(redisSessionService).saveSession(chatId, session);
    }

    @Test
    void testHandle_NoJobs() {
        JsonNode jobsNode = JsonNodeFactory.instance.objectNode();
        when(jenkins.listJobs()).thenReturn(Mono.just(jobsNode));
        boolean handled = command.handle(update);
        assertTrue(handled);
        verify(bot).execute(argThat((SendMessage m) ->
                m.getParameters().get("text").toString().contains("No Pipelines Found")));
    }

    @Test
    void testHandle_JenkinsError() {
        when(jenkins.listJobs()).thenReturn(Mono.error(new RuntimeException("fail!")));
        boolean handled = command.handle(update);
        assertTrue(handled);
        verify(bot, atLeastOnce()).execute(argThat((SendMessage m) ->
                m.getParameters().get("text").toString().contains("Failed to fetch Jenkins jobs")));
    }

    @Test
    void testHandle_NotPipelinesCommand() {
        when(message.text()).thenReturn("/other");
        boolean handled = command.handle(update);
        assertFalse(handled);
        verify(bot, never()).execute(any());
    }

    @Test
    void testGetCommand() {
        assertEquals("/pipelines", command.getCommand());
    }
}
