package com.finme.backend.repository;

import com.finme.backend.entity.PaymentMethod;
import com.finme.backend.entity.SourceType;
import com.finme.backend.entity.Transaction;
import com.finme.backend.entity.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findByUserIdAndStatusOrderByDateDesc(Long userId, TransactionStatus status);

    /** Duplicate-detection lookup (docs/03-system-design.md): card-payment receipt-sourced
     *  transactions only - never cash - within a date window, exact amount match. */
    List<Transaction> findByUserIdAndSourceTypeAndPaymentMethodAndStatusAndAmountAndDateBetween(
            Long userId,
            SourceType sourceType,
            PaymentMethod paymentMethod,
            TransactionStatus status,
            BigDecimal amount,
            LocalDate startDate,
            LocalDate endDate);

    /** Bulk removal for "clear my data" / account deletion (UserService) - every other delete
     *  path in this app removes one owned row at a time; this is the one deliberate exception. */
    void deleteByUserId(Long userId);
}
