package com.example.schemasync.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import static org.mockito.Mockito.*;

class RateLimitingFilterTest {
    private RateLimitingFilter filter;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain chain;

    @BeforeEach
    void setup() {
        filter = new RateLimitingFilter();
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        chain = mock(FilterChain.class);
        // Set rate limit fields via reflection
        try {
            java.lang.reflect.Field cap = RateLimitingFilter.class.getDeclaredField("capacity");
            cap.setAccessible(true);
            cap.set(filter, 1);
            java.lang.reflect.Field refill = RateLimitingFilter.class.getDeclaredField("refillSeconds");
            refill.setAccessible(true);
            refill.set(filter, 60);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getRequestURI()).thenReturn("/api/test");
        when(request.getMethod()).thenReturn("GET");
        // Mock getWriter
        try {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            when(response.getWriter()).thenReturn(pw);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void doFilterInternal_allowsFirstRequest() throws ServletException, IOException {
        filter.doFilterInternal(request, response, chain);
        verify(chain).doFilter(request, response);
        verify(response, never()).setStatus(429);
    }

    @Test
    void doFilterInternal_blocksAfterLimit() throws ServletException, IOException {
        // First request allowed
        filter.doFilterInternal(request, response, chain);
        // Second request should be rate limited
        filter.doFilterInternal(request, response, chain);
        verify(response).setStatus(429);
        verify(response).getWriter();
    }
} 