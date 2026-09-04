package com.ecoverse.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_achievements",
    indexes = {
        @Index(name = "idx_user_achievement_user_id", columnList = "user_id"),
        @Index(name = "idx_user_achievement_achievement_id", columnList = "achievement_id")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_achievement", columnNames = {"user_id", "achievement_id"})
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserAchievement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "achievement_id", nullable = false)
    private Long achievementId;

    @Column(name = "unlocked_at", nullable = false)
    private LocalDateTime unlockedAt;

    @PrePersist
    protected void onCreate() {
        if (this.unlockedAt == null) {
            this.unlockedAt = LocalDateTime.now();
        }
    }
}
