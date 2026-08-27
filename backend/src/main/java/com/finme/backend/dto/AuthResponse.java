package com.finme.backend.dto;

public record AuthResponse(String token, String email, String displayName) {
}
