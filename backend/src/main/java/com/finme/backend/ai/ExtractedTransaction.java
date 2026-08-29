package com.finme.backend.ai;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Structured output of an AI provider call (FR-1.4.1). Financial figures arrive here as whatever
 * the AI extracted from the source text - they are persisted as-is, but any downstream
 * calculation (e.g. credit utilization) is always deterministic code, never AI arithmetic.
 */
public record ExtractedTransaction(
        LocalDate date,
        String merchant,
        BigDecimal amount,
        String category,
        String description,
        String paymentMethod,
        /**
         * "DEBIT" (money out) or "CREDIT" (money in), as the AI read it off the statement's
         * Debit/Credit columns - kept as a raw String here for the same reason paymentMethod is:
         * this record is the AI's unvalidated output, and mapping to the entity enum happens at
         * the ingestion boundary where an unrecognised value can be defaulted rather than
         * throwing. Null means the AI didn't say, which callers treat as DEBIT.
         */
        String direction
) {
    public ExtractedTransaction(LocalDate date, String merchant, BigDecimal amount, String category, String description) {
        this(date, merchant, amount, category, description, null, null);
    }

    public ExtractedTransaction(LocalDate date, String merchant, BigDecimal amount, String category, String description, String paymentMethod) {
        this(date, merchant, amount, category, description, paymentMethod, null);
    }
}
