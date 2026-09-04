package com.ecoverse.repository;

import com.ecoverse.model.HealthLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface HealthLogRepository extends JpaRepository<HealthLog, Long> {

    List<HealthLog> findByUserId(Long userId);

    List<HealthLog> findByUserIdAndType(Long userId, String type);

    List<HealthLog> findByUserIdAndEntryDateBetween(Long userId, Instant start, Instant end);

    // Paginated version for API endpoints
    Page<HealthLog> findByUserIdAndEntryDateBetween(Long userId, Instant start, Instant end, Pageable pageable);

    // Combined type + period filter (replaces in-memory filtering in HealthService)
    List<HealthLog> findByUserIdAndTypeAndEntryDateBetween(Long userId, String type, Instant start, Instant end);

    // Paginated version of combined type + period filter
    Page<HealthLog> findByUserIdAndTypeAndEntryDateBetween(Long userId, String type, Instant start, Instant end, Pageable pageable);

    Optional<HealthLog> findTopByUserIdAndTypeOrderByEntryDateDesc(Long userId, String type);

    // ===== Aggregate queries (replaces in-memory loading for dashboard) =====

    @Query("SELECT COUNT(h) FROM HealthLog h WHERE h.userId = :userId AND h.entryDate >= :start AND h.entryDate < :end")
    Long countByUserIdAndPeriod(@Param("userId") Long userId, @Param("start") Instant start, @Param("end") Instant end);

    @Query("SELECT COALESCE(SUM(h.steps), 0) FROM HealthLog h WHERE h.userId = :userId AND h.type = 'steps' AND h.entryDate >= :start AND h.entryDate < :end")
    Integer sumStepsByUserIdAndPeriod(@Param("userId") Long userId, @Param("start") Instant start, @Param("end") Instant end);

    @Query("SELECT COALESCE(SUM(h.calories), 0) FROM HealthLog h WHERE h.userId = :userId AND h.type = 'workout' AND h.entryDate >= :start AND h.entryDate < :end")
    Integer sumCaloriesByUserIdAndPeriod(@Param("userId") Long userId, @Param("start") Instant start, @Param("end") Instant end);

    @Query("SELECT COALESCE(SUM(h.waterMl), 0) FROM HealthLog h WHERE h.userId = :userId AND h.type = 'water' AND h.entryDate >= :start AND h.entryDate < :end")
    Integer sumWaterByUserIdAndPeriod(@Param("userId") Long userId, @Param("start") Instant start, @Param("end") Instant end);

    @Query("SELECT COALESCE(SUM(h.hours), 0) FROM HealthLog h WHERE h.userId = :userId AND h.type = 'sleep' AND h.entryDate >= :start AND h.entryDate < :end")
    Double sumSleepHoursByUserIdAndPeriod(@Param("userId") Long userId, @Param("start") Instant start, @Param("end") Instant end);

    // ===== Streak queries (single SQL, no N+1) =====

    /**
     * Calculate the current consecutive-day streak for a user.
     * Uses a window function to find the longest streak ending at today.
     * Counts days where the user has either a carbon entry OR a health log.
     */
    @Query(value = """
        WITH active_days AS (
            SELECT DISTINCT CAST((entry_date AT TIME ZONE :timezone) AS date) AS day
            FROM carbon_entries WHERE user_id = :userId
            UNION
            SELECT DISTINCT CAST((entry_date AT TIME ZONE :timezone) AS date) AS day
            FROM health_logs WHERE user_id = :userId
        ),
        numbered AS (
            SELECT day,
                   day - CAST(ROW_NUMBER() OVER (ORDER BY day) AS integer) AS grp
            FROM active_days
        ),
        streak_groups AS (
            SELECT grp, MIN(day) AS start_day, MAX(day) AS end_day, COUNT(*) AS len
            FROM numbered
            GROUP BY grp
        )
        SELECT COALESCE(
            (SELECT len FROM streak_groups
             WHERE end_day = CAST((CURRENT_DATE AT TIME ZONE :timezone) AS date)
             LIMIT 1),
            (SELECT len FROM streak_groups
             WHERE end_day = CAST((CURRENT_DATE AT TIME ZONE :timezone) AS date) - 1
             LIMIT 1),
            0)
        """, nativeQuery = true)
    Integer calculateCurrentStreak(@Param("userId") Long userId, @Param("timezone") String timezone);

    /**
     * Calculate the best (longest) streak ever for a user.
     * Uses a window function to find the maximum consecutive-day group.
     */
    @Query(value = """
        WITH active_days AS (
            SELECT DISTINCT CAST((entry_date AT TIME ZONE :timezone) AS date) AS day
            FROM carbon_entries WHERE user_id = :userId
            UNION
            SELECT DISTINCT CAST((entry_date AT TIME ZONE :timezone) AS date) AS day
            FROM health_logs WHERE user_id = :userId
        ),
        numbered AS (
            SELECT day,
                   day - CAST(ROW_NUMBER() OVER (ORDER BY day) AS integer) AS grp
            FROM active_days
        ),
        streak_groups AS (
            SELECT grp, COUNT(*) AS len
            FROM numbered
            GROUP BY grp
        )
        SELECT COALESCE(MAX(len), 0) FROM streak_groups
        """, nativeQuery = true)
    Integer calculateBestStreak(@Param("userId") Long userId, @Param("timezone") String timezone);

    // ===== Latest entry queries =====

    /**
     * Check if a user has any health log entry on a given date (used by achievement checks).
     */
    @Query("SELECT COUNT(h) > 0 FROM HealthLog h WHERE h.userId = :userId AND h.entryDate >= :start AND h.entryDate < :end")
    boolean existsByUserIdAndPeriod(@Param("userId") Long userId, @Param("start") Instant start, @Param("end") Instant end);

    // ===== ADMIN AGGREGATE QUERIES =====

    long countByUserId(Long userId);

    @Query("SELECT COUNT(DISTINCT h.userId) FROM HealthLog h WHERE h.entryDate >= :start AND h.entryDate < :end")
    long countActiveUsersByPeriod(@Param("start") Instant start, @Param("end") Instant end);
}
