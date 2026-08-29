package com.finme.backend.service;

import com.finme.backend.dto.CalendarResponse;
import com.finme.backend.dto.CalendarResponse.CalendarDayResponse;
import com.finme.backend.dto.DashboardSummaryResponse;
import com.finme.backend.dto.DashboardSummaryResponse.CategoryAmount;
import com.finme.backend.dto.DashboardSummaryResponse.MonthlyAmount;
import com.finme.backend.entity.Transaction;
import com.finme.backend.entity.TransactionStatus;
import com.finme.backend.repository.TransactionRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Derives total spend, category breakdown, monthly trend, and the daily calendar view from one
 * fetch of a user's active transactions - not one SQL aggregate query per view, which would
 * also have to handle H2 (dev) vs. MySQL (prod) date-function differences for the grouping.
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
    private final Clock clock;

    public DashboardService(TransactionRepository transactionRepository, Clock clock) {
        this.transactionRepository = transactionRepository;
        this.clock = clock;
    }

    public DashboardSummaryResponse getSummary(Long userId) {
        List<Transaction> spendRelated = spendRelatedTransactions(userId);

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

    public CalendarResponse getCalendar(Long userId, YearMonth month) {
        YearMonth target = month == null ? YearMonth.now(clock) : month;

        Map<LocalDate, BigDecimal> spendByDay = spendRelatedTransactions(userId).stream()
                .filter(t -> YearMonth.from(t.getDate()).equals(target))
                .collect(Collectors.groupingBy(
                        Transaction::getDate,
                        Collectors.reducing(BigDecimal.ZERO, SpendMath::contribution, BigDecimal::add)));

        List<CalendarDayResponse> days = new ArrayList<>();
        for (int day = 1; day <= target.lengthOfMonth(); day++) {
            LocalDate date = target.atDay(day);
            days.add(new CalendarDayResponse(date, spendByDay.getOrDefault(date, BigDecimal.ZERO)));
        }

        return new CalendarResponse(target.format(MONTH_FORMAT), days);
    }

    private List<Transaction> spendRelatedTransactions(Long userId) {
        return transactionRepository
                .findByUserIdAndStatusOrderByDateDesc(userId, TransactionStatus.ACTIVE)
                .stream()
                .filter(SpendMath::affectsSpend)
                .toList();
    }
}
