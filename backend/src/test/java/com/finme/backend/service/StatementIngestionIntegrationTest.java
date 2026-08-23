package com.finme.backend.service;

import com.finme.backend.ai.AiProvider;
import com.finme.backend.ai.ExtractedTransaction;
import com.finme.backend.ai.VisionAiProvider;
import com.finme.backend.entity.BankStatement;
import com.finme.backend.entity.StatementStatus;
import com.finme.backend.entity.TransactionStatus;
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
 * Exercises the full statement upload flow (extract -> redact -> AI structuring -> persist)
 * against a real H2 database. The AiProvider bean is overridden here with one that records
 * exactly what text it received, so the test can assert - not assume - that the account
 * number and ID number never reach the "AI" call. That's the concrete check behind NFR-2 and
 * Test Plan Sec. 3.
 */
// ai.provider is deliberately neither "mock" nor "chain" here, so MockAiProvider and the real
// provider chain both stay inactive and CapturingAiProvider below is the sole AiProvider bean
// - no @Primary needed, and no ambiguity against MockAiProvider's own @Primary.
@SpringBootTest(properties = "ai.provider=test-capturing")
class StatementIngestionIntegrationTest {

    @Autowired
    private StatementIngestionService statementIngestionService;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private BankStatementRepository bankStatementRepository;

    @Autowired
    private CapturingAiProvider capturingAiProvider;

    @TestConfiguration
    static class TestAiConfig {
        @Bean
        CapturingAiProvider capturingAiProvider() {
            return new CapturingAiProvider();
        }

        // ReceiptController -> ReceiptIngestionService needs a VisionAiProvider bean to exist
        // for the full app context to load here; this test doesn't exercise the receipt flow
        // at all, so a never-called stub is enough.
        @Bean
        VisionAiProvider stubVisionAiProvider() {
            return (imageBytes, mimeType) -> {
                throw new UnsupportedOperationException("not used by this test");
            };
        }
    }

    static class CapturingAiProvider extends com.finme.backend.ai.StubAiProvider {
        private final AtomicReference<String> lastInput = new AtomicReference<>();

        @Override
        public List<ExtractedTransaction> structureTransactions(String redactedText) {
            lastInput.set(redactedText);
            return List.of(new ExtractedTransaction(
                    LocalDate.now(), "Woolworths", new BigDecimal("450.00"), "Groceries", "test"));
        }

        String lastInput() {
            return lastInput.get();
        }
    }

    /**
     * Polls until the background extraction leaves PROCESSING. A fixed sleep would either be
     * flaky or needlessly slow; this returns as soon as the work is actually done.
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

    @Test
    void uploadedStatementIsExtractedRedactedAndPersistedWithoutLeakingPiiToTheAiCall() throws Exception {
        byte[] pdfBytes = SamplePdfFixture.buildSampleStatementPdf();
        MockMultipartFile file = new MockMultipartFile("file", "statement.pdf", "application/pdf", pdfBytes);

        BankStatement accepted = statementIngestionService.ingest(1L, file);

        // ingest() now only records the upload - extraction runs on a background thread, so the
        // response is PROCESSING and the test has to wait for the work rather than assume it.
        assertThat(accepted.getStatus()).isEqualTo(StatementStatus.PROCESSING);
        BankStatement result = awaitSettled(accepted.getId());

        assertThat(result.getStatus()).isEqualTo(StatementStatus.COMPLETE);
        assertThat(transactionRepository.findByUserIdAndStatusOrderByDateDesc(1L, TransactionStatus.ACTIVE)).isNotEmpty();

        String textSeenByAi = capturingAiProvider.lastInput();
        assertThat(textSeenByAi).isNotBlank();
        assertThat(textSeenByAi).doesNotContain(SamplePdfFixture.ACCOUNT_NUMBER);
        assertThat(textSeenByAi).doesNotContain(SamplePdfFixture.ID_NUMBER);
    }
}
