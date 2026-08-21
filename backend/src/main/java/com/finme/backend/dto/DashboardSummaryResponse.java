package com.finme.backend.dto;

import java.math.BigDecimal;
import java.util.List;

public record DashboardSummaryResponse(
        BigDecimal totalSpend,
        List<CategoryAmount> categoryBreakdown,
        List<MonthlyAmount> trend,
        List<SpendLocation> locations
) {
    public record CategoryAmount(String category, BigDecimal amount) {
    }

    public record MonthlyAmount(String month, BigDecimal amount) {
    }

    /**
     * approximate=true means TransactionGeocoder resolved this from a merchant name +
     * locationHint fallback (a plausible branch, not necessarily the one visited), not an
     * exact receipt address - the frontend must render these visibly differently.
     */
    public record SpendLocation(String merchant, BigDecimal amount, double latitude, double longitude, boolean approximate) {
    }
}
