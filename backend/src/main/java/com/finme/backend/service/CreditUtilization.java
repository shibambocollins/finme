package com.finme.backend.service;

import com.finme.backend.entity.CreditAccount;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;

/**
 * Credit utilization, computed in code (FR-2.2.1, FR-2.2.2).
 * <p>
 * Every figure the credit module reports originates here. Nothing in this file calls an AI
 * provider, and nothing outside it recomputes these numbers - the model is given the finished
 * results to narrate, exactly as the spend recommendations work. For a feature that tells
 * someone which debt to pay first, a plausible-sounding estimate is worse than no advice.
 *
 * @param overall          total balance divided by total limit, or null when there are no accounts
 * @param totalBalance     sum of balances
 * @param totalLimit       sum of limits
 * @param accounts         per-account figures, largest contributor to overall first
 */
record CreditUtilization(
        BigDecimal overall,
        BigDecimal totalBalance,
        BigDecimal totalLimit,
        List<AccountUtilization> accounts
) {

    /**
     * @param utilization        this account's balance divided by its own limit
     * @param overallReduction   how far overall utilization would fall if this account were
     *                           cleared to zero. Derived rather than guessed: overall is
     *                           totalBalance/totalLimit, so removing this balance moves it by
     *                           exactly balance/totalLimit. This is what makes "pay this one
     *                           first" a claim the app can stand behind.
     */
    record AccountUtilization(
            Long accountId,
            String accountName,
            BigDecimal balance,
            BigDecimal creditLimit,
            BigDecimal utilization,
            BigDecimal overallReduction
    ) {
    }

    /** Percentages are carried as ratios (0.42), formatted at the edges. */
    private static final int RATIO_SCALE = 4;

    static CreditUtilization of(List<CreditAccount> accounts) {
        BigDecimal totalBalance = accounts.stream()
                .map(CreditAccount::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalLimit = accounts.stream()
                .map(CreditAccount::getCreditLimit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // No accounts means utilization is undefined, not zero. Reporting 0% would read as
        // "excellent" to a user who has simply entered nothing yet.
        BigDecimal overall = ratio(totalBalance, totalLimit);

        List<AccountUtilization> perAccount = accounts.stream()
                .map(account -> new AccountUtilization(
                        account.getId(),
                        account.getAccountName(),
                        account.getBalance(),
                        account.getCreditLimit(),
                        ratio(account.getBalance(), account.getCreditLimit()),
                        ratio(account.getBalance(), totalLimit)))
                // Ranked by contribution to the overall figure, largest first - which is what
                // FR-2.2.2 asks for: "contributing most negatively to overall utilization".
                //
                // An earlier version ranked by each account's own utilization instead. That is a
                // real credit-health signal, but it is a different question, and the two
                // disagree often: a store card at 95% of a R10,000 limit is more stretched than
                // a Visa at 56% of R25,000, while clearing the Visa moves overall utilization
                // 35.0% against the store card's 23.8%. Ranking by the wrong one puts the
                // smaller win at the top of a plan headed "highest impact first".
                //
                // Per-account utilization stays on every row, because an account near its own
                // limit matters independently of the total.
                .sorted(Comparator.comparing(
                        (AccountUtilization a) -> a.overallReduction() == null ? BigDecimal.ZERO : a.overallReduction())
                        .reversed()
                        .thenComparing(AccountUtilization::accountName))
                .toList();

        return new CreditUtilization(overall, totalBalance, totalLimit, perAccount);
    }

    /**
     * Recomputes overall utilization with one account's balance replaced (FR-2.3.2).
     * <p>
     * The simulation is arithmetic, not a prediction: it answers "what would this figure be",
     * which is knowable, rather than "what would this do to my score", which is not.
     */
    static BigDecimal simulateOverall(List<CreditAccount> accounts, Long accountId, BigDecimal newBalance) {
        BigDecimal totalBalance = BigDecimal.ZERO;
        BigDecimal totalLimit = BigDecimal.ZERO;
        for (CreditAccount account : accounts) {
            BigDecimal balance = account.getId().equals(accountId) ? newBalance : account.getBalance();
            totalBalance = totalBalance.add(balance);
            totalLimit = totalLimit.add(account.getCreditLimit());
        }
        return ratio(totalBalance, totalLimit);
    }

    /** Null rather than zero or an exception when there is nothing to divide by. */
    private static BigDecimal ratio(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.signum() == 0) {
            return null;
        }
        return numerator.divide(denominator, RATIO_SCALE, RoundingMode.HALF_UP);
    }
}
