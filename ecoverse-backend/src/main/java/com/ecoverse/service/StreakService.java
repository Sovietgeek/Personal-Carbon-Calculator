package com.ecoverse.service;

import com.ecoverse.model.User;
import com.ecoverse.repository.HealthLogRepository;
import com.ecoverse.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Streak Service — handles streak calculation and best-streak updates.
 *
 * Extracted from DashboardService to avoid circular dependency:
 * - DashboardService → HealthService (for health score)
 * - HealthService → StreakService (for best streak update on mutation)
 * - CarbonService → StreakService (for best streak update on mutation)
 * - DashboardService → StreakService (for current/best streak display)
 *
 * Streak is calculated via efficient SQL window function (no N+1).
 * Best streak is updated ONLY on mutation endpoints (addEntry, logHealth),
 * never in GET endpoints (no state mutation in reads).
 */
@Service
public class StreakService {

    @Autowired
    private HealthLogRepository healthLogRepository;

    @Autowired
    private UserRepository userRepository;

    /**
     * Get the current consecutive-day streak for a user.
     * Uses efficient SQL window function — no N+1 query loop.
     */
    public Integer getCurrentStreak(Long userId, String timezone) {
        Integer streak = healthLogRepository.calculateCurrentStreak(userId, timezone);
        return streak != null ? streak : 0;
    }

    /**
     * Get the best (longest) streak ever for a user.
     * Uses efficient SQL window function.
     */
    public Integer getBestStreak(Long userId, String timezone) {
        Integer best = healthLogRepository.calculateBestStreak(userId, timezone);
        return best != null ? best : 0;
    }

    /**
     * Update the user's bestStreak field if the current streak exceeds it.
     * Called ONLY from mutation endpoints (addEntry, logHealth) — never from GET.
     */
    public void updateBestStreakIfNeeded(Long userId) {
        String timezone = getUserTimezone(userId);
        Integer currentStreak = getCurrentStreak(userId, timezone);

        User user = userRepository.findById(userId).orElse(null);
        if (user != null) {
            Integer bestStreak = user.getBestStreak() != null ? user.getBestStreak() : 0;
            if (currentStreak > bestStreak) {
                user.setBestStreak(currentStreak);
                userRepository.save(user);
            }
        }
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
