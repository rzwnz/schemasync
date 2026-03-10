package com.example.schemasyncbot.commands;

import com.example.schemasyncbot.model.BotState;
import com.example.schemasyncbot.service.RedisSessionService;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.User;
import com.pengrad.telegrambot.request.SendMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class StartCommandTest {
    private TelegramBot bot;
    private RedisSessionService redisSessionService;
    private StartCommand command;
    private final Long chatId = 123L;

    @BeforeEach
    void setup() {
        bot = mock(TelegramBot.class);
        redisSessionService = mock(RedisSessionService.class);
        command = new StartCommand(bot, redisSessionService);
    }

    @Test
    void handle_withStartMessage_sendsWelcomeAndResetsSession() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Chat chat = mock(Chat.class);
        User user = mock(User.class);
        when(update.message()).thenReturn(message);
        when(message.text()).thenReturn("/start");
        when(message.chat()).thenReturn(chat);
        when(chat.id()).thenReturn(chatId);
        when(message.from()).thenReturn(user);
        when(user.firstName()).thenReturn("Test");

        boolean result = command.handle(update);

        assertThat(result).isTrue();
        verify(redisSessionService).saveSession(eq(chatId), argThat(session ->
                session.getState() == BotState.IDLE));
        verify(bot, atLeastOnce()).execute(argThat((SendMessage m) ->
                m.getParameters().get("text").toString().contains("SchemaSync Bot")));
    }

    @Test
    void handle_withStartMessage_noUser_sendsGenericWelcome() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Chat chat = mock(Chat.class);
        when(update.message()).thenReturn(message);
        when(message.text()).thenReturn("/start");
        when(message.chat()).thenReturn(chat);
        when(chat.id()).thenReturn(chatId);
        when(message.from()).thenReturn(null);

        boolean result = command.handle(update);

        assertThat(result).isTrue();
        verify(bot, atLeastOnce()).execute(argThat((SendMessage m) ->
                m.getParameters().get("text").toString().contains("Hello!")));
    }

    @Test
    void handle_withOtherMessage_returnsFalse() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        when(update.message()).thenReturn(message);
        when(message.text()).thenReturn("/other");
        boolean result = command.handle(update);
        assertThat(result).isFalse();
        verify(bot, never()).execute(any(SendMessage.class));
    }

    @Test
    void getCommand_returnsStart() {
        assertThat(command.getCommand()).isEqualTo("/start");
    }
}
