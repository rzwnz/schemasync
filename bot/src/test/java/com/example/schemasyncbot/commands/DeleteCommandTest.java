package com.example.schemasyncbot.commands;

import com.example.schemasyncbot.model.SessionData;
import com.example.schemasyncbot.service.RedisSessionService;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.SendMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeleteCommandTest {
    private TelegramBot bot;
    private RedisSessionService redisSessionService;
    private DeleteCommand command;
    private Update update;
    private Message message;
    private Chat chat;
    private final Long chatId = 123L;

    @BeforeEach
    void setUp() {
        bot = mock(TelegramBot.class);
        redisSessionService = mock(RedisSessionService.class);
        command = new DeleteCommand(bot, redisSessionService);
        update = mock(Update.class);
        message = mock(Message.class);
        chat = mock(Chat.class);
        when(update.message()).thenReturn(message);
        when(message.text()).thenReturn("/delete foo");
        when(message.chat()).thenReturn(chat);
        when(chat.id()).thenReturn(chatId);
    }

    @Test
    void testHandle_ParameterExists_RemovesFromEnvAndConfirmed() {
        SessionData session = new SessionData();
        Map<String, String> env = new HashMap<>();
        env.put("foo", "bar");
        session.setEnv(env);
        session.setConfirmedParams(new HashSet<>(Set.of("foo")));
        when(redisSessionService.getSession(chatId)).thenReturn(session);

        boolean handled = command.handle(update);

        assertTrue(handled);
        verify(bot).execute(argThat((SendMessage m) ->
                m.getParameters().get("text").toString().contains("has been removed")));
        verify(redisSessionService).saveSession(chatId, session);
        assertFalse(session.getEnv().containsKey("foo"));
        assertFalse(session.getConfirmedParams().contains("foo"));
    }

    @Test
    void testHandle_ParameterNotExists() {
        SessionData session = new SessionData();
        Map<String, String> env = new HashMap<>();
        env.put("bar", "baz");
        session.setEnv(env);
        when(redisSessionService.getSession(chatId)).thenReturn(session);

        boolean handled = command.handle(update);

        assertTrue(handled);
        verify(bot).execute(argThat((SendMessage m) ->
                m.getParameters().get("text").toString().contains("not found")));
        verify(redisSessionService, never()).saveSession(any(), any());
    }

    @Test
    void testHandle_NotDeleteCommand() {
        when(message.text()).thenReturn("/other");
        boolean handled = command.handle(update);
        assertFalse(handled);
        verifyNoInteractions(redisSessionService);
    }

    @Test
    void testHandle_DeleteWithoutKey_NotMatched() {
        when(message.text()).thenReturn("/delete");
        boolean handled = command.handle(update);
        assertFalse(handled);
        verifyNoInteractions(redisSessionService);
    }

    @Test
    void testGetCommand() {
        assertEquals("/delete", command.getCommand());
    }
}
