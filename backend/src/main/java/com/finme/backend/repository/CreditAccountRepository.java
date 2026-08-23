package com.finme.backend.repository;

import com.finme.backend.entity.CreditAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CreditAccountRepository extends JpaRepository<CreditAccount, Long> {

    List<CreditAccount> findByCreditProfileIdOrderByAccountNameAsc(Long creditProfileId);

    void deleteByCreditProfileId(Long creditProfileId);
}
