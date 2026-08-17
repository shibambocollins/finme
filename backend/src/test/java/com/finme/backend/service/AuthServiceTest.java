package com.finme.backend.service;

import com.finme.backend.dto.AuthResponse;
import com.finme.backend.dto.LoginRequest;
import com.finme.backend.dto.RegisterRequest;
import com.finme.backend.entity.User;
import com.finme.backend.exception.EmailAlreadyRegisteredException;
import com.finme.backend.exception.InvalidCredentialsException;
import com.finme.backend.repository.UserRepository;
import com.finme.backend.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final JwtService jwtService = mock(JwtService.class);
    private final AuthService authService = new AuthService(userRepository, passwordEncoder, jwtService);

    @Test
    void findOrCreateOAuthUserAuthenticatesIntoAnExistingAccountByEmail() {
        User existing = new User();
        existing.setId(1L);
        existing.setEmail("existing@example.com");
        existing.setPasswordHash("some-hash-from-password-signup");
        when(userRepository.findByEmail("existing@example.com")).thenReturn(Optional.of(existing));

        User result = authService.findOrCreateOAuthUser("existing@example.com");

        assertThat(result).isSameAs(existing);
    }

    @Test
    void findOrCreateOAuthUserCreatesANewPasswordlessUserWhenNoneExists() {
        when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(42L);
            return saved;
        });

        User result = authService.findOrCreateOAuthUser("new@example.com");

        assertThat(result.getId()).isEqualTo(42L);
        assertThat(result.getEmail()).isEqualTo("new@example.com");
        assertThat(result.getPasswordHash()).isNull();
    }

    @Test
    void loginFailsCleanlyRatherThanNpeForAGoogleOnlyAccount() {
        User googleOnlyUser = new User();
        googleOnlyUser.setEmail("google-only@example.com");
        googleOnlyUser.setPasswordHash(null);
        when(userRepository.findByEmail("google-only@example.com")).thenReturn(Optional.of(googleOnlyUser));

        assertThatThrownBy(() -> authService.login(new LoginRequest("google-only@example.com", "anyPassword")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void loginStillWorksNormallyForARegularPasswordAccount() {
        User user = new User();
        user.setId(7L);
        user.setEmail("user@example.com");
        user.setPasswordHash("hashed");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "hashed")).thenReturn(true);
        when(jwtService.issueToken(7L, "user@example.com")).thenReturn("token123");

        AuthResponse response = authService.login(new LoginRequest("user@example.com", "password123"));

        assertThat(response.token()).isEqualTo("token123");
    }

    @Test
    void registerRejectsADuplicateEmail() {
        when(userRepository.existsByEmail("dup@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(new RegisterRequest("dup@example.com", "password123")))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
    }
}
