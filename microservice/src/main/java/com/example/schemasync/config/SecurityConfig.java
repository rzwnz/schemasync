// src/main/java/com/example/schemasync/config/SecurityConfig.java
package com.example.schemasync.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SecurityConfig {

    private final ApiKeyFilter apiKeyFilter;
    private final RateLimitingFilter rateLimitingFilter;

    public SecurityConfig(ApiKeyFilter apiKeyFilter, RateLimitingFilter rateLimitingFilter) {
        this.apiKeyFilter = apiKeyFilter;
        this.rateLimitingFilter = rateLimitingFilter;
    }

    @Bean
    public FilterRegistrationBean<RateLimitingFilter> rateLimitingFilterRegistration() {
        FilterRegistrationBean<RateLimitingFilter> reg = new FilterRegistrationBean<>(rateLimitingFilter);
        reg.addUrlPatterns("/api/diffs/*", "/api/merge/*");
        reg.setOrder(1);
        return reg;
    }

    @Bean
    public FilterRegistrationBean<ApiKeyFilter> apiKeyFilterRegistration() {
        FilterRegistrationBean<ApiKeyFilter> reg = new FilterRegistrationBean<>(apiKeyFilter);
        reg.addUrlPatterns("/api/diffs/*", "/api/merge/*");
        reg.setOrder(2);
        return reg;
    }
}
