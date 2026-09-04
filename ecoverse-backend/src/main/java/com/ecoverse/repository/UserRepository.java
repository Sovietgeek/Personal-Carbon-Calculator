package com.ecoverse.repository;

import com.ecoverse.model.Role;
import com.ecoverse.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<User> findByEmailVerificationToken(String token);

    Optional<User> findByPasswordResetToken(String token);

    // ===== ADMIN QUERIES =====

    Page<User> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<User> findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
            String name, String email, Pageable pageable);

    Page<User> findByRole(Role role, Pageable pageable);

    long countByRole(Role role);

    long countByEnabled(boolean enabled);

    Page<User> findByEnabledOrderByCreatedAtDesc(boolean enabled, Pageable pageable);

    Page<User> findByRoleAndEnabledOrderByCreatedAtDesc(Role role, boolean enabled, Pageable pageable);

    @Query("SELECT COUNT(u) FROM User u WHERE u.createdAt >= :since")
    long countCreatedSince(@Param("since") java.time.LocalDateTime since);
}
