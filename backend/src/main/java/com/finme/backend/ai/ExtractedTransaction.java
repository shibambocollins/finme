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
        String address,
        /**
         * A city/suburb/place name the source text associates with this merchant (e.g. a card
         * statement line reading "KFC V&A WATERFRONT", or a receipt header naming a branch
         * area) - distinct from address: never a full street address, just enough text for
         * TransactionGeocoder to disambiguate a chain's branches without geocoding a bare
         * merchant name nationally. Statements are "specific about location but not address"
         * (Collins, 2026-08-21) - this is what lets statement-sourced transactions appear on
         * the map at all, approximately, when they'll never have a printed address.
         */
        String locationHint,
        /**
         * "DEBIT" (money out) or "CREDIT" (money in), as the AI read it off the statement's
         * Debit/Credit columns - kept as a raw String here for the same reason paymentMethod
         * is: this record is the AI's unvalidated output, and mapping to the entity enum
         * happens at the ingestion boundary where an unrecognised value can be defaulted
         * rather than throwing. Null means the AI didn't say, which callers treat as DEBIT.
         */
        String direction
) {
    /**
     * Statement extraction never asks for paymentMethod or address (statement text has no
     * printed address to extract) - this keeps that call site unchanged.
     */
    public ExtractedTransaction(LocalDate date, String merchant, BigDecimal amount, String category, String description) {
        this(date, merchant, amount, category, description, null, null, null, null);
    }

    /** Pre-address receipt/vision call sites and tests - address defaults to not-extracted. */
    public ExtractedTransaction(LocalDate date, String merchant, BigDecimal amount, String category, String description, String paymentMethod) {
        this(date, merchant, amount, category, description, paymentMethod, null, null, null);
    }

    /** Pre-locationHint receipt call sites and tests - locationHint defaults to not-extracted. */
    public ExtractedTransaction(LocalDate date, String merchant, BigDecimal amount, String category, String description, String paymentMethod, String address) {
        this(date, merchant, amount, category, description, paymentMethod, address, null, null);
    }

    /** Pre-direction call sites and tests - direction defaults to not-extracted (i.e. DEBIT). */
    public ExtractedTransaction(LocalDate date, String merchant, BigDecimal amount, String category, String description, String paymentMethod, String address, String locationHint) {
        this(date, merchant, amount, category, description, paymentMethod, address, locationHint, null);
    }
}
