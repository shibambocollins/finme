package com.finme.backend.controller;

import com.finme.backend.dto.AuthResponse;
import com.finme.backend.dto.LoginRequest;
import com.finme.backend.dto.MessageResponse;
import com.finme.backend.dto.RegisterRequest;
import com.finme.backend.dto.ResendVerificationRequest;
import com.finme.backend.exception.InvalidVerificationTokenException;
import com.finme.backend.service.AuthService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final String frontendRedirectUri;
    private final String frontendLoginUri;

    public AuthController(
            AuthService authService,
            @Value("${app.auth.frontend-redirect-uri}") String frontendRedirectUri,
            @Value("${app.auth.frontend-login-uri}") String frontendLoginUri) {
        this.authService = authService;
        this.frontendRedirectUri = frontendRedirectUri;
        this.frontendLoginUri = frontendLoginUri;
    }

    @PostMapping("/register")
    public ResponseEntity<MessageResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /**
     * Reached by the browser clicking the link in the verification email, not an API call the
     * SPA makes directly - redirects rather than returning JSON, same pattern as the OAuth2
     * login handlers, and lands on the same /auth-callback page they use.
     */
    @GetMapping("/verify-email")
    public void verifyEmail(@RequestParam String token, HttpServletResponse response) throws IOException {
        try {
            AuthResponse auth = authService.verifyEmail(token);
            String encodedToken = URLEncoder.encode(auth.token(), StandardCharsets.UTF_8);
            response.sendRedirect(frontendRedirectUri + "?token=" + encodedToken);
        } catch (InvalidVerificationTokenException ex) {
            response.sendRedirect(frontendLoginUri + "?error=verification");
        }
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<MessageResponse> resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        return ResponseEntity.ok(authService.resendVerification(request.email()));
    }
}
