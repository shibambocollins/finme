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

@Entity
@Table(name = "credit_profiles")
@Getter
@Setter
@NoArgsConstructor
public class CreditProfile {

    public static final String DEFAULT_BUREAU = "Experian";
    public static final int DEFAULT_MAX_SCORE = 740;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(nullable = false)
    private String bureau = DEFAULT_BUREAU;

    @Column(name = "max_score", nullable = false)
    private int maxScore = DEFAULT_MAX_SCORE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
