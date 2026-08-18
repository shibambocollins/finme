package com.finme.backend.dto;

import com.finme.backend.entity.Receipt;
import com.finme.backend.entity.ReceiptStatus;

import java.time.Instant;

public record ReceiptResponse(Long id, Instant uploadDate, ReceiptStatus status) {

    public static ReceiptResponse from(Receipt receipt) {
        return new ReceiptResponse(receipt.getId(), receipt.getUploadDate(), receipt.getStatus());
    }
}
