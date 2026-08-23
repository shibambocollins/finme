package com.finme.backend.exception;

import org.springframework.http.HttpStatus;

public class CreditProfileAlreadyExistsException extends ApiException {

    public CreditProfileAlreadyExistsException() {
        super(HttpStatus.CONFLICT, "You already have a credit profile.");
    }
}
