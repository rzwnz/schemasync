package com.example.schemasync.service.interfaces;

import com.example.schemasync.dto.DbMergeConfig;

public interface IDataTransferService {
    void transfer(DbMergeConfig cfg) throws Exception;
}