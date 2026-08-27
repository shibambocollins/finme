package com.finme.backend.service;

import com.finme.backend.dto.BudgetStatusResponse;
import com.finme.backend.dto.CreateOrUpdateBudgetRequest;
import com.finme.backend.entity.Budget;
import com.finme.backend.entity.TransactionStatus;
import com.finme.backend.exception.BudgetNotFoundException;
import com.finme.backend.repository.BudgetRepository;
import com.finme.backend.repository.TransactionRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.YearMonth;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Monthly per-category spending targets and how the user is tracking against them this month.
 * <p>
 * Every figure here is deterministic arithmetic over stored transactions via {@link SpendMath} -
 * no AI call anywhere in this feature. There is nothing to narrate: "you have spent R2,400 of
 * your R3,000 Groceries budget" does not benefit from a model's interpretation the way a
 * multi-category spend summary does, and adding one would only add a provider dependency,
 * latency, and a rate-limit surface to a feature that does not need any of them.
 */
@Service
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final TransactionRepository transactionRepository;
    private final Clock clock;

    public BudgetService(BudgetRepository budgetRepository, TransactionRepository transactionRepository, Clock clock) {
        this.budgetRepository = budgetRepository;
        this.transactionRepository = transactionRepository;
        this.clock = clock;
    }

    /**
     * Creates a budget for a category, or updates the limit if that user already budgets that
     * category (matched case-insensitively, since category is free text everywhere else in this
     * app and "Groceries" typed once and "groceries" typed later must hit the same row).
     */
    public BudgetStatusResponse createOrUpdate(Long userId, CreateOrUpdateBudgetRequest request) {
        String category = request.category().strip();
        Budget budget = budgetRepository.findByUserIdAndCategoryIgnoreCase(userId, category)
                .orElseGet(Budget::new);
        budget.setUserId(userId);
        budget.setCategory(category);
        budget.setMonthlyLimit(request.monthlyLimit());
        budget = budgetRepository.save(budget);
        return statusOf(budget, spendByCategoryThisMonth(userId));
    }

    public List<BudgetStatusResponse> list(Long userId) {
        Map<String, BigDecimal> spend = spendByCategoryThisMonth(userId);
        return budgetRepository.findByUserIdOrderByCategoryAsc(userId).stream()
                .map(budget -> statusOf(budget, spend))
                .toList();
    }

    public void delete(Long userId, Long budgetId) {
        budgetRepository.delete(requireOwned(userId, budgetId));
    }

    private Budget requireOwned(Long userId, Long budgetId) {
        return budgetRepository.findById(budgetId)
                .filter(b -> b.getUserId().equals(userId))
                .orElseThrow(() -> new BudgetNotFoundException(budgetId));
    }

    /**
     * One fetch, grouped by lower-cased category, reused across every budget - matching the
     * "single fetch then filter in Java" pattern DashboardService and SpendAnalysisService
     * already use, rather than one query per budget.
     */
    private Map<String, BigDecimal> spendByCategoryThisMonth(Long userId) {
        YearMonth thisMonth = YearMonth.now(clock);
        return transactionRepository
                .findByUserIdAndStatusOrderByDateDesc(userId, TransactionStatus.ACTIVE)
                .stream()
                .filter(SpendMath::affectsSpend)
                .filter(t -> YearMonth.from(t.getDate()).equals(thisMonth))
                .collect(Collectors.groupingBy(
                        t -> SpendMath.categoryOf(t).toLowerCase(Locale.ROOT),
                        Collectors.reducing(BigDecimal.ZERO, SpendMath::contribution, BigDecimal::add)));
    }

    private static BudgetStatusResponse statusOf(Budget budget, Map<String, BigDecimal> spendByCategory) {
        BigDecimal spent = spendByCategory.getOrDefault(
                budget.getCategory().toLowerCase(Locale.ROOT), BigDecimal.ZERO);
        BigDecimal remaining = budget.getMonthlyLimit().subtract(spent);
        BigDecimal percentUsed = spent.divide(budget.getMonthlyLimit(), 4, RoundingMode.HALF_UP);

        return new BudgetStatusResponse(
                budget.getId(),
                budget.getCategory(),
                budget.getMonthlyLimit(),
                spent,
                remaining,
                percentUsed,
                spent.compareTo(budget.getMonthlyLimit()) > 0);
    }
}
