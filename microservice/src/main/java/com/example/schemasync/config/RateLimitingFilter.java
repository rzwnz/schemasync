package com.example.schemasync.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;

import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Collections;

@Component
public class RateLimitingFilter extends OncePerRequestFilter {
    private static final Logger logger = LoggerFactory.getLogger(RateLimitingFilter.class);
    private static final int MAX_TRACKED_IPS = 10_000;

    /** LRU-bounded map to prevent unbounded memory growth from unique IPs. */
    private final Map<String, Bucket> buckets = Collections.synchronizedMap(
            new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Bucket> eldest) {
                    return size() > MAX_TRACKED_IPS;
                }
            });

    @Value("${ratelimit.capacity:20}")
    private int capacity;
    @Value("${ratelimit.refill:60}")
    private int refillSeconds;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String ip = request.getRemoteAddr();
        Bucket bucket = buckets.computeIfAbsent(ip, k -> Bucket.builder()
    .addLimit(
        Bandwidth.builder()
            .capacity(capacity)
            .refillIntervally(capacity, Duration.ofSeconds(refillSeconds))
            .build()
    )
    .build());
        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            logger.warn("Rate limit exceeded: IP={}, Endpoint={}, Time={}", ip, request.getRequestURI(), java.time.Instant.now());
            response.setStatus(429);
            response.getWriter().write("Too Many Requests");
        }
    }
} 