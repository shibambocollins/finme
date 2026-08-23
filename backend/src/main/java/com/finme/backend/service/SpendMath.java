package com.finme.backend.service;

import com.finme.backend.entity.Transaction;
import com.finme.backend.entity.TransactionDirection;

import java.math.BigDecimal;
import java.util.Collection;

/**
 * The single definition of what counts as spending. Every figure the app reports - the
 * dashboard totals, and the facts the AI narrates - is derived from here (FR-2.2.1: financial
 * math is deterministic code, never AI arithmetic).
 * <p>
 * It lives in one place deliberately. The income/refund rules below are subtle enough that a
 * second copy would eventually drift, and the failure mode of drift is the worst kind: the
 * dashboard showing one total while the AI confidently narrates a different one, with both
 * looking plausible.
 */
final class SpendMath {

    private static final String UNCATEGORIZED = "Uncategorized";
    private static final String INCOME = "Income";

    private SpendMath() {
    }

    /**
     * Income is money in that was never spending, so it is excluded from every spend figure.
     * A refund is also money in, but it is NOT excluded: it reverses spending the user really
     * did make, so it belongs in the totals as a negative. That distinction is why direction is
     * a stored field rather than inferred from category - a grocery refund is categorised
     * "Groceries", correctly, and no category filter could tell it from a grocery purchase.
     */
    static boolean affectsSpend(Transaction transaction) {
        return transaction.getDirection() != TransactionDirection.CREDIT
                || !INCOME.equalsIgnoreCase(categoryOf(transaction));
    }

    /** Debits add to spend; credits (refunds/reversals, by this point) subtract from it. */
    static BigDecimal contribution(Transaction transaction) {
        BigDecimal amount = transaction.getAmount();
        return transaction.getDirection() == TransactionDirection.CREDIT ? amount.negate() : amount;
    }

    static BigDecimal totalOf(Collection<Transaction> transactions) {
        return transactions.stream()
                .filter(SpendMath::affectsSpend)
                .map(SpendMath::contribution)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    static String categoryOf(Transaction transaction) {
        String category = transaction.getCategory();
        return (category == null || category.isBlank()) ? UNCATEGORIZED : category;
    }
}
