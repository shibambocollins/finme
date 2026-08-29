package com.finme.backend.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateCreditProfileRequest(
        @Size(max = 60, message = "Bureau name is too long")
        String bureau,

        @Positive(message = "Maximum score must be greater than zero")
        Integer maxScore) {
}
