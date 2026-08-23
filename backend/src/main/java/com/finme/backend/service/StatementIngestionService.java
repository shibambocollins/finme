package com.finme.backend.service;

import com.finme.backend.ai.AiProvider;
import com.finme.backend.ai.AllAiProvidersFailedException;
import com.finme.backend.ai.ExtractedTransaction;
import com.finme.backend.entity.BankStatement;
import com.finme.backend.entity.PaymentMethod;
import com.finme.backend.entity.SourceType;
import com.finme.backend.entity.StatementStatus;
import com.finme.backend.entity.Transaction;
import com.finme.backend.entity.TransactionDirection;
import com.finme.backend.entity.TransactionStatus;
import com.finme.backend.exception.StatementNotFoundException;
import com.finme.backend.exception.StatementProcessingException;
import com.finme.backend.repository.BankStatementRepository;
import com.finme.backend.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Statement Upload Flow (docs/03-system-design.md Sec. 4): extract locally -> redact -> AI
 * structuring/categorization -> persist -> check each new transaction against existing
 * receipt-sourced transactions for a duplicate.
 * <p>
 * Extraction runs in the background rather than inside the upload request. This is forced by
 * measured provider limits, not by preference: Groq's free tier allows 8000 tokens per minute,
 * an 80-transaction statement needs roughly twice that, and the run therefore has to pause for
 * the rate limit partway through. Measured on 2026-08-22, that statement took 99 seconds end to
 * end. Holding an HTTP request open that long fails in every direction - the browser appears
 * hung, and a deployed gateway would cut the connection before the work finished. So
 * {@link #ingest} records the statement and returns immediately; {@link #process} does the work
 * and moves the status to COMPLETE or FAILED for the client to poll.
 */
@Service
public class StatementIngestionService {

    private static final Logger log = LoggerFactory.getLogger(StatementIngestionService.class);

    private final BankStatementRepository bankStatementRepository;
    private final TransactionRepository transactionRepository;
    private final PdfExtractionService pdfExtractionService;
    private final RedactionService redactionService;
    private final AiProvider aiProvider;
    private final DuplicateDetectionService duplicateDetectionService;
    private final StatementTextChunker statementTextChunker;
    private final BackgroundRunner backgroundRunner;

    public StatementIngestionService(
            BankStatementRepository bankStatementRepository,
            TransactionRepository transactionRepository,
            PdfExtractionService pdfExtractionService,
            RedactionService redactionService,
            AiProvider aiProvider,
            DuplicateDetectionService duplicateDetectionService,
            StatementTextChunker statementTextChunker,
            BackgroundRunner backgroundRunner) {
        this.bankStatementRepository = bankStatementRepository;
        this.transactionRepository = transactionRepository;
        this.pdfExtractionService = pdfExtractionService;
        this.redactionService = redactionService;
        this.aiProvider = aiProvider;
        this.duplicateDetectionService = duplicateDetectionService;
        this.statementTextChunker = statementTextChunker;
        this.backgroundRunner = backgroundRunner;
    }

    /**
     * Records the upload and hands the work off, returning a statement in PROCESSING. The PDF is
     * read into memory here, inside the request, because the MultipartFile's backing store is
     * released once the request completes - the background thread would find it gone.
     */
    public BankStatement ingest(Long userId, MultipartFile file) {
        byte[] pdfBytes;
        try {
            pdfBytes = file.getBytes();
        } catch (IOException ex) {
            throw new StatementProcessingException(null, ex);
        }

        BankStatement statement = new BankStatement();
        statement.setUserId(userId);
        statement = bankStatementRepository.save(statement);

        Long statementId = statement.getId();
        backgroundRunner.run("statement " + statementId,
                () -> process(statementId, userId, pdfBytes));
        return statement;
    }

    /**
     * Runs the full pipeline for an already-recorded statement, leaving it COMPLETE or FAILED.
     * Synchronous and public so it can be called directly by tests, and by the background task
     * {@link #ingest} hands to BackgroundRunner.
     */
    public BankStatement process(Long statementId, Long userId, byte[] pdfBytes) {
        BankStatement statement = bankStatementRepository.findById(statementId)
                .orElseThrow(() -> new StatementProcessingException(statementId, null));

        try {
            String rawText = pdfExtractionService.extractText(new ByteArrayInputStream(pdfBytes));
            String redactedText = redactionService.redact(rawText);

            // One call per chunk, not one call for the statement. A real statement exceeds what
            // a single free-tier call can take in and give back - see StatementTextChunker for
            // the measured limits. Results are concatenated in order, so the transaction list
            // still reads top-to-bottom like the source document.
            List<String> chunks = statementTextChunker.chunk(redactedText);
            statement.setTotalChunks(chunks.size());
            statement.setProcessedChunks(0);
            statement = bankStatementRepository.save(statement);

            List<ExtractedTransaction> extracted = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                log.info("Statement {}: extracting chunk {} of {}", statementId, i + 1, chunks.size());
                extracted.addAll(aiProvider.structureTransactions(chunks.get(i)));
                statement.setProcessedChunks(i + 1);
                statement = bankStatementRepository.save(statement);
            }
            log.info("Statement {}: extracted {} transactions from {} chunk(s)",
                    statementId, extracted.size(), chunks.size());

            for (ExtractedTransaction et : extracted) {
                Transaction transaction = transactionRepository.save(toTransaction(userId, statementId, et));
                duplicateDetectionService.checkForDuplicate(transaction);
            }

            statement.setStatus(StatementStatus.COMPLETE);
            statement.setFailureReason(null);
            return bankStatementRepository.save(statement);
        } catch (IOException | AllAiProvidersFailedException ex) {
            statement.setStatus(StatementStatus.FAILED);
            // Persist why. Until this existed, a failed upload was a status with no explanation,
            // and the only way to find the real cause was to read the server log.
            statement.setFailureReason(describe(ex));
            bankStatementRepository.save(statement);
            throw new StatementProcessingException(statementId, ex);
        }
    }

    /**
     * Looks up a statement for polling, scoped to its owner. The ownership check is the point:
     * without it, any authenticated user could read another account's statement simply by
     * guessing a sequential id.
     */
    public BankStatement getForUser(Long userId, Long statementId) {
        return bankStatementRepository.findById(statementId)
                .filter(statement -> statement.getUserId().equals(userId))
                .orElseThrow(() -> new StatementNotFoundException(statementId));
    }

    /** Root-cause message, trimmed to fit the column and to stay readable in a UI. */
    private static String describe(Exception ex) {
        Throwable cause = ex;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String message = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
        return message.length() <= 480 ? message : message.substring(0, 480);
    }

    private Transaction toTransaction(Long userId, Long statementId, ExtractedTransaction et) {
        Transaction transaction = new Transaction();
        transaction.setUserId(userId);
        transaction.setSourceType(SourceType.STATEMENT);
        transaction.setSourceId(statementId);
        transaction.setDate(et.date());
        transaction.setMerchant(et.merchant());
        transaction.setAmount(et.amount());
        transaction.setCategory(et.category());
        transaction.setDescription(et.description());
        transaction.setPaymentMethod(PaymentMethod.CARD);
        transaction.setDirection(TransactionDirection.fromExtracted(et.direction()));
        transaction.setStatus(TransactionStatus.ACTIVE);
        return transaction;
    }
}
