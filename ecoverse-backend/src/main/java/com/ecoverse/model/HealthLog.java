package com.ecoverse.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "health_logs", indexes = {
    @Index(name = "idx_health_user_date", columnList = "user_id, entry_date")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HealthLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String type;

    // Steps fields
    private Integer steps;
    private Double distance;

    // Workout fields
    @Column(name = "workout_type")
    private String workoutType;
    private Integer duration;
    private String intensity;
    private Integer calories;

    // Weight fields
    private Double weight;
    private Double height;
    @Column(name = "body_fat")
    private Double bodyFat;

    // Sleep fields
    private Double hours;
    private String quality;
    private String bedtime;
    @Column(name = "wake_time")
    private String wakeTime;

    // Water fields
    @Column(name = "water_ml")
    private Integer waterMl;

    @Column(name = "entry_date", nullable = false)
    private Instant entryDate;

    @Column(name = "user_timezone")
    private String userTimezone;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }
}
