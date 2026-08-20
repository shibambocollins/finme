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

    /** Only transactions geocoded from a receipt's printed address ever appear here - see
     * Transaction.address/latitude/longitude. */
    public record SpendLocation(String merchant, BigDecimal amount, double latitude, double longitude) {
    }
}
