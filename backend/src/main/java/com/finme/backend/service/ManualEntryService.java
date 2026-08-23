package com.finme.backend.service;

import com.finme.backend.ai.AiProvider;
import com.finme.backend.ai.AllAiProvidersFailedException;
import com.finme.backend.ai.ExtractedTransaction;
import com.finme.backend.entity.PaymentMethod;
import com.finme.backend.entity.SourceType;
import com.finme.backend.entity.Transaction;
import com.finme.backend.entity.TransactionDirection;
import com.finme.backend.entity.TransactionStatus;
import com.finme.backend.exception.ManualEntryException;
import com.finme.backend.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Logs a transaction from a plain-language description (FR-1.5.1, FR-1.5.2).
 * <p>
 * This exists for the spending that leaves no trace anywhere else - cash purchases that produce
 * no receipt and never appear on a statement. Those are invisible to both other pipelines, so
 * without this the dashboard's totals are wrong by exactly the amount of a user's cash spending
 * and nothing in the app would ever reveal it.
 * <p>
 * Unlike the statement path this runs synchronously: it is one short sentence and one provider
 * call, so the answer is back in seconds and the user is waiting to see the transaction they
 * just described.
 */
@Service
public class ManualEntryService {

    private static final Logger log = LoggerFactory.getLogger(ManualEntryService.class);

    private final TransactionRepository transactionRepository;
    private final AiProvider aiProvider;
    private final Clock clock;

    /**
     * The clock is injected so "today" is a value this service is given rather than one it reads
     * from the environment - which is what lets tests assert on relative-date handling
     * ("yesterday") deterministically instead of hoping the suite does not run near midnight.
     */
    public ManualEntryService(TransactionRepository transactionRepository, AiProvider aiProvider, Clock clock) {
        this.transactionRepository = transactionRepository;
        this.aiProvider = aiProvider;
        this.clock = clock;
    }

    public List<Transaction> log(Long userId, String naturalLanguage) {
        if (naturalLanguage == null || naturalLanguage.isBlank()) {
            throw new ManualEntryException("Describe what you spent, for example: lunch R150 cash today");
        }

        LocalDate today = LocalDate.now(clock);
        List<ExtractedTransaction> extracted;
        try {
            extracted = aiProvider.parseManualEntry(naturalLanguage.strip(), today);
        } catch (AllAiProvidersFailedException ex) {
            log.warn("Manual entry failed for user {}: {}", userId, ex.getMessage());
            throw new ManualEntryException("Could not process that right now - please try again shortly");
        }

        if (extracted.isEmpty()) {
            // Better to say nothing was understood than to store a guess. A wrong transaction is
            // harder for the user to notice and undo than an entry that plainly did not work.
            throw new ManualEntryException(
                    "Could not find a purchase in that. Try something like: lunch R150 cash today");
        }

        List<Transaction> saved = new ArrayList<>();
        for (ExtractedTransaction et : extracted) {
            saved.add(transactionRepository.save(toTransaction(userId, et, today)));
        }
        return saved;
    }

    private Transaction toTransaction(Long userId, ExtractedTransaction et, LocalDate today) {
        Transaction transaction = new Transaction();
        transaction.setUserId(userId);
        transaction.setSourceType(SourceType.MANUAL);
        // No sourceId: a manual entry has no uploaded document behind it to point at.
        transaction.setDate(et.date() == null ? today : et.date());
        transaction.setMerchant(et.merchant());
        transaction.setAmount(et.amount());
        transaction.setCategory(et.category());
        transaction.setDescription(et.description());
        transaction.setPaymentMethod(parsePaymentMethod(et.paymentMethod()));
        transaction.setDirection(TransactionDirection.fromExtracted(et.direction()));
        transaction.setStatus(TransactionStatus.ACTIVE);
        return transaction;
    }

    /**
     * Defaults to CASH rather than UNKNOWN. This feature is for purchases that leave no other
     * record, and payment method is not cosmetic here - DuplicateDetectionService only ever
     * matches CARD transactions, so marking a genuine cash purchase as CARD would expose it to
     * being superseded by an unrelated statement line of the same amount (FR-1.6.2).
     */
    private static PaymentMethod parsePaymentMethod(String raw) {
        if (raw == null) {
            return PaymentMethod.CASH;
        }
        try {
            return PaymentMethod.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return PaymentMethod.CASH;
        }
    }
}
