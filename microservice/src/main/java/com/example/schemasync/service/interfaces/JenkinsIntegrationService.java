package com.example.schemasync.service.interfaces;

import java.util.Map;

public interface JenkinsIntegrationService {
    /** Trigger the configured Jenkins job with a DIFF_ID parameter. */
    void triggerApplyJob(Long diffId);

    /** Trigger a Jenkins job with arbitrary parameters. */
    void triggerJobWithParams(Long diffId, Map<String, String> extraParams);

    /** Get the current status of the latest build for the configured job. */
    String getLastBuildStatus();
}