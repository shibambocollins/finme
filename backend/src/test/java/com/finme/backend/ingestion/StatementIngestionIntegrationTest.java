package com.finme.backend.ingestion;

import com.finme.backend.ai.AiProvider;
import com.finme.backend.ai.ExtractedTransaction;
import com.finme.backend.transaction.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
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
@SpringBootTest
class StatementIngestionIntegrationTest {

    @Autowired
    private StatementIngestionService statementIngestionService;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private CapturingAiProvider capturingAiProvider;

    @TestConfiguration
    static class TestAiConfig {
        @Bean
        @Primary
        CapturingAiProvider capturingAiProvider() {
            return new CapturingAiProvider();
        }
    }

    static class CapturingAiProvider implements AiProvider {
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

    @Test
    void uploadedStatementIsExtractedRedactedAndPersistedWithoutLeakingPiiToTheAiCall() throws Exception {
        byte[] pdfBytes = SamplePdfFixture.buildSampleStatementPdf();
        MockMultipartFile file = new MockMultipartFile("file", "statement.pdf", "application/pdf", pdfBytes);

        BankStatement result = statementIngestionService.ingest(1L, file);

        assertThat(result.getStatus()).isEqualTo(StatementStatus.COMPLETE);
        assertThat(transactionRepository.findByUserIdOrderByDateDesc(1L)).isNotEmpty();

        String textSeenByAi = capturingAiProvider.lastInput();
        assertThat(textSeenByAi).isNotBlank();
        assertThat(textSeenByAi).doesNotContain(SamplePdfFixture.ACCOUNT_NUMBER);
        assertThat(textSeenByAi).doesNotContain(SamplePdfFixture.ID_NUMBER);
    }
}
