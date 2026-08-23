package com.finme.backend.service;

import com.finme.backend.dto.CreateCreditProfileRequest;
import com.finme.backend.dto.CreditAccountRequest;
import com.finme.backend.dto.CreditProfileResponse;
import com.finme.backend.dto.RecordCreditScoreRequest;
import com.finme.backend.entity.CreditSnapshot;
import com.finme.backend.entity.PaymentStatus;
import com.finme.backend.exception.CreditAccountNotFoundException;
import com.finme.backend.exception.CreditProfileAlreadyExistsException;
import com.finme.backend.exception.CreditProfileNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The credit module against a real database rather than mocked repositories.
 * <p>
 * Mocks cannot catch the failures that actually bite here: a column named after a reserved word,
 * a unique constraint that is declared but never created, or a "newest first" ordering that only
 * looks right because a mock returned a hand-sorted list. Those all appear at the persistence
 * boundary or not at all.
 */
@SpringBootTest
class CreditProfileIntegrationTest {

    @Autowired
    private CreditProfileService creditProfileService;

    private static CreditAccountRequest account(String name, String balance, String limit, PaymentStatus status) {
        return new CreditAccountRequest(name, new BigDecimal(balance), new BigDecimal(limit), status);
    }

    @Test
    void storesAndReturnsAWholeCreditPosition() {
        long userId = 8100L;
        creditProfileService.createProfile(userId, new CreateCreditProfileRequest("Experian", 740));

        creditProfileService.addAccount(userId, account("Visa Gold", "4200.00", "15000.00", PaymentStatus.ON_TIME));
        CreditProfileResponse profile = creditProfileService.addAccount(
                userId, account("Store Card", "1800.50", "3000.00", PaymentStatus.LATE));

        assertThat(profile.accounts()).hasSize(2);
        // Ordered by name in the query, so the response is stable between requests rather than
        // shuffling with insertion order.
        assertThat(profile.accounts()).extracting(CreditProfileResponse.CreditAccountResponse::accountName)
                .containsExactly("Store Card", "Visa Gold");

        var visa = profile.accounts().stream()
                .filter(a -> a.accountName().equals("Visa Gold")).findFirst().orElseThrow();
        // Survives a real round trip at the declared scale - not a double that drifted.
        assertThat(visa.balance()).isEqualByComparingTo("4200.00");
        assertThat(visa.creditLimit()).isEqualByComparingTo("15000.00");
        assertThat(visa.paymentStatus()).isEqualTo(PaymentStatus.ON_TIME);
    }

    @Test
    void keepsEveryScoreAndReportsTheNewestAsCurrent() {
        long userId = 8101L;
        creditProfileService.createProfile(userId, null);

        creditProfileService.recordScore(userId, new RecordCreditScoreRequest(601));
        creditProfileService.recordScore(userId, new RecordCreditScoreRequest(618));
        CreditProfileResponse profile = creditProfileService.recordScore(userId, new RecordCreditScoreRequest(640));

        assertThat(profile.currentScore()).isEqualTo(640);

        List<CreditSnapshot> history = creditProfileService.scoreHistory(userId);
        assertThat(history).hasSize(3);
        assertThat(history).extracting(CreditSnapshot::getScore).containsExactly(640, 618, 601);
    }

    @Test
    void enforcesOneProfilePerUserInTheDatabase() {
        long userId = 8102L;
        creditProfileService.createProfile(userId, null);

        assertThatThrownBy(() -> creditProfileService.createProfile(userId, null))
                .isInstanceOf(CreditProfileAlreadyExistsException.class);
    }

    @Test
    void keepsOneUsersAccountsOutOfReachOfAnother() {
        long owner = 8103L;
        long stranger = 8104L;
        creditProfileService.createProfile(owner, null);
        creditProfileService.createProfile(stranger, null);

        CreditProfileResponse owned = creditProfileService.addAccount(
                owner, account("Private Card", "100.00", "1000.00", PaymentStatus.ON_TIME));
        Long accountId = owned.accounts().get(0).id();

        assertThatThrownBy(() -> creditProfileService.deleteAccount(stranger, accountId))
                .isInstanceOf(CreditAccountNotFoundException.class);

        // And it is still there afterwards - the rejection was not a partial delete.
        assertThat(creditProfileService.getProfile(owner).accounts()).hasSize(1);
    }

    @Test
    void removesAccountsAndSnapshotsWithTheProfile() {
        long userId = 8105L;
        creditProfileService.createProfile(userId, null);
        creditProfileService.addAccount(userId, account("Card", "10.00", "100.00", PaymentStatus.UNKNOWN));
        creditProfileService.recordScore(userId, new RecordCreditScoreRequest(500));

        creditProfileService.deleteProfile(userId);

        assertThatThrownBy(() -> creditProfileService.getProfile(userId))
                .isInstanceOf(CreditProfileNotFoundException.class);

        // A fresh profile must start empty. If the old rows survived, they would reappear here
        // attached to the new profile the moment an id were reused.
        creditProfileService.createProfile(userId, null);
        CreditProfileResponse fresh = creditProfileService.getProfile(userId);
        assertThat(fresh.accounts()).isEmpty();
        assertThat(fresh.currentScore()).isNull();
    }
}
