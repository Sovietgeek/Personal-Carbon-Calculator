package com.ecoverse.service;

import com.ecoverse.dto.health.*;
import com.ecoverse.exception.BadRequestException;
import com.ecoverse.model.HealthLog;
import com.ecoverse.model.User;
import com.ecoverse.repository.HealthLogRepository;
import com.ecoverse.repository.UserRepository;
import com.ecoverse.util.InputSanitizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Production Health Service — Phase 3.
 *
 * Key changes from Phase E:
 * - HealthEntryValidator for all input validation (replaces inline switch)
 * - Server-side pagination on health logs
 * - Efficient type+period query via repository (no in-memory filtering)
 * - BMI response includes informational disclaimer
 */
@Service
public class HealthService {

    private static final String BMI_DISCLAIMER =
            "BMI is an informational estimate and not a medical diagnosis. Consult a healthcare provider for personalized advice.";

    private static final int MAX_PAGE_SIZE = 100;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private HealthLogRepository healthLogRepository;

    @Autowired
    private TimezoneService timezoneService;

    @Autowired
    private StreakService streakService;

    @Autowired
    private HealthEntryValidator healthEntryValidator;

    public HealthLogResponse logHealth(Long userId, HealthLogRequest req) {
        // Sanitize text inputs
        String type = InputSanitizer.sanitize(req.getType(), 50);

        // Validate all inputs via dedicated validator
        healthEntryValidator.validate(req);

        // Auto-calculate workout calories if not provided
        Integer calories = req.getCalories();
        if ("workout".equalsIgnoreCase(type) && (calories == null || calories == 0)) {
            calories = calculateWorkoutCalories(
                    InputSanitizer.sanitize(req.getIntensity(), 20),
                    req.getDuration()
            );
        }

        // Resolve user timezone
        String userTimezone = getUserTimezone(userId);

        HealthLog healthLog = HealthLog.builder()
                .userId(userId).type(type).steps(req.getSteps()).distance(req.getDistance())
                .workoutType(InputSanitizer.sanitize(req.getWorkoutType(), 50))
                .duration(req.getDuration())
                .intensity(InputSanitizer.sanitize(req.getIntensity(), 20))
                .calories(calories).weight(req.getWeight()).height(req.getHeight()).bodyFat(req.getBodyFat())
                .hours(req.getHours())
                .quality(InputSanitizer.sanitize(req.getQuality(), 20))
                .bedtime(InputSanitizer.sanitize(req.getBedtime(), 10))
                .wakeTime(InputSanitizer.sanitize(req.getWakeTime(), 10))
                .waterMl(req.getWaterMl())
                .entryDate(timezoneService.now())
                .userTimezone(userTimezone)
                .build();

        healthLog = healthLogRepository.save(healthLog);

        // Update best streak on mutation (NOT in GET endpoint)
        streakService.updateBestStreakIfNeeded(userId);

        return mapToResponse(healthLog);
    }

    private Integer calculateWorkoutCalories(String intensity, Integer duration) {
        if (duration == null || duration <= 0) return 0;
        int rate;
        if (intensity == null) { rate = 5; }
        else {
            switch (intensity.toLowerCase()) {
                case "low": rate = 4; break;
                case "moderate": rate = 7; break;
                case "high": rate = 10; break;
                case "extreme": rate = 14; break;
                default: rate = 5;
            }
        }
        return rate * duration;
    }

    /**
     * Get health logs with server-side pagination.
     * Filters by type and period using timezone-aware date ranges.
     *
     * @param userId  authenticated user ID
     * @param type    optional health log type filter
     * @param period  time period (today/week/month/year)
     * @param page    zero-based page number
     * @param size    page size (max 100)
     * @return paginated health log responses
     */
    public Page<HealthLogResponse> getHealthLogs(Long userId, String type, String period, int page, int size) {
        String userTimezone = getUserTimezone(userId);
        ZoneId zoneId = timezoneService.getUserZoneId(userTimezone);
        Instant[] range = timezoneService.getPeriodRange(period != null ? period : "today", zoneId);

        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(page, safeSize, Sort.by(Sort.Direction.DESC, "entryDate"));

        Page<HealthLog> logPage;
        if (type != null && !type.isEmpty()) {
            logPage = healthLogRepository.findByUserIdAndTypeAndEntryDateBetween(
                    userId, type, range[0], range[1], pageable);
        } else {
            logPage = healthLogRepository.findByUserIdAndEntryDateBetween(
                    userId, range[0], range[1], pageable);
        }

        return logPage.map(this::mapToResponse);
    }

    /**
     * Get health logs as a flat list (for dashboard/achievement internal use).
     * Not exposed via API — use the paginated version for user-facing endpoints.
     */
    public List<HealthLog> getHealthLogsList(Long userId, String type, String period) {
        String userTimezone = getUserTimezone(userId);
        ZoneId zoneId = timezoneService.getUserZoneId(userTimezone);
        Instant[] range = timezoneService.getPeriodRange(period != null ? period : "today", zoneId);

        if (type != null && !type.isEmpty()) {
            return healthLogRepository.findByUserIdAndTypeAndEntryDateBetween(
                    userId, type, range[0], range[1]);
        }
        return healthLogRepository.findByUserIdAndEntryDateBetween(userId, range[0], range[1]);
    }

    public BMIResponse calculateBMI(BMIRequest req) {
        if (req.getHeight() == null || req.getHeight() <= 0) {
            throw new BadRequestException("Height must be a positive value");
        }
        if (req.getWeight() == null || req.getWeight() <= 0) {
            throw new BadRequestException("Weight must be a positive value");
        }

        double heightInMeters = req.getHeight() / 100.0;
        double bmi = req.getWeight() / (heightInMeters * heightInMeters);
        bmi = Math.round(bmi * 10.0) / 10.0;

        String category, color, advice;
        if (bmi < 18.5) { category = "Underweight"; color = "#3B82F6"; advice = "Consider increasing caloric intake with nutrient-rich foods."; }
        else if (bmi < 25) { category = "Normal"; color = "#10B981"; advice = "Great job! Maintain your current lifestyle."; }
        else if (bmi < 30) { category = "Overweight"; color = "#F59E0B"; advice = "Consider increasing physical activity and dietary adjustments."; }
        else { category = "Obese"; color = "#EF4444"; advice = "Consult a healthcare provider for a personalized plan."; }

        return BMIResponse.builder()
                .bmi(bmi).category(category).color(color).advice(advice)
                .disclaimer(BMI_DISCLAIMER)
                .build();
    }

    /**
     * Get health score for a user. Fetches today's logs from DB.
     */
    public HealthScoreResponse getHealthScore(Long userId) {
        String userTimezone = getUserTimezone(userId);
        ZoneId zoneId = timezoneService.getUserZoneId(userTimezone);
        Instant[] todayRange = timezoneService.getTodayRange(zoneId);
        List<HealthLog> todayLogs = healthLogRepository.findByUserIdAndEntryDateBetween(userId, todayRange[0], todayRange[1]);
        return getHealthScore(todayLogs);
    }

    /**
     * Get health score from pre-fetched logs (avoids duplicate DB query when DashboardService
     * has already fetched today's logs).
     */
    public HealthScoreResponse getHealthScore(List<HealthLog> todayLogs) {
        int score = 50;
        boolean stepsGoalMet = todayLogs.stream()
                .filter(log -> "steps".equalsIgnoreCase(log.getType()))
                .mapToInt(log -> log.getSteps() != null ? log.getSteps() : 0)
                .sum() >= 10000;
        if (stepsGoalMet) score += 15;

        boolean workoutDone = todayLogs.stream()
                .anyMatch(log -> "workout".equalsIgnoreCase(log.getType()));
        if (workoutDone) score += 15;

        boolean sleepGoalMet = todayLogs.stream()
                .filter(log -> "sleep".equalsIgnoreCase(log.getType()))
                .anyMatch(log -> log.getHours() != null && log.getHours() >= 7);
        if (sleepGoalMet) score += 10;

        boolean waterGoalMet = todayLogs.stream()
                .filter(log -> "water".equalsIgnoreCase(log.getType()))
                .mapToInt(log -> log.getWaterMl() != null ? log.getWaterMl() : 0)
                .sum() >= 3000;
        if (waterGoalMet) score += 10;

        score = Math.min(score, 100);

        return HealthScoreResponse.builder()
                .score(score)
                .stepsGoalMet(stepsGoalMet)
                .workoutDone(workoutDone)
                .sleepGoalMet(sleepGoalMet)
                .waterGoalMet(waterGoalMet)
                .build();
    }

    /**
     * Calculate current streak using efficient SQL window function (no N+1).
     */
    public Integer calculateStreak(Long userId) {
        String userTimezone = getUserTimezone(userId);
        Integer streak = healthLogRepository.calculateCurrentStreak(userId, userTimezone);
        return streak != null ? streak : 0;
    }

    public Double getLatestWeight(Long userId) {
        return healthLogRepository.findTopByUserIdAndTypeOrderByEntryDateDesc(userId, "weight")
                .map(HealthLog::getWeight)
                .orElse(null);
    }

    private HealthLogResponse mapToResponse(HealthLog log) {
        return HealthLogResponse.builder()
                .id(log.getId()).type(log.getType())
                .entryDate(log.getEntryDate() != null ? log.getEntryDate().toString() : null)
                .steps(log.getSteps()).distance(log.getDistance()).workoutType(log.getWorkoutType())
                .duration(log.getDuration()).intensity(log.getIntensity()).calories(log.getCalories())
                .weight(log.getWeight()).height(log.getHeight()).bodyFat(log.getBodyFat())
                .hours(log.getHours()).quality(log.getQuality()).bedtime(log.getBedtime())
                .wakeTime(log.getWakeTime()).waterMl(log.getWaterMl())
                .build();
    }

    private String getUserTimezone(Long userId) {
        try {
            User user = userRepository.findById(userId).orElse(null);
            if (user != null && user.getTimezone() != null && !user.getTimezone().isBlank()) {
                return user.getTimezone();
            }
        } catch (Exception e) {
            // Fall through to default
        }
        return "Asia/Kolkata";
    }
}
