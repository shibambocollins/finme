package com.finme.backend.controller;

import com.finme.backend.dto.ConfirmAccountActionRequest;
import com.finme.backend.dto.ProfileResponse;
import com.finme.backend.dto.UpdateDisplayNameRequest;
import com.finme.backend.security.AuthenticatedUser;
import com.finme.backend.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The signed-in user's own profile and data. Deliberately not under /api/auth - that prefix is
 * entirely permitAll in SecurityConfig (register/login/verify have to be reachable before a
 * token exists), and these must never accidentally inherit that. /api/users/** falls under the
 * default anyRequest().authenticated() rule instead, same as every other domain controller.
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

    /** Keeps the account and login; removes every transaction, statement, receipt, budget, and
     *  the credit profile if one exists. */
    @DeleteMapping("/me/data")
    public ResponseEntity<Void> clearFinancialData(@Valid @RequestBody ConfirmAccountActionRequest request) {
        userService.clearFinancialData(authenticatedUser.currentUserId(), request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteAccount(@Valid @RequestBody ConfirmAccountActionRequest request) {
        userService.deleteAccount(authenticatedUser.currentUserId(), request);
        return ResponseEntity.noContent().build();
    }
}
