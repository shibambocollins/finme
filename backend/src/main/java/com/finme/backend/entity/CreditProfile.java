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
 * A user's optional credit profile (FR-2.1.1), keyed to the account they already have - no
 * separate login or identity system.
 * <p>
 * Optional means optional: a user who never opens the credit module has no row here, and every
 * read path must treat its absence as an ordinary state rather than an error.
 */
@Entity
@Table(name = "credit_profiles")
@Getter
@Setter
@NoArgsConstructor
public class CreditProfile {

    /** Documented default in docs/03-system-design.md. Stored per profile, never assumed. */
    public static final String DEFAULT_BUREAU = "Experian";
    public static final int DEFAULT_MAX_SCORE = 740;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique: one profile per user. Enforced in the database as well as in the service, because
     * two concurrent create requests would otherwise both pass a service-level "does one exist"
     * check and leave the user with two profiles and no way to tell which is authoritative.
     */
    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    /**
     * Which bureau's scale this profile is on, and that scale's maximum (FR-2.1.4). Stored
     * rather than hardcoded so a different bureau - with a different range - needs configuration
     * rather than a schema change and a migration of every existing score.
     */
    @Column(nullable = false)
    private String bureau = DEFAULT_BUREAU;

    @Column(name = "max_score", nullable = false)
    private int maxScore = DEFAULT_MAX_SCORE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
