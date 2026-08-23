package com.finme.backend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * A credit score reading (FR-2.1.3). The upper bound is not annotated here because it is the
 * profile's own max_score, which this record cannot see - that check lives in the service,
 * where the profile is loaded.
 */
public record RecordCreditScoreRequest(
        @NotNull(message = "Score is required")
        @PositiveOrZero(message = "Score cannot be negative")
        Integer score) {
}
