package com.ecoverse.service;

import com.ecoverse.dto.health.HealthLogRequest;
import com.ecoverse.exception.BadRequestException;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * Validates health log entry requests before they are processed.
 * Enforces type-specific input contracts and rejects invalid/dangerous inputs.
 *
 * Rules:
 * - Type must be one of the 5 supported types
 * - Intensity must be one of the 4 supported intensities (if provided)
 * - Quality must be one of the 4 supported qualities (if provided)
 * - All numeric inputs must be positive and within reasonable bounds
 * - No negative values allowed for any measurement
 *
 * Pattern mirrors CarbonEntryValidator for consistency.
 */
@Service
public class HealthEntryValidator {

    private static final Set<String> VALID_TYPES = Set.of("steps", "workout", "weight", "sleep", "water");
    private static final Set<String> VALID_INTENSITIES = Set.of("low", "moderate", "high", "extreme");
    private static final Set<String> VALID_QUALITIES = Set.of("poor", "fair", "good", "excellent");

    // Maximum reasonable values to prevent absurd entries
    private static final int MAX_STEPS = 100_000;          // ~80km walk
    private static final double MAX_DISTANCE_KM = 200.0;   // per entry
    private static final int MAX_DURATION_MIN = 600;       // 10 hours
    private static final int MAX_CALORIES = 5_000;         // per workout entry
    private static final double MAX_WEIGHT_KG = 300.0;     // heaviest person ever ~635kg, 300 is generous
    private static final double MIN_WEIGHT_KG = 2.0;       // infant minimum
    private static final double MAX_HEIGHT_CM = 300.0;     // tallest ever 272cm
    private static final double MIN_HEIGHT_CM = 30.0;      // infant minimum
    private static final double MAX_BODY_FAT = 70.0;       // percent
    private static final double MAX_SLEEP_HOURS = 24.0;    // impossible to sleep more
    private static final double MIN_SLEEP_HOURS = 0.1;     // at least 6 min
    private static final int MAX_WATER_ML = 20_000;        // 20 liters per entry
    private static final int MAX_WORKOUT_TYPE_LENGTH = 50;
    private static final int MAX_BEDTIME_LENGTH = 10;

    /**
     * Validate a health log entry request. Throws BadRequestException if invalid.
     *
     * @param req the health log request to validate
     */
    public void validate(HealthLogRequest req) {
        if (req == null) {
            throw new BadRequestException("Request must not be null");
        }

        // Validate type
        if (req.getType() == null || req.getType().isBlank()) {
            throw new BadRequestException("Health log type is required");
        }
        String type = req.getType().toLowerCase().trim();
        if (!VALID_TYPES.contains(type)) {
            throw new BadRequestException("Invalid health log type: " + req.getType() +
                    ". Valid types: " + VALID_TYPES);
        }

        // Type-specific validation
        switch (type) {
            case "steps":   validateSteps(req); break;
            case "workout": validateWorkout(req); break;
            case "weight":  validateWeight(req); break;
            case "sleep":   validateSleep(req); break;
            case "water":   validateWater(req); break;
        }

        // Cross-field validations (optional fields with bounds)
        if (req.getBodyFat() != null) {
            if (req.getBodyFat() < 0) {
                throw new BadRequestException("Body fat cannot be negative");
            }
            if (req.getBodyFat() > MAX_BODY_FAT) {
                throw new BadRequestException("Body fat exceeds maximum allowed value (" + MAX_BODY_FAT + "%)");
            }
        }
        if (req.getDistance() != null) {
            if (req.getDistance() < 0) {
                throw new BadRequestException("Distance cannot be negative");
            }
            if (req.getDistance() > MAX_DISTANCE_KM) {
                throw new BadRequestException("Distance exceeds maximum allowed value (" + MAX_DISTANCE_KM + " km)");
            }
        }
        if (req.getCalories() != null) {
            if (req.getCalories() < 0) {
                throw new BadRequestException("Calories cannot be negative");
            }
            if (req.getCalories() > MAX_CALORIES) {
                throw new BadRequestException("Calories exceed maximum allowed value (" + MAX_CALORIES + " kcal)");
            }
        }
        if (req.getHeight() != null) {
            if (req.getHeight() < MIN_HEIGHT_CM) {
                throw new BadRequestException("Height must be at least " + MIN_HEIGHT_CM + " cm");
            }
            if (req.getHeight() > MAX_HEIGHT_CM) {
                throw new BadRequestException("Height exceeds maximum allowed value (" + MAX_HEIGHT_CM + " cm)");
            }
        }
        if (req.getIntensity() != null && !req.getIntensity().isBlank()) {
            if (!VALID_INTENSITIES.contains(req.getIntensity().toLowerCase().trim())) {
                throw new BadRequestException("Invalid intensity: " + req.getIntensity() +
                        ". Valid intensities: " + VALID_INTENSITIES);
            }
        }
        if (req.getWorkoutType() != null && req.getWorkoutType().length() > MAX_WORKOUT_TYPE_LENGTH) {
            throw new BadRequestException("Workout type exceeds maximum length (" + MAX_WORKOUT_TYPE_LENGTH + " characters)");
        }
        if (req.getBedtime() != null && req.getBedtime().length() > MAX_BEDTIME_LENGTH) {
            throw new BadRequestException("Bedtime value exceeds maximum length (" + MAX_BEDTIME_LENGTH + " characters)");
        }
        if (req.getWakeTime() != null && req.getWakeTime().length() > MAX_BEDTIME_LENGTH) {
            throw new BadRequestException("Wake time value exceeds maximum length (" + MAX_BEDTIME_LENGTH + " characters)");
        }
    }

    private void validateSteps(HealthLogRequest req) {
        if (req.getSteps() == null) {
            throw new BadRequestException("Steps value is required for steps log");
        }
        if (req.getSteps() <= 0) {
            throw new BadRequestException("Steps must be a positive number");
        }
        if (req.getSteps() > MAX_STEPS) {
            throw new BadRequestException("Steps exceed maximum allowed value (" + MAX_STEPS + ")");
        }
    }

    private void validateWorkout(HealthLogRequest req) {
        if (req.getDuration() == null) {
            throw new BadRequestException("Duration is required for workout log");
        }
        if (req.getDuration() <= 0) {
            throw new BadRequestException("Duration must be a positive number");
        }
        if (req.getDuration() > MAX_DURATION_MIN) {
            throw new BadRequestException("Duration exceeds maximum allowed value (" + MAX_DURATION_MIN + " minutes)");
        }
    }

    private void validateWeight(HealthLogRequest req) {
        if (req.getWeight() == null) {
            throw new BadRequestException("Weight value is required for weight log");
        }
        if (req.getWeight() < MIN_WEIGHT_KG) {
            throw new BadRequestException("Weight must be at least " + MIN_WEIGHT_KG + " kg");
        }
        if (req.getWeight() > MAX_WEIGHT_KG) {
            throw new BadRequestException("Weight exceeds maximum allowed value (" + MAX_WEIGHT_KG + " kg)");
        }
    }

    private void validateSleep(HealthLogRequest req) {
        if (req.getHours() == null) {
            throw new BadRequestException("Hours of sleep is required for sleep log");
        }
        if (req.getHours() < MIN_SLEEP_HOURS) {
            throw new BadRequestException("Sleep hours must be at least " + MIN_SLEEP_HOURS);
        }
        if (req.getHours() > MAX_SLEEP_HOURS) {
            throw new BadRequestException("Sleep hours cannot exceed " + MAX_SLEEP_HOURS);
        }
        if (req.getQuality() != null && !req.getQuality().isBlank()) {
            if (!VALID_QUALITIES.contains(req.getQuality().toLowerCase().trim())) {
                throw new BadRequestException("Invalid sleep quality: " + req.getQuality() +
                        ". Valid qualities: " + VALID_QUALITIES);
            }
        }
    }

    private void validateWater(HealthLogRequest req) {
        if (req.getWaterMl() == null) {
            throw new BadRequestException("Water amount (ml) is required for water log");
        }
        if (req.getWaterMl() <= 0) {
            throw new BadRequestException("Water amount must be a positive number");
        }
        if (req.getWaterMl() > MAX_WATER_ML) {
            throw new BadRequestException("Water amount exceeds maximum allowed value (" + MAX_WATER_ML + " ml)");
        }
    }
}
