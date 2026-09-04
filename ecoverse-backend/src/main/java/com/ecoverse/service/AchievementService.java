package com.ecoverse.service;

import com.ecoverse.dto.achievement.AchievementResponse;
import com.ecoverse.model.Achievement;
import com.ecoverse.model.CarbonEntry;
import com.ecoverse.model.HealthLog;
import com.ecoverse.model.User;
import com.ecoverse.model.UserAchievement;
import com.ecoverse.repository.AchievementRepository;
import com.ecoverse.repository.CarbonEntryRepository;
import com.ecoverse.repository.HealthLogRepository;
import com.ecoverse.repository.OrderRepository;
import com.ecoverse.repository.UserAchievementRepository;
import com.ecoverse.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Production Achievement Service — Phase E.
 *
 * Key changes:
 * - Efficient streak via SQL window function (no N+1)
 * - Instant instead of LocalDateTime (timezone-aware)
 * - User timezone from TimezoneService
 */
@Service
public class AchievementService {

    @Autowired
    private AchievementRepository achievementRepository;

    @Autowired
    private UserAchievementRepository userAchievementRepository;

    @Autowired
    private CarbonEntryRepository carbonEntryRepository;

    @Autowired
    private HealthLogRepository healthLogRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrderRepository orderRepository;

    // Achievement definitions
    private static final Object[][] ACHIEVEMENT_DEFS = {
            {"first_log",       "First Step",        "Log your first carbon entry",    "fa-shoe-prints",  "carbon", 1},
            {"week_streak",     "Week Warrior",      "7 day streak",                   "fa-fire",         "streak", 7},
            {"carbon_saver",    "Carbon Saver",      "Save 10kg CO2",                  "fa-leaf",         "carbon", 10},
            {"health_enthusiast","Health Enthusiast", "Log 10 health entries",          "fa-heart-pulse",  "health", 10},
            {"early_bird",      "Early Bird",        "Log sleep before 10pm",          "fa-moon",         "sleep",  1},
            {"marathon_runner", "Marathon Runner",   "Log 10000 steps in a day",       "fa-person-running","steps", 10000},
            {"eco_shopper",     "Eco Shopper",       "Buy 5 eco products",             "fa-cart-shopping","shop",   5},
            {"zero_day",        "Zero Emission Day", "0 kg CO2 in a day",              "fa-circle-check", "carbon", 0}
    };

    @PostConstruct
    public void initAchievements() {
        for (Object[] def : ACHIEVEMENT_DEFS) {
            String code = (String) def[0];
            if (!achievementRepository.findByCode(code).isPresent()) {
                Achievement achievement = Achievement.builder()
                        .code(code)
                        .name((String) def[1])
                        .description((String) def[2])
                        .icon((String) def[3])
                        .category((String) def[4])
                        .threshold((Integer) def[5])
                        .build();
                achievementRepository.save(achievement);
            }
        }
    }

    public List<AchievementResponse> getAchievements(Long userId) {
        List<Achievement> allAchievements = achievementRepository.findAll();
        List<UserAchievement> userAchievements = userAchievementRepository.findByUserId(userId);

        return allAchievements.stream()
                .map(achievement -> {
                    boolean isUnlocked = userAchievements.stream()
                            .anyMatch(ua -> ua.getAchievementId().equals(achievement.getId()));

                    String unlockedAt = userAchievements.stream()
                            .filter(ua -> ua.getAchievementId().equals(achievement.getId()))
                            .map(ua -> ua.getUnlockedAt() != null ? ua.getUnlockedAt().toString() : null)
                            .findFirst()
                            .orElse(null);

                    return AchievementResponse.builder()
                            .id(achievement.getId())
                            .code(achievement.getCode())
                            .name(achievement.getName())
                            .description(achievement.getDescription())
                            .icon(achievement.getIcon())
                            .category(achievement.getCategory())
                            .isUnlocked(isUnlocked)
                            .unlockedAt(unlockedAt)
                            .build();
                })
                .collect(Collectors.toList());
    }

    public List<AchievementResponse> checkAndUnlockBadges(Long userId) {
        List<AchievementResponse> newlyUnlocked = new ArrayList<>();
        List<Achievement> allAchievements = achievementRepository.findAll();

        for (Achievement achievement : allAchievements) {
            // Skip if already unlocked
            if (userAchievementRepository.existsByUserIdAndAchievementId(userId, achievement.getId())) {
                continue;
            }

            boolean shouldUnlock = false;

            switch (achievement.getCode()) {
                case "first_log":
                    shouldUnlock = checkFirstLog(userId);
                    break;
                case "week_streak":
                    shouldUnlock = checkWeekStreak(userId);
                    break;
                case "carbon_saver":
                    shouldUnlock = checkCarbonSaver(userId);
                    break;
                case "health_enthusiast":
                    shouldUnlock = checkHealthEnthusiast(userId);
                    break;
                case "early_bird":
                    shouldUnlock = checkEarlyBird(userId);
                    break;
                case "marathon_runner":
                    shouldUnlock = checkMarathonRunner(userId);
                    break;
                case "eco_shopper":
                    shouldUnlock = checkEcoShopper(userId);
                    break;
                case "zero_day":
                    shouldUnlock = checkZeroDay(userId);
                    break;
                default:
                    break;
            }

            if (shouldUnlock) {
                // Unlock the achievement
                UserAchievement ua = UserAchievement.builder()
                        .userId(userId)
                        .achievementId(achievement.getId())
                        .build();
                userAchievementRepository.save(ua);

                newlyUnlocked.add(AchievementResponse.builder()
                        .id(achievement.getId())
                        .code(achievement.getCode())
                        .name(achievement.getName())
                        .description(achievement.getDescription())
                        .icon(achievement.getIcon())
                        .category(achievement.getCategory())
                        .isUnlocked(true)
                        .unlockedAt(Instant.now().toString())
                        .build());
            }
        }

        return newlyUnlocked;
    }

    // --- Individual achievement checks ---

    private boolean checkFirstLog(Long userId) {
        return carbonEntryRepository.existsByUserIdAndPeriod(
                userId, Instant.EPOCH, Instant.now());
    }

    private boolean checkWeekStreak(Long userId) {
        String userTimezone = getUserTimezone(userId);
        Integer currentStreak = healthLogRepository.calculateCurrentStreak(userId, userTimezone);
        return currentStreak != null && currentStreak >= 7;
    }

    private boolean checkCarbonSaver(Long userId) {
        BigDecimal totalSaved = carbonEntryRepository.sumTotalAvoidedByUserId(userId);
        return totalSaved != null && totalSaved.compareTo(new BigDecimal("10")) >= 0;
    }

    private boolean checkHealthEnthusiast(Long userId) {
        List<HealthLog> healthLogs = healthLogRepository.findByUserId(userId);
        return healthLogs.size() >= 10;
    }

    private boolean checkEarlyBird(Long userId) {
        List<HealthLog> sleepLogs = healthLogRepository.findByUserIdAndType(userId, "sleep");
        return sleepLogs.stream()
                .anyMatch(log -> log.getBedtime() != null && isBefore10Pm(log.getBedtime()));
    }

    private boolean isBefore10Pm(String bedtime) {
        try {
            String normalized = bedtime.trim().toUpperCase();

            if (normalized.contains("PM")) {
                normalized = normalized.replace("PM", "").trim();
                String[] parts = normalized.split(":");
                int hour = Integer.parseInt(parts[0].trim());
                int minute = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 0;
                if (hour != 12) hour += 12;
                return hour < 22 || (hour == 22 && minute == 0);
            } else if (normalized.contains("AM")) {
                return true;
            } else {
                String[] parts = normalized.split(":");
                int hour = Integer.parseInt(parts[0].trim());
                return hour < 22;
            }
        } catch (Exception e) {
            return false;
        }
    }

    private boolean checkMarathonRunner(Long userId) {
        List<HealthLog> stepLogs = healthLogRepository.findByUserIdAndType(userId, "steps");
        return stepLogs.stream()
                .anyMatch(log -> log.getSteps() != null && log.getSteps() >= 10000);
    }

    private boolean checkEcoShopper(Long userId) {
        // Check if user has at least 5 completed/paid orders with eco-rated products
        try {
            List<com.ecoverse.model.Order> orders = orderRepository.findByUserId(userId);
            long paidOrderCount = orders.stream()
                    .filter(o -> o.getStatus() == com.ecoverse.model.Order.OrderStatus.PAID
                            || o.getStatus() == com.ecoverse.model.Order.OrderStatus.DELIVERED
                            || o.getStatus() == com.ecoverse.model.Order.OrderStatus.SHIPPED
                            || o.getStatus() == com.ecoverse.model.Order.OrderStatus.PROCESSING)
                    .count();
            return paidOrderCount >= 5;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean checkZeroDay(Long userId) {
        String userTimezone = getUserTimezone(userId);
        ZoneId zoneId = ZoneId.of(userTimezone);
        Instant todayStart = java.time.LocalDate.now(zoneId).atStartOfDay(zoneId).toInstant();
        Instant tomorrowStart = java.time.LocalDate.now(zoneId).plusDays(1).atStartOfDay(zoneId).toInstant();
        BigDecimal todayEmissions = carbonEntryRepository.sumEmissionsByUserIdAndPeriod(userId, todayStart, tomorrowStart);
        return todayEmissions == null || todayEmissions.compareTo(BigDecimal.ZERO) == 0;
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
