package com.finme.backend.security;

import com.finme.backend.entity.User;
import com.finme.backend.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Bridges a completed Google OAuth2 login into the same JWT scheme AuthService issues for
 * password login (docs/03-system-design.md). Redirect-based, not JSON, since this handler
 * runs at the tail of a full-page browser redirect chain (Google -> backend callback), not an
 * API call the SPA made directly.
 */
@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final AuthService authService;
    private final JwtService jwtService;
    private final String frontendRedirectUri;

    public OAuth2LoginSuccessHandler(
            AuthService authService,
            JwtService jwtService,
            @Value("${app.oauth2.frontend-redirect-uri}") String frontendRedirectUri) {
        this.authService = authService;
        this.jwtService = jwtService;
        this.frontendRedirectUri = frontendRedirectUri;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException {
        OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
        String email = oauth2User.getAttribute("email");

        if (email == null || email.isBlank()) {
            response.sendRedirect(frontendRedirectUri + "?error=no_email");
            return;
        }

        User user = authService.findOrCreateOAuthUser(email);
        String token = jwtService.issueToken(user.getId(), user.getEmail());
        String encodedToken = URLEncoder.encode(token, StandardCharsets.UTF_8);

        response.sendRedirect(frontendRedirectUri + "?token=" + encodedToken);
    }
}
