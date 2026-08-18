package com.finme.backend.exception;

import org.springframework.http.HttpStatus;

public class ReceiptProcessingException extends ApiException {

    public ReceiptProcessingException(Long receiptId, Throwable cause) {
        super(HttpStatus.UNPROCESSABLE_ENTITY,
                "Failed to process receipt " + receiptId + ": " + cause.getMessage(), cause);
    }
}
