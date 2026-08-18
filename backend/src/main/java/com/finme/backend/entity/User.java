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
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    // Nullable - a user who only ever logged in via Google OAuth has no password of their own.
    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    // Google OAuth accounts are verified at creation (Google already proved ownership).
    // Password accounts start unverified until the emailed link is clicked.
    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    // Single-use, short-lived, cleared once verified - not hashed like a password, since the
    // risk model (single-use, 24h expiry) is different from a long-lived credential.
    @Column(name = "verification_token")
    private String verificationToken;

    @Column(name = "verification_token_expires_at")
    private Instant verificationTokenExpiresAt;
}
