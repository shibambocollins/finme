package com.finme.backend.dto;

import com.finme.backend.entity.PaymentMethod;
import com.finme.backend.entity.SourceType;
import com.finme.backend.entity.Transaction;
import com.finme.backend.entity.TransactionDirection;
import com.finme.backend.entity.TransactionStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TransactionResponse(
        Long id,
        SourceType sourceType,
        LocalDate date,
        String merchant,
        BigDecimal amount,
        TransactionDirection direction,
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
                transaction.getDirection(),
                transaction.getCategory(),
                transaction.getDescription(),
                transaction.getPaymentMethod(),
                transaction.getStatus());
    }
}
