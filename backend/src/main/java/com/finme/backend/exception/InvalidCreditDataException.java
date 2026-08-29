package com.finme.backend.exception;

import org.springframework.http.HttpStatus;

public class InvalidCreditDataException extends ApiException {

    public InvalidCreditDataException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
