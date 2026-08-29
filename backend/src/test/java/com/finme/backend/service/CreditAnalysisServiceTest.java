package com.finme.backend.service;

import com.finme.backend.ai.AiProvider;
import com.finme.backend.ai.AiProviderException;
import com.finme.backend.ai.AllAiProvidersFailedException;
import com.finme.backend.ai.StubAiProvider;
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
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CreditAnalysisServiceTest {

    private static final Long USER = 1L;
    private static final Long PROFILE = 10L;

    private final CreditProfileRepository profileRepository = mock(CreditProfileRepository.class);
    private final CreditAccountRepository accountRepository = mock(CreditAccountRepository.class);
    private final CreditSnapshotRepository snapshotRepository = mock(CreditSnapshotRepository.class);
    private final AtomicReference<String> promptGivenToModel = new AtomicReference<>();

    private AiProvider planning(List<String> plan) {
        return new StubAiProvider() {
            @Override
            public List<String> recommendCredit(String creditFactsSummary) {
                promptGivenToModel.set(creditFactsSummary);
                return plan;
            }
        };
    }

    private AiProvider failing() {
        return new StubAiProvider() {
            @Override
            public List<String> recommendCredit(String creditFactsSummary) {
                throw new AllAiProvidersFailedException(new AiProviderException("down"));
            }
        };
    }

    private CreditAnalysisService serviceWith(AiProvider provider) {
        CreditProfile profile = new CreditProfile();
        profile.setId(PROFILE);
        profile.setUserId(USER);
        when(profileRepository.findByUserId(USER)).thenReturn(Optional.of(profile));
        return new CreditAnalysisService(profileRepository, accountRepository, snapshotRepository, provider);
    }

    private static CreditAccount account(long id, String name, String balance, String limit) {
        CreditAccount a = new CreditAccount();
        a.setId(id);
        a.setAccountName(name);
        a.setBalance(new BigDecimal(balance));
        a.setCreditLimit(new BigDecimal(limit));
        return a;
    }

    private void givenAccounts(CreditAccount... accounts) {
        when(accountRepository.findByCreditProfileIdOrderByAccountNameAsc(PROFILE))
                .thenReturn(List.of(accounts));
    }

    private void givenSnapshots(CreditSnapshot... snapshots) {
        when(snapshotRepository.findByCreditProfileIdOrderByRecordedAtDesc(PROFILE))
                .thenReturn(List.of(snapshots));
    }

    private static CreditSnapshot snapshot(long id, int score, String instant) {
        CreditSnapshot s = new CreditSnapshot();
        s.setId(id);
        s.setScore(score);
        s.setRecordedAt(Instant.parse(instant));
        return s;
    }

    // ------------------------------------------------------------------ analysis

    @Test
    void reportsCalculatedFiguresAlongsideThePlan() {
        givenAccounts(account(1, "Visa", "4000.00", "10000.00"), account(2, "Store", "1000.00", "10000.00"));
        CreditAnalysisResponse response = serviceWith(planning(List.of("Pay the Visa down."))).analyse(USER);

        assertThat(response.overallUtilization()).isEqualByComparingTo("0.2500");
        assertThat(response.totalBalance()).isEqualByComparingTo("5000.00");
        assertThat(response.plan()).containsExactly("Pay the Visa down.");
        assertThat(response.planUnavailableReason()).isNull();
    }

    @Test
    void alwaysCarriesTheDisclaimer() {
        // FR-2.3.3. It is a constant, not model output - so it is present whether the plan
        // succeeded, failed, or was never requested.
        givenAccounts(account(1, "Visa", "4000.00", "10000.00"));
        assertThat(serviceWith(planning(List.of("step"))).analyse(USER).disclaimer())
                .isEqualTo(CreditAnalysisService.DISCLAIMER)
                .contains("not guaranteed to increase your score");

        assertThat(serviceWith(failing()).analyse(USER).disclaimer())
                .isEqualTo(CreditAnalysisService.DISCLAIMER);

        givenAccounts();
        assertThat(serviceWith(planning(List.of())).analyse(USER).disclaimer())
                .isEqualTo(CreditAnalysisService.DISCLAIMER);
    }

    @Test
    void keepsTheFiguresWhenTheModelCannotBeReached() {
        // The numbers were computed locally and remain correct; losing the narration must not
        // take the calculated position with it.
        givenAccounts(account(1, "Visa", "4000.00", "10000.00"));
        CreditAnalysisResponse response = serviceWith(failing()).analyse(USER);

        assertThat(response.overallUtilization()).isEqualByComparingTo("0.4000");
        assertThat(response.plan()).isEmpty();
        assertThat(response.planUnavailableReason()).contains("unavailable");
    }

    @Test
    void doesNotCallAProviderWhenThereIsNothingToAdviseOn() {
        givenAccounts();
        CreditAnalysisResponse response = serviceWith(new StubAiProvider()).analyse(USER);

        assertThat(response.overallUtilization()).isNull();
        assertThat(response.plan()).isEmpty();
        assertThat(response.planUnavailableReason()).contains("Add a credit account");
    }

    @Test
    void givesTheModelOnlyCalculatedFiguresToNarrate() {
        givenAccounts(account(1, "Visa", "4000.00", "10000.00"));
        serviceWith(planning(List.of("step"))).analyse(USER);

        // Percentages are pre-formatted so the model has something to quote rather than a ratio
        // it might convert - and converting is arithmetic, which it must not do.
        assertThat(promptGivenToModel.get()).contains("40.0%", "Visa", "4000.00");
    }

    // ------------------------------------------------------------------ simulation

    @Test
    void simulatesAPaymentWithoutChangingStoredBalances() {
        CreditAccount visa = account(1, "Visa", "8000.00", "10000.00");
        givenAccounts(visa, account(2, "Store", "2000.00", "10000.00"));

        UtilizationSimulationResponse response = serviceWith(new StubAiProvider())
                .simulate(USER, new UtilizationSimulationRequest(1L, new BigDecimal("3000.00")));

        assertThat(response.currentOverall()).isEqualByComparingTo("0.5000");
        assertThat(response.simulatedOverall()).isEqualByComparingTo("0.2500");
        assertThat(response.change()).isEqualByComparingTo("-0.2500");
        assertThat(visa.getBalance()).isEqualByComparingTo("8000.00");
    }

    @Test
    void saysPlainlyThatASimulationIsNotAScorePrediction() {
        givenAccounts(account(1, "Visa", "8000.00", "10000.00"));
        UtilizationSimulationResponse response = serviceWith(new StubAiProvider())
                .simulate(USER, new UtilizationSimulationRequest(1L, BigDecimal.ZERO));

        assertThat(response.note()).contains("not a prediction of your credit score");
    }

    @Test
    void refusesToSimulateAnAccountTheUserDoesNotHave() {
        // Silently ignoring it would return the user's real current figure as though the change
        // had no effect - a wrong answer that looks like a right one.
        givenAccounts(account(1, "Visa", "8000.00", "10000.00"));

        assertThatThrownBy(() -> serviceWith(new StubAiProvider())
                .simulate(USER, new UtilizationSimulationRequest(99L, BigDecimal.ZERO)))
                .isInstanceOf(CreditAccountNotFoundException.class);
    }

    @Test
    void refusesToSimulateWithNoAccountsAtAll() {
        givenAccounts();

        assertThatThrownBy(() -> serviceWith(new StubAiProvider())
                .simulate(USER, new UtilizationSimulationRequest(1L, BigDecimal.ZERO)))
                .isInstanceOf(InvalidCreditDataException.class);
    }

    // ------------------------------------------------------------------ comparison

    @Test
    void comparesTheCurrentScoreWithThePreviousOne() {
        givenSnapshots(
                snapshot(3, 640, "2026-08-01T09:00:00Z"),
                snapshot(2, 618, "2026-07-01T09:00:00Z"),
                snapshot(1, 601, "2026-06-01T09:00:00Z"));

        ScoreComparisonResponse response = serviceWith(new StubAiProvider()).compareScores(USER, null);

        assertThat(response.currentScore()).isEqualTo(640);
        assertThat(response.previousScore()).isEqualTo(618);
        assertThat(response.change()).isEqualTo(22);
        assertThat(response.daysApart()).isEqualTo(31);
        assertThat(response.message()).contains("Up 22 points over 31 days");
    }

    @Test
    void comparesAgainstAnyChosenEarlierReading() {
        givenSnapshots(
                snapshot(3, 640, "2026-08-01T09:00:00Z"),
                snapshot(2, 618, "2026-07-01T09:00:00Z"),
                snapshot(1, 601, "2026-06-01T09:00:00Z"));

        ScoreComparisonResponse response = serviceWith(new StubAiProvider()).compareScores(USER, 1L);

        assertThat(response.previousScore()).isEqualTo(601);
        assertThat(response.change()).isEqualTo(39);
    }

    @Test
    void rejectsASnapshotThatIsNotInThisUsersHistory() {
        givenSnapshots(snapshot(3, 640, "2026-08-01T09:00:00Z"), snapshot(2, 618, "2026-07-01T09:00:00Z"));

        assertThatThrownBy(() -> serviceWith(new StubAiProvider()).compareScores(USER, 999L))
                .isInstanceOf(InvalidCreditDataException.class);
    }

    @Test
    void reportsADropAsADropRatherThanANegativeNumber() {
        givenSnapshots(
                snapshot(2, 590, "2026-08-01T09:00:00Z"),
                snapshot(1, 640, "2026-07-01T09:00:00Z"));

        ScoreComparisonResponse response = serviceWith(new StubAiProvider()).compareScores(USER, null);

        assertThat(response.change()).isEqualTo(-50);
        assertThat(response.message()).contains("Down 50 points");
    }

    @Test
    void handlesAFirstEverReadingAndNoReadingsAtAll() {
        givenSnapshots(snapshot(1, 600, "2026-08-01T09:00:00Z"));
        ScoreComparisonResponse first = serviceWith(new StubAiProvider()).compareScores(USER, null);
        assertThat(first.currentScore()).isEqualTo(600);
        assertThat(first.previousScore()).isNull();
        assertThat(first.message()).contains("first reading");

        givenSnapshots();
        ScoreComparisonResponse none = serviceWith(new StubAiProvider()).compareScores(USER, null);
        assertThat(none.currentScore()).isNull();
        assertThat(none.message()).contains("Record a score");
    }

    @Test
    void requiresAProfileBeforeAnalysing() {
        when(profileRepository.findByUserId(USER)).thenReturn(Optional.empty());
        CreditAnalysisService service =
                new CreditAnalysisService(profileRepository, accountRepository, snapshotRepository, new StubAiProvider());

        assertThatThrownBy(() -> service.analyse(USER)).isInstanceOf(CreditProfileNotFoundException.class);
        assertThatThrownBy(() -> service.compareScores(USER, null)).isInstanceOf(CreditProfileNotFoundException.class);
    }
}
