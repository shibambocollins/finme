package com.finme.backend.exception;

import org.springframework.http.HttpStatus;

public class InvalidReceiptFileException extends ApiException {

    public InvalidReceiptFileException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
