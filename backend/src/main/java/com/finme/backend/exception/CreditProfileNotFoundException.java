package com.finme.backend.exception;

import org.springframework.http.HttpStatus;

public class CreditProfileNotFoundException extends ApiException {

    public CreditProfileNotFoundException() {
        super(HttpStatus.NOT_FOUND, "No credit profile yet. Create one to start tracking your credit position.");
    }
}
