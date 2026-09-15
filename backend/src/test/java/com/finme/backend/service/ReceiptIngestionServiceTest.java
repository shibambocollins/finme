package com.finme.backend.service;

import com.finme.backend.ai.AllAiProvidersFailedException;
import com.finme.backend.ai.ExtractedTransaction;
import com.finme.backend.ai.VisionAiProvider;
import com.finme.backend.entity.PaymentMethod;
import com.finme.backend.entity.Receipt;
import com.finme.backend.entity.ReceiptStatus;
import com.finme.backend.entity.SourceType;
import com.finme.backend.entity.Transaction;
import com.finme.backend.entity.TransactionStatus;
import com.finme.backend.exception.ReceiptProcessingException;
import com.finme.backend.repository.ReceiptRepository;
import com.finme.backend.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReceiptIngestionServiceTest {

    private final ReceiptRepository receiptRepository = mock(ReceiptRepository.class);
    private final TransactionRepository transactionRepository = mock(TransactionRepository.class);
    private final VisionAiProvider visionAiProvider = mock(VisionAiProvider.class);
    private final ReceiptIngestionService receiptIngestionService = new ReceiptIngestionService(
            receiptRepository, transactionRepository, visionAiProvider, new InlineBackgroundRunner());

    private static MockMultipartFile jpegFile() {
        return new MockMultipartFile("file", "receipt.jpg", "image/jpeg", TestImages.jpeg());
    }

    private final List<Receipt> savedReceipts = new ArrayList<>();

    private void stubReceiptSaveAssignsId(Long id) {
        when(receiptRepository.save(any(Receipt.class))).thenAnswer(invocation -> {
            Receipt receipt = invocation.getArgument(0);
            if (receipt.getId() == null) {
                receipt.setId(id);
                receipt.setUserId(1L);
            }
            savedReceipts.add(receipt);
            return receipt;
        });
        // process() re-reads the row rather than trusting the instance ingest() saved, since in
        // production it runs on another thread. Returning the same instance the save stub has
        // been handed keeps the fake behaving like a real store, where both calls see one row.
        when(receiptRepository.findById(id)).thenAnswer(invocation ->
                Optional.of(savedReceipts.isEmpty() ? new Receipt() : savedReceipts.get(savedReceipts.size() - 1)));
    }

    @Test
    void persistsExtractedTransactionsAsActiveReceiptSourcedAndMarksReceiptComplete() throws Exception {
        stubReceiptSaveAssignsId(7L);
        when(visionAiProvider.extractFromImage(any(), any())).thenReturn(List.of(
                new ExtractedTransaction(LocalDate.of(2026, 1, 10), "Woolworths",
                        new BigDecimal("120.00"), "Groceries", "milk and bread", "CARD")));

        Receipt result = receiptIngestionService.ingest(1L, jpegFile());

        // ingest() hands the vision call to the background and returns immediately, so the
        // row it returns is still PROCESSING - that is the point of the change. COMPLETE is
        // asserted on the persisted row, which the background task is what updates.
        assertThat(result.getId()).isEqualTo(7L);
        assertThat(savedReceipts.get(savedReceipts.size() - 1).getStatus())
                .isEqualTo(ReceiptStatus.COMPLETE);

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        Transaction saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(1L);
        assertThat(saved.getSourceType()).isEqualTo(SourceType.RECEIPT);
        assertThat(saved.getSourceId()).isEqualTo(7L);
        assertThat(saved.getMerchant()).isEqualTo("Woolworths");
        assertThat(saved.getAmount()).isEqualByComparingTo("120.00");
        assertThat(saved.getPaymentMethod()).isEqualTo(PaymentMethod.CARD);
        assertThat(saved.getStatus()).isEqualTo(TransactionStatus.ACTIVE);
    }

    @Test
    void fallsBackToUnknownPaymentMethodWhenVisionProviderOmitsOrGarblesIt() throws Exception {
        stubReceiptSaveAssignsId(8L);
        when(visionAiProvider.extractFromImage(any(), any())).thenReturn(List.of(
                new ExtractedTransaction(LocalDate.of(2026, 1, 10), "Uber",
                        new BigDecimal("85.50"), "Transport", "ride", null),
                new ExtractedTransaction(LocalDate.of(2026, 1, 11), "Spar",
                        new BigDecimal("40.00"), "Groceries", "snacks", "not-a-real-method")));

        receiptIngestionService.ingest(1L, jpegFile());

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(Transaction::getPaymentMethod)
                .containsExactly(PaymentMethod.UNKNOWN, PaymentMethod.UNKNOWN);
    }

    @Test
    void recordsFailureOnTheRowWithAReasonWhenAllVisionProvidersFail() {
        stubReceiptSaveAssignsId(9L);
        RuntimeException cause = new RuntimeException("boom");
        when(visionAiProvider.extractFromImage(any(), any()))
                .thenThrow(new AllAiProvidersFailedException(cause));

        // No longer throws: the caller is a background thread with nobody waiting on it, so a
        // thrown exception would vanish into the executor. The row carries the outcome now,
        // and failureReason is the only way the user ever finds out why.
        receiptIngestionService.ingest(1L, jpegFile());

        Receipt stored = savedReceipts.get(savedReceipts.size() - 1);
        assertThat(stored.getStatus()).isEqualTo(ReceiptStatus.FAILED);
        assertThat(stored.getFailureReason()).isNotBlank();
        verify(transactionRepository, never()).save(any());
    }
}
