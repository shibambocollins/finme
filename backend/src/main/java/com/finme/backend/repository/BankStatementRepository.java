package com.finme.backend.repository;

import com.finme.backend.entity.BankStatement;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BankStatementRepository extends JpaRepository<BankStatement, Long> {

    void deleteByUserId(Long userId);
}
