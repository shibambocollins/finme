package com.finme.backend.ingestion;

import com.finme.backend.common.ApiException;
import org.springframework.http.HttpStatus;

public class StatementProcessingException extends ApiException {

    public StatementProcessingException(Long statementId, Throwable cause) {
        super(HttpStatus.UNPROCESSABLE_ENTITY,
                "Failed to process statement " + statementId + ": " + cause.getMessage(), cause);
    }
}
