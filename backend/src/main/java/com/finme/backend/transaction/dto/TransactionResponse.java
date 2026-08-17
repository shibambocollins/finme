package com.finme.backend.transaction.dto;

import com.finme.backend.transaction.PaymentMethod;
import com.finme.backend.transaction.SourceType;
import com.finme.backend.transaction.Transaction;
import com.finme.backend.transaction.TransactionStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TransactionResponse(
        Long id,
        SourceType sourceType,
        LocalDate date,
        String merchant,
        BigDecimal amount,
        String category,
        String description,
        PaymentMethod paymentMethod,
        TransactionStatus status
) {
    public static TransactionResponse from(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getSourceType(),
                transaction.getDate(),
                transaction.getMerchant(),
                transaction.getAmount(),
                transaction.getCategory(),
                transaction.getDescription(),
                transaction.getPaymentMethod(),
                transaction.getStatus());
    }
}
