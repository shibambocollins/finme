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
import java.util.List;

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
    private final ReceiptIngestionService receiptIngestionService =
            new ReceiptIngestionService(receiptRepository, transactionRepository, visionAiProvider);

    private static MockMultipartFile jpegFile() {
        return new MockMultipartFile("file", "receipt.jpg", "image/jpeg", new byte[]{1, 2, 3});
    }

    private void stubReceiptSaveAssignsId(Long id) {
        when(receiptRepository.save(any(Receipt.class))).thenAnswer(invocation -> {
            Receipt receipt = invocation.getArgument(0);
            if (receipt.getId() == null) {
                receipt.setId(id);
            }
            return receipt;
        });
    }

    @Test
    void persistsExtractedTransactionsAsActiveReceiptSourcedAndMarksReceiptComplete() throws Exception {
        stubReceiptSaveAssignsId(7L);
        when(visionAiProvider.extractFromImage(any(), any())).thenReturn(List.of(
                new ExtractedTransaction(LocalDate.of(2026, 1, 10), "Woolworths",
                        new BigDecimal("120.00"), "Groceries", "milk and bread", "CARD")));

        Receipt result = receiptIngestionService.ingest(1L, jpegFile());

        assertThat(result.getStatus()).isEqualTo(ReceiptStatus.COMPLETE);

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
    void marksReceiptFailedAndThrowsWhenAllVisionProvidersFail() {
        stubReceiptSaveAssignsId(9L);
        RuntimeException cause = new RuntimeException("boom");
        when(visionAiProvider.extractFromImage(any(), any()))
                .thenThrow(new AllAiProvidersFailedException(cause));

        assertThatThrownBy(() -> receiptIngestionService.ingest(1L, jpegFile()))
                .isInstanceOf(ReceiptProcessingException.class);

        ArgumentCaptor<Receipt> captor = ArgumentCaptor.forClass(Receipt.class);
        verify(receiptRepository, times(2)).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ReceiptStatus.FAILED);
        verify(transactionRepository, never()).save(any());
    }
}
