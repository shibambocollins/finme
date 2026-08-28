package com.finme.backend.service;

import com.finme.backend.dto.ProfileResponse;
import com.finme.backend.dto.UpdateDisplayNameRequest;
import com.finme.backend.entity.User;
import com.finme.backend.repository.UserRepository;
import org.springframework.stereotype.Service;

/**
 * Account-settings operations for the signed-in user managing their own profile - distinct from
 * {@link AuthService}, which only ever handles proving who someone is (register/login/OAuth/
 * verify), never what they can change about their account afterwards.
 */
@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * No JWT re-issue on a name change: the JWT's displayName claim only ever existed to hand
     * the frontend a name at OAuth-callback time without a round trip (see JwtService) - nothing
     * server-side reads it back. The frontend already holds an authenticated session here and
     * updates its own stored displayName straight from this response, so a stale claim in the
     * still-valid token never surfaces anywhere.
     */
    public ProfileResponse updateDisplayName(Long userId, UpdateDisplayNameRequest request) {
        User user = userRepository.findById(userId).orElseThrow();
        user.setDisplayName(request.displayName().strip());
        userRepository.save(user);
        return new ProfileResponse(user.getEmail(), user.getDisplayName());
    }
}
