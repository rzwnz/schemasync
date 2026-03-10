package com.example.schemasyncbot.service;

import com.example.schemasyncbot.model.SessionData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class RedisSessionService {
    private static final String PREFIX = "telegram:session:";
    private final RedisTemplate<String, Object> redisTemplate;

    @Autowired
    public RedisSessionService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public SessionData getSession(Long chatId) {
        Object obj = redisTemplate.opsForValue().get(PREFIX + chatId);
        return obj instanceof SessionData ? (SessionData) obj : new SessionData();
    }

    public void saveSession(Long chatId, SessionData session) {
        redisTemplate.opsForValue().set(PREFIX + chatId, session, 24, TimeUnit.HOURS);
    }

    public void deleteSession(Long chatId) {
        redisTemplate.delete(PREFIX + chatId);
    }
} 