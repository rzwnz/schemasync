package com.example.schemasyncbot.service;

import com.example.schemasyncbot.model.SessionData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisSessionServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOps;

    @InjectMocks
    private RedisSessionService service;

    @Test
    void getSession_existingSession_returnsSessionData() {
        SessionData expected = new SessionData();
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("telegram:session:123")).thenReturn(expected);

        SessionData result = service.getSession(123L);
        assertThat(result).isSameAs(expected);
    }

    @Test
    void getSession_nullValue_returnsNewSessionData() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("telegram:session:456")).thenReturn(null);

        SessionData result = service.getSession(456L);
        assertThat(result).isNotNull();
    }

    @Test
    void getSession_wrongType_returnsNewSessionData() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("telegram:session:789")).thenReturn("not a SessionData");

        SessionData result = service.getSession(789L);
        assertThat(result).isNotNull();
    }

    @Test
    void saveSession_savesWithTTL() {
        SessionData session = new SessionData();
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        service.saveSession(100L, session);

        verify(valueOps).set("telegram:session:100", session, 24, TimeUnit.HOURS);
    }

    @Test
    void deleteSession_deletesKey() {
        when(redisTemplate.delete("telegram:session:200")).thenReturn(true);

        service.deleteSession(200L);

        verify(redisTemplate).delete("telegram:session:200");
    }
}
