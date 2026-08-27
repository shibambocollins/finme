package com.finme.backend.dto;

import java.math.BigDecimal;

/**
 * A budget alongside this month's calculated spend against it - deterministic arithmetic, no
 * AI involved anywhere in this feature (same principle as FR-2.2.1, applied by consistency
 * rather than a specific requirement).
 *
 * @param spent       this month's spend in this category, computed via SpendMath - same
 *                    income-exclusion and refund-netting rules the dashboard total uses
 * @param remaining   monthlyLimit - spent; negative once over budget, not clamped to zero,
 *                    since "R450 over" is the actionable number, not "R0 remaining"
 * @param percentUsed a ratio (0.42 is 42%), uncapped - it can exceed 1.0
 * @param overBudget  spent > monthlyLimit
 */
public record BudgetStatusResponse(
        Long id,
        String category,
        BigDecimal monthlyLimit,
        BigDecimal spent,
        BigDecimal remaining,
        BigDecimal percentUsed,
        boolean overBudget) {
}
