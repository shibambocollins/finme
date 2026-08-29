package com.finme.backend.exception;

import org.springframework.http.HttpStatus;

public class CreditAccountNotFoundException extends ApiException {

    public CreditAccountNotFoundException(Long accountId) {
        super(HttpStatus.NOT_FOUND, "Credit account " + accountId + " was not found");
    }
}
