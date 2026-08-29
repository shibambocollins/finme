package com.finme.backend.repository;

import com.finme.backend.entity.Receipt;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReceiptRepository extends JpaRepository<Receipt, Long> {

    void deleteByUserId(Long userId);
}
