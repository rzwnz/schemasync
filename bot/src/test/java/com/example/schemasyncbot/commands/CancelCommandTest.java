package com.example.schemasyncbot.commands;

import com.example.schemasyncbot.model.BotState;
import com.example.schemasyncbot.model.SessionData;
import com.example.schemasyncbot.service.RedisSessionService;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.SendMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CancelCommandTest {
    private TelegramBot bot;
    private RedisSessionService redisSessionService;
    private CancelCommand command;
    private Update update;
    private Message message;
    private Chat chat;
    private final Long chatId = 123L;

    @BeforeEach
    void setUp() {
        bot = mock(TelegramBot.class);
        redisSessionService = mock(RedisSessionService.class);
        SessionData session = new SessionData();
        session.setSelectedPipeline("pipe");
        when(redisSessionService.getSession(chatId)).thenReturn(session);
        command = new CancelCommand(bot, redisSessionService);
        update = mock(Update.class);
        message = mock(Message.class);
        chat = mock(Chat.class);
        when(update.message()).thenReturn(message);
        when(message.text()).thenReturn("/cancel");
        when(message.chat()).thenReturn(chat);
        when(chat.id()).thenReturn(chatId);
    }

    @Test
    void testHandle_ResetsSessionAndSaves() {
        boolean handled = command.handle(update);
        assertTrue(handled);
        verify(bot).execute(argThat((SendMessage m) ->
                m.getParameters().get("chat_id").equals(chatId)
                        && m.getParameters().get("text").toString().contains("Operation Cancelled")));
        verify(redisSessionService).saveSession(eq(chatId), argThat(s ->
                s.getState() == BotState.IDLE));
    }

    @Test
    void testHandle_NotCancelCommand() {
        when(message.text()).thenReturn("/other");
        boolean handled = command.handle(update);
        assertFalse(handled);
        verify(bot, never()).execute(any());
    }

    @Test
    void testGetCommand() {
        assertEquals("/cancel", command.getCommand());
    }
}
