package com.finme.backend.repository;

import com.finme.backend.entity.BankStatement;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BankStatementRepository extends JpaRepository<BankStatement, Long> {

    /** Bulk removal for "clear my data" / account deletion (UserService). */
    void deleteByUserId(Long userId);
}
