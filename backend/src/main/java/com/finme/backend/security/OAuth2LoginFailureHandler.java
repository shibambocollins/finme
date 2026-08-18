package com.finme.backend.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Spring Security's default OAuth2 failure behavior redirects to /login?error, which doesn't
 * exist in this SPA - send the browser back to the real frontend login page instead.
 */
@Component
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {

    private final String frontendLoginUri;

    public OAuth2LoginFailureHandler(@Value("${app.auth.frontend-login-uri}") String frontendLoginUri) {
        this.frontendLoginUri = frontendLoginUri;
    }

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException {
        response.sendRedirect(frontendLoginUri + "?error=oauth2");
    }
}
