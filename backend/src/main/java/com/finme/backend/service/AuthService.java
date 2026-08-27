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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String GENERIC_RESEND_MESSAGE =
            "If that email is registered and not yet verified, a new verification link has been sent.";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailService emailService;
    private final long tokenExpiryHours;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            EmailService emailService,
            @Value("${app.email-verification.token-expiry-hours}") long tokenExpiryHours) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.emailService = emailService;
        this.tokenExpiryHours = tokenExpiryHours;
    }

    public MessageResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyRegisteredException(request.email());
        }

        User user = new User();
        user.setEmail(request.email());
        user.setDisplayName(request.displayName().strip());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        issueVerificationToken(user);
        userRepository.save(user);

        sendVerificationEmailQuietly(user);

        return new MessageResponse("Registered. Check your email to verify your account before logging in.");
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(InvalidCredentialsException::new);

        // A Google-only account has no password hash to check against - reject cleanly
        // rather than NPE-ing inside passwordEncoder.matches.
        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        if (!user.isEmailVerified()) {
            throw new EmailNotVerifiedException();
        }

        return new AuthResponse(
                jwtService.issueToken(user.getId(), user.getEmail(), user.getDisplayName()),
                user.getEmail(),
                user.getDisplayName());
    }

    /** Clicked from the emailed link - issues a JWT immediately, no separate login step needed. */
    public AuthResponse verifyEmail(String token) {
        User user = userRepository.findByVerificationToken(token)
                .orElseThrow(InvalidVerificationTokenException::new);

        if (user.getVerificationTokenExpiresAt() == null
                || user.getVerificationTokenExpiresAt().isBefore(Instant.now())) {
            throw new InvalidVerificationTokenException();
        }

        user.setEmailVerified(true);
        user.setVerificationToken(null);
        user.setVerificationTokenExpiresAt(null);
        userRepository.save(user);

        return new AuthResponse(
                jwtService.issueToken(user.getId(), user.getEmail(), user.getDisplayName()),
                user.getEmail(),
                user.getDisplayName());
    }

    public MessageResponse resendVerification(String email) {
        userRepository.findByEmail(email)
                .filter(user -> !user.isEmailVerified())
                .ifPresent(user -> {
                    issueVerificationToken(user);
                    userRepository.save(user);
                    sendVerificationEmailQuietly(user);
                });

        // Same message regardless of whether the email exists or was already verified - this
        // endpoint must not be usable to enumerate registered accounts.
        return new MessageResponse(GENERIC_RESEND_MESSAGE);
    }

    /**
     * Google OAuth login (docs/03-system-design.md: "both paths converge on the same token
     * scheme"). Matched by email - an existing password-registered account is authenticated
     * into directly, not duplicated; a first-time OAuth login creates a User with no password
     * hash. Google has already proven ownership of the email, so the account is verified
     * immediately either way - including flipping a previously-unverified password account,
     * since a successful Google login is equally strong proof of ownership.
     *
     * @param googleDisplayName the Google account's own display name, used only to backfill a
     *                          user who does not already have one - a Google login must never
     *                          overwrite a name the user set at registration, or one an earlier
     *                          Google login already backfilled.
     */
    public User findOrCreateOAuthUser(String email, String googleDisplayName) {
        User user = userRepository.findByEmail(email)
                .orElseGet(() -> {
                    User newUser = new User();
                    newUser.setEmail(email);
                    return newUser;
                });

        boolean changed = false;
        if (!user.isEmailVerified()) {
            user.setEmailVerified(true);
            changed = true;
        }
        if ((user.getDisplayName() == null || user.getDisplayName().isBlank())
                && googleDisplayName != null && !googleDisplayName.isBlank()) {
            user.setDisplayName(googleDisplayName.strip());
            changed = true;
        }
        if (changed) {
            user = userRepository.save(user);
        }

        return user;
    }

    private void issueVerificationToken(User user) {
        user.setVerificationToken(UUID.randomUUID().toString());
        user.setVerificationTokenExpiresAt(Instant.now().plus(tokenExpiryHours, ChronoUnit.HOURS));
    }

    private void sendVerificationEmailQuietly(User user) {
        try {
            emailService.sendVerificationEmail(user.getEmail(), user.getVerificationToken());
        } catch (Exception ex) {
            // The account still exists and resend-verification covers this - a transient SMTP
            // hiccup shouldn't fail registration outright.
            log.warn("Failed to send verification email to {}: {}", user.getEmail(), ex.getMessage());
        }
    }
}
