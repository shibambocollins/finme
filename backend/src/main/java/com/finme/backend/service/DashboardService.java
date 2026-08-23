package com.finme.backend.service;

import com.finme.backend.dto.DashboardSummaryResponse;
import com.finme.backend.dto.DashboardSummaryResponse.CategoryAmount;
import com.finme.backend.dto.DashboardSummaryResponse.MonthlyAmount;
import com.finme.backend.entity.Transaction;
import com.finme.backend.entity.TransactionDirection;
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
 * output is an input to this, never the source of a total.
 */
@Service
public class DashboardService {

    private static final String UNCATEGORIZED = "Uncategorized";
    private static final String INCOME = "Income";
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final TransactionRepository transactionRepository;

    public DashboardService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    public DashboardSummaryResponse getSummary(Long userId) {
        List<Transaction> transactions =
                transactionRepository.findByUserIdAndStatusOrderByDateDesc(userId, TransactionStatus.ACTIVE);

        List<Transaction> spendRelated = transactions.stream().filter(this::affectsSpend).toList();

        BigDecimal totalSpend = spendRelated.stream()
                .map(this::spendContribution)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<CategoryAmount> categoryBreakdown = spendRelated.stream()
                .collect(Collectors.groupingBy(
                        this::categoryOrUncategorized,
                        Collectors.reducing(BigDecimal.ZERO, this::spendContribution, BigDecimal::add)))
                .entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .map(entry -> new CategoryAmount(entry.getKey(), entry.getValue()))
                .toList();

        List<MonthlyAmount> trend = spendRelated.stream()
                .collect(Collectors.groupingBy(
                        t -> t.getDate().format(MONTH_FORMAT),
                        Collectors.reducing(BigDecimal.ZERO, this::spendContribution, BigDecimal::add)))
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new MonthlyAmount(entry.getKey(), entry.getValue()))
                .toList();

        return new DashboardSummaryResponse(totalSpend, categoryBreakdown, trend);
    }

    /**
     * Income is money in that was never spending, so it is dropped from every spend figure -
     * it is not a category of spend, and including it was overstating a sample July statement
     * by R18,500 (verified live, 2026-08-21).
     * <p>
     * A refund is also money in, but it is NOT dropped: it is a reversal of spending that this
     * user really did make, so it belongs in the totals as a negative. That distinction is
     * exactly why direction is a stored field and not inferred from category - the refund in
     * that same statement was categorised "Groceries", correctly, and no category filter could
     * have told it apart from a grocery purchase.
     */
    private boolean affectsSpend(Transaction transaction) {
        return transaction.getDirection() != TransactionDirection.CREDIT
                || !INCOME.equalsIgnoreCase(categoryOrUncategorized(transaction));
    }

    /** Debits add to spend; credits (refunds/reversals, by this point) subtract from it. */
    private BigDecimal spendContribution(Transaction transaction) {
        BigDecimal amount = transaction.getAmount();
        return transaction.getDirection() == TransactionDirection.CREDIT ? amount.negate() : amount;
    }

    private String categoryOrUncategorized(Transaction transaction) {
        String category = transaction.getCategory();
        return (category == null || category.isBlank()) ? UNCATEGORIZED : category;
    }
}
