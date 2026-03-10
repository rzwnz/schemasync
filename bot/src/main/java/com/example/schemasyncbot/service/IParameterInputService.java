package com.example.schemasyncbot.service;

import com.example.schemasyncbot.model.SessionData;

public interface IParameterInputService {
    boolean handle(Long chatId, SessionData session, String text);
} 