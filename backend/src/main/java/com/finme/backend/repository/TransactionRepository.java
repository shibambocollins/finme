package com.finme.backend.repository;

import com.finme.backend.entity.Transaction;
import com.finme.backend.entity.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findByUserIdAndStatusOrderByDateDesc(Long userId, TransactionStatus status);
}
