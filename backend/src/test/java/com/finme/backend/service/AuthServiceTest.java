package com.finme.backend.service;

import com.finme.backend.dto.AuthResponse;
import com.finme.backend.dto.LoginRequest;
import com.finme.backend.dto.MessageResponse;
import com.finme.backend.dto.RegisterRequest;
import com.finme.backend.entity.User;
import com.finme.backend.exception.EmailAlreadyRegisteredException;
import com.finme.backend.exception.EmailNotVerifiedException;
import com.finme.backend.exception.InvalidCredentialsException;
import com.finme.backend.exception.InvalidVerificationTokenException;
import com.finme.backend.repository.UserRepository;
import com.finme.backend.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final JwtService jwtService = mock(JwtService.class);
    private final EmailService emailService = mock(EmailService.class);
    private final AuthService authService =
            new AuthService(userRepository, passwordEncoder, jwtService, emailService, 24L);

    @Test
    void findOrCreateOAuthUserAuthenticatesIntoAnExistingVerifiedAccountByEmail() {
        User existing = new User();
        existing.setId(1L);
        existing.setEmail("existing@example.com");
        existing.setPasswordHash("some-hash-from-password-signup");
        existing.setEmailVerified(true);
        when(userRepository.findByEmail("existing@example.com")).thenReturn(Optional.of(existing));

        User result = authService.findOrCreateOAuthUser("existing@example.com");

        assertThat(result).isSameAs(existing);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void findOrCreateOAuthUserVerifiesAPreviouslyUnverifiedExistingAccount() {
        User existing = new User();
        existing.setId(2L);
        existing.setEmail("was-unverified@example.com");
        existing.setEmailVerified(false);
        when(userRepository.findByEmail("was-unverified@example.com")).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = authService.findOrCreateOAuthUser("was-unverified@example.com");

        assertThat(result.isEmailVerified()).isTrue();
        verify(userRepository).save(existing);
    }

    @Test
    void findOrCreateOAuthUserCreatesANewVerifiedPasswordlessUserWhenNoneExists() {
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
        assertThat(result.isEmailVerified()).isTrue();
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
    void loginRejectsAnUnverifiedAccountEvenWithTheCorrectPassword() {
        User unverified = new User();
        unverified.setId(9L);
        unverified.setEmail("unverified@example.com");
        unverified.setPasswordHash("hashed");
        unverified.setEmailVerified(false);
        when(userRepository.findByEmail("unverified@example.com")).thenReturn(Optional.of(unverified));
        when(passwordEncoder.matches("password123", "hashed")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest("unverified@example.com", "password123")))
                .isInstanceOf(EmailNotVerifiedException.class);
    }

    @Test
    void loginStillWorksNormallyForAVerifiedPasswordAccount() {
        User user = new User();
        user.setId(7L);
        user.setEmail("user@example.com");
        user.setPasswordHash("hashed");
        user.setEmailVerified(true);
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

    @Test
    void registerCreatesAnUnverifiedUserAndSendsAVerificationEmail() {
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MessageResponse response = authService.register(new RegisterRequest("new@example.com", "password123"));

        assertThat(response.message()).isNotBlank();
        verify(emailService).sendVerificationEmail(eq("new@example.com"), anyString());
    }

    @Test
    void registerStillSucceedsEvenIfSendingTheVerificationEmailFails() {
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new RuntimeException("SMTP is down")).when(emailService).sendVerificationEmail(anyString(), anyString());

        MessageResponse response = authService.register(new RegisterRequest("new@example.com", "password123"));

        assertThat(response.message()).isNotBlank();
    }

    @Test
    void verifyEmailSucceedsForAValidToken() {
        User user = new User();
        user.setId(3L);
        user.setEmail("verify-me@example.com");
        user.setVerificationToken("valid-token");
        user.setVerificationTokenExpiresAt(Instant.now().plus(1, ChronoUnit.HOURS));
        when(userRepository.findByVerificationToken("valid-token")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtService.issueToken(3L, "verify-me@example.com")).thenReturn("issued-token");

        AuthResponse response = authService.verifyEmail("valid-token");

        assertThat(response.token()).isEqualTo("issued-token");
        assertThat(user.isEmailVerified()).isTrue();
        assertThat(user.getVerificationToken()).isNull();
    }

    @Test
    void verifyEmailFailsForAnUnknownToken() {
        when(userRepository.findByVerificationToken("bogus")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.verifyEmail("bogus"))
                .isInstanceOf(InvalidVerificationTokenException.class);
    }

    @Test
    void verifyEmailFailsForAnExpiredToken() {
        User user = new User();
        user.setEmail("expired@example.com");
        user.setVerificationToken("expired-token");
        user.setVerificationTokenExpiresAt(Instant.now().minus(1, ChronoUnit.HOURS));
        when(userRepository.findByVerificationToken("expired-token")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.verifyEmail("expired-token"))
                .isInstanceOf(InvalidVerificationTokenException.class);
    }

    @Test
    void resendVerificationSendsANewTokenForAnUnverifiedUser() {
        User user = new User();
        user.setEmail("resend-me@example.com");
        user.setEmailVerified(false);
        when(userRepository.findByEmail("resend-me@example.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MessageResponse response = authService.resendVerification("resend-me@example.com");

        assertThat(response.message()).isNotBlank();
        verify(emailService).sendVerificationEmail(eq("resend-me@example.com"), anyString());
    }

    @Test
    void resendVerificationDoesNothingForAnAlreadyVerifiedUserButReturnsTheSameGenericMessage() {
        User user = new User();
        user.setEmail("already-verified@example.com");
        user.setEmailVerified(true);
        when(userRepository.findByEmail("already-verified@example.com")).thenReturn(Optional.of(user));

        MessageResponse verifiedResponse = authService.resendVerification("already-verified@example.com");
        MessageResponse unknownEmailResponse = authService.resendVerification("no-such-user@example.com");

        assertThat(verifiedResponse.message()).isEqualTo(unknownEmailResponse.message());
        verify(emailService, never()).sendVerificationEmail(anyString(), anyString());
    }
}
