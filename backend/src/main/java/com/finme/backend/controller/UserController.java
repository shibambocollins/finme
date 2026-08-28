package com.finme.backend.controller;

import com.finme.backend.dto.ProfileResponse;
import com.finme.backend.dto.UpdateDisplayNameRequest;
import com.finme.backend.security.AuthenticatedUser;
import com.finme.backend.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The signed-in user's own profile. Deliberately not under /api/auth - that prefix is entirely
 * permitAll in SecurityConfig (register/login/verify have to be reachable before a token
 * exists), and a profile-update endpoint must never accidentally inherit that. /api/users/**
 * falls under the default anyRequest().authenticated() rule instead, same as every other
 * domain controller.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final AuthenticatedUser authenticatedUser;

    public UserController(UserService userService, AuthenticatedUser authenticatedUser) {
        this.userService = userService;
        this.authenticatedUser = authenticatedUser;
    }

    @PutMapping("/me")
    public ProfileResponse updateDisplayName(@Valid @RequestBody UpdateDisplayNameRequest request) {
        return userService.updateDisplayName(authenticatedUser.currentUserId(), request);
    }
}
