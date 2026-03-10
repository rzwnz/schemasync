// src/main/java/com/example/schemasync/config/ApiKeyFilter.java
package com.example.schemasync.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class ApiKeyFilter extends OncePerRequestFilter {
    private static final Logger logger = LoggerFactory.getLogger(ApiKeyFilter.class);
    @Value("${security.apiKey}")
    private String apiKey;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        String key = request.getHeader("X-API-KEY");
        if (key == null || !timeSafeEquals(key, apiKey)) {
            // Log unauthorized access attempt
            String ip = request.getRemoteAddr();
            String endpoint = request.getRequestURI();
            String method = request.getMethod();
            logger.warn("Unauthorized access attempt: IP={}, Method={}, Endpoint={}, Time={}", ip, method, endpoint, java.time.Instant.now());
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
            return;
        }
        filterChain.doFilter(request, response);
    }

    /**
     * Constant-time string comparison to prevent timing-attack side-channel
     * leakage of the API key value.
     */
    private boolean timeSafeEquals(String a, String b) {
        byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(aBytes, bBytes);
    }
}
