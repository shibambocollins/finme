package com.finme.backend.exception;

import org.springframework.http.HttpStatus;

/**
 * Raised for a transaction id that does not exist <em>or</em> belongs to another user - the
 * same 404 either way, so sequential ids cannot be probed to learn what exists. Same pattern as
 * StatementNotFoundException and CreditAccountNotFoundException.
 */
public class TransactionNotFoundException extends ApiException {

    public TransactionNotFoundException(Long transactionId) {
        super(HttpStatus.NOT_FOUND, "Transaction " + transactionId + " was not found");
    }
}
