package com.finme.backend.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Both fields are optional (FR-2.1.4): omitting them takes the documented defaults, Experian
 * and 740. They exist so a user on a different bureau's scale can say so at creation time
 * rather than needing a schema change.
 */
public record CreateCreditProfileRequest(
        @Size(max = 60, message = "Bureau name is too long")
        String bureau,

        @Positive(message = "Maximum score must be greater than zero")
        Integer maxScore) {
}
