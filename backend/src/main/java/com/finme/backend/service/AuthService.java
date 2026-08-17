package com.finme.backend.service;

import com.finme.backend.dto.AuthResponse;
import com.finme.backend.dto.LoginRequest;
import com.finme.backend.dto.RegisterRequest;
import com.finme.backend.entity.User;
import com.finme.backend.exception.EmailAlreadyRegisteredException;
import com.finme.backend.exception.InvalidCredentialsException;
import com.finme.backend.repository.UserRepository;
import com.finme.backend.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyRegisteredException(request.email());
        }

        User user = new User();
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user = userRepository.save(user);

        return new AuthResponse(jwtService.issueToken(user.getId(), user.getEmail()), user.getEmail());
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

        return new AuthResponse(jwtService.issueToken(user.getId(), user.getEmail()), user.getEmail());
    }

    /**
     * Google OAuth login (docs/03-system-design.md: "both paths converge on the same token
     * scheme"). Matched by email - an existing password-registered account is authenticated
     * into directly, not duplicated; a first-time OAuth login creates a User with no password
     * hash.
     */
    public User findOrCreateOAuthUser(String email) {
        return userRepository.findByEmail(email)
                .orElseGet(() -> {
                    User user = new User();
                    user.setEmail(email);
                    return userRepository.save(user);
                });
    }
}
