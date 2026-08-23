package com.finme.backend.service;

import com.finme.backend.dto.DashboardSummaryResponse;
import com.finme.backend.dto.DashboardSummaryResponse.CategoryAmount;
import com.finme.backend.dto.DashboardSummaryResponse.MonthlyAmount;
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
 * <p>
 * Every figure here is deterministic arithmetic over stored values (FR-2.2.1) - the AI's
 * output is an input to this, never the source of a total. What counts as spending lives in
 * {@link SpendMath}, shared with the analysis that feeds recommendations, so the numbers the
 * dashboard shows and the numbers the AI narrates can never disagree.
 */
@Service
public class DashboardService {

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final TransactionRepository transactionRepository;

    public DashboardService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    public DashboardSummaryResponse getSummary(Long userId) {
        List<Transaction> transactions =
                transactionRepository.findByUserIdAndStatusOrderByDateDesc(userId, TransactionStatus.ACTIVE);

        List<Transaction> spendRelated = transactions.stream().filter(SpendMath::affectsSpend).toList();

        BigDecimal totalSpend = spendRelated.stream()
                .map(SpendMath::contribution)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<CategoryAmount> categoryBreakdown = spendRelated.stream()
                .collect(Collectors.groupingBy(
                        SpendMath::categoryOf,
                        Collectors.reducing(BigDecimal.ZERO, SpendMath::contribution, BigDecimal::add)))
                .entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .map(entry -> new CategoryAmount(entry.getKey(), entry.getValue()))
                .toList();

        List<MonthlyAmount> trend = spendRelated.stream()
                .collect(Collectors.groupingBy(
                        t -> t.getDate().format(MONTH_FORMAT),
                        Collectors.reducing(BigDecimal.ZERO, SpendMath::contribution, BigDecimal::add)))
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new MonthlyAmount(entry.getKey(), entry.getValue()))
                .toList();

        return new DashboardSummaryResponse(totalSpend, categoryBreakdown, trend);
    }
}
