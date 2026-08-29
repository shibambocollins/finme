package com.finme.backend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record RecordCreditScoreRequest(
        @NotNull(message = "Score is required")
        @PositiveOrZero(message = "Score cannot be negative")
        Integer score) {
}
