package com.finme.backend.service;

import com.finme.backend.ai.AiProvider;
import com.finme.backend.ai.AllAiProvidersFailedException;
import com.finme.backend.ai.ExtractedTransaction;
import com.finme.backend.entity.BankStatement;
import com.finme.backend.entity.PaymentMethod;
import com.finme.backend.entity.SourceType;
import com.finme.backend.entity.StatementStatus;
import com.finme.backend.entity.Transaction;
import com.finme.backend.entity.TransactionStatus;
import com.finme.backend.exception.StatementProcessingException;
import com.finme.backend.geocoding.TransactionGeocoder;
import com.finme.backend.repository.BankStatementRepository;
import com.finme.backend.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * Statement Upload Flow (docs/03-system-design.md Sec. 4): extract locally -> redact -> AI
 * structuring/categorization -> persist -> check each new transaction against existing
 * receipt-sourced transactions for a duplicate.
 */
@Service
public class StatementIngestionService {

    private final BankStatementRepository bankStatementRepository;
    private final TransactionRepository transactionRepository;
    private final PdfExtractionService pdfExtractionService;
    private final RedactionService redactionService;
    private final AiProvider aiProvider;
    private final DuplicateDetectionService duplicateDetectionService;
    private final TransactionGeocoder transactionGeocoder;

    public StatementIngestionService(
            BankStatementRepository bankStatementRepository,
            TransactionRepository transactionRepository,
            PdfExtractionService pdfExtractionService,
            RedactionService redactionService,
            AiProvider aiProvider,
            DuplicateDetectionService duplicateDetectionService,
            TransactionGeocoder transactionGeocoder) {
        this.bankStatementRepository = bankStatementRepository;
        this.transactionRepository = transactionRepository;
        this.pdfExtractionService = pdfExtractionService;
        this.redactionService = redactionService;
        this.aiProvider = aiProvider;
        this.duplicateDetectionService = duplicateDetectionService;
        this.transactionGeocoder = transactionGeocoder;
    }

    public BankStatement ingest(Long userId, MultipartFile file) {
        BankStatement statement = new BankStatement();
        statement.setUserId(userId);
        statement = bankStatementRepository.save(statement);

        try {
            String rawText = pdfExtractionService.extractText(file.getInputStream());
            String redactedText = redactionService.redact(rawText);
            List<ExtractedTransaction> extracted = aiProvider.structureTransactions(redactedText);

            for (ExtractedTransaction et : extracted) {
                Transaction transaction = transactionRepository.save(toTransaction(userId, statement.getId(), et));
                duplicateDetectionService.checkForDuplicate(transaction);
            }

            statement.setStatus(StatementStatus.COMPLETE);
        } catch (IOException | AllAiProvidersFailedException ex) {
            statement.setStatus(StatementStatus.FAILED);
            bankStatementRepository.save(statement);
            throw new StatementProcessingException(statement.getId(), ex);
        }

        return bankStatementRepository.save(statement);
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
        transaction.setStatus(TransactionStatus.ACTIVE);
        transactionGeocoder.enrich(transaction, et);
        return transaction;
    }
}
