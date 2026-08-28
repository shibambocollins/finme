package com.finme.backend.repository;

import com.finme.backend.entity.Receipt;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReceiptRepository extends JpaRepository<Receipt, Long> {

    /** Bulk removal for "clear my data" / account deletion (UserService). */
    void deleteByUserId(Long userId);
}
