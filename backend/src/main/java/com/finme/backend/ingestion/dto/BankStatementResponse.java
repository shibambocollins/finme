package com.finme.backend.ingestion.dto;

import com.finme.backend.ingestion.BankStatement;
import com.finme.backend.ingestion.StatementStatus;

import java.time.Instant;

public record BankStatementResponse(Long id, Instant uploadDate, StatementStatus status) {

    public static BankStatementResponse from(BankStatement statement) {
        return new BankStatementResponse(statement.getId(), statement.getUploadDate(), statement.getStatus());
    }
}
