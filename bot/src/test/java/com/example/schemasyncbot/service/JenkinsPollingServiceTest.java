package com.example.schemasyncbot.service;

import com.example.schemasyncbot.model.SessionData;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.request.SendMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import reactor.core.publisher.Mono;


class JenkinsPollingServiceTest {
    private TelegramBot bot;
    private JenkinsService jenkins;
    private SchemaSyncService schemaSync;
    private JenkinsPollingService service;
    private SessionData session;
    private final Long chatId = 123L;

    @BeforeEach
    void setup() {
        bot = mock(TelegramBot.class);
        jenkins = mock(JenkinsService.class);
        schemaSync = mock(SchemaSyncService.class);
        service = new JenkinsPollingService(bot, jenkins, schemaSync);
        session = new SessionData();
    }

    @Test
    void pollJenkinsForDiffArtifact_handlesBuildNumberError() {
        when(jenkins.getLastBuildNumber(anyString()))
            .thenReturn(Mono.error(new RuntimeException("fail")));
        service.pollJenkinsForDiffArtifact(chatId, "job", session);
        // No need to block, just verify bot interaction
        verify(bot, atLeastOnce()).execute(any(SendMessage.class));
    }
} 