package com.finme.backend.repository;

import com.finme.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<User> findByVerificationToken(String verificationToken);

    /**
     * Recipients for the weekly analysis. Verified only: an unverified address has not been
     * proven to belong to the person who typed it, and sending spending summaries to it would
     * mean mailing someone's finances to an address nobody confirmed.
     */
    List<User> findByEmailVerifiedTrue();
}
