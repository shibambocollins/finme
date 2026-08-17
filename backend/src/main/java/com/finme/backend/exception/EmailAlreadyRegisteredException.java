package com.finme.backend.exception;

import com.finme.backend.exception.ApiException;
import org.springframework.http.HttpStatus;

public class EmailAlreadyRegisteredException extends ApiException {

    public EmailAlreadyRegisteredException(String email) {
        super(HttpStatus.CONFLICT, "Email already registered: " + email);
    }
}
