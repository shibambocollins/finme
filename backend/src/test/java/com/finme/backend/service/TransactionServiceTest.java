package com.finme.backend.service;

import com.finme.backend.dto.UpdateTransactionRequest;
import com.finme.backend.entity.PaymentMethod;
import com.finme.backend.entity.SourceType;
import com.finme.backend.entity.Transaction;
import com.finme.backend.entity.TransactionDirection;
import com.finme.backend.entity.TransactionStatus;
import com.finme.backend.exception.TransactionNotFoundException;
import com.finme.backend.repository.TransactionRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TransactionServiceTest {

    private static final Long USER = 1L;
    private static final Long OTHER_USER = 2L;

    private final TransactionRepository transactionRepository = mock(TransactionRepository.class);
    private final TransactionService service = new TransactionService(transactionRepository);

    private static Transaction transaction(long id, Long userId, LocalDate date, String merchant,
                                           String amount, String category, SourceType sourceType,
                                           TransactionDirection direction, String description) {
        Transaction t = new Transaction();
        t.setId(id);
        t.setUserId(userId);
        t.setSourceType(sourceType);
        t.setDate(date);
        t.setMerchant(merchant);
        t.setAmount(new BigDecimal(amount));
        t.setCategory(category);
        t.setDescription(description);
        t.setPaymentMethod(PaymentMethod.CARD);
        t.setDirection(direction);
        t.setStatus(TransactionStatus.ACTIVE);
        return t;
    }

    private void givenTransactions(Transaction... transactions) {
        when(transactionRepository.findByUserIdAndStatusOrderByDateDesc(USER, TransactionStatus.ACTIVE))
                .thenReturn(List.of(transactions));
    }

    // ------------------------------------------------------------------ search

    @Test
    void returnsEverythingWhenNoFilterIsGiven() {
        givenTransactions(
                transaction(1, USER, LocalDate.of(2026, 7, 2), "Woolworths", "100.00", "Groceries",
                        SourceType.STATEMENT, TransactionDirection.DEBIT, "milk"),
                transaction(2, USER, LocalDate.of(2026, 7, 3), "Uber", "50.00", "Transport",
                        SourceType.STATEMENT, TransactionDirection.DEBIT, "ride"));

        assertThat(service.search(USER, null, null, null, null, null, null)).hasSize(2);
    }

    @Test
    void filtersByCategoryCaseInsensitively() {
        givenTransactions(
                transaction(1, USER, LocalDate.of(2026, 7, 2), "Woolworths", "100.00", "Groceries",
                        SourceType.STATEMENT, TransactionDirection.DEBIT, null),
                transaction(2, USER, LocalDate.of(2026, 7, 3), "Uber", "50.00", "Transport",
                        SourceType.STATEMENT, TransactionDirection.DEBIT, null));

        List<Transaction> result = service.search(USER, "groceries", null, null, null, null, null);

        assertThat(result).extracting(Transaction::getMerchant).containsExactly("Woolworths");
    }

    @Test
    void filtersBySourceTypeAndDirection() {
        givenTransactions(
                transaction(1, USER, LocalDate.of(2026, 7, 2), "Salary", "18500.00", "Income",
                        SourceType.STATEMENT, TransactionDirection.CREDIT, null),
                transaction(2, USER, LocalDate.of(2026, 7, 3), "Coffee", "35.00", "Dining",
                        SourceType.MANUAL, TransactionDirection.DEBIT, null));

        assertThat(service.search(USER, null, null, TransactionDirection.CREDIT, null, null, null))
                .extracting(Transaction::getMerchant).containsExactly("Salary");
        assertThat(service.search(USER, null, SourceType.MANUAL, null, null, null, null))
                .extracting(Transaction::getMerchant).containsExactly("Coffee");
    }

    @Test
    void filtersByInclusiveDateRange() {
        givenTransactions(
                transaction(1, USER, LocalDate.of(2026, 7, 1), "A", "10.00", "Other",
                        SourceType.STATEMENT, TransactionDirection.DEBIT, null),
                transaction(2, USER, LocalDate.of(2026, 7, 15), "B", "10.00", "Other",
                        SourceType.STATEMENT, TransactionDirection.DEBIT, null),
                transaction(3, USER, LocalDate.of(2026, 7, 31), "C", "10.00", "Other",
                        SourceType.STATEMENT, TransactionDirection.DEBIT, null));

        List<Transaction> result = service.search(USER, null, null, null,
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 15), null);

        assertThat(result).extracting(Transaction::getMerchant).containsExactlyInAnyOrder("A", "B");
    }

    @Test
    void searchesMerchantAndDescriptionCaseInsensitively() {
        givenTransactions(
                transaction(1, USER, LocalDate.of(2026, 7, 2), "Woolworths Sandton", "100.00", "Groceries",
                        SourceType.STATEMENT, TransactionDirection.DEBIT, "weekly shop"),
                transaction(2, USER, LocalDate.of(2026, 7, 3), "Uber", "50.00", "Transport",
                        SourceType.STATEMENT, TransactionDirection.DEBIT, "trip to sandton mall"),
                transaction(3, USER, LocalDate.of(2026, 7, 4), "Netflix", "199.00", "Entertainment",
                        SourceType.STATEMENT, TransactionDirection.DEBIT, null));

        List<Transaction> result = service.search(USER, null, null, null, null, null, "SANDTON");

        assertThat(result).extracting(Transaction::getMerchant)
                .containsExactlyInAnyOrder("Woolworths Sandton", "Uber");
    }

    @Test
    void combinesMultipleFiltersAsAnd() {
        givenTransactions(
                transaction(1, USER, LocalDate.of(2026, 7, 2), "Woolworths", "100.00", "Groceries",
                        SourceType.STATEMENT, TransactionDirection.DEBIT, null),
                transaction(2, USER, LocalDate.of(2026, 6, 2), "Checkers", "80.00", "Groceries",
                        SourceType.STATEMENT, TransactionDirection.DEBIT, null));

        List<Transaction> result = service.search(USER, "Groceries", null, null,
                LocalDate.of(2026, 7, 1), null, null);

        assertThat(result).extracting(Transaction::getMerchant).containsExactly("Woolworths");
    }

    // ------------------------------------------------------------------ update

    @Test
    void updatesEveryEditableFieldAndLeavesProvenanceAlone() {
        Transaction existing = transaction(1, USER, LocalDate.of(2026, 7, 2), "Wolworths", "45.00",
                "Shopping", SourceType.STATEMENT, TransactionDirection.DEBIT, null);
        existing.setSourceId(77L);
        existing.setStatus(TransactionStatus.ACTIVE);
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> i.getArgument(0));

        Transaction updated = service.update(USER, 1L, new UpdateTransactionRequest(
                LocalDate.of(2026, 7, 3), "Woolworths Sandton", new BigDecimal("47.50"),
                TransactionDirection.DEBIT, "Groceries", "corrected", PaymentMethod.CARD));

        assertThat(updated.getMerchant()).isEqualTo("Woolworths Sandton");
        assertThat(updated.getAmount()).isEqualByComparingTo("47.50");
        assertThat(updated.getCategory()).isEqualTo("Groceries");
        assertThat(updated.getDescription()).isEqualTo("corrected");
        // sourceType, sourceId, status are not part of the request shape - they must survive.
        assertThat(updated.getSourceType()).isEqualTo(SourceType.STATEMENT);
        assertThat(updated.getSourceId()).isEqualTo(77L);
        assertThat(updated.getStatus()).isEqualTo(TransactionStatus.ACTIVE);
    }

    @Test
    void acceptsAnyCategoryTextRatherThanARestrictedList() {
        // Category was always a plain String on the entity - the AI's fixed list is a prompt
        // hint for extraction, not a schema constraint. A correction should not be limited by it.
        Transaction existing = transaction(1, USER, LocalDate.of(2026, 7, 2), "Studio", "500.00",
                "Other", SourceType.MANUAL, TransactionDirection.DEBIT, null);
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> i.getArgument(0));

        Transaction updated = service.update(USER, 1L, new UpdateTransactionRequest(
                LocalDate.of(2026, 7, 2), "Studio", new BigDecimal("500.00"),
                TransactionDirection.DEBIT, "Pilates Membership", null, PaymentMethod.CARD));

        assertThat(updated.getCategory()).isEqualTo("Pilates Membership");
    }

    @Test
    void refusesToUpdateAnotherUsersTransaction() {
        Transaction theirs = transaction(1, OTHER_USER, LocalDate.of(2026, 7, 2), "X", "10.00",
                "Other", SourceType.STATEMENT, TransactionDirection.DEBIT, null);
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(theirs));

        assertThatThrownBy(() -> service.update(USER, 1L, new UpdateTransactionRequest(
                LocalDate.of(2026, 7, 2), "X", new BigDecimal("10.00"),
                TransactionDirection.DEBIT, "Other", null, PaymentMethod.CARD)))
                .isInstanceOf(TransactionNotFoundException.class);
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void reportsAnUnknownTransactionTheSameWayAsAnUnownedOne() {
        when(transactionRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(USER, 999L))
                .isInstanceOf(TransactionNotFoundException.class);
    }

    // ------------------------------------------------------------------ delete

    @Test
    void deletesAnOwnedTransaction() {
        Transaction mine = transaction(1, USER, LocalDate.of(2026, 7, 2), "X", "10.00",
                "Other", SourceType.STATEMENT, TransactionDirection.DEBIT, null);
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(mine));

        service.delete(USER, 1L);

        verify(transactionRepository).delete(mine);
    }

    @Test
    void refusesToDeleteAnotherUsersTransaction() {
        Transaction theirs = transaction(1, OTHER_USER, LocalDate.of(2026, 7, 2), "X", "10.00",
                "Other", SourceType.STATEMENT, TransactionDirection.DEBIT, null);
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(theirs));

        assertThatThrownBy(() -> service.delete(USER, 1L))
                .isInstanceOf(TransactionNotFoundException.class);
        verify(transactionRepository, never()).delete(any(Transaction.class));
    }
}
