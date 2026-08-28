package com.finme.backend.exception;

import org.springframework.http.HttpStatus;

/** The typed confirmation on a destructive account action didn't match the signed-in account's
 *  own email (UserService.clearFinancialData, UserService.deleteAccount). */
public class InvalidAccountDeletionConfirmationException extends ApiException {

    public InvalidAccountDeletionConfirmationException() {
        super(HttpStatus.BAD_REQUEST, "That doesn't match your account email. Type it exactly to confirm.");
    }
}
