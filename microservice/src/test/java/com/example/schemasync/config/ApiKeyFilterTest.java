package com.example.schemasync.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import static org.mockito.Mockito.*;

class ApiKeyFilterTest {
    private ApiKeyFilter filter;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain chain;
    private static final String API_KEY = System.getenv().getOrDefault("API_KEY", "dummy-key-for-local");

    @BeforeEach
    void setup() {
        filter = new ApiKeyFilter();
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        chain = mock(FilterChain.class);
        // Set the API key via reflection
        try {
            java.lang.reflect.Field f = ApiKeyFilter.class.getDeclaredField("apiKey");
            f.setAccessible(true);
            f.set(filter, API_KEY);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void doFilterInternal_validKey_allowsRequest() throws ServletException, IOException {
        when(request.getHeader("X-API-KEY")).thenReturn(API_KEY);
        filter.doFilterInternal(request, response, chain);
        verify(chain).doFilter(request, response);
        verify(response, never()).sendError(anyInt(), anyString());
    }

    @Test
    void doFilterInternal_missingKey_blocksRequest() throws ServletException, IOException {
        when(request.getHeader("X-API-KEY")).thenReturn(null);
        filter.doFilterInternal(request, response, chain);
        verify(response).sendError(eq(HttpServletResponse.SC_UNAUTHORIZED), anyString());
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void doFilterInternal_invalidKey_blocksRequest() throws ServletException, IOException {
        when(request.getHeader("X-API-KEY")).thenReturn("wrong-key");
        filter.doFilterInternal(request, response, chain);
        verify(response).sendError(eq(HttpServletResponse.SC_UNAUTHORIZED), anyString());
        verify(chain, never()).doFilter(request, response);
    }
} 