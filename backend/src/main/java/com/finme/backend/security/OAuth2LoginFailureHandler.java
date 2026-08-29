package com.finme.backend.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

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
