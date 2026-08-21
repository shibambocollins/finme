package com.finme.backend.service;

import com.finme.backend.dto.DashboardSummaryResponse;
import com.finme.backend.dto.DashboardSummaryResponse.CategoryAmount;
import com.finme.backend.dto.DashboardSummaryResponse.MonthlyAmount;
import com.finme.backend.dto.DashboardSummaryResponse.SpendLocation;
import com.finme.backend.entity.Transaction;
import com.finme.backend.entity.TransactionStatus;
import com.finme.backend.repository.TransactionRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Derives total spend, category breakdown, and monthly trend from one fetch of a user's
 * active transactions - not three separate SQL aggregate queries, which would also have to
 * handle H2 (dev) vs. MySQL (prod) date-function differences for the monthly grouping.
 */
@Service
public class DashboardService {

    private static final String UNCATEGORIZED = "Uncategorized";
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final TransactionRepository transactionRepository;

    public DashboardService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    public DashboardSummaryResponse getSummary(Long userId) {
        List<Transaction> transactions =
                transactionRepository.findByUserIdAndStatusOrderByDateDesc(userId, TransactionStatus.ACTIVE);

        BigDecimal totalSpend = transactions.stream()
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<CategoryAmount> categoryBreakdown = transactions.stream()
                .collect(Collectors.groupingBy(
                        this::categoryOrUncategorized,
                        Collectors.reducing(BigDecimal.ZERO, Transaction::getAmount, BigDecimal::add)))
                .entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .map(entry -> new CategoryAmount(entry.getKey(), entry.getValue()))
                .toList();

        List<MonthlyAmount> trend = transactions.stream()
                .collect(Collectors.groupingBy(
                        t -> t.getDate().format(MONTH_FORMAT),
                        Collectors.reducing(BigDecimal.ZERO, Transaction::getAmount, BigDecimal::add)))
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new MonthlyAmount(entry.getKey(), entry.getValue()))
                .toList();

        List<SpendLocation> locations = transactions.stream()
                .filter(t -> t.getLatitude() != null && t.getLongitude() != null)
                .map(t -> new SpendLocation(t.getMerchant(), t.getAmount(), t.getLatitude(), t.getLongitude(), t.isLocationApproximate()))
                .toList();

        return new DashboardSummaryResponse(totalSpend, categoryBreakdown, trend, locations);
    }

    private String categoryOrUncategorized(Transaction transaction) {
        String category = transaction.getCategory();
        return (category == null || category.isBlank()) ? UNCATEGORIZED : category;
    }
}
