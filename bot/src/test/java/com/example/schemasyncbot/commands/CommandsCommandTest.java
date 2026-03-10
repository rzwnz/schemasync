package com.example.schemasyncbot.commands;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.SendMessage;
import com.example.schemasyncbot.utils.localization.Strings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CommandsCommandTest {
    private TelegramBot bot;
    private CommandsCommand command;
    private Update update;
    private Message message;
    private Chat chat;
    private final Long chatId = 123L;

    @BeforeEach
    void setUp() {
        bot = mock(TelegramBot.class);
        command = new CommandsCommand(bot);
        update = mock(Update.class);
        message = mock(Message.class);
        chat = mock(Chat.class);
        when(update.message()).thenReturn(message);
        when(message.text()).thenReturn("/commands");
        when(message.chat()).thenReturn(chat);
        when(chat.id()).thenReturn(chatId);
    }

    @Test
    void testHandle_CorrectTrigger() {
        Map<String, String> descs = new HashMap<>();
        descs.put("/a", "desc a");
        descs.put("/b", "desc b");
        try (MockedStatic<Strings> mocked = mockStatic(Strings.class)) {
            mocked.when(() -> Strings.getCommandDescriptions(Locale.ENGLISH)).thenReturn(descs);
            boolean handled = command.handle(update);
            assertTrue(handled);
            verify(bot).execute(argThat((SendMessage m) -> m.getParameters().get("chat_id").equals(chatId)
                    && m.getParameters().get("text").toString().contains("Available Commands")
                    && m.getParameters().get("text").toString().contains("/a")
                    && m.getParameters().get("text").toString().contains("/b")));
        }
    }

    @Test
    void testHandle_NotCommandsCommand() {
        when(message.text()).thenReturn("/other");
        boolean handled = command.handle(update);
        assertFalse(handled);
        verify(bot, never()).execute(any());
    }

    @Test
    void testGetCommand() {
        assertEquals("/commands", command.getCommand());
    }
}
