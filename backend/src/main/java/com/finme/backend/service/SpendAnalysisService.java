package com.finme.backend.service;

import com.finme.backend.entity.Transaction;
import com.finme.backend.entity.TransactionStatus;
import com.finme.backend.repository.TransactionRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Computes {@link SpendFacts} - every number the recommendation feature will ever quote
 * (FR-2.2.1). Nothing here is estimated, and nothing here calls an AI provider.
 */
@Service
public class SpendAnalysisService {

    /** How many categories are described to the model. Enough to reason over, short enough to stay cheap. */
    private static final int TOP_CATEGORIES = 6;

    private final TransactionRepository transactionRepository;

    public SpendAnalysisService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    SpendFacts factsFor(Long userId) {
        List<Transaction> transactions =
                transactionRepository.findByUserIdAndStatusOrderByDateDesc(userId, TransactionStatus.ACTIVE);

        // "This month" means the most recent month the user actually has data for, not the
        // calendar month. Statements are uploaded after the fact, so anchoring on today would
        // report an empty current month for a user who just uploaded a full statement for July.
        YearMonth month = transactions.stream()
                .filter(SpendMath::affectsSpend)
                .map(t -> YearMonth.from(t.getDate()))
                .max(Comparator.naturalOrder())
                .orElse(null);

        if (month == null) {
            return new SpendFacts(YearMonth.now(), BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, null, 0, List.of());
        }

        YearMonth previous = month.minusMonths(1);
        List<Transaction> current = inMonth(transactions, month);
        List<Transaction> prior = inMonth(transactions, previous);

        BigDecimal monthSpend = SpendMath.totalOf(current);
        BigDecimal previousSpend = SpendMath.totalOf(prior);

        return new SpendFacts(
                month,
                monthSpend,
                previousSpend,
                monthSpend.subtract(previousSpend),
                percentChange(previousSpend, monthSpend),
                current.size(),
                categoryFacts(current, prior));
    }

    private static List<Transaction> inMonth(List<Transaction> transactions, YearMonth month) {
        return transactions.stream()
                .filter(SpendMath::affectsSpend)
                .filter(t -> YearMonth.from(t.getDate()).equals(month))
                .toList();
    }

    private static List<SpendFacts.CategoryFact> categoryFacts(
            List<Transaction> current, List<Transaction> prior) {

        Map<String, BigDecimal> currentByCategory = totalsByCategory(current);
        Map<String, BigDecimal> priorByCategory = totalsByCategory(prior);

        // Current month's categories first (largest first), then any category that existed last
        // month and has since dropped to nothing - a category the user stopped spending in is a
        // real change, and omitting it would hide it from the narration.
        Set<String> categories = new LinkedHashSet<>(currentByCategory.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .toList());
        categories.addAll(priorByCategory.keySet());

        List<SpendFacts.CategoryFact> facts = new ArrayList<>();
        for (String category : categories) {
            if (facts.size() == TOP_CATEGORIES) {
                break;
            }
            BigDecimal amount = currentByCategory.getOrDefault(category, BigDecimal.ZERO);
            BigDecimal previousAmount = priorByCategory.getOrDefault(category, BigDecimal.ZERO);
            facts.add(new SpendFacts.CategoryFact(
                    category,
                    amount,
                    previousAmount,
                    amount.subtract(previousAmount),
                    percentChange(previousAmount, amount)));
        }
        return facts;
    }

    private static Map<String, BigDecimal> totalsByCategory(List<Transaction> transactions) {
        return transactions.stream().collect(Collectors.groupingBy(
                SpendMath::categoryOf,
                Collectors.reducing(BigDecimal.ZERO, SpendMath::contribution, BigDecimal::add)));
    }

    /**
     * Percent change from {@code from} to {@code to}, or null when there is no meaningful
     * baseline. Returning null rather than 0 or "infinity" matters: the prompt omits the
     * percentage entirely in that case, so the model cannot narrate "up 100%" about a category
     * that simply did not exist last month.
     */
    private static BigDecimal percentChange(BigDecimal from, BigDecimal to) {
        if (from == null || from.signum() == 0) {
            return null;
        }
        return to.subtract(from)
                .multiply(BigDecimal.valueOf(100))
                .divide(from.abs(), 1, RoundingMode.HALF_UP);
    }
}
