package com.finme.backend.service;

import com.finme.backend.dto.BudgetStatusResponse;
import com.finme.backend.dto.CreateOrUpdateBudgetRequest;
import com.finme.backend.entity.Budget;
import com.finme.backend.entity.PaymentMethod;
import com.finme.backend.entity.SourceType;
import com.finme.backend.entity.Transaction;
import com.finme.backend.entity.TransactionDirection;
import com.finme.backend.entity.TransactionStatus;
import com.finme.backend.exception.BudgetNotFoundException;
import com.finme.backend.repository.BudgetRepository;
import com.finme.backend.repository.TransactionRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BudgetServiceTest {

    private static final Long USER = 1L;
    private static final Long OTHER_USER = 2L;
    private static final Clock JULY_20 =
            Clock.fixed(LocalDate.of(2026, 7, 20).atStartOfDay(ZoneId.systemDefault()).toInstant(),
                    ZoneId.systemDefault());

    private final BudgetRepository budgetRepository = mock(BudgetRepository.class);
    private final TransactionRepository transactionRepository = mock(TransactionRepository.class);
    private final BudgetService service = new BudgetService(budgetRepository, transactionRepository, JULY_20);

    private static Transaction debit(LocalDate date, String amount, String category) {
        Transaction t = new Transaction();
        t.setUserId(USER);
        t.setSourceType(SourceType.STATEMENT);
        t.setDate(date);
        t.setMerchant("merchant");
        t.setAmount(new BigDecimal(amount));
        t.setCategory(category);
        t.setPaymentMethod(PaymentMethod.CARD);
        t.setStatus(TransactionStatus.ACTIVE);
        t.setDirection(TransactionDirection.DEBIT);
        return t;
    }

    private static Transaction credit(LocalDate date, String amount, String category) {
        Transaction t = debit(date, amount, category);
        t.setDirection(TransactionDirection.CREDIT);
        return t;
    }

    private void givenTransactions(Transaction... transactions) {
        when(transactionRepository.findByUserIdAndStatusOrderByDateDesc(USER, TransactionStatus.ACTIVE))
                .thenReturn(List.of(transactions));
    }

    private static Budget budget(long id, Long userId, String category, String limit) {
        Budget b = new Budget();
        b.setId(id);
        b.setUserId(userId);
        b.setCategory(category);
        b.setMonthlyLimit(new BigDecimal(limit));
        return b;
    }

    // ------------------------------------------------------------------ status calculation

    @Test
    void reportsSpendRemainingAndPercentUsed() {
        givenTransactions(
                debit(LocalDate.of(2026, 7, 5), "1200.00", "Groceries"),
                debit(LocalDate.of(2026, 7, 15), "800.00", "Groceries"));
        when(budgetRepository.findByUserIdOrderByCategoryAsc(USER))
                .thenReturn(List.of(budget(10, USER, "Groceries", "3000.00")));

        BudgetStatusResponse status = service.list(USER).get(0);

        assertThat(status.spent()).isEqualByComparingTo("2000.00");
        assertThat(status.remaining()).isEqualByComparingTo("1000.00");
        assertThat(status.percentUsed()).isEqualByComparingTo("0.6667");
        assertThat(status.overBudget()).isFalse();
    }

    @Test
    void flagsOverBudgetAndReportsANegativeRemainingRatherThanClampingToZero() {
        // R450 over is the actionable number; "R0 remaining" hides how far over it is.
        givenTransactions(debit(LocalDate.of(2026, 7, 5), "3450.00", "Groceries"));
        when(budgetRepository.findByUserIdOrderByCategoryAsc(USER))
                .thenReturn(List.of(budget(10, USER, "Groceries", "3000.00")));

        BudgetStatusResponse status = service.list(USER).get(0);

        assertThat(status.remaining()).isEqualByComparingTo("-450.00");
        assertThat(status.overBudget()).isTrue();
    }

    @Test
    void matchesTransactionCategoryCaseInsensitively() {
        givenTransactions(debit(LocalDate.of(2026, 7, 5), "500.00", "groceries"));
        when(budgetRepository.findByUserIdOrderByCategoryAsc(USER))
                .thenReturn(List.of(budget(10, USER, "Groceries", "3000.00")));

        assertThat(service.list(USER).get(0).spent()).isEqualByComparingTo("500.00");
    }

    @Test
    void onlyCountsThisMonthsSpending() {
        givenTransactions(
                debit(LocalDate.of(2026, 7, 5), "500.00", "Groceries"),
                debit(LocalDate.of(2026, 6, 30), "9000.00", "Groceries"),
                debit(LocalDate.of(2026, 8, 1), "9000.00", "Groceries"));
        when(budgetRepository.findByUserIdOrderByCategoryAsc(USER))
                .thenReturn(List.of(budget(10, USER, "Groceries", "3000.00")));

        assertThat(service.list(USER).get(0).spent()).isEqualByComparingTo("500.00");
    }

    @Test
    void excludesIncomeAndNetsRefundsExactlyAsTheDashboardDoes() {
        givenTransactions(
                credit(LocalDate.of(2026, 7, 1), "18500.00", "Income"),
                debit(LocalDate.of(2026, 7, 5), "800.00", "Groceries"),
                credit(LocalDate.of(2026, 7, 10), "200.00", "Groceries"));
        when(budgetRepository.findByUserIdOrderByCategoryAsc(USER))
                .thenReturn(List.of(budget(10, USER, "Groceries", "3000.00")));

        assertThat(service.list(USER).get(0).spent()).isEqualByComparingTo("600.00");
    }

    @Test
    void reportsZeroSpentForABudgetWithNoMatchingTransactions() {
        givenTransactions(debit(LocalDate.of(2026, 7, 5), "500.00", "Dining"));
        when(budgetRepository.findByUserIdOrderByCategoryAsc(USER))
                .thenReturn(List.of(budget(10, USER, "Groceries", "3000.00")));

        BudgetStatusResponse status = service.list(USER).get(0);
        assertThat(status.spent()).isEqualByComparingTo("0.00");
        assertThat(status.remaining()).isEqualByComparingTo("3000.00");
    }

    // ------------------------------------------------------------------ create/update

    @Test
    void createsANewBudgetWhenTheCategoryHasNoneYet() {
        when(budgetRepository.findByUserIdAndCategoryIgnoreCase(USER, "Dining")).thenReturn(Optional.empty());
        when(budgetRepository.save(any(Budget.class))).thenAnswer(i -> {
            Budget b = i.getArgument(0);
            b.setId(20L);
            return b;
        });
        givenTransactions();

        service.createOrUpdate(USER, new CreateOrUpdateBudgetRequest("Dining", new BigDecimal("1500.00")));

        verify(budgetRepository).save(any(Budget.class));
    }

    @Test
    void updatesTheExistingBudgetInsteadOfCreatingASecondOneForTheSameCategory() {
        Budget existing = budget(10, USER, "Groceries", "3000.00");
        when(budgetRepository.findByUserIdAndCategoryIgnoreCase(USER, "groceries")).thenReturn(Optional.of(existing));
        when(budgetRepository.save(any(Budget.class))).thenAnswer(i -> i.getArgument(0));
        givenTransactions();

        service.createOrUpdate(USER, new CreateOrUpdateBudgetRequest("groceries", new BigDecimal("3500.00")));

        assertThat(existing.getMonthlyLimit()).isEqualByComparingTo("3500.00");
        verify(budgetRepository).save(existing);
    }

    // ------------------------------------------------------------------ delete / ownership

    @Test
    void deletesAnOwnedBudget() {
        Budget mine = budget(10, USER, "Groceries", "3000.00");
        when(budgetRepository.findById(10L)).thenReturn(Optional.of(mine));

        service.delete(USER, 10L);

        verify(budgetRepository).delete(mine);
    }

    @Test
    void refusesToDeleteAnotherUsersBudget() {
        Budget theirs = budget(10, OTHER_USER, "Groceries", "3000.00");
        when(budgetRepository.findById(10L)).thenReturn(Optional.of(theirs));

        assertThatThrownBy(() -> service.delete(USER, 10L))
                .isInstanceOf(BudgetNotFoundException.class);
        verify(budgetRepository, never()).delete(any(Budget.class));
    }
}
