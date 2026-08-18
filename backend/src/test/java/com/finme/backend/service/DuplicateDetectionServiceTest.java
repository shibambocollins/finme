package com.finme.backend.service;

import com.finme.backend.entity.PaymentMethod;
import com.finme.backend.entity.SourceType;
import com.finme.backend.entity.Transaction;
import com.finme.backend.entity.TransactionStatus;
import com.finme.backend.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DuplicateDetectionServiceTest {

    private final TransactionRepository transactionRepository = mock(TransactionRepository.class);
    private final DuplicateDetectionService duplicateDetectionService =
            new DuplicateDetectionService(transactionRepository);

    private static Transaction statementTransaction(Long id, LocalDate date, String amount) {
        Transaction t = new Transaction();
        t.setId(id);
        t.setUserId(1L);
        t.setSourceType(SourceType.STATEMENT);
        t.setDate(date);
        t.setMerchant("Woolworths");
        t.setAmount(new BigDecimal(amount));
        t.setPaymentMethod(PaymentMethod.CARD);
        t.setStatus(TransactionStatus.ACTIVE);
        return t;
    }

    private static Transaction receiptTransaction(Long id, LocalDate date, String amount) {
        Transaction t = new Transaction();
        t.setId(id);
        t.setUserId(1L);
        t.setSourceType(SourceType.RECEIPT);
        t.setDate(date);
        t.setMerchant("Woolworths");
        t.setAmount(new BigDecimal(amount));
        t.setPaymentMethod(PaymentMethod.CARD);
        t.setStatus(TransactionStatus.ACTIVE);
        return t;
    }

    @Test
    void marksAMatchingReceiptTransactionSupersededAndLinksItToTheStatementTransaction() {
        Transaction statementTx = statementTransaction(100L, LocalDate.of(2026, 1, 15), "450.00");
        Transaction receiptTx = receiptTransaction(50L, LocalDate.of(2026, 1, 14), "450.00");

        when(transactionRepository.findByUserIdAndSourceTypeAndPaymentMethodAndStatusAndAmountAndDateBetween(
                eq(1L), eq(SourceType.RECEIPT), eq(PaymentMethod.CARD), eq(TransactionStatus.ACTIVE),
                eq(new BigDecimal("450.00")), any(), any()))
                .thenReturn(List.of(receiptTx));

        duplicateDetectionService.checkForDuplicate(statementTx);

        assertThat(receiptTx.getStatus()).isEqualTo(TransactionStatus.SUPERSEDED);
        assertThat(receiptTx.getSupersededBy()).isEqualTo(100L);
        verify(transactionRepository).save(receiptTx);
    }

    @Test
    void doesNothingWhenNoMatchingReceiptExists() {
        Transaction statementTx = statementTransaction(100L, LocalDate.of(2026, 1, 15), "450.00");
        when(transactionRepository.findByUserIdAndSourceTypeAndPaymentMethodAndStatusAndAmountAndDateBetween(
                any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        duplicateDetectionService.checkForDuplicate(statementTx);

        verify(transactionRepository, never()).save(any());
    }

    @Test
    void neverRunsForANonCardStatementTransaction() {
        Transaction cashLikeTx = statementTransaction(100L, LocalDate.of(2026, 1, 15), "450.00");
        cashLikeTx.setPaymentMethod(PaymentMethod.CASH);

        duplicateDetectionService.checkForDuplicate(cashLikeTx);

        verify(transactionRepository, never())
                .findByUserIdAndSourceTypeAndPaymentMethodAndStatusAndAmountAndDateBetween(
                        any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void queriesReceiptSourceTypeCardPaymentMethodAndActiveStatusOnlyWithinAThreeDayWindow() {
        Transaction statementTx = statementTransaction(100L, LocalDate.of(2026, 1, 15), "450.00");
        when(transactionRepository.findByUserIdAndSourceTypeAndPaymentMethodAndStatusAndAmountAndDateBetween(
                any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        duplicateDetectionService.checkForDuplicate(statementTx);

        ArgumentCaptor<LocalDate> startCaptor = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> endCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(transactionRepository).findByUserIdAndSourceTypeAndPaymentMethodAndStatusAndAmountAndDateBetween(
                eq(1L), eq(SourceType.RECEIPT), eq(PaymentMethod.CARD), eq(TransactionStatus.ACTIVE),
                eq(new BigDecimal("450.00")), startCaptor.capture(), endCaptor.capture());

        assertThat(startCaptor.getValue()).isEqualTo(LocalDate.of(2026, 1, 12));
        assertThat(endCaptor.getValue()).isEqualTo(LocalDate.of(2026, 1, 18));
    }
}
