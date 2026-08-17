package com.finme.backend.ingestion;

import com.finme.backend.common.ApiException;
import org.springframework.http.HttpStatus;

public class InvalidStatementFileException extends ApiException {

    public InvalidStatementFileException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
