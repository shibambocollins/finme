package com.finme.backend.service;

import com.finme.backend.dto.ProfileResponse;
import com.finme.backend.dto.UpdateDisplayNameRequest;
import com.finme.backend.entity.User;
import com.finme.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserService userService = new UserService(userRepository);

    @Test
    void updateDisplayNameStripsAndSavesTheNewName() {
        User user = new User();
        user.setId(1L);
        user.setEmail("user@example.com");
        user.setDisplayName("Old Name");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        ProfileResponse result = userService.updateDisplayName(1L, new UpdateDisplayNameRequest("  New Name  "));

        assertThat(result.email()).isEqualTo("user@example.com");
        assertThat(result.displayName()).isEqualTo("New Name");
        assertThat(user.getDisplayName()).isEqualTo("New Name");
        verify(userRepository).save(user);
    }

    @Test
    void updateDisplayNameThrowsForAUserIdThatDoesNotExist() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateDisplayName(99L, new UpdateDisplayNameRequest("Name")))
                .isInstanceOf(java.util.NoSuchElementException.class);
    }
}
