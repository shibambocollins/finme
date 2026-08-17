package com.finme.backend.ai;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Structured output of an AI provider call (FR-1.4.1). Financial figures arrive here as
 * whatever the AI extracted from the source text - they are persisted as-is, but any
 * downstream calculation (e.g. credit utilization, Phase 2) is always deterministic code,
 * never AI arithmetic.
 */
public record ExtractedTransaction(
        LocalDate date,
        String merchant,
        BigDecimal amount,
        String category,
        String description
) {
}
