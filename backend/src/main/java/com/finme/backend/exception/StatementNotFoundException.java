package com.finme.backend.exception;

import org.springframework.http.HttpStatus;

public class StatementNotFoundException extends ApiException {

    public StatementNotFoundException(Long statementId) {
        super(HttpStatus.NOT_FOUND, "Statement " + statementId + " was not found");
    }
}
