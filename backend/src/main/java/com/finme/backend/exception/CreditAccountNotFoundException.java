package com.finme.backend.exception;

import org.springframework.http.HttpStatus;

/**
 * Raised for a credit account id that does not exist <em>or</em> belongs to another user's
 * profile - the same 404 either way, so sequential ids cannot be probed to learn about other
 * accounts. Same reasoning as StatementNotFoundException.
 */
public class CreditAccountNotFoundException extends ApiException {

    public CreditAccountNotFoundException(Long accountId) {
        super(HttpStatus.NOT_FOUND, "Credit account " + accountId + " was not found");
    }
}
