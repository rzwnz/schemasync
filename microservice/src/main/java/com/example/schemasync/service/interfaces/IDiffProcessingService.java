package com.example.schemasync.service.interfaces;

import com.example.schemasync.dto.CreateDiffRequest;

public interface IDiffProcessingService {
    void processAsync(Long diffId, CreateDiffRequest request);
}