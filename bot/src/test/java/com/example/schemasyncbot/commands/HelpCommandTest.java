package com.example.schemasyncbot.commands;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.SendMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HelpCommandTest {
    private TelegramBot bot;
    private HelpCommand command;
    private Update update;
    private Message message;
    private Chat chat;
    private final Long chatId = 123L;

    @BeforeEach
    void setUp() {
        bot = mock(TelegramBot.class);
        command = new HelpCommand(bot);
        update = mock(Update.class);
        message = mock(Message.class);
        chat = mock(Chat.class);
        when(update.message()).thenReturn(message);
        when(message.text()).thenReturn("/help");
        when(message.chat()).thenReturn(chat);
        when(chat.id()).thenReturn(chatId);
    }

    @Test
    void testHandle_SendsHelpOverview() {
        boolean handled = command.handle(update);
        assertTrue(handled);
        verify(bot).execute(argThat((SendMessage m) ->
                m.getParameters().get("text").toString().contains("SchemaSync Bot")
                        && m.getParameters().get("text").toString().contains("Help")));
    }

    @Test
    void testHandle_NotHelpCommand() {
        when(message.text()).thenReturn("/other");
        boolean handled = command.handle(update);
        assertFalse(handled);
        verify(bot, never()).execute(any());
    }

    @Test
    void testGetCommand() {
        assertEquals("/help", command.getCommand());
    }
}
