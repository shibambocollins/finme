package com.finme.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finme.backend.exception.GlobalExceptionHandler.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;

/**
 * Overrides Spring Security's default unauthenticated-request handling, which - because
 * .oauth2Login() is registered in SecurityConfig - is a 302 redirect to
 * /oauth2/authorization/google. That's correct for a browser navigating to a protected page,
 * but every caller here is api/client.ts's fetch(), which follows the redirect transparently,
 * lands on non-JSON content, and throws inside response.json() - surfacing as a generic
 * "Failed to load X" that masks the real cause (missing/expired token). A REST API should
 * answer 401 with a body the frontend can actually read, same ErrorResponse shape
 * GlobalExceptionHandler uses everywhere else - a plain AuthenticationException never reaches
 * that controller-advice layer, since it's rejected by the security filter chain first.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorResponse body = new ErrorResponse(
                HttpStatus.UNAUTHORIZED.value(), "Authentication required or session expired", Instant.now());
        response.getWriter().write(MAPPER.writeValueAsString(body));
    }
}
