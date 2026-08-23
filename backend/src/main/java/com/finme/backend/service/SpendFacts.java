package com.finme.backend.service;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

/**
 * A deterministic snapshot of a user's spending, computed entirely in code.
 * <p>
 * This is the boundary that keeps FR-2.2.1 honest. The AI that writes recommendations is given
 * <em>this</em> - already-calculated totals, differences and percentages - and is asked only to
 * interpret and prioritise them. It never sees raw transactions and is never asked to add
 * anything up, so a recommendation cannot contain a figure the app did not itself calculate.
 */
record SpendFacts(
        YearMonth month,
        BigDecimal monthSpend,
        BigDecimal previousMonthSpend,
        /** monthSpend - previousMonthSpend. Negative means the user spent less. */
        BigDecimal changeAmount,
        /** Percentage change, or null when the previous month has no spend to compare against. */
        BigDecimal changePercent,
        int transactionCount,
        List<CategoryFact> topCategories
) {

    record CategoryFact(
            String category,
            BigDecimal amount,
            BigDecimal previousAmount,
            BigDecimal changeAmount,
            BigDecimal changePercent
    ) {
    }

    /** True when there is nothing worth narrating - the caller skips the AI call entirely. */
    boolean isEmpty() {
        return transactionCount == 0;
    }

    /**
     * Renders the facts as the compact text block handed to the model.
     * <p>
     * Only categories and figures go in - no merchant names, no dates, no account details.
     * Recommendations do not need them, and the smallest prompt that answers the question is
     * also the one that discloses least (NFR-2) and costs least against a per-minute token
     * budget.
     */
    String asPromptText() {
        StringBuilder text = new StringBuilder();
        text.append("Month: ").append(month).append('\n');
        text.append("Total spend this month: ").append(monthSpend).append('\n');
        text.append("Total spend previous month: ").append(previousMonthSpend).append('\n');
        text.append("Change: ").append(changeAmount);
        if (changePercent != null) {
            text.append(" (").append(changePercent).append("%)");
        }
        text.append('\n');
        text.append("Number of transactions this month: ").append(transactionCount).append('\n');
        text.append("Spend by category this month (category, amount, previous month, change):\n");
        for (CategoryFact category : topCategories) {
            text.append("- ").append(category.category())
                    .append(": ").append(category.amount())
                    .append(", previous ").append(category.previousAmount())
                    .append(", change ").append(category.changeAmount());
            if (category.changePercent() != null) {
                text.append(" (").append(category.changePercent()).append("%)");
            }
            text.append('\n');
        }
        return text.toString();
    }
}
