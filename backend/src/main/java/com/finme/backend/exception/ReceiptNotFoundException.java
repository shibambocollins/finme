package com.finme.backend.exception;

import org.springframework.http.HttpStatus;

public class ReceiptNotFoundException extends ApiException {

    public ReceiptNotFoundException(Long receiptId) {
        super(HttpStatus.NOT_FOUND, "Receipt " + receiptId + " was not found");
    }
}
