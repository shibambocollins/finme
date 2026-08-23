package com.finme.backend.entity;

/**
 * How a credit account is being repaid (FR-2.1.2, "payment history").
 * <p>
 * A small enum rather than free text: this feeds the prioritisation in Iteration 9, and code
 * that has to reason about "late", "Late", "1 month behind" and "arrears" as equivalent strings
 * would be guessing. UNKNOWN is a real answer - a user adding accounts from memory may not know,
 * and forcing them to pick a wrong value would be worse than recording that they did not say.
 */
public enum PaymentStatus {
    ON_TIME, LATE, DEFAULTED, UNKNOWN
}
