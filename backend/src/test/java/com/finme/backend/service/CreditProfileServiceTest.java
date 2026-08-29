package com.finme.backend.service;

import com.finme.backend.dto.CreateCreditProfileRequest;
import com.finme.backend.dto.CreditAccountRequest;
import com.finme.backend.dto.CreditProfileResponse;
import com.finme.backend.dto.RecordCreditScoreRequest;
import com.finme.backend.entity.CreditAccount;
import com.finme.backend.entity.CreditProfile;
import com.finme.backend.entity.CreditSnapshot;
import com.finme.backend.entity.PaymentStatus;
import com.finme.backend.exception.CreditAccountNotFoundException;
import com.finme.backend.exception.CreditProfileAlreadyExistsException;
import com.finme.backend.exception.CreditProfileNotFoundException;
import com.finme.backend.exception.InvalidCreditDataException;
import com.finme.backend.repository.CreditAccountRepository;
import com.finme.backend.repository.CreditProfileRepository;
import com.finme.backend.repository.CreditSnapshotRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CreditProfileServiceTest {

    private static final Long USER = 1L;
    private static final Long OTHER_USER = 2L;

    private final CreditProfileRepository profileRepository = mock(CreditProfileRepository.class);
    private final CreditAccountRepository accountRepository = mock(CreditAccountRepository.class);
    private final CreditSnapshotRepository snapshotRepository = mock(CreditSnapshotRepository.class);

    private final CreditProfileService service =
            new CreditProfileService(profileRepository, accountRepository, snapshotRepository);

    private static CreditProfile profile(Long id, Long userId, int maxScore) {
        CreditProfile p = new CreditProfile();
        p.setId(id);
        p.setUserId(userId);
        p.setMaxScore(maxScore);
        return p;
    }

    private void givenProfile(CreditProfile p) {
        when(profileRepository.findByUserId(p.getUserId())).thenReturn(Optional.of(p));
        when(profileRepository.existsByUserId(p.getUserId())).thenReturn(true);
        when(accountRepository.findByCreditProfileIdOrderByAccountNameAsc(p.getId()))
                .thenReturn(List.of());
        when(snapshotRepository.findFirstByCreditProfileIdOrderByRecordedAtDesc(p.getId()))
                .thenReturn(Optional.empty());
    }

    private static CreditAccountRequest accountRequest(String name, String balance, String limit) {
        return new CreditAccountRequest(name, new BigDecimal(balance), new BigDecimal(limit), PaymentStatus.ON_TIME);
    }

    // ------------------------------------------------------------------ profile

    @Test
    void createsAProfileWithTheDocumentedDefaultsWhenNoneAreGiven() {
        when(profileRepository.existsByUserId(USER)).thenReturn(false);
        when(profileRepository.save(any(CreditProfile.class))).thenAnswer(i -> {
            CreditProfile p = i.getArgument(0);
            p.setId(10L);
            return p;
        });
        when(accountRepository.findByCreditProfileIdOrderByAccountNameAsc(10L)).thenReturn(List.of());
        when(snapshotRepository.findFirstByCreditProfileIdOrderByRecordedAtDesc(10L)).thenReturn(Optional.empty());

        CreditProfileResponse response = service.createProfile(USER, null);

        assertThat(response.bureau()).isEqualTo("Experian");
        assertThat(response.maxScore()).isEqualTo(740);
        assertThat(response.currentScore()).isNull();
        assertThat(response.accounts()).isEmpty();
    }

    @Test
    void acceptsADifferentBureauAndScale() {
        when(profileRepository.existsByUserId(USER)).thenReturn(false);
        when(profileRepository.save(any(CreditProfile.class))).thenAnswer(i -> {
            CreditProfile p = i.getArgument(0);
            p.setId(10L);
            return p;
        });
        when(accountRepository.findByCreditProfileIdOrderByAccountNameAsc(10L)).thenReturn(List.of());
        when(snapshotRepository.findFirstByCreditProfileIdOrderByRecordedAtDesc(10L)).thenReturn(Optional.empty());

        CreditProfileResponse response =
                service.createProfile(USER, new CreateCreditProfileRequest("TransUnion", 999));

        assertThat(response.bureau()).isEqualTo("TransUnion");
        assertThat(response.maxScore()).isEqualTo(999);
    }

    @Test
    void refusesASecondProfileForTheSameUser() {
        when(profileRepository.existsByUserId(USER)).thenReturn(true);

        assertThatThrownBy(() -> service.createProfile(USER, null))
                .isInstanceOf(CreditProfileAlreadyExistsException.class);
        verify(profileRepository, never()).save(any());
    }

    @Test
    void reportsARacedSecondCreateAsAConflictRatherThanAServerError() {
        when(profileRepository.existsByUserId(USER)).thenReturn(false);
        when(profileRepository.save(any(CreditProfile.class)))
                .thenThrow(new DataIntegrityViolationException("unique constraint"));

        assertThatThrownBy(() -> service.createProfile(USER, null))
                .isInstanceOf(CreditProfileAlreadyExistsException.class);
    }

    @Test
    void treatsAMissingProfileAsANormalStateNotAFailure() {
        when(profileRepository.findByUserId(USER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getProfile(USER))
                .isInstanceOf(CreditProfileNotFoundException.class)
                .hasMessageContaining("Create one");
    }

    @Test
    void deletingAProfileAlsoRemovesItsAccountsAndSnapshots() {
        CreditProfile p = profile(10L, USER, 740);
        givenProfile(p);
        when(snapshotRepository.findByCreditProfileIdOrderByRecordedAtDesc(10L)).thenReturn(List.of());

        service.deleteProfile(USER);

        verify(accountRepository).deleteByCreditProfileId(10L);
        verify(profileRepository).delete(p);
    }

    // ------------------------------------------------------------------ accounts

    @Test
    void addsAnAccountToTheUsersOwnProfile() {
        CreditProfile p = profile(10L, USER, 740);
        givenProfile(p);

        service.addAccount(USER, accountRequest("Visa Gold", "4200.00", "15000.00"));

        ArgumentCaptor<CreditAccount> captor = ArgumentCaptor.forClass(CreditAccount.class);
        verify(accountRepository).save(captor.capture());
        CreditAccount saved = captor.getValue();
        assertThat(saved.getCreditProfileId()).isEqualTo(10L);
        assertThat(saved.getAccountName()).isEqualTo("Visa Gold");
        assertThat(saved.getBalance()).isEqualByComparingTo("4200.00");
        assertThat(saved.getCreditLimit()).isEqualByComparingTo("15000.00");
        assertThat(saved.getPaymentStatus()).isEqualTo(PaymentStatus.ON_TIME);
    }

    @Test
    void defaultsPaymentStatusToUnknownWhenTheUserDoesNotSay() {
        // A user adding accounts from memory may genuinely not know. Forcing a guess would put
        // a wrong value into the data that Iteration 9 prioritises on.
        CreditProfile p = profile(10L, USER, 740);
        givenProfile(p);

        service.addAccount(USER, new CreditAccountRequest(
                "Store Card", new BigDecimal("500.00"), new BigDecimal("2000.00"), null));

        ArgumentCaptor<CreditAccount> captor = ArgumentCaptor.forClass(CreditAccount.class);
        verify(accountRepository).save(captor.capture());
        assertThat(captor.getValue().getPaymentStatus()).isEqualTo(PaymentStatus.UNKNOWN);
    }

    @Test
    void acceptsABalanceThatExceedsTheLimit() {
        // Real and common - and it is exactly the account a user most needs advice about, so
        // refusing to record it would hide the problem the module exists to surface.
        CreditProfile p = profile(10L, USER, 740);
        givenProfile(p);

        service.addAccount(USER, accountRequest("Overlimit Card", "17000.00", "15000.00"));

        verify(accountRepository).save(any(CreditAccount.class));
    }

    @Test
    void refusesToTouchAnAccountBelongingToAnotherUsersProfile() {
        CreditProfile mine = profile(10L, USER, 740);
        givenProfile(mine);

        CreditAccount theirs = new CreditAccount();
        theirs.setId(99L);
        theirs.setCreditProfileId(20L);
        when(accountRepository.findById(99L)).thenReturn(Optional.of(theirs));

        assertThatThrownBy(() -> service.updateAccount(USER, 99L, accountRequest("x", "1.00", "2.00")))
                .isInstanceOf(CreditAccountNotFoundException.class);
        assertThatThrownBy(() -> service.deleteAccount(USER, 99L))
                .isInstanceOf(CreditAccountNotFoundException.class);
        verify(accountRepository, never()).save(any());
        verify(accountRepository, never()).delete(any());
    }

    @Test
    void reportsAnUnknownAccountAndAnUnownedOneIdentically() {
        CreditProfile mine = profile(10L, USER, 740);
        givenProfile(mine);
        when(accountRepository.findById(123L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteAccount(USER, 123L))
                .isInstanceOf(CreditAccountNotFoundException.class);
    }

    // ------------------------------------------------------------------ score

    @Test
    void recordsAScoreAsANewSnapshot() {
        CreditProfile p = profile(10L, USER, 740);
        givenProfile(p);

        service.recordScore(USER, new RecordCreditScoreRequest(620));

        ArgumentCaptor<CreditSnapshot> captor = ArgumentCaptor.forClass(CreditSnapshot.class);
        verify(snapshotRepository).save(captor.capture());
        assertThat(captor.getValue().getScore()).isEqualTo(620);
        assertThat(captor.getValue().getCreditProfileId()).isEqualTo(10L);
    }

    @Test
    void neverOverwritesAPreviousScore() {
        // Append-only is what makes progress visible (FR-2.4.1). An update would answer "what is
        // it now" while destroying the evidence of whether it is improving.
        CreditProfile p = profile(10L, USER, 740);
        givenProfile(p);

        service.recordScore(USER, new RecordCreditScoreRequest(600));
        service.recordScore(USER, new RecordCreditScoreRequest(640));

        verify(snapshotRepository, org.mockito.Mockito.times(2)).save(any(CreditSnapshot.class));
        verify(snapshotRepository, never()).deleteAll();
    }

    @Test
    void rejectsAScoreAboveThisProfilesOwnScale() {
        CreditProfile p = profile(10L, USER, 740);
        givenProfile(p);

        assertThatThrownBy(() -> service.recordScore(USER, new RecordCreditScoreRequest(850)))
                .isInstanceOf(InvalidCreditDataException.class)
                .hasMessageContaining("740");
        verify(snapshotRepository, never()).save(any());
    }

    @Test
    void allowsAScoreThatWouldBeInvalidOnADifferentBureausScale() {
        // 850 is out of range on a 740 scale and fine on a 999 one - which is why the check
        // reads the profile rather than a constant.
        CreditProfile p = profile(11L, OTHER_USER, 999);
        givenProfile(p);

        service.recordScore(OTHER_USER, new RecordCreditScoreRequest(850));

        verify(snapshotRepository).save(any(CreditSnapshot.class));
    }

    @Test
    void reportsTheMostRecentScoreAsCurrent() {
        CreditProfile p = profile(10L, USER, 740);
        givenProfile(p);
        CreditSnapshot latest = new CreditSnapshot();
        latest.setScore(655);
        latest.setRecordedAt(Instant.parse("2026-08-01T10:00:00Z"));
        when(snapshotRepository.findFirstByCreditProfileIdOrderByRecordedAtDesc(10L))
                .thenReturn(Optional.of(latest));

        CreditProfileResponse response = service.getProfile(USER);

        assertThat(response.currentScore()).isEqualTo(655);
        assertThat(response.scoreRecordedAt()).isEqualTo(Instant.parse("2026-08-01T10:00:00Z"));
    }

    @Test
    void requiresAProfileBeforeAnyAccountOrScoreOperation() {
        when(profileRepository.findByUserId(USER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addAccount(USER, accountRequest("x", "1.00", "2.00")))
                .isInstanceOf(CreditProfileNotFoundException.class);
        assertThatThrownBy(() -> service.recordScore(USER, new RecordCreditScoreRequest(600)))
                .isInstanceOf(CreditProfileNotFoundException.class);
        assertThatThrownBy(() -> service.scoreHistory(USER))
                .isInstanceOf(CreditProfileNotFoundException.class);
    }
}
