package com.finme.backend.exception;

import org.springframework.http.HttpStatus;

public class InvalidAccountDeletionConfirmationException extends ApiException {

    public InvalidAccountDeletionConfirmationException() {
        super(HttpStatus.BAD_REQUEST, "That doesn't match your account email. Type it exactly to confirm.");
    }
}
