package com.finme.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A dated record of the user's credit score (FR-2.4.1) - one row per update, never overwritten.
 * <p>
 * Append-only is the whole point. Progress over time is the thing the credit module is for, and
 * a single mutable "current score" column would answer "what is it now" while destroying the
 * only evidence of whether it is improving. Iteration 9's previous-vs-current comparison reads
 * these rows.
 */
@Entity
@Table(name = "credit_snapshots")
@Getter
@Setter
@NoArgsConstructor
public class CreditSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "credit_profile_id", nullable = false)
    private Long creditProfileId;

    @Column(nullable = false)
    private int score;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt = Instant.now();
}
