package com.finme.backend.service;

import com.finme.backend.entity.PaymentMethod;
import com.finme.backend.entity.SourceType;
import com.finme.backend.entity.Transaction;
import com.finme.backend.entity.TransactionDirection;
import com.finme.backend.entity.TransactionStatus;
import com.finme.backend.repository.TransactionRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * These figures are what the AI is later asked to narrate, so every one of them has to be
 * right here - a wrong number reaching the model becomes a confident, plausible-sounding wrong
 * recommendation (FR-2.2.1).
 */
class SpendAnalysisServiceTest {

    private final TransactionRepository transactionRepository = mock(TransactionRepository.class);
    private final SpendAnalysisService spendAnalysisService = new SpendAnalysisService(transactionRepository);

    private static Transaction debit(LocalDate date, String amount, String category) {
        Transaction t = new Transaction();
        t.setUserId(1L);
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
        when(transactionRepository.findByUserIdAndStatusOrderByDateDesc(1L, TransactionStatus.ACTIVE))
                .thenReturn(List.of(transactions));
    }

    @Test
    void reportsNothingWhenThereAreNoTransactions() {
        givenTransactions();

        SpendFacts facts = spendAnalysisService.factsFor(1L);

        assertThat(facts.isEmpty()).isTrue();
        assertThat(facts.topCategories()).isEmpty();
    }

    @Test
    void anchorsOnTheLatestMonthWithDataRatherThanTheCalendarMonth() {
        // Statements are uploaded after the fact. Anchoring on today would report an empty
        // month to a user who has just uploaded a full statement for an earlier period.
        givenTransactions(
                debit(LocalDate.of(2026, 7, 2), "500.00", "Groceries"),
                debit(LocalDate.of(2026, 6, 2), "300.00", "Groceries"));

        SpendFacts facts = spendAnalysisService.factsFor(1L);

        assertThat(facts.month()).isEqualTo(YearMonth.of(2026, 7));
        assertThat(facts.monthSpend()).isEqualByComparingTo("500.00");
        assertThat(facts.previousMonthSpend()).isEqualByComparingTo("300.00");
        assertThat(facts.changeAmount()).isEqualByComparingTo("200.00");
    }

    @Test
    void computesPercentChangeAgainstThePreviousMonth() {
        givenTransactions(
                debit(LocalDate.of(2026, 7, 2), "150.00", "Groceries"),
                debit(LocalDate.of(2026, 6, 2), "100.00", "Groceries"));

        assertThat(spendAnalysisService.factsFor(1L).changePercent()).isEqualByComparingTo("50.0");
    }

    @Test
    void leavesPercentChangeNullWhenThereIsNoBaselineToCompareAgainst() {
        // Not zero, and not "infinite growth" - the prompt omits the percentage entirely so the
        // model cannot narrate a rise for a month that had nothing to rise from.
        givenTransactions(debit(LocalDate.of(2026, 7, 2), "150.00", "Groceries"));

        SpendFacts facts = spendAnalysisService.factsFor(1L);

        assertThat(facts.previousMonthSpend()).isEqualByComparingTo("0");
        assertThat(facts.changePercent()).isNull();
    }

    @Test
    void excludesIncomeAndNetsOutRefundsExactlyAsTheDashboardDoes() {
        givenTransactions(
                credit(LocalDate.of(2026, 7, 1), "18500.00", "Income"),
                debit(LocalDate.of(2026, 7, 2), "842.15", "Groceries"),
                credit(LocalDate.of(2026, 7, 15), "842.15", "Groceries"),
                debit(LocalDate.of(2026, 7, 17), "1256.40", "Groceries"));

        SpendFacts facts = spendAnalysisService.factsFor(1L);

        assertThat(facts.monthSpend()).isEqualByComparingTo("1256.40");
        assertThat(facts.topCategories())
                .noneMatch(c -> c.category().equals("Income"));
        assertThat(facts.topCategories())
                .singleElement()
                .satisfies(c -> assertThat(c.amount()).isEqualByComparingTo("1256.40"));
    }

    @Test
    void includesACategoryTheUserStoppedSpendingIn() {
        // Dropping to zero is a real change and arguably the most useful thing to say - it must
        // not vanish just because it has no current-month rows.
        givenTransactions(
                debit(LocalDate.of(2026, 7, 2), "500.00", "Groceries"),
                debit(LocalDate.of(2026, 6, 2), "400.00", "Dining"));

        SpendFacts facts = spendAnalysisService.factsFor(1L);

        assertThat(facts.topCategories()).extracting(SpendFacts.CategoryFact::category)
                .containsExactly("Groceries", "Dining");
        SpendFacts.CategoryFact dining = facts.topCategories().get(1);
        assertThat(dining.amount()).isEqualByComparingTo("0");
        assertThat(dining.previousAmount()).isEqualByComparingTo("400.00");
        assertThat(dining.changeAmount()).isEqualByComparingTo("-400.00");
    }

    @Test
    void promptTextCarriesOnlyCategoriesAndFigures() {
        givenTransactions(debit(LocalDate.of(2026, 7, 2), "500.00", "Groceries"));

        String prompt = spendAnalysisService.factsFor(1L).asPromptText();

        assertThat(prompt).contains("Groceries", "500.00", "2026-07");
        // The merchant name on every fixture row above is "merchant" - it must not travel.
        assertThat(prompt).doesNotContain("merchant");
    }
}
