package com.finme.backend.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Shared confirmation payload for the two destructive account-wide actions (UserService:
 * clearFinancialData, deleteAccount). The caller must type the account's own email back -
 * enforced server-side, not just as a frontend prompt, so a direct API call cannot skip the
 * confirmation step. Chosen over a password re-check because a Google-only account has no
 * password to re-enter.
 */
public record ConfirmAccountActionRequest(@NotBlank String confirmationEmail) {
}
