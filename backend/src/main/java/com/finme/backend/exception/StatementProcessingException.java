package com.finme.backend.exception;

import com.finme.backend.exception.ApiException;
import org.springframework.http.HttpStatus;

public class StatementProcessingException extends ApiException {

    public StatementProcessingException(Long statementId, Throwable cause) {
        super(HttpStatus.UNPROCESSABLE_ENTITY,
                "Failed to process statement " + statementId + ": " + cause.getMessage(), cause);
    }
}
