package com.finme.backend.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * The full credit picture: calculated figures, then narration over them (FR-2.2.1, FR-2.2.2,
 * FR-2.3.1, FR-2.3.3).
 *
 * @param overallUtilization a ratio - 0.42 is 42% - or null when no accounts have been entered
 * @param accounts           worst first, each with its own utilization and what clearing it
 *                           would do to the overall figure
 * @param plan               AI-written prioritised steps, empty when the providers were
 *                           unreachable. The figures above stand on their own without it.
 * @param planUnavailableReason why the plan is empty, so the UI can distinguish "no advice yet"
 *                           from "advice failed"
 * @param disclaimer         FR-2.3.3. Always populated, never model-generated - see
 *                           CreditAnalysisService for why that distinction is load-bearing.
 */
public record CreditAnalysisResponse(
        BigDecimal overallUtilization,
        BigDecimal totalBalance,
        BigDecimal totalLimit,
        List<AccountUtilizationResponse> accounts,
        List<String> plan,
        String planUnavailableReason,
        String disclaimer) {

    public record AccountUtilizationResponse(
            Long accountId,
            String accountName,
            BigDecimal balance,
            BigDecimal creditLimit,
            BigDecimal utilization,
            BigDecimal overallReduction) {
    }
}
