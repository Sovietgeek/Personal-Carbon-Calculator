package com.ecoverse.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    private String country;

    // ===== LOCATION FIELDS (V18 Migration) =====

    private String city;

    private String state;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Builder.Default
    @Column(name = "carbon_budget", precision = 8, scale = 2)
    private BigDecimal carbonBudget = new BigDecimal("4.20");

    @Builder.Default
    @Column(name = "is_premium")
    private Boolean isPremium = false;

    @Column(name = "joined_date")
    private LocalDateTime joinedDate;

    @Builder.Default
    @Column(name = "goals_steps")
    private Integer goalsSteps = 10000;

    @Builder.Default
    @Column(name = "goals_sleep")
    private Integer goalsSleep = 8;

    @Builder.Default
    @Column(name = "goals_water")
    private Integer goalsWater = 3;

    @Builder.Default
    @Column(name = "goals_calories")
    private Integer goalsCalories = 2000;

    @Builder.Default
    @Column(name = "best_streak")
    private Integer bestStreak = 0;

    // ===== SECURITY FIELDS (V3 Migration) =====

    @Builder.Default
    @Column(name = "failed_login_attempts")
    private Integer failedLoginAttempts = 0;

    @Column(name = "lockout_until")
    private LocalDateTime lockoutUntil;

    @Builder.Default
    @Column(name = "account_non_locked")
    private Boolean accountNonLocked = true;

    @Builder.Default
    @Column(name = "enabled")
    private Boolean enabled = true;

    @Builder.Default
    @Column(name = "provider", length = 50)
    private String provider = "LOCAL";

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 20, nullable = false)
    private Role role = Role.USER;

    @Column(name = "provider_id")
    private String providerId;

    @Column(name = "profile_image")
    private String profileImage;

    @Column(name = "email_verification_token")
    private String emailVerificationToken;

    @Column(name = "verification_token_expiry")
    private LocalDateTime verificationTokenExpiry;

    @Column(name = "password_reset_token")
    private String passwordResetToken;

    @Column(name = "reset_token_expiry")
    private LocalDateTime resetTokenExpiry;

    // ===== CARBON ENGINE FIELDS (V10 Migration) =====

    /**
     * IANA timezone string for the user's preferred timezone.
     * Used for timezone-correct period calculations (today, week, month, year).
     * Default: Asia/Kolkata (IST)
     */
    @Builder.Default
    @Column(name = "timezone", length = 50)
    private String timezone = "Asia/Kolkata";

    // ===== TIMESTAMPS =====

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.joinedDate == null) {
            this.joinedDate = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
