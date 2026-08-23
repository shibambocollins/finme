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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Manages the credit profile, its accounts, and its score history (FR-2.1.1 to FR-2.1.4,
 * FR-2.4.1).
 * <p>
 * No arithmetic on credit figures happens here. Utilization is FR-2.2.1 and arrives in Iteration
 * 9 as deterministic code; this iteration is strictly about getting accurate data in and back
 * out again, because a calculation over wrong inputs is worse than no calculation at all.
 * <p>
 * Every method takes the caller's userId and resolves the profile from it. No method accepts a
 * profile id from the client, so there is no path where one user reaches another user's credit
 * data by supplying an id - the ownership check cannot be forgotten because it is the only way
 * in.
 */
@Service
public class CreditProfileService {

    private final CreditProfileRepository creditProfileRepository;
    private final CreditAccountRepository creditAccountRepository;
    private final CreditSnapshotRepository creditSnapshotRepository;

    public CreditProfileService(CreditProfileRepository creditProfileRepository,
                                CreditAccountRepository creditAccountRepository,
                                CreditSnapshotRepository creditSnapshotRepository) {
        this.creditProfileRepository = creditProfileRepository;
        this.creditAccountRepository = creditAccountRepository;
        this.creditSnapshotRepository = creditSnapshotRepository;
    }

    // ------------------------------------------------------------------ profile

    public CreditProfileResponse createProfile(Long userId, CreateCreditProfileRequest request) {
        if (creditProfileRepository.existsByUserId(userId)) {
            throw new CreditProfileAlreadyExistsException();
        }

        CreditProfile profile = new CreditProfile();
        profile.setUserId(userId);
        if (request != null) {
            if (request.bureau() != null && !request.bureau().isBlank()) {
                profile.setBureau(request.bureau().strip());
            }
            if (request.maxScore() != null) {
                profile.setMaxScore(request.maxScore());
            }
        }

        try {
            profile = creditProfileRepository.save(profile);
        } catch (DataIntegrityViolationException ex) {
            // The unique constraint on user_id caught a second create that raced the check
            // above. Reported as the conflict it is, not as a 500.
            throw new CreditProfileAlreadyExistsException();
        }
        return toResponse(profile);
    }

    @Transactional(readOnly = true)
    public CreditProfileResponse getProfile(Long userId) {
        return toResponse(requireProfile(userId));
    }

    @Transactional(readOnly = true)
    public boolean hasProfile(Long userId) {
        return creditProfileRepository.existsByUserId(userId);
    }

    /**
     * Removes the profile and everything under it. Deliberately explicit rather than relying on
     * JPA cascades: the child rows are linked by a plain id column, not a mapped association, so
     * nothing would delete them automatically and the orphans would silently outlive the profile
     * - and reappear attached to a new profile if the id were ever reused.
     */
    @Transactional
    public void deleteProfile(Long userId) {
        CreditProfile profile = requireProfile(userId);
        creditAccountRepository.deleteByCreditProfileId(profile.getId());
        creditSnapshotRepository.deleteAll(
                creditSnapshotRepository.findByCreditProfileIdOrderByRecordedAtDesc(profile.getId()));
        creditProfileRepository.delete(profile);
    }

    // ------------------------------------------------------------------ accounts

    @Transactional
    public CreditProfileResponse addAccount(Long userId, CreditAccountRequest request) {
        CreditProfile profile = requireProfile(userId);

        CreditAccount account = new CreditAccount();
        account.setCreditProfileId(profile.getId());
        apply(request, account);
        creditAccountRepository.save(account);

        return toResponse(profile);
    }

    @Transactional
    public CreditProfileResponse updateAccount(Long userId, Long accountId, CreditAccountRequest request) {
        CreditProfile profile = requireProfile(userId);
        CreditAccount account = requireOwnedAccount(profile, accountId);

        apply(request, account);
        creditAccountRepository.save(account);

        return toResponse(profile);
    }

    @Transactional
    public CreditProfileResponse deleteAccount(Long userId, Long accountId) {
        CreditProfile profile = requireProfile(userId);
        creditAccountRepository.delete(requireOwnedAccount(profile, accountId));
        return toResponse(profile);
    }

    private static void apply(CreditAccountRequest request, CreditAccount account) {
        account.setAccountName(request.accountName().strip());
        account.setBalance(request.balance());
        account.setCreditLimit(request.creditLimit());
        account.setPaymentStatus(
                request.paymentStatus() == null ? PaymentStatus.UNKNOWN : request.paymentStatus());
    }

    /**
     * Loads an account only if it belongs to this profile. Without the ownership check, an
     * account id from another user's profile would be updated or deleted quite happily - the id
     * alone says nothing about who owns it.
     */
    private CreditAccount requireOwnedAccount(CreditProfile profile, Long accountId) {
        return creditAccountRepository.findById(accountId)
                .filter(account -> account.getCreditProfileId().equals(profile.getId()))
                .orElseThrow(() -> new CreditAccountNotFoundException(accountId));
    }

    // ------------------------------------------------------------------ score

    /**
     * Records a score reading as a new snapshot (FR-2.1.3, FR-2.4.1). Always an insert, never an
     * update: the history is what makes progress visible, and overwriting would answer "what is
     * it now" while destroying the evidence of whether it is moving.
     */
    @Transactional
    public CreditProfileResponse recordScore(Long userId, RecordCreditScoreRequest request) {
        CreditProfile profile = requireProfile(userId);

        // Checked against this profile's own scale rather than a constant - the point of
        // FR-2.1.4 is that a different bureau has a different ceiling.
        if (request.score() > profile.getMaxScore()) {
            throw new InvalidCreditDataException(String.format(
                    "Score %d is above the maximum for %s (%d). Check the scale your score is on.",
                    request.score(), profile.getBureau(), profile.getMaxScore()));
        }

        CreditSnapshot snapshot = new CreditSnapshot();
        snapshot.setCreditProfileId(profile.getId());
        snapshot.setScore(request.score());
        creditSnapshotRepository.save(snapshot);

        return toResponse(profile);
    }

    @Transactional(readOnly = true)
    public List<CreditSnapshot> scoreHistory(Long userId) {
        return creditSnapshotRepository
                .findByCreditProfileIdOrderByRecordedAtDesc(requireProfile(userId).getId());
    }

    // ------------------------------------------------------------------ shared

    private CreditProfile requireProfile(Long userId) {
        return creditProfileRepository.findByUserId(userId)
                .orElseThrow(CreditProfileNotFoundException::new);
    }

    private CreditProfileResponse toResponse(CreditProfile profile) {
        return CreditProfileResponse.from(
                profile,
                creditAccountRepository.findByCreditProfileIdOrderByAccountNameAsc(profile.getId()),
                creditSnapshotRepository
                        .findFirstByCreditProfileIdOrderByRecordedAtDesc(profile.getId())
                        .orElse(null));
    }
}
