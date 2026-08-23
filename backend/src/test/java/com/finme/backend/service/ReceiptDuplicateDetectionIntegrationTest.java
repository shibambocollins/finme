package com.finme.backend.service;

import com.finme.backend.ai.AiProvider;
import com.finme.backend.ai.ExtractedTransaction;
import com.finme.backend.ai.VisionAiProvider;
import com.finme.backend.entity.PaymentMethod;
import com.finme.backend.entity.Receipt;
import com.finme.backend.entity.SourceType;
import com.finme.backend.entity.Transaction;
import com.finme.backend.entity.TransactionStatus;
import com.finme.backend.entity.BankStatement;
import com.finme.backend.entity.StatementStatus;
import com.finme.backend.repository.BankStatementRepository;
import com.finme.backend.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises ReceiptIngestionService, StatementIngestionService and DuplicateDetectionService
 * together against a real H2 database - the "receipt uploaded now, statement confirms it later"
 * flow from docs/04-product-backlog.md Iteration 4. Neither of the two existing tests covers
 * this path together: DuplicateDetectionServiceTest mocks the repository in isolation, and
 * StatementIngestionIntegrationTest stubs the vision provider out entirely.
 */
@SpringBootTest(properties = "ai.provider=test-capturing")
class ReceiptDuplicateDetectionIntegrationTest {

    @Autowired
    private BankStatementRepository bankStatementRepository;

    /**
     * Statement extraction runs on a background thread now, so duplicate detection - which
     * happens as each extracted transaction is saved - has not necessarily run when ingest()
     * returns. Without this wait these assertions raced the extraction and saw the receipt
     * transaction still ACTIVE.
     */
    private BankStatement awaitSettled(Long statementId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            BankStatement statement = bankStatementRepository.findById(statementId).orElseThrow();
            if (statement.getStatus() != StatementStatus.PROCESSING) {
                return statement;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Statement " + statementId + " was still PROCESSING after 30s");
    }

    @Autowired
    private ReceiptIngestionService receiptIngestionService;

    @Autowired
    private StatementIngestionService statementIngestionService;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private ScriptedAiProvider scriptedAiProvider;

    @Autowired
    private ScriptedVisionAiProvider scriptedVisionAiProvider;

    @TestConfiguration
    static class TestAiConfig {
        @Bean
        ScriptedAiProvider scriptedAiProvider() {
            return new ScriptedAiProvider();
        }

        @Bean
        ScriptedVisionAiProvider scriptedVisionAiProvider() {
            return new ScriptedVisionAiProvider();
        }
    }

    static class ScriptedAiProvider implements AiProvider {
        private final AtomicReference<List<ExtractedTransaction>> next = new AtomicReference<>(List.of());

        void willReturn(ExtractedTransaction transaction) {
            next.set(List.of(transaction));
        }

        @Override
        public List<ExtractedTransaction> structureTransactions(String redactedText) {
            return next.get();
        }
    }

    static class ScriptedVisionAiProvider implements VisionAiProvider {
        private final AtomicReference<List<ExtractedTransaction>> next = new AtomicReference<>(List.of());

        void willReturn(ExtractedTransaction transaction) {
            next.set(List.of(transaction));
        }

        @Override
        public List<ExtractedTransaction> extractFromImage(byte[] imageBytes, String mimeType) {
            return next.get();
        }
    }

    private static MockMultipartFile jpegFile() {
        return new MockMultipartFile("file", "receipt.jpg", "image/jpeg", new byte[]{1, 2, 3});
    }

    private static MockMultipartFile pdfFile() throws Exception {
        return new MockMultipartFile("file", "statement.pdf", "application/pdf",
                SamplePdfFixture.buildSampleStatementPdf());
    }

    @Test
    void statementConfirmingACardReceiptSupersedesTheReceiptTransaction() throws Exception {
        long userId = 101L;
        LocalDate receiptDate = LocalDate.of(2026, 2, 10);

        scriptedVisionAiProvider.willReturn(new ExtractedTransaction(
                receiptDate, "Woolworths", new BigDecimal("450.00"), "Groceries", "receipt", "CARD"));
        Receipt receipt = receiptIngestionService.ingest(userId, jpegFile());

        List<Transaction> afterReceipt =
                transactionRepository.findByUserIdAndStatusOrderByDateDesc(userId, TransactionStatus.ACTIVE);
        assertThat(afterReceipt).hasSize(1);
        Transaction receiptTransaction = afterReceipt.get(0);
        assertThat(receiptTransaction.getSourceType()).isEqualTo(SourceType.RECEIPT);
        assertThat(receiptTransaction.getSourceId()).isEqualTo(receipt.getId());

        scriptedAiProvider.willReturn(new ExtractedTransaction(
                receiptDate.plusDays(1), "Woolworths", new BigDecimal("450.00"), "Groceries", "statement"));
        awaitSettled(statementIngestionService.ingest(userId, pdfFile()).getId());

        Transaction reloadedReceiptTransaction =
                transactionRepository.findById(receiptTransaction.getId()).orElseThrow();
        assertThat(reloadedReceiptTransaction.getStatus()).isEqualTo(TransactionStatus.SUPERSEDED);
        assertThat(reloadedReceiptTransaction.getSupersededBy()).isNotNull();

        List<Transaction> activeAfterStatement =
                transactionRepository.findByUserIdAndStatusOrderByDateDesc(userId, TransactionStatus.ACTIVE);
        assertThat(activeAfterStatement).hasSize(1);
        assertThat(activeAfterStatement.get(0).getSourceType()).isEqualTo(SourceType.STATEMENT);
    }

    @Test
    void statementNeverSupersedesACashReceiptEvenOnAnExactAmountMatch() throws Exception {
        long userId = 102L;
        LocalDate receiptDate = LocalDate.of(2026, 2, 10);

        scriptedVisionAiProvider.willReturn(new ExtractedTransaction(
                receiptDate, "Corner Cafe", new BigDecimal("65.00"), "Dining", "receipt", "CASH"));
        receiptIngestionService.ingest(userId, jpegFile());
        Transaction receiptTransaction = transactionRepository
                .findByUserIdAndStatusOrderByDateDesc(userId, TransactionStatus.ACTIVE).get(0);
        assertThat(receiptTransaction.getPaymentMethod()).isEqualTo(PaymentMethod.CASH);

        scriptedAiProvider.willReturn(new ExtractedTransaction(
                receiptDate, "Corner Cafe", new BigDecimal("65.00"), "Dining", "statement"));
        awaitSettled(statementIngestionService.ingest(userId, pdfFile()).getId());

        Transaction reloadedReceiptTransaction =
                transactionRepository.findById(receiptTransaction.getId()).orElseThrow();
        assertThat(reloadedReceiptTransaction.getStatus()).isEqualTo(TransactionStatus.ACTIVE);
        assertThat(reloadedReceiptTransaction.getSupersededBy()).isNull();

        List<Transaction> activeAfterStatement =
                transactionRepository.findByUserIdAndStatusOrderByDateDesc(userId, TransactionStatus.ACTIVE);
        assertThat(activeAfterStatement).hasSize(2);
    }
}
