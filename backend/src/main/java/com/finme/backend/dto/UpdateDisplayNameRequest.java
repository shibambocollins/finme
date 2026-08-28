package com.finme.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateDisplayNameRequest(
        @NotBlank @Size(max = 100, message = "Name is too long") String displayName
) {
}
