package com.finme.backend.repository;

import com.finme.backend.entity.CreditSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CreditSnapshotRepository extends JpaRepository<CreditSnapshot, Long> {

    /** Newest first - the head of this list is the user's current score. */
    List<CreditSnapshot> findByCreditProfileIdOrderByRecordedAtDesc(Long creditProfileId);

    Optional<CreditSnapshot> findFirstByCreditProfileIdOrderByRecordedAtDesc(Long creditProfileId);
}
