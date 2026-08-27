package com.finme.backend.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private final JwtService jwtService = new JwtService(
            "test-secret-key-that-is-at-least-32-bytes-long-for-hs256-signing",
            3_600_000L
    );

    @Test
    void issuedTokenRoundTripsUserIdEmailAndDisplayName() {
        String token = jwtService.issueToken(42L, "user@example.com", "Jane Doe");

        assertThat(jwtService.extractUserId(token)).isEqualTo(42L);
        assertThat(jwtService.parseClaims(token).get("email", String.class)).isEqualTo("user@example.com");
        assertThat(jwtService.parseClaims(token).get("displayName", String.class)).isEqualTo("Jane Doe");
    }
}
