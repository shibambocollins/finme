package com.finme.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record ConfirmAccountActionRequest(@NotBlank String confirmationEmail) {
}
