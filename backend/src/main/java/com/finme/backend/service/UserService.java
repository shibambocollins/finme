package com.finme.backend.service;

import com.finme.backend.dto.ConfirmAccountActionRequest;
import com.finme.backend.dto.ProfileResponse;
import com.finme.backend.dto.UpdateDisplayNameRequest;
import com.finme.backend.entity.User;
import com.finme.backend.exception.InvalidAccountDeletionConfirmationException;
import com.finme.backend.repository.BankStatementRepository;
import com.finme.backend.repository.BudgetRepository;
import com.finme.backend.repository.ReceiptRepository;
import com.finme.backend.repository.TransactionRepository;
import com.finme.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final BankStatementRepository bankStatementRepository;
    private final ReceiptRepository receiptRepository;
    private final BudgetRepository budgetRepository;
    private final CreditProfileService creditProfileService;

    public UserService(
            UserRepository userRepository,
            TransactionRepository transactionRepository,
            BankStatementRepository bankStatementRepository,
            ReceiptRepository receiptRepository,
            BudgetRepository budgetRepository,
            CreditProfileService creditProfileService) {
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
        this.bankStatementRepository = bankStatementRepository;
        this.receiptRepository = receiptRepository;
        this.budgetRepository = budgetRepository;
        this.creditProfileService = creditProfileService;
    }

    /**
     * No JWT re-issue on a name change: the JWT's displayName claim only ever existed to hand
     * the frontend a name at OAuth-callback time without a round trip (see JwtService) - nothing
     * server-side reads it back. The frontend already holds an authenticated session here and
     * updates its own stored displayName straight from this response, so a stale claim in the
     * still-valid token never surfaces anywhere.
     */
    public ProfileResponse updateDisplayName(Long userId, UpdateDisplayNameRequest request) {
        User user = userRepository.findById(userId).orElseThrow();
        user.setDisplayName(request.displayName().strip());
        userRepository.save(user);
        return new ProfileResponse(user.getEmail(), user.getDisplayName());
    }

    @Transactional
    public void clearFinancialData(Long userId, ConfirmAccountActionRequest request) {
        User user = requireConfirmed(userId, request);
        deleteAllOwnedData(user.getId());
    }

    /**
     * Removes the account and everything it owns. Deliberately explicit rather than relying on
     * JPA cascades, same reasoning as {@link CreditProfileService#deleteProfile}: every child row
     * here is linked by a plain userId column, not a mapped association, so nothing removes them
     * automatically.
     * <p>
     * Not undone by this call alone: a JWT already issued to this account stays cryptographically
     * valid until it naturally expires (up to app.jwt.expiration-ms, 24h by default) - this app
     * has no server-side token revocation list. It is functionally harmless once the underlying
     * rows are gone (every endpoint it could still call would find nothing to act on), but it is
     * not instant revocation, and the frontend must still explicitly log the caller out itself
     * rather than relying on the deletion to do that.
     */
    @Transactional
    public void deleteAccount(Long userId, ConfirmAccountActionRequest request) {
        User user = requireConfirmed(userId, request);
        deleteAllOwnedData(user.getId());
        userRepository.delete(user);
    }

    private User requireConfirmed(Long userId, ConfirmAccountActionRequest request) {
        User user = userRepository.findById(userId).orElseThrow();
        if (!user.getEmail().equalsIgnoreCase(request.confirmationEmail().strip())) {
            throw new InvalidAccountDeletionConfirmationException();
        }
        return user;
    }

    private void deleteAllOwnedData(Long userId) {
        transactionRepository.deleteByUserId(userId);
        bankStatementRepository.deleteByUserId(userId);
        receiptRepository.deleteByUserId(userId);
        budgetRepository.deleteByUserId(userId);
        if (creditProfileService.hasProfile(userId)) {
            creditProfileService.deleteProfile(userId);
        }
    }
}
