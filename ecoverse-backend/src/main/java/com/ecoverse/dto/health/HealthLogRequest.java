package com.ecoverse.dto.health;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HealthLogRequest {

    @NotBlank(message = "Type is required")
    @Size(min = 1, max = 30, message = "Type must be between 1 and 30 characters")
    private String type; // steps/workout/weight/sleep/water

    @Min(value = 1, message = "Steps must be positive")
    @Max(value = 100000, message = "Steps exceed maximum allowed value")
    private Integer steps;

    @DecimalMin(value = "0.0", inclusive = true, message = "Distance cannot be negative")
    @DecimalMax(value = "200.0", message = "Distance exceeds maximum allowed value")
    private Double distance;

    @Size(max = 50, message = "Workout type must be at most 50 characters")
    private String workoutType;

    @Min(value = 1, message = "Duration must be positive")
    @Max(value = 600, message = "Duration exceeds maximum allowed value (600 minutes)")
    private Integer duration;

    @Size(max = 20, message = "Intensity must be at most 20 characters")
    private String intensity;

    @Min(value = 0, message = "Calories cannot be negative")
    @Max(value = 5000, message = "Calories exceed maximum allowed value")
    private Integer calories;

    @DecimalMin(value = "2.0", message = "Weight must be at least 2 kg")
    @DecimalMax(value = "300.0", message = "Weight must be at most 300 kg")
    private Double weight;

    @DecimalMin(value = "30.0", message = "Height must be at least 30 cm")
    @DecimalMax(value = "300.0", message = "Height must be at most 300 cm")
    private Double height;

    @DecimalMin(value = "0.0", message = "Body fat cannot be negative")
    @DecimalMax(value = "70.0", message = "Body fat exceeds maximum allowed value")
    private Double bodyFat;

    @DecimalMin(value = "0.1", message = "Sleep hours must be at least 0.1")
    @DecimalMax(value = "24.0", message = "Sleep hours cannot exceed 24")
    private Double hours;

    @Size(max = 20, message = "Quality must be at most 20 characters")
    private String quality;
    @Size(max = 10, message = "Bedtime must be at most 10 characters")
    private String bedtime;
    @Size(max = 10, message = "Wake time must be at most 10 characters")
    private String wakeTime;

    @Min(value = 1, message = "Water amount must be positive")
    @Max(value = 20000, message = "Water amount exceeds maximum allowed value (20000 ml)")
    private Integer waterMl;
}
