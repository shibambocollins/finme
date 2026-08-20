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
        String description,
        String paymentMethod,
        String address
) {
    /**
     * Statement extraction never asks for paymentMethod or address (statement text has no
     * printed address to extract) - this keeps that call site unchanged.
     */
    public ExtractedTransaction(LocalDate date, String merchant, BigDecimal amount, String category, String description) {
        this(date, merchant, amount, category, description, null, null);
    }

    /** Pre-address receipt/vision call sites and tests - address defaults to not-extracted. */
    public ExtractedTransaction(LocalDate date, String merchant, BigDecimal amount, String category, String description, String paymentMethod) {
        this(date, merchant, amount, category, description, paymentMethod, null);
    }
}
