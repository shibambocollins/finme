package com.finme.backend.exception;

import com.finme.backend.exception.ApiException;
import org.springframework.http.HttpStatus;

public class InvalidStatementFileException extends ApiException {

    public InvalidStatementFileException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
