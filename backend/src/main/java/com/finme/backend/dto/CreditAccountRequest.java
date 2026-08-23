package com.finme.backend.dto;

import com.finme.backend.entity.PaymentStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * One credit account as entered by the user (FR-2.1.2).
 *
 * @param balance     may be zero, and may exceed the limit - both are real situations, and
 *                    refusing them would stop a user recording an account that is genuinely
 *                    over its limit, which is exactly the account they most need advice on.
 * @param creditLimit must be positive: Iteration 9 divides by it to produce utilization, and a
 *                    zero limit would make that undefined.
 */
public record CreditAccountRequest(
        @NotBlank(message = "Give the account a name")
        @Size(max = 100, message = "Account name is too long")
        String accountName,

        @NotNull(message = "Balance is required")
        @DecimalMin(value = "0.00", message = "Balance cannot be negative")
        @Digits(integer = 10, fraction = 2, message = "Balance must be an amount like 1234.56")
        BigDecimal balance,

        @NotNull(message = "Credit limit is required")
        @DecimalMin(value = "0.01", message = "Credit limit must be greater than zero")
        @Digits(integer = 10, fraction = 2, message = "Credit limit must be an amount like 1234.56")
        BigDecimal creditLimit,

        PaymentStatus paymentStatus) {
}
