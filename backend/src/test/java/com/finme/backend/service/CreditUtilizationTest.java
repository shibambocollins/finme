package com.finme.backend.service;

import com.finme.backend.entity.CreditAccount;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FR-2.2.1 says these figures are calculated, not estimated - so they are tested to the cent
 * and to the exact ratio. This is the arithmetic behind "pay this account first"; if it is
 * wrong, the app confidently sends someone at their most stretched account when a different one
 * mattered more.
 */
class CreditUtilizationTest {

    private static CreditAccount account(long id, String name, String balance, String limit) {
        CreditAccount a = new CreditAccount();
        a.setId(id);
        a.setAccountName(name);
        a.setBalance(new BigDecimal(balance));
        a.setCreditLimit(new BigDecimal(limit));
        return a;
    }

    @Test
    void dividesTotalBalanceByTotalLimit() {
        CreditUtilization result = CreditUtilization.of(List.of(
                account(1, "Visa", "4000.00", "10000.00"),
                account(2, "Store", "1000.00", "10000.00")));

        assertThat(result.totalBalance()).isEqualByComparingTo("5000.00");
        assertThat(result.totalLimit()).isEqualByComparingTo("20000.00");
        assertThat(result.overall()).isEqualByComparingTo("0.2500");
    }

    @Test
    void reportsUtilizationAsUndefinedRatherThanZeroWhenThereAreNoAccounts() {
        // 0% would read as "excellent" to a user who has simply entered nothing yet.
        CreditUtilization result = CreditUtilization.of(List.of());

        assertThat(result.overall()).isNull();
        assertThat(result.accounts()).isEmpty();
    }

    @Test
    void computesEachAccountAgainstItsOwnLimit() {
        CreditUtilization result = CreditUtilization.of(List.of(
                account(1, "Visa", "9000.00", "10000.00"),
                account(2, "Store", "500.00", "5000.00")));

        var visa = result.accounts().stream()
                .filter(a -> a.accountName().equals("Visa")).findFirst().orElseThrow();
        var store = result.accounts().stream()
                .filter(a -> a.accountName().equals("Store")).findFirst().orElseThrow();
        assertThat(visa.utilization()).isEqualByComparingTo("0.9000");
        assertThat(store.utilization()).isEqualByComparingTo("0.1000");
    }

    @Test
    void ranksByContributionToOverallUtilization() {
        // FR-2.2.2: "contributing most negatively to overall utilization" - which is each
        // account's balance measured against the TOTAL limit, not against its own.
        CreditUtilization result = CreditUtilization.of(List.of(
                account(1, "Low", "1000.00", "20000.00"),
                account(2, "Maxed", "4800.00", "5000.00"),
                account(3, "Middling", "5000.00", "10000.00")));

        assertThat(result.accounts()).extracting(CreditUtilization.AccountUtilization::accountName)
                .containsExactly("Middling", "Maxed", "Low");
    }

    @Test
    void reportsWhatClearingAnAccountWouldDoToTheOverallFigure() {
        // balance / totalLimit, by derivation: overall is totalBalance/totalLimit, so removing
        // this balance moves it by exactly that much.
        CreditUtilization result = CreditUtilization.of(List.of(
                account(1, "Visa", "4000.00", "10000.00"),
                account(2, "Store", "1000.00", "10000.00")));

        var visa = result.accounts().stream()
                .filter(a -> a.accountName().equals("Visa")).findFirst().orElseThrow();
        assertThat(visa.overallReduction()).isEqualByComparingTo("0.2000");

        // And it checks out against a recomputation from scratch.
        BigDecimal afterClearing = CreditUtilization.simulateOverall(
                List.of(account(1, "Visa", "0.00", "10000.00"), account(2, "Store", "1000.00", "10000.00")),
                1L, new BigDecimal("0.00"));
        assertThat(result.overall().subtract(afterClearing)).isEqualByComparingTo("0.2000");
    }

    @Test
    void ranksByImpactEvenWhenAnotherAccountIsMoreStretched() {
        // The case that exposed the original ranking being wrong. The store card is nearly
        // maxed, but clearing the far larger Visa balance moves overall utilization much
        // further - and a plan headed "highest impact first" must lead with the Visa.
        CreditUtilization result = CreditUtilization.of(List.of(
                account(1, "Visa", "20000.00", "50000.00"),
                account(2, "Store", "950.00", "1000.00")));

        assertThat(result.accounts().get(0).accountName()).isEqualTo("Visa");

        var visa = result.accounts().get(0);
        var store = result.accounts().get(1);
        // Both figures survive, because they answer different questions and disagree here.
        assertThat(store.utilization()).isGreaterThan(visa.utilization());
        assertThat(visa.overallReduction()).isGreaterThan(store.overallReduction());
    }

    @Test
    void handlesAnAccountOverItsLimit() {
        // Real, and exactly the account someone needs advice about - it must not be rejected or
        // silently clamped to 100%.
        CreditUtilization result = CreditUtilization.of(List.of(
                account(1, "Overlimit", "11000.00", "10000.00")));

        assertThat(result.accounts().get(0).utilization()).isEqualByComparingTo("1.1000");
        assertThat(result.overall()).isEqualByComparingTo("1.1000");
    }

    @Test
    void simulatesAReducedBalanceWithoutTouchingTheStoredAccounts() {
        List<CreditAccount> accounts = List.of(
                account(1, "Visa", "8000.00", "10000.00"),
                account(2, "Store", "2000.00", "10000.00"));

        BigDecimal simulated = CreditUtilization.simulateOverall(accounts, 1L, new BigDecimal("3000.00"));

        assertThat(simulated).isEqualByComparingTo("0.2500");
        // The stored objects are unchanged - a "what if" must never become a "what is".
        assertThat(accounts.get(0).getBalance()).isEqualByComparingTo("8000.00");
        assertThat(CreditUtilization.of(accounts).overall()).isEqualByComparingTo("0.5000");
    }

    @Test
    void simulatingAnUnknownAccountLeavesTheFigureUnchanged() {
        List<CreditAccount> accounts = List.of(account(1, "Visa", "5000.00", "10000.00"));

        assertThat(CreditUtilization.simulateOverall(accounts, 999L, BigDecimal.ZERO))
                .isEqualByComparingTo("0.5000");
    }

    @Test
    void keepsPrecisionOnFiguresThatDoNotDivideEvenly() {
        // 1/3 has no exact decimal form. Rounded to a fixed scale rather than left to a double,
        // so the same inputs always produce the same displayed figure.
        CreditUtilization result = CreditUtilization.of(List.of(
                account(1, "Visa", "1000.00", "3000.00")));

        assertThat(result.overall()).isEqualByComparingTo("0.3333");
    }
}
