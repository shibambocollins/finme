package com.finme.backend.entity;

/**
 * Which way the money moved. Statements print this as two separate columns (Debit / Credit)
 * and the amounts in both are positive, so direction cannot be inferred from the number - it
 * has to be carried explicitly from extraction through to the dashboard.
 * <p>
 * Category is not a substitute for this. Verified live on 2026-08-21: a refund line extracted
 * as merchant "REFUND WOOLWORTHS SANDTON CITY", category "Groceries" - which is correct, it IS
 * a groceries refund - while being money coming in. Filtering the "Income" category alone
 * would still have counted that R842.15 as spending.
 */
public enum TransactionDirection {

    /** Money out - a purchase, fee, or debit-order. The default for anything unlabelled. */
    DEBIT,

    /** Money in - a salary/deposit (category Income) or a refund/reversal (any category). */
    CREDIT;

    /**
     * Maps the AI's raw direction string onto this enum. Anything unrecognised, missing, or
     * blank falls back to DEBIT rather than throwing: a single odd value from a model should
     * degrade one row's accuracy, not fail an entire statement upload that was otherwise fine.
     */
    public static TransactionDirection fromExtracted(String value) {
        return value != null && "CREDIT".equalsIgnoreCase(value.trim()) ? CREDIT : DEBIT;
    }
}
