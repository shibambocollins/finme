package com.finme.backend.repository;

import com.finme.backend.entity.CreditProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CreditProfileRepository extends JpaRepository<CreditProfile, Long> {

    Optional<CreditProfile> findByUserId(Long userId);

    boolean existsByUserId(Long userId);
}
