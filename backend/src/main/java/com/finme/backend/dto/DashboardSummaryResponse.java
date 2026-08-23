package com.finme.backend.dto;

import java.math.BigDecimal;
import java.util.List;

public record DashboardSummaryResponse(
        BigDecimal totalSpend,
        List<CategoryAmount> categoryBreakdown,
        List<MonthlyAmount> trend
) {
    public record CategoryAmount(String category, BigDecimal amount) {
    }

    public record MonthlyAmount(String month, BigDecimal amount) {
    }
}
