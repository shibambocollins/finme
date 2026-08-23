package com.finme.backend.service;

import com.finme.backend.ai.AiProvider;
import com.finme.backend.ai.AllAiProvidersFailedException;
import com.finme.backend.dto.CreditAnalysisResponse;
import com.finme.backend.dto.ScoreComparisonResponse;
import com.finme.backend.dto.UtilizationSimulationRequest;
import com.finme.backend.dto.UtilizationSimulationResponse;
import com.finme.backend.entity.CreditAccount;
import com.finme.backend.entity.CreditProfile;
import com.finme.backend.entity.CreditSnapshot;
import com.finme.backend.exception.CreditAccountNotFoundException;
import com.finme.backend.exception.CreditProfileNotFoundException;
import com.finme.backend.exception.InvalidCreditDataException;
import com.finme.backend.repository.CreditAccountRepository;
import com.finme.backend.repository.CreditProfileRepository;
import com.finme.backend.repository.CreditSnapshotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * The credit analysis engine (FR-2.2.x, FR-2.3.x, FR-2.4.2).
 * <p>
 * Split cleanly in two: {@link CreditUtilization} calculates, and the AI narrates what was
 * calculated. The model never sees an account row, is never asked to divide anything, and its
 * output never becomes a number the app displays.
 */
@Service
public class CreditAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(CreditAnalysisService.class);

    /**
     * FR-2.3.3, and deliberately a constant rather than something the model is asked to include.
     * <p>
     * A disclaimer generated alongside the advice is written by the same process it is meant to
     * qualify - it could be softened, shortened or dropped entirely on any given run, and
     * nothing would fail. Making it a constant means it cannot vary with model output, and the
     * one guarantee the app owes a user about credit advice does not depend on a third party
     * having a good day.
     */
    static final String DISCLAIMER =
            "These suggestions are based on the figures you entered. Credit scoring is decided by "
                    + "the bureau using factors beyond this app, so following them is not "
                    + "guaranteed to increase your score.";

    private static final String PLAN_UNAVAILABLE =
            "The improvement plan is unavailable right now. Your calculated figures below are unaffected.";

    private final CreditProfileRepository creditProfileRepository;
    private final CreditAccountRepository creditAccountRepository;
    private final CreditSnapshotRepository creditSnapshotRepository;
    private final AiProvider aiProvider;

    public CreditAnalysisService(CreditProfileRepository creditProfileRepository,
                                 CreditAccountRepository creditAccountRepository,
                                 CreditSnapshotRepository creditSnapshotRepository,
                                 AiProvider aiProvider) {
        this.creditProfileRepository = creditProfileRepository;
        this.creditAccountRepository = creditAccountRepository;
        this.creditSnapshotRepository = creditSnapshotRepository;
        this.aiProvider = aiProvider;
    }

    @Transactional(readOnly = true)
    public CreditAnalysisResponse analyse(Long userId) {
        List<CreditAccount> accounts = accountsOf(userId);
        CreditUtilization utilization = CreditUtilization.of(accounts);

        List<String> plan = List.of();
        String unavailable = null;
        if (utilization.overall() == null) {
            // Nothing to advise on yet. Calling a provider to say so would spend quota to
            // produce a sentence the app can write itself.
            unavailable = "Add a credit account to get a prioritised plan.";
        } else {
            try {
                plan = aiProvider.recommendCredit(summarise(utilization));
            } catch (AllAiProvidersFailedException ex) {
                log.warn("Credit plan unavailable for user {}: {}", userId, ex.getMessage());
                unavailable = PLAN_UNAVAILABLE;
            }
        }

        return new CreditAnalysisResponse(
                utilization.overall(),
                utilization.totalBalance(),
                utilization.totalLimit(),
                utilization.accounts().stream()
                        .map(a -> new CreditAnalysisResponse.AccountUtilizationResponse(
                                a.accountId(), a.accountName(), a.balance(), a.creditLimit(),
                                a.utilization(), a.overallReduction()))
                        .toList(),
                plan,
                unavailable,
                DISCLAIMER);
    }

    /**
     * Recomputes utilization with one account's balance replaced (FR-2.3.2). Read-only in every
     * sense - nothing is written, and the stored balance is untouched.
     */
    @Transactional(readOnly = true)
    public UtilizationSimulationResponse simulate(Long userId, UtilizationSimulationRequest request) {
        List<CreditAccount> accounts = accountsOf(userId);
        if (accounts.isEmpty()) {
            throw new InvalidCreditDataException("Add a credit account before simulating a payment.");
        }
        // Checked rather than silently ignored: simulating an account the user does not own
        // would return their real current figure as though the change had no effect.
        if (accounts.stream().noneMatch(a -> a.getId().equals(request.accountId()))) {
            throw new CreditAccountNotFoundException(request.accountId());
        }

        BigDecimal current = CreditUtilization.of(accounts).overall();
        BigDecimal simulated = CreditUtilization.simulateOverall(
                accounts, request.accountId(), request.newBalance());

        return new UtilizationSimulationResponse(
                current,
                simulated,
                simulated.subtract(current),
                "This shows the effect on your utilization only. It is not a prediction of your "
                        + "credit score.");
    }

    /**
     * Current score against an earlier reading (FR-2.4.2). Defaults to the one immediately
     * before it, which is the comparison a user means by "am I improving".
     */
    @Transactional(readOnly = true)
    public ScoreComparisonResponse compareScores(Long userId, Long againstSnapshotId) {
        CreditProfile profile = requireProfile(userId);
        List<CreditSnapshot> history =
                creditSnapshotRepository.findByCreditProfileIdOrderByRecordedAtDesc(profile.getId());

        if (history.isEmpty()) {
            return new ScoreComparisonResponse(null, null, null, null, null, null,
                    "Record a score to start tracking progress.");
        }
        CreditSnapshot current = history.get(0);
        if (history.size() == 1) {
            return new ScoreComparisonResponse(current.getScore(), current.getRecordedAt(),
                    null, null, null, null,
                    "This is your first reading - record another later to see the change.");
        }

        CreditSnapshot previous = againstSnapshotId == null
                ? history.get(1)
                : history.stream()
                        .filter(s -> s.getId().equals(againstSnapshotId))
                        .findFirst()
                        // Scoped to this profile's own history, so another user's snapshot id
                        // cannot be compared against.
                        .orElseThrow(() -> new InvalidCreditDataException(
                                "That earlier reading was not found in your history."));

        int change = current.getScore() - previous.getScore();
        long days = Duration.between(previous.getRecordedAt(), current.getRecordedAt()).toDays();

        return new ScoreComparisonResponse(
                current.getScore(), current.getRecordedAt(),
                previous.getScore(), previous.getRecordedAt(),
                change, days, describe(change, days, profile));
    }

    private static String describe(int change, long days, CreditProfile profile) {
        String window = days <= 0 ? "since your previous reading" : "over " + days + " days";
        if (change > 0) {
            return String.format(Locale.ROOT, "Up %d points %s.", change, window);
        }
        if (change < 0) {
            return String.format(Locale.ROOT, "Down %d points %s.", Math.abs(change), window);
        }
        return "Unchanged " + window + ".";
    }

    /**
     * Renders the calculated position for the model. Percentages are formatted here so the model
     * has a figure to quote verbatim rather than a ratio it might be tempted to convert - and a
     * conversion is arithmetic, which is exactly what it must not do.
     */
    private static String summarise(CreditUtilization utilization) {
        StringBuilder text = new StringBuilder();
        text.append("Overall utilization: ").append(percent(utilization.overall())).append('\n');
        text.append("Total balance: ").append(utilization.totalBalance()).append('\n');
        text.append("Total credit limit: ").append(utilization.totalLimit()).append('\n');
        text.append("Accounts, biggest effect on overall utilization first:\n");
        for (CreditUtilization.AccountUtilization account : utilization.accounts()) {
            text.append("- ").append(account.accountName())
                    .append(": balance ").append(account.balance())
                    .append(" of ").append(account.creditLimit())
                    .append(", utilization ").append(percent(account.utilization()))
                    .append(", clearing it would lower overall utilization by ")
                    .append(percent(account.overallReduction()))
                    .append('\n');
        }
        return text.toString();
    }

    private static String percent(BigDecimal ratio) {
        if (ratio == null) {
            return "not available";
        }
        return ratio.multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP) + "%";
    }

    private List<CreditAccount> accountsOf(Long userId) {
        return creditAccountRepository
                .findByCreditProfileIdOrderByAccountNameAsc(requireProfile(userId).getId());
    }

    private CreditProfile requireProfile(Long userId) {
        return creditProfileRepository.findByUserId(userId)
                .orElseThrow(CreditProfileNotFoundException::new);
    }
}
