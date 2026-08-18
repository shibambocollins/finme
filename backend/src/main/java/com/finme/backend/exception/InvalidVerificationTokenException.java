package com.finme.backend.exception;

import org.springframework.http.HttpStatus;

public class InvalidVerificationTokenException extends ApiException {

    public InvalidVerificationTokenException() {
        super(HttpStatus.BAD_REQUEST, "Verification link is invalid or has expired");
    }
}
