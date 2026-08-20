package com.finme.backend.service;

import com.finme.backend.dto.DashboardSummaryResponse;
import com.finme.backend.entity.PaymentMethod;
import com.finme.backend.entity.SourceType;
import com.finme.backend.entity.Transaction;
import com.finme.backend.entity.TransactionStatus;
import com.finme.backend.repository.TransactionRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DashboardServiceTest {

    private final TransactionRepository transactionRepository = mock(TransactionRepository.class);
    private final DashboardService dashboardService = new DashboardService(transactionRepository);

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
    void includesOnlyTransactionsThatWereActuallyGeocoded() {
        Transaction geocoded = transaction(LocalDate.of(2026, 1, 5), "Woolworths", "120.00", "Groceries");
        geocoded.setLatitude(-26.1076);
        geocoded.setLongitude(28.0567);
        Transaction ungeocoded = transaction(LocalDate.of(2026, 1, 6), "Corner Cafe", "65.00", "Dining");

        when(transactionRepository.findByUserIdAndStatusOrderByDateDesc(1L, TransactionStatus.ACTIVE))
                .thenReturn(List.of(geocoded, ungeocoded));

        DashboardSummaryResponse summary = dashboardService.getSummary(1L);

        assertThat(summary.locations()).hasSize(1);
        var location = summary.locations().get(0);
        assertThat(location.merchant()).isEqualTo("Woolworths");
        assertThat(location.amount()).isEqualByComparingTo("120.00");
        assertThat(location.latitude()).isEqualTo(-26.1076);
        assertThat(location.longitude()).isEqualTo(28.0567);
    }
}
