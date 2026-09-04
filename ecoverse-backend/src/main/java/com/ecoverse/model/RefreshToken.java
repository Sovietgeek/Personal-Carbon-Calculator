package com.ecoverse.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "refresh_tokens")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Plaintext token value — kept only for transition compatibility.
     * New tokens will store only the hash. This column will be removed in a future migration.
     */
    @Column(nullable = true)
    private String token;

    /**
     * SHA-256 hash of the refresh token. This is the primary lookup field.
     * Stored as lowercase hex (64 characters).
     */
    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "expiry_date", nullable = false)
    private LocalDateTime expiryDate;

    @Builder.Default
    @Column(nullable = false)
    private Boolean revoked = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /**
     * Tracks when this refresh token was last used for auditing.
     */
    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
