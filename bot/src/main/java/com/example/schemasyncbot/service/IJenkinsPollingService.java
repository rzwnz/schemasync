package com.example.schemasyncbot.service;

import com.example.schemasyncbot.model.SessionData;

public interface IJenkinsPollingService {
    void pollJenkinsForDiffArtifact(Long chatId, String jobName, SessionData session);
} 