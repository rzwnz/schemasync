package com.example.schemasyncbot.service;

import com.example.schemasyncbot.model.SessionData;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.request.SendMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ParameterInputServiceTest {
    private TelegramBot bot;
    private RedisSessionService redisSessionService;
    private ParameterInputService service;
    private SessionData session;
    private final Long chatId = 123L;

    @BeforeEach
    void setup() {
        bot = mock(TelegramBot.class);
        redisSessionService = mock(RedisSessionService.class);
        service = new ParameterInputService(bot, redisSessionService);
        session = new SessionData();
        session.setEnv(new HashMap<>());
        session.setPendingParams(new ArrayList<>());
        session.setConfirmedParams(new HashSet<>());
    }

    @Test
    void handle_editingParam_setsValueAndClears() {
        session.setEditingParam("foo");
        boolean result = service.handle(chatId, session, "bar");
        assertThat(result).isTrue();
        assertThat(session.getEnv().get("foo")).isEqualTo("bar");
        assertThat(session.getEditingParam()).isNull();
        verify(bot, atLeastOnce()).execute(any(SendMessage.class));
    }

    @Test
    void handle_editingParam_extractsValueFromKeyValueInput() {
        session.setEditingParam("foo");
        boolean result = service.handle(chatId, session, "ignored=actual_value");
        assertThat(result).isTrue();
        assertThat(session.getEnv().get("foo")).isEqualTo("actual_value");
        assertThat(session.getEditingParam()).isNull();
    }

    @Test
    void handle_keyValue_setsEnvAndSendsButtons() {
        session.setPendingParams(new ArrayList<>(List.of("foo")));
        boolean result = service.handle(chatId, session, "foo=bar");
        assertThat(result).isTrue();
        assertThat(session.getEnv().get("foo")).isEqualTo("bar");
        verify(bot, atLeastOnce()).execute(any(SendMessage.class));
    }

    @Test
    void handle_keyValue_setsEnvEvenWithoutPendingParams() {
        boolean result = service.handle(chatId, session, "foo=bar");
        assertThat(result).isTrue();
        assertThat(session.getEnv().get("foo")).isEqualTo("bar");
        verify(bot, atLeastOnce()).execute(any(SendMessage.class));
    }

    @Test
    void handle_slashCommand_returnsFalse() {
        boolean result = service.handle(chatId, session, "/notaparam");
        assertThat(result).isFalse();
        verify(bot, never()).execute(any(SendMessage.class));
    }

    @Test
    void handle_plainTextWithoutEquals_returnsFalse() {
        boolean result = service.handle(chatId, session, "just some text");
        assertThat(result).isFalse();
        verify(bot, never()).execute(any(SendMessage.class));
    }
}
