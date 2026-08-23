package com.finme.backend.dto;

import com.finme.backend.entity.BankStatement;
import com.finme.backend.entity.StatementStatus;

import java.time.Instant;

public record BankStatementResponse(
        Long id,
        Instant uploadDate,
        StatementStatus status,
        Integer totalChunks,
        Integer processedChunks,
        String failureReason) {

    public static BankStatementResponse from(BankStatement statement) {
        return new BankStatementResponse(
                statement.getId(),
                statement.getUploadDate(),
                statement.getStatus(),
                statement.getTotalChunks(),
                statement.getProcessedChunks(),
                statement.getFailureReason());
    }
}
