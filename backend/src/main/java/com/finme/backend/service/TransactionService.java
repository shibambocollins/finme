package com.finme.backend.service;

import com.finme.backend.dto.UpdateTransactionRequest;
import com.finme.backend.entity.SourceType;
import com.finme.backend.entity.Transaction;
import com.finme.backend.entity.TransactionDirection;
import com.finme.backend.entity.TransactionStatus;
import com.finme.backend.exception.TransactionNotFoundException;
import com.finme.backend.repository.TransactionRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * Lets a user correct or remove a transaction, and search/filter their own list.
 * <p>
 * Filtering is done in Java over one repository fetch, matching how DashboardService and
 * SpendAnalysisService already work in this codebase - a personal finance app's transaction
 * count is small enough that a second SQL query per filter combination would be complexity
 * without a measurable benefit.
 */
@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;

    public TransactionService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    /**
     * @param category   exact match, case-insensitive - null/blank means "any category"
     * @param sourceType null means "any source"
     * @param direction  null means "any direction"
     * @param from       inclusive lower bound on date, null means unbounded
     * @param to         inclusive upper bound on date, null means unbounded
     * @param query      case-insensitive substring match against merchant or description
     */
    public List<Transaction> search(Long userId, String category, SourceType sourceType,
                                    TransactionDirection direction, LocalDate from, LocalDate to,
                                    String query) {
        String normalizedCategory = blankToNull(category);
        String normalizedQuery = blankToNull(query);

        return transactionRepository
                .findByUserIdAndStatusOrderByDateDesc(userId, TransactionStatus.ACTIVE)
                .stream()
                .filter(t -> normalizedCategory == null
                        || normalizedCategory.equalsIgnoreCase(t.getCategory()))
                .filter(t -> sourceType == null || sourceType == t.getSourceType())
                .filter(t -> direction == null || direction == t.getDirection())
                .filter(t -> from == null || !t.getDate().isBefore(from))
                .filter(t -> to == null || !t.getDate().isAfter(to))
                .filter(t -> normalizedQuery == null || matches(t, normalizedQuery))
                .toList();
    }

    private static boolean matches(Transaction transaction, String query) {
        String needle = query.toLowerCase(Locale.ROOT);
        String merchant = transaction.getMerchant() == null ? "" : transaction.getMerchant().toLowerCase(Locale.ROOT);
        String description = transaction.getDescription() == null ? "" : transaction.getDescription().toLowerCase(Locale.ROOT);
        return merchant.contains(needle) || description.contains(needle);
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.strip();
    }

    /**
     * Applies a user's correction. sourceType, sourceId and status are untouched - they are
     * system-managed provenance, not something a correction form edits.
     */
    public Transaction update(Long userId, Long transactionId, UpdateTransactionRequest request) {
        Transaction transaction = requireOwned(userId, transactionId);
        transaction.setDate(request.date());
        transaction.setMerchant(request.merchant().strip());
        transaction.setAmount(request.amount());
        transaction.setDirection(request.direction());
        transaction.setCategory(request.category().strip());
        transaction.setDescription(request.description() == null ? null : request.description().strip());
        transaction.setPaymentMethod(request.paymentMethod());
        return transactionRepository.save(transaction);
    }

    /**
     * Removes a transaction outright rather than marking it SUPERSEDED - that status is
     * DuplicateDetectionService's system-level "a statement line replaced this receipt" signal,
     * and a user deleting an entry they made a mistake on is a different action with a
     * different meaning. No re-run of duplicate detection follows a delete or an edit; that
     * matching only ever runs once, at statement ingestion time.
     */
    public void delete(Long userId, Long transactionId) {
        transactionRepository.delete(requireOwned(userId, transactionId));
    }

    private Transaction requireOwned(Long userId, Long transactionId) {
        return transactionRepository.findById(transactionId)
                .filter(t -> t.getUserId().equals(userId))
                .orElseThrow(() -> new TransactionNotFoundException(transactionId));
    }
}
