package com.finme.backend.service;

import com.finme.backend.entity.PaymentMethod;
import com.finme.backend.entity.SourceType;
import com.finme.backend.entity.Transaction;
import com.finme.backend.entity.TransactionStatus;
import com.finme.backend.repository.TransactionRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * docs/03-system-design.md: on ingestion of a new statement transaction, check card-payment
 * receipt-sourced transactions for the same user within a date window for an exact amount
 * match; on match, flag the receipt-sourced record SUPERSEDED and keep the statement record
 * authoritative. Never runs against cash-sourced transactions - enforced here by only ever
 * querying paymentMethod=CARD on the receipt side, not by trusting the caller.
 *
 * One direction only: called when a new STATEMENT transaction lands, looking backward at
 * existing RECEIPT transactions - not the reverse. Receipts are typically uploaded promptly;
 * statements arrive later (monthly), so by the time a statement transaction is ingested any
 * matching receipt already exists. No symmetric receipt-side check.
 */
@Service
public class DuplicateDetectionService {

    // Tunable assumption, not derived from a spec value - docs/06-risk-register.md R4 already
    // accepts false-positive/negative edge cases here as a known limitation.
    private static final int DATE_WINDOW_DAYS = 3;

    private final TransactionRepository transactionRepository;

    public DuplicateDetectionService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    public void checkForDuplicate(Transaction newStatementTransaction) {
        if (newStatementTransaction.getPaymentMethod() != PaymentMethod.CARD) {
            return;
        }

        LocalDate date = newStatementTransaction.getDate();
        List<Transaction> matches = transactionRepository
                .findByUserIdAndSourceTypeAndPaymentMethodAndStatusAndAmountAndDateBetween(
                        newStatementTransaction.getUserId(),
                        SourceType.RECEIPT,
                        PaymentMethod.CARD,
                        TransactionStatus.ACTIVE,
                        newStatementTransaction.getAmount(),
                        date.minusDays(DATE_WINDOW_DAYS),
                        date.plusDays(DATE_WINDOW_DAYS));

        for (Transaction receiptTransaction : matches) {
            receiptTransaction.setStatus(TransactionStatus.SUPERSEDED);
            receiptTransaction.setSupersededBy(newStatementTransaction.getId());
            transactionRepository.save(receiptTransaction);
        }
    }
}
