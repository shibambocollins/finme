package com.finme.backend.exception;

import org.springframework.http.HttpStatus;

public class TransactionNotFoundException extends ApiException {

    public TransactionNotFoundException(Long transactionId) {
        super(HttpStatus.NOT_FOUND, "Transaction " + transactionId + " was not found");
    }
}
