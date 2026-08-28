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
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final TransactionRepository transactionRepository = mock(TransactionRepository.class);
    private final BankStatementRepository bankStatementRepository = mock(BankStatementRepository.class);
    private final ReceiptRepository receiptRepository = mock(ReceiptRepository.class);
    private final BudgetRepository budgetRepository = mock(BudgetRepository.class);
    private final CreditProfileService creditProfileService = mock(CreditProfileService.class);
    private final UserService userService = new UserService(
            userRepository, transactionRepository, bankStatementRepository,
            receiptRepository, budgetRepository, creditProfileService);

    private User user(Long id, String email) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setDisplayName("Old Name");
        return user;
    }

    @Test
    void updateDisplayNameStripsAndSavesTheNewName() {
        User user = user(1L, "user@example.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        ProfileResponse result = userService.updateDisplayName(1L, new UpdateDisplayNameRequest("  New Name  "));

        assertThat(result.email()).isEqualTo("user@example.com");
        assertThat(result.displayName()).isEqualTo("New Name");
        assertThat(user.getDisplayName()).isEqualTo("New Name");
        verify(userRepository).save(user);
    }

    @Test
    void updateDisplayNameThrowsForAUserIdThatDoesNotExist() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateDisplayName(99L, new UpdateDisplayNameRequest("Name")))
                .isInstanceOf(java.util.NoSuchElementException.class);
    }

    @Test
    void clearFinancialDataRemovesEveryOwnedRecordButKeepsTheAccount() {
        User user = user(1L, "user@example.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(creditProfileService.hasProfile(1L)).thenReturn(true);

        userService.clearFinancialData(1L, new ConfirmAccountActionRequest("user@example.com"));

        verify(transactionRepository).deleteByUserId(1L);
        verify(bankStatementRepository).deleteByUserId(1L);
        verify(receiptRepository).deleteByUserId(1L);
        verify(budgetRepository).deleteByUserId(1L);
        verify(creditProfileService).deleteProfile(1L);
        verify(userRepository, never()).delete(user);
    }

    @Test
    void clearFinancialDataSkipsCreditCleanupWhenNoProfileExists() {
        User user = user(1L, "user@example.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(creditProfileService.hasProfile(1L)).thenReturn(false);

        userService.clearFinancialData(1L, new ConfirmAccountActionRequest("user@example.com"));

        verify(creditProfileService, never()).deleteProfile(1L);
    }

    @Test
    void clearFinancialDataRejectsAConfirmationThatDoesNotMatchTheAccountEmail() {
        User user = user(1L, "user@example.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.clearFinancialData(1L, new ConfirmAccountActionRequest("wrong@example.com")))
                .isInstanceOf(InvalidAccountDeletionConfirmationException.class);
        verify(transactionRepository, never()).deleteByUserId(1L);
    }

    @Test
    void deleteAccountRemovesOwnedDataAndTheAccountItself() {
        User user = user(1L, "user@example.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(creditProfileService.hasProfile(1L)).thenReturn(true);

        userService.deleteAccount(1L, new ConfirmAccountActionRequest("USER@EXAMPLE.COM"));

        verify(transactionRepository).deleteByUserId(1L);
        verify(bankStatementRepository).deleteByUserId(1L);
        verify(receiptRepository).deleteByUserId(1L);
        verify(budgetRepository).deleteByUserId(1L);
        verify(creditProfileService).deleteProfile(1L);
        verify(userRepository).delete(user);
    }

    @Test
    void deleteAccountRejectsAConfirmationThatDoesNotMatchTheAccountEmail() {
        User user = user(1L, "user@example.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.deleteAccount(1L, new ConfirmAccountActionRequest("someone-else@example.com")))
                .isInstanceOf(InvalidAccountDeletionConfirmationException.class);
        verify(userRepository, never()).delete(user);
    }
}
