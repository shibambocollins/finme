package com.finme.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A plain-language description of spending (FR-1.5.1), e.g. "lunch R150 cash today".
 *
 * @param text the user's own words. Length-capped because this goes into an AI prompt - an
 *             unbounded field would let a single request consume a large share of a per-minute
 *             token budget shared with statement extraction.
 */
public record ManualEntryRequest(
        @NotBlank(message = "Describe what you spent")
        @Size(max = 500, message = "Keep it under 500 characters")
        String text) {
}
