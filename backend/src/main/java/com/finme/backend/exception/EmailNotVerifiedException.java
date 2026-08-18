package com.finme.backend.exception;

import org.springframework.http.HttpStatus;

public class EmailNotVerifiedException extends ApiException {

    public EmailNotVerifiedException() {
        super(HttpStatus.FORBIDDEN, "Please verify your email before logging in");
    }
}
