package com.ecoverse.repository;

import com.ecoverse.model.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /**
     * Find a refresh token by its SHA-256 hash.
     * This is the primary lookup method — always hash the incoming token before querying.
     */
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Legacy lookup by plaintext token. Kept for transition compatibility only.
     * New code should use findByTokenHash() instead.
     */
    Optional<RefreshToken> findByToken(String token);

    List<RefreshToken> findByUserId(Long userId);

    /**
     * Revoke ALL refresh tokens for a user by setting revoked=true.
     * This is an UPDATE (not DELETE) to support reuse detection:
     * if a revoked token is reused, we know the token was compromised.
     */
    @Modifying
    @Query("UPDATE RefreshToken r SET r.revoked = true WHERE r.userId = :userId AND r.revoked = false")
    int revokeAllByUserId(@Param("userId") Long userId);

    /**
     * Delete all refresh tokens for a user. Used during account deletion.
     */
    @Modifying
    @Query("DELETE FROM RefreshToken r WHERE r.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);

    long countByUserIdAndRevokedFalse(Long userId);
}
