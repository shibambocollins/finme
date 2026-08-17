package com.finme.backend.auth;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Single point of access to "who is making this request" (NFR-1). Repository queries must be
 * scoped by the id this returns, never by a client-supplied id.
 */
@Component
public class AuthenticatedUser {

    public Long currentUserId() {
        return (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
