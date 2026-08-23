package com.finme.backend.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** A hypothetical balance for one account (FR-2.3.2). Nothing stored is changed. */
public record UtilizationSimulationRequest(
        @NotNull(message = "Choose an account to simulate")
        Long accountId,

        @NotNull(message = "Enter the balance to simulate")
        @DecimalMin(value = "0.00", message = "Simulated balance cannot be negative")
        @Digits(integer = 10, fraction = 2, message = "Balance must be an amount like 1234.56")
        BigDecimal newBalance) {
}
