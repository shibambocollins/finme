package com.finme.backend.service;

import com.finme.backend.ai.AiProvider;
import com.finme.backend.ai.ExtractedTransaction;
import com.finme.backend.ai.StubAiProvider;
import com.finme.backend.ai.VisionAiProvider;
import com.finme.backend.entity.BankStatement;
import com.finme.backend.entity.Receipt;
import com.finme.backend.entity.ReceiptStatus;
import com.finme.backend.entity.StatementStatus;
import com.finme.backend.exception.InvalidReceiptFileException;
import com.finme.backend.exception.InvalidStatementFileException;
import com.finme.backend.exception.UnrecognisedDocumentException;
import com.finme.backend.repository.BankStatementRepository;
import com.finme.backend.repository.ReceiptRepository;
import com.finme.backend.repository.TransactionRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The upload paths a user actually stumbles into: the wrong file entirely, the right file type
 * holding the wrong kind of document, and a scanned PDF with no text in it.
 * <p>
 * Each of these used to end in a confident success with nothing to show for it - a COMPLETE
 * statement, an unchanged dashboard, and no explanation anywhere. Silence is the worst possible
 * response here, because the user's reasonable conclusion is that the app is broken rather than
 * that they uploaded the wrong thing.
 */
class DocumentValidationTest {

    private final BankStatementRepository bankStatementRepository = mock(BankStatementRepository.class);
    private final ReceiptRepository receiptRepository = mock(ReceiptRepository.class);
    private final TransactionRepository transactionRepository = mock(TransactionRepository.class);
    private final VisionAiProvider visionAiProvider = mock(VisionAiProvider.class);

    private AiProvider extractingNothing() {
        return new StubAiProvider() {
            @Override
            public List<ExtractedTransaction> structureTransactions(String redactedText) {
                return List.of();
            }
        };
    }

    private StatementIngestionService statementService(AiProvider provider) {
        when(bankStatementRepository.save(any(BankStatement.class))).thenAnswer(i -> {
            BankStatement s = i.getArgument(0);
            if (s.getId() == null) {
                s.setId(1L);
            }
            return s;
        });
        when(bankStatementRepository.findById(1L)).thenAnswer(i -> {
            BankStatement s = new BankStatement();
            s.setId(1L);
            s.setUserId(1L);
            return Optional.of(s);
        });
        return new StatementIngestionService(bankStatementRepository, transactionRepository,
                new PdfExtractionService(), new RedactionService(), provider,
                mock(DuplicateDetectionService.class), new StatementTextChunker(),
                mock(BackgroundRunner.class));
    }

    private ReceiptIngestionService receiptService() {
        when(receiptRepository.save(any(Receipt.class))).thenAnswer(i -> {
            Receipt r = i.getArgument(0);
            if (r.getId() == null) {
                r.setId(1L);
            }
            return r;
        });
        return new ReceiptIngestionService(receiptRepository, transactionRepository, visionAiProvider);
    }

    private static byte[] pdfWithNoText() throws IOException {
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    @Test
    void rejectsAFileThatIsNotAPdfEvenWhenTheRequestClaimsItIs() {
        // Content type is a client-supplied header - a renamed file sets it from the extension.
        MockMultipartFile notAPdf = new MockMultipartFile(
                "file", "statement.pdf", "application/pdf", TestImages.png());

        assertThatThrownBy(() -> statementService(extractingNothing()).ingest(1L, notAPdf))
                .isInstanceOf(InvalidStatementFileException.class)
                .hasMessageContaining("not a PDF");

        verify(bankStatementRepository, never()).save(any());
    }

    @Test
    void reportsAScannedPdfAsUnreadableRatherThanSucceedingWithNothing() throws IOException {
        MockMultipartFile scanned = new MockMultipartFile(
                "file", "statement.pdf", "application/pdf", pdfWithNoText());
        StatementIngestionService service = statementService(extractingNothing());
        service.ingest(1L, scanned);

        assertThatThrownBy(() -> service.process(1L, 1L, pdfWithNoText()))
                .isInstanceOf(UnrecognisedDocumentException.class)
                .hasMessageContaining("No readable text");
    }

    @Test
    void reportsAPdfWithNoTransactionsAsNotABankStatement() throws IOException {
        // A payslip or invoice: readable text, no transactions. The model returns an empty list
        // exactly as the prompt asks it to; the app must act on that rather than ignore it.
        byte[] pdf = SamplePdfFixture.buildSampleStatementPdf();
        StatementIngestionService service = statementService(extractingNothing());

        assertThatThrownBy(() -> service.process(1L, 1L, pdf))
                .isInstanceOf(UnrecognisedDocumentException.class)
                .hasMessageContaining("does not look like a bank statement");
    }

    @Test
    void recordsTheReasonOnTheStatementSoAPollingClientCanShowIt() throws IOException {
        byte[] pdf = SamplePdfFixture.buildSampleStatementPdf();
        StatementIngestionService service = statementService(extractingNothing());

        assertThatThrownBy(() -> service.process(1L, 1L, pdf))
                .isInstanceOf(UnrecognisedDocumentException.class);

        ArgumentCaptor<BankStatement> captor = ArgumentCaptor.forClass(BankStatement.class);
        verify(bankStatementRepository, atLeastOnce()).save(captor.capture());
        BankStatement saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(StatementStatus.FAILED);
        assertThat(saved.getFailureReason()).contains("does not look like a bank statement");
    }

    @Test
    void rejectsAReceiptUploadThatIsNotActuallyAnImage() {
        MockMultipartFile notAnImage = new MockMultipartFile(
                "file", "receipt.jpg", "image/jpeg", "just some text".getBytes());

        assertThatThrownBy(() -> receiptService().ingest(1L, notAnImage))
                .isInstanceOf(InvalidReceiptFileException.class)
                .hasMessageContaining("not a JPEG or PNG");

        verify(transactionRepository, never()).save(any());
    }

    @Test
    void reportsAPhotoThatIsNotAReceiptRatherThanSavingNothingQuietly() {
        when(visionAiProvider.extractFromImage(any(), any())).thenReturn(List.of());
        MockMultipartFile photo = new MockMultipartFile(
                "file", "receipt.jpg", "image/jpeg", TestImages.jpeg());

        assertThatThrownBy(() -> receiptService().ingest(1L, photo))
                .isInstanceOf(UnrecognisedDocumentException.class)
                .hasMessageContaining("does not look like a receipt");

        verify(transactionRepository, never()).save(any());
    }

    @Test
    void doesNotLeaveARejectedReceiptStuckInProcessing() {
        when(visionAiProvider.extractFromImage(any(), any())).thenReturn(List.of());
        MockMultipartFile photo = new MockMultipartFile(
                "file", "receipt.jpg", "image/jpeg", TestImages.jpeg());

        assertThatThrownBy(() -> receiptService().ingest(1L, photo))
                .isInstanceOf(UnrecognisedDocumentException.class);

        ArgumentCaptor<Receipt> captor = ArgumentCaptor.forClass(Receipt.class);
        verify(receiptRepository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ReceiptStatus.FAILED);
    }

    @Test
    void acceptsAPngReceiptAsWellAsJpeg() {
        when(visionAiProvider.extractFromImage(any(), any())).thenReturn(List.of(
                new ExtractedTransaction(java.time.LocalDate.of(2026, 7, 2), "Cafe",
                        new java.math.BigDecimal("42.00"), "Dining", "coffee", "CASH")));
        MockMultipartFile png = new MockMultipartFile(
                "file", "receipt.png", "image/png", TestImages.png());

        assertThat(receiptService().ingest(1L, png).getStatus()).isEqualTo(ReceiptStatus.COMPLETE);
    }
}
