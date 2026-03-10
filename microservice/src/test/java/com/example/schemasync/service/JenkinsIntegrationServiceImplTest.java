package com.example.schemasync.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JenkinsIntegrationServiceImplTest {
    private JenkinsIntegrationServiceImpl service;

    @BeforeEach
    void setup() {
        service = new JenkinsIntegrationServiceImpl();
        try {
            java.lang.reflect.Field url = JenkinsIntegrationServiceImpl.class.getDeclaredField("jenkinsUrl");
            url.setAccessible(true);
            url.set(service, "http://localhost:8080");
            java.lang.reflect.Field user = JenkinsIntegrationServiceImpl.class.getDeclaredField("jenkinsUser");
            user.setAccessible(true);
            user.set(service, "user");
            java.lang.reflect.Field token = JenkinsIntegrationServiceImpl.class.getDeclaredField("jenkinsToken");
            token.setAccessible(true);
            token.set(service, "token");
            java.lang.reflect.Field job = JenkinsIntegrationServiceImpl.class.getDeclaredField("jobName");
            job.setAccessible(true);
            job.set(service, "test-job");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void triggerApplyJob_handlesHttpError() throws Exception {
        service.triggerApplyJob(123L);
    }
} 