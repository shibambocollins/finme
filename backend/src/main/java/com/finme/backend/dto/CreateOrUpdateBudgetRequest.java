package com.finme.backend.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Sets or changes a monthly budget for one category. Posting a category that already has a
 * budget updates its limit rather than erroring - there is no reason to make "change your
 * Groceries budget from 3000 to 3500" a two-step delete-then-recreate.
 */
public record CreateOrUpdateBudgetRequest(
        @NotBlank(message = "Category is required")
        @Size(max = 60, message = "Category is too long")
        String category,

        @NotNull(message = "Monthly limit is required")
        @DecimalMin(value = "0.01", message = "Monthly limit must be greater than zero")
        @Digits(integer = 10, fraction = 2, message = "Monthly limit must be an amount like 1234.56")
        BigDecimal monthlyLimit) {
}
