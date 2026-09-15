package com.finme.backend.dto;

import com.finme.backend.entity.Receipt;
import com.finme.backend.entity.ReceiptStatus;

import java.time.Instant;

/**
 * Carries failureReason because receipt extraction now runs in the background: the upload
 * request returns before the vision call has happened, so an error can no longer come back as
 * the response to it. Polling this is how the user finds out what went wrong.
 */
public record ReceiptResponse(Long id, Instant uploadDate, ReceiptStatus status, String failureReason) {

    public static ReceiptResponse from(Receipt receipt) {
        return new ReceiptResponse(
                receipt.getId(),
                receipt.getUploadDate(),
                receipt.getStatus(),
                receipt.getFailureReason());
    }
}
