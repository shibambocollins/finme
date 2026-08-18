package com.finme.backend.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Deliberately separate from SecurityConfig: AuthService depends on PasswordEncoder, and
 * SecurityConfig now depends (via OAuth2LoginSuccessHandler) on AuthService - keeping the
 * PasswordEncoder bean in SecurityConfig itself would make that a circular dependency
 * (SecurityConfig's own instantiation would require AuthService, which requires a bean only
 * SecurityConfig's instance can produce).
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
