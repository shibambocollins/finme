package com.finme.backend.dto;

import com.finme.backend.entity.PaymentMethod;
import com.finme.backend.entity.TransactionDirection;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A user's correction to an already-extracted transaction.
 * <p>
 * Deliberately narrower than the entity: sourceType, sourceId, and status are system-managed
 * and not exposed here, so this request shape cannot be used to make a receipt-sourced
 * transaction masquerade as a statement one, or resurrect a superseded row. Category is a plain
 * string with no fixed list - the AI's category list is a prompt hint for extraction, not a
 * schema constraint, so a user can rename or invent a category freely here.
 */
public record UpdateTransactionRequest(
        @NotNull(message = "Date is required")
        LocalDate date,

        @NotBlank(message = "Merchant is required")
        @Size(max = 200, message = "Merchant name is too long")
        String merchant,

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.00", message = "Amount cannot be negative")
        @Digits(integer = 10, fraction = 2, message = "Amount must be an amount like 1234.56")
        BigDecimal amount,

        @NotNull(message = "Direction is required")
        TransactionDirection direction,

        @NotBlank(message = "Category is required")
        @Size(max = 60, message = "Category is too long")
        String category,

        @Size(max = 500, message = "Description is too long")
        String description,

        @NotNull(message = "Payment method is required")
        PaymentMethod paymentMethod) {
}
