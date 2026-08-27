package com.finme.backend.service;

import com.finme.backend.dto.DashboardSummaryResponse;
import com.finme.backend.entity.PaymentMethod;
import com.finme.backend.entity.SourceType;
import com.finme.backend.entity.Transaction;
import com.finme.backend.entity.TransactionDirection;
import com.finme.backend.entity.TransactionStatus;
import com.finme.backend.repository.TransactionRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DashboardServiceTest {

    private static final Clock JULY_20_2026 =
            Clock.fixed(LocalDate.of(2026, 7, 20).atStartOfDay(ZoneId.systemDefault()).toInstant(),
                    ZoneId.systemDefault());

    private final TransactionRepository transactionRepository = mock(TransactionRepository.class);
    private final DashboardService dashboardService = new DashboardService(transactionRepository, JULY_20_2026);

    private static Transaction transaction(LocalDate date, String merchant, String amount, String category) {
        Transaction t = new Transaction();
        t.setUserId(1L);
        t.setSourceType(SourceType.STATEMENT);
        t.setDate(date);
        t.setMerchant(merchant);
        t.setAmount(new BigDecimal(amount));
        t.setCategory(category);
        t.setPaymentMethod(PaymentMethod.CARD);
        t.setStatus(TransactionStatus.ACTIVE);
        return t;
    }

    /** Same as transaction(), but money coming in - a salary/deposit or a refund. */
    private static Transaction credit(LocalDate date, String merchant, String amount, String category) {
        Transaction t = transaction(date, merchant, amount, category);
        t.setDirection(TransactionDirection.CREDIT);
        return t;
    }


    @Test
    void queriesOnlyActiveTransactionsForTheGivenUser() {
        when(transactionRepository.findByUserIdAndStatusOrderByDateDesc(1L, TransactionStatus.ACTIVE))
                .thenReturn(List.of());

        dashboardService.getSummary(1L);

        verify(transactionRepository).findByUserIdAndStatusOrderByDateDesc(eq(1L), eq(TransactionStatus.ACTIVE));
    }

    @Test
    void returnsZeroAndEmptyListsWhenThereAreNoTransactions() {
        when(transactionRepository.findByUserIdAndStatusOrderByDateDesc(1L, TransactionStatus.ACTIVE))
                .thenReturn(List.of());

        DashboardSummaryResponse summary = dashboardService.getSummary(1L);

        assertThat(summary.totalSpend()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.categoryBreakdown()).isEmpty();
        assertThat(summary.trend()).isEmpty();
    }

    @Test
    void totalSpendSumsAllTransactionAmounts() {
        when(transactionRepository.findByUserIdAndStatusOrderByDateDesc(1L, TransactionStatus.ACTIVE))
                .thenReturn(List.of(
                        transaction(LocalDate.of(2026, 1, 12), "Woolworths", "450.00", "Groceries"),
                        transaction(LocalDate.of(2026, 1, 14), "Uber", "85.50", "Transport")
                ));

        DashboardSummaryResponse summary = dashboardService.getSummary(1L);

        assertThat(summary.totalSpend()).isEqualByComparingTo("535.50");
    }

    @Test
    void groupsCategoryBreakdownAndFoldsNullCategoryIntoUncategorized() {
        when(transactionRepository.findByUserIdAndStatusOrderByDateDesc(1L, TransactionStatus.ACTIVE))
                .thenReturn(List.of(
                        transaction(LocalDate.of(2026, 1, 1), "Woolworths", "100.00", "Groceries"),
                        transaction(LocalDate.of(2026, 1, 2), "Pick n Pay", "50.00", "Groceries"),
                        transaction(LocalDate.of(2026, 1, 3), "Unknown Merchant", "20.00", null)
                ));

        DashboardSummaryResponse summary = dashboardService.getSummary(1L);

        assertThat(summary.categoryBreakdown()).hasSize(2);
        var groceries = summary.categoryBreakdown().stream()
                .filter(c -> c.category().equals("Groceries"))
                .findFirst().orElseThrow();
        assertThat(groceries.amount()).isEqualByComparingTo("150.00");

        var uncategorized = summary.categoryBreakdown().stream()
                .filter(c -> c.category().equals("Uncategorized"))
                .findFirst().orElseThrow();
        assertThat(uncategorized.amount()).isEqualByComparingTo("20.00");
    }

    @Test
    void categoryBreakdownIsSortedDescendingByAmount() {
        when(transactionRepository.findByUserIdAndStatusOrderByDateDesc(1L, TransactionStatus.ACTIVE))
                .thenReturn(List.of(
                        transaction(LocalDate.of(2026, 1, 1), "Uber", "10.00", "Transport"),
                        transaction(LocalDate.of(2026, 1, 2), "Woolworths", "450.00", "Groceries")
                ));

        DashboardSummaryResponse summary = dashboardService.getSummary(1L);

        assertThat(summary.categoryBreakdown()).extracting("category")
                .containsExactly("Groceries", "Transport");
    }

    @Test
    void groupsTrendByMonthAndSortsChronologically() {
        when(transactionRepository.findByUserIdAndStatusOrderByDateDesc(1L, TransactionStatus.ACTIVE))
                .thenReturn(List.of(
                        transaction(LocalDate.of(2026, 3, 1), "March purchase", "30.00", "Other"),
                        transaction(LocalDate.of(2026, 1, 1), "January purchase 1", "10.00", "Other"),
                        transaction(LocalDate.of(2026, 1, 15), "January purchase 2", "5.00", "Other"),
                        transaction(LocalDate.of(2026, 2, 1), "February purchase", "20.00", "Other")
                ));

        DashboardSummaryResponse summary = dashboardService.getSummary(1L);

        assertThat(summary.trend()).extracting("month").containsExactly("2026-01", "2026-02", "2026-03");
        assertThat(summary.trend().get(0).amount()).isEqualByComparingTo("15.00");
    }

    @Test
    void incomeIsExcludedFromTotalSpendEntirely() {
        when(transactionRepository.findByUserIdAndStatusOrderByDateDesc(1L, TransactionStatus.ACTIVE))
                .thenReturn(List.of(
                        credit(LocalDate.of(2026, 7, 1), "Salary", "18500.00", "Income"),
                        transaction(LocalDate.of(2026, 7, 2), "Woolworths", "842.15", "Groceries")
                ));

        DashboardSummaryResponse summary = dashboardService.getSummary(1L);

        assertThat(summary.totalSpend()).isEqualByComparingTo(new BigDecimal("842.15"));
        assertThat(summary.categoryBreakdown()).extracting(DashboardSummaryResponse.CategoryAmount::category)
                .doesNotContain("Income");
    }

    @Test
    void refundSubtractsFromSpendAndFromItsOwnCategory() {
        // The refund carries category "Groceries", not "Income" - which is correct, it is a
        // groceries refund. Only its direction distinguishes it from a purchase, which is the
        // whole reason direction is stored rather than inferred from category.
        when(transactionRepository.findByUserIdAndStatusOrderByDateDesc(1L, TransactionStatus.ACTIVE))
                .thenReturn(List.of(
                        transaction(LocalDate.of(2026, 7, 2), "Woolworths", "842.15", "Groceries"),
                        transaction(LocalDate.of(2026, 7, 17), "Checkers", "1256.40", "Groceries"),
                        credit(LocalDate.of(2026, 7, 15), "Refund Woolworths", "842.15", "Groceries")
                ));

        DashboardSummaryResponse summary = dashboardService.getSummary(1L);

        assertThat(summary.totalSpend()).isEqualByComparingTo(new BigDecimal("1256.40"));
        assertThat(summary.categoryBreakdown())
                .singleElement()
                .satisfies(c -> {
                    assertThat(c.category()).isEqualTo("Groceries");
                    assertThat(c.amount()).isEqualByComparingTo(new BigDecimal("1256.40"));
                });
    }

    @Test
    void refundAlsoNetsOutOfTheMonthlyTrend() {
        when(transactionRepository.findByUserIdAndStatusOrderByDateDesc(1L, TransactionStatus.ACTIVE))
                .thenReturn(List.of(
                        transaction(LocalDate.of(2026, 7, 2), "Woolworths", "842.15", "Groceries"),
                        credit(LocalDate.of(2026, 7, 15), "Refund Woolworths", "842.15", "Groceries"),
                        transaction(LocalDate.of(2026, 8, 3), "Uber", "87.50", "Transport")
                ));

        DashboardSummaryResponse summary = dashboardService.getSummary(1L);

        assertThat(summary.trend()).hasSize(2);
        assertThat(summary.trend().get(0).month()).isEqualTo("2026-07");
        assertThat(summary.trend().get(0).amount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.trend().get(1).amount()).isEqualByComparingTo(new BigDecimal("87.50"));
    }

    @Test
    void aTransactionWithNoDirectionSetCountsAsSpending() {
        Transaction unset = transaction(LocalDate.of(2026, 7, 2), "Woolworths", "450.00", "Groceries");

        when(transactionRepository.findByUserIdAndStatusOrderByDateDesc(1L, TransactionStatus.ACTIVE))
                .thenReturn(List.of(unset));

        assertThat(unset.getDirection()).isEqualTo(TransactionDirection.DEBIT);
        assertThat(dashboardService.getSummary(1L).totalSpend())
                .isEqualByComparingTo(new BigDecimal("450.00"));
    }

    // ------------------------------------------------------------------ calendar

    @Test
    void returnsOneEntryForEveryDayOfTheMonthIncludingZeroSpendDays() {
        when(transactionRepository.findByUserIdAndStatusOrderByDateDesc(1L, TransactionStatus.ACTIVE))
                .thenReturn(List.of(transaction(LocalDate.of(2026, 7, 2), "Woolworths", "450.00", "Groceries")));

        var calendar = dashboardService.getCalendar(1L, YearMonth.of(2026, 7));

        assertThat(calendar.month()).isEqualTo("2026-07");
        assertThat(calendar.days()).hasSize(31); // July has 31 days
        assertThat(calendar.days().get(0).date()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(calendar.days().get(30).date()).isEqualTo(LocalDate.of(2026, 7, 31));
    }

    @Test
    void putsEachTransactionsAmountOnItsOwnDay() {
        when(transactionRepository.findByUserIdAndStatusOrderByDateDesc(1L, TransactionStatus.ACTIVE))
                .thenReturn(List.of(
                        transaction(LocalDate.of(2026, 7, 2), "Woolworths", "450.00", "Groceries"),
                        transaction(LocalDate.of(2026, 7, 2), "Uber", "50.00", "Transport"),
                        transaction(LocalDate.of(2026, 7, 5), "Netflix", "199.00", "Entertainment")));

        var calendar = dashboardService.getCalendar(1L, YearMonth.of(2026, 7));

        BigDecimal day2 = dayTotal(calendar, LocalDate.of(2026, 7, 2));
        BigDecimal day5 = dayTotal(calendar, LocalDate.of(2026, 7, 5));
        BigDecimal day3 = dayTotal(calendar, LocalDate.of(2026, 7, 3));

        assertThat(day2).isEqualByComparingTo("500.00");
        assertThat(day5).isEqualByComparingTo("199.00");
        assertThat(day3).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void excludesIncomeAndNetsRefundsInTheCalendarTooJustLikeTheSummary() {
        when(transactionRepository.findByUserIdAndStatusOrderByDateDesc(1L, TransactionStatus.ACTIVE))
                .thenReturn(List.of(
                        credit(LocalDate.of(2026, 7, 1), "Salary", "18500.00", "Income"),
                        transaction(LocalDate.of(2026, 7, 2), "Woolworths", "842.15", "Groceries")));

        var calendar = dashboardService.getCalendar(1L, YearMonth.of(2026, 7));

        assertThat(dayTotal(calendar, LocalDate.of(2026, 7, 1))).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(dayTotal(calendar, LocalDate.of(2026, 7, 2))).isEqualByComparingTo("842.15");
    }

    @Test
    void ignoresTransactionsFromOtherMonths() {
        when(transactionRepository.findByUserIdAndStatusOrderByDateDesc(1L, TransactionStatus.ACTIVE))
                .thenReturn(List.of(
                        transaction(LocalDate.of(2026, 6, 30), "June spend", "100.00", "Other"),
                        transaction(LocalDate.of(2026, 7, 1), "July spend", "50.00", "Other"),
                        transaction(LocalDate.of(2026, 8, 1), "August spend", "75.00", "Other")));

        var calendar = dashboardService.getCalendar(1L, YearMonth.of(2026, 7));

        BigDecimal total = calendar.days().stream().map(d -> d.total()).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(total).isEqualByComparingTo("50.00");
    }

    @Test
    void defaultsToTheCurrentMonthWhenNoneIsGiven() {
        when(transactionRepository.findByUserIdAndStatusOrderByDateDesc(1L, TransactionStatus.ACTIVE))
                .thenReturn(List.of());

        // JULY_20_2026 is the fixed clock this test class runs on.
        assertThat(dashboardService.getCalendar(1L, null).month()).isEqualTo("2026-07");
    }

    private static BigDecimal dayTotal(com.finme.backend.dto.CalendarResponse calendar, LocalDate date) {
        return calendar.days().stream()
                .filter(d -> d.date().equals(date))
                .findFirst()
                .orElseThrow()
                .total();
    }
}
