package com.ecoverse.service;

import com.ecoverse.dto.dashboard.ActivityItem;
import com.ecoverse.dto.dashboard.DashboardResponse;
import com.ecoverse.dto.dashboard.DashboardTrendResponse;
import com.ecoverse.dto.dashboard.TrendDataPoint;
import com.ecoverse.dto.health.HealthScoreResponse;
import com.ecoverse.model.CarbonEntry;
import com.ecoverse.model.HealthLog;
import com.ecoverse.model.User;
import com.ecoverse.repository.CarbonEntryRepository;
import com.ecoverse.repository.HealthLogRepository;
import com.ecoverse.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Production Dashboard Service — Phase E.
 *
 * Key principles:
 * - Single-pass data assembly (no duplicate queries)
 * - NO state mutation in GET (no bestStreak update)
 * - All data from server (no localStorage, no mock data)
 * - Timezone-aware period calculations via TimezoneService
 * - Efficient streak via StreakService (SQL window function, no N+1)
 * - BigDecimal for all carbon values
 * - Streak logic delegated to StreakService (avoids circular deps)
 */
@Service
public class DashboardService {

    @Autowired
    private CarbonEntryRepository carbonEntryRepository;

    @Autowired
    private HealthLogRepository healthLogRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private HealthService healthService;

    @Autowired
    private RiskService riskService;

    @Autowired
    private TimezoneService timezoneService;

    @Autowired
    private StreakService streakService;

    // 23 eco tips
    private static final String[] TIPS = {
            "Switch to LED bulbs – they use 75% less energy than incandescent lighting",
            "Unplug devices when not in use – standby power accounts for 5-10% of household energy",
            "Take shorter showers – cutting just 2 minutes saves 10 gallons of water",
            "Bring reusable bags – a single plastic bag takes 500 years to degrade",
            "Choose public transport – it reduces CO2 emissions by 45% per passenger mile",
            "Eat one plant-based meal a day – it saves 1,200 kg CO2 per year",
            "Compost food scraps – it reduces methane emissions from landfills",
            "Use a reusable water bottle – it saves an average of 156 plastic bottles per year",
            "Wash clothes in cold water – 90% of washing machine energy goes to heating water",
            "Buy local produce – it reduces food miles and supports local farmers",
            "Turn off lights when leaving a room – save 0.5 kg CO2 per bulb per day",
            "Use a clothesline instead of a dryer – save 2 kg CO2 per load",
            "Reduce meat consumption – livestock produces 14.5% of global greenhouse gases",
            "Plant a tree – one tree absorbs about 22 kg of CO2 per year",
            "Carpool to work – sharing with one person cuts your commute emissions in half",
            "Use both sides of paper – it saves 1 kg CO2 per 500 sheets",
            "Choose energy-efficient appliances – they use 10-50% less energy",
            "Fix leaky faucets – a drip wastes 3,000 gallons per year",
            "Walk or bike for trips under 3 km – zero emissions and great for health",
            "Switch to e-statements – save 6 kg CO2 per year per account",
            "Support renewable energy – consider green energy plans from your utility",
            "Reduce food waste – plan meals to avoid throwing away 1.3 billion tons annually",
            "Recycle – one ton of recycled paper saves 17 trees and 2.3 m³ of landfill"
    };

    /**
     * Get full dashboard data for a user. Single API call, no state mutation.
     * Every metric traces: DATABASE → Repository → Service → DTO → Frontend → UI
     */
    @Transactional(readOnly = true)
    public DashboardResponse getDashboard(Long userId) {
        // 1. Fetch user + resolve timezone
        User user = userRepository.findById(userId).orElse(null);
        String userTimezone = (user != null && user.getTimezone() != null) ? user.getTimezone() : "Asia/Kolkata";
        ZoneId zoneId = timezoneService.getUserZoneId(userTimezone);

        // 2. Timezone-aware period ranges
        Instant[] todayRange = timezoneService.getTodayRange(zoneId);
        Instant[] monthRange = timezoneService.getMonthRange(zoneId);
        Instant[] yearRange = timezoneService.getYearRange(zoneId);

        // 3. Carbon metrics from DB aggregates (no in-memory loading)
        BigDecimal todayEmissions = orZero(carbonEntryRepository.sumEmissionsByUserIdAndPeriod(userId, todayRange[0], todayRange[1]));
        BigDecimal todayAvoided = orZero(carbonEntryRepository.sumAvoidedByUserIdAndPeriod(userId, todayRange[0], todayRange[1]));
        BigDecimal monthEmissions = orZero(carbonEntryRepository.sumEmissionsByUserIdAndPeriod(userId, monthRange[0], monthRange[1]));
        BigDecimal yearEmissions = orZero(carbonEntryRepository.sumEmissionsByUserIdAndPeriod(userId, yearRange[0], yearRange[1]));

        // 4. Budget (from user profile, not hardcoded)
        BigDecimal budget = (user != null && user.getCarbonBudget() != null)
                ? user.getCarbonBudget() : new BigDecimal("4.20");
        BigDecimal budgetUsedPercent = budget.compareTo(BigDecimal.ZERO) > 0
                ? todayEmissions.multiply(new BigDecimal("100")).divide(budget, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal budgetRemaining = budget.subtract(todayEmissions).max(BigDecimal.ZERO);

        // 5. Risk level from deduplicated RiskService
        String riskLevel = riskService.getRiskLevel(todayEmissions, budget);
        BigDecimal riskPercentage = budget.compareTo(BigDecimal.ZERO) > 0
                ? todayEmissions.multiply(new BigDecimal("100")).divide(budget, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // 6. Trees needed
        int treesNeeded = riskService.calculateTreesNeeded(yearEmissions);

        // 7. Streak — efficient SQL window function via StreakService (no N+1)
        Integer streakDays = streakService.getCurrentStreak(userId, userTimezone);
        Integer bestStreak = streakService.getBestStreak(userId, userTimezone);

        // NOTE: NO state mutation in GET — bestStreak is NOT written to the user table here.
        // Best streak update happens only in mutation endpoints via StreakService.

        // 8. Health data — single query for today's logs, compute all metrics from it
        List<HealthLog> todayLogs = healthLogRepository.findByUserIdAndEntryDateBetween(userId, todayRange[0], todayRange[1]);

        Integer steps = todayLogs.stream()
                .filter(log -> "steps".equalsIgnoreCase(log.getType()))
                .mapToInt(log -> log.getSteps() != null ? log.getSteps() : 0)
                .sum();

        Integer calories = todayLogs.stream()
                .filter(log -> "workout".equalsIgnoreCase(log.getType()))
                .mapToInt(log -> log.getCalories() != null ? log.getCalories() : 0)
                .sum();

        Double sleep = todayLogs.stream()
                .filter(log -> "sleep".equalsIgnoreCase(log.getType()))
                .mapToDouble(log -> log.getHours() != null ? log.getHours() : 0.0)
                .sum();

        Double water = todayLogs.stream()
                .filter(log -> "water".equalsIgnoreCase(log.getType()))
                .mapToInt(log -> log.getWaterMl() != null ? log.getWaterMl() : 0)
                .sum() / 1000.0;

        Double weight = healthService.getLatestWeight(userId);

        // 9. Health score — reuse already-fetched logs (no duplicate query)
        HealthScoreResponse healthScoreResponse = healthService.getHealthScore(todayLogs);

        // 10. Category breakdown from DB aggregate
        List<Object[]> breakdownRows = carbonEntryRepository.categoryEmissionBreakdownByUserId(userId);
        Map<String, BigDecimal> categoryBreakdown = new LinkedHashMap<>();
        for (Object[] row : breakdownRows) {
            String category = (String) row[0];
            BigDecimal total = (BigDecimal) row[1];
            categoryBreakdown.put(category, total != null ? total.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO);
        }

        // 11. Recent activity — last 5 combined entries
        List<ActivityItem> recentActivity = buildRecentActivity(userId, todayRange[0], todayRange[1]);

        // 12. Eco tip based on day of year
        int dayOfYear = java.time.LocalDate.now(zoneId).getDayOfYear();
        String ecoTip = TIPS[dayOfYear % TIPS.length];

        return DashboardResponse.builder()
                .carbonToday(todayEmissions.setScale(2, RoundingMode.HALF_UP))
                .todayAvoided(todayAvoided.setScale(2, RoundingMode.HALF_UP))
                .monthEmissions(monthEmissions.setScale(2, RoundingMode.HALF_UP))
                .yearEmissions(yearEmissions.setScale(2, RoundingMode.HALF_UP))
                .budgetRemaining(budgetRemaining.setScale(2, RoundingMode.HALF_UP))
                .budgetUsedPercent(budgetUsedPercent)
                .treesNeeded(treesNeeded)
                .riskLevel(riskLevel)
                .riskPercentage(riskPercentage)
                .streakDays(streakDays)
                .bestStreak(bestStreak)
                .healthScore(healthScoreResponse.getScore())
                .stepsGoalMet(healthScoreResponse.getStepsGoalMet())
                .workoutDone(healthScoreResponse.getWorkoutDone())
                .sleepGoalMet(healthScoreResponse.getSleepGoalMet())
                .waterGoalMet(healthScoreResponse.getWaterGoalMet())
                .steps(steps)
                .calories(calories)
                .sleep(Math.round(sleep * 10.0) / 10.0)
                .water(Math.round(water * 100.0) / 100.0)
                .weight(weight)
                .categoryBreakdown(categoryBreakdown)
                .recentActivity(recentActivity)
                .ecoTip(ecoTip)
                .build();
    }

    /**
     * Get trend data for the dashboard chart.
     * Uses DB aggregates grouped by day — no in-memory loading.
     */
    @Transactional(readOnly = true)
    public DashboardTrendResponse getTrend(Long userId, String period) {
        String userTimezone = getUserTimezone(userId);
        ZoneId zoneId = timezoneService.getUserZoneId(userTimezone);

        Instant[] range = timezoneService.getPeriodRange(period, zoneId);

        // Fetch daily emission + avoided aggregates
        List<Object[]> emissionRows = carbonEntryRepository.dailyEmissionsByUserIdAndPeriod(userId, range[0], range[1]);
        List<Object[]> avoidedRows = carbonEntryRepository.dailyAvoidedByUserIdAndPeriod(userId, range[0], range[1]);

        // Build a map of date → avoided for quick lookup
        Map<String, BigDecimal> avoidedMap = new LinkedHashMap<>();
        for (Object[] row : avoidedRows) {
            String date = row[0].toString();
            BigDecimal total = (BigDecimal) row[1];
            avoidedMap.put(date, total != null ? total : BigDecimal.ZERO);
        }

        // Merge into trend data points
        List<TrendDataPoint> dataPoints = new ArrayList<>();
        for (Object[] row : emissionRows) {
            String date = row[0].toString();
            BigDecimal emissions = (BigDecimal) row[1];
            BigDecimal avoided = avoidedMap.getOrDefault(date, BigDecimal.ZERO);
            dataPoints.add(TrendDataPoint.builder()
                    .date(date)
                    .emissions(emissions != null ? emissions.setScale(4, RoundingMode.HALF_UP) : BigDecimal.ZERO)
                    .avoided(avoided.setScale(4, RoundingMode.HALF_UP))
                    .build());
        }

        return DashboardTrendResponse.builder()
                .period(period)
                .dataPoints(dataPoints)
                .build();
    }

    // ===== Private Helpers =====

    private List<ActivityItem> buildRecentActivity(Long userId, Instant todayStart, Instant todayEnd) {
        List<ActivityItem> items = new ArrayList<>();

        // Last 5 carbon entries today
        try {
            List<CarbonEntry> recentCarbon = carbonEntryRepository
                    .findByUserIdAndEntryDateBetween(userId, todayStart, todayEnd);
            recentCarbon.stream()
                    .sorted((a, b) -> b.getEntryDate().compareTo(a.getEntryDate()))
                    .limit(5)
                    .forEach(e -> items.add(ActivityItem.builder()
                            .id(e.getId())
                            .type("carbon")
                            .category(e.getCategory())
                            .description(e.getType())
                            .value(e.getCo2())
                            .timestamp(e.getEntryDate() != null ? e.getEntryDate().toString() : null)
                            .build()));
        } catch (Exception e) {
            // Graceful degradation — don't fail dashboard if this query errors
        }

        // Last 5 health logs today
        try {
            List<HealthLog> recentHealth = healthLogRepository
                    .findByUserIdAndEntryDateBetween(userId, todayStart, todayEnd);
            recentHealth.stream()
                    .sorted((a, b) -> b.getEntryDate().compareTo(a.getEntryDate()))
                    .limit(5)
                    .forEach(h -> items.add(ActivityItem.builder()
                            .id(h.getId())
                            .type("health")
                            .category(h.getType())
                            .description(formatHealthDescription(h))
                            .value(formatHealthValue(h))
                            .timestamp(h.getEntryDate() != null ? h.getEntryDate().toString() : null)
                            .build()));
        } catch (Exception e) {
            // Graceful degradation
        }

        // Sort combined by timestamp (newest first) and take top 5
        return items.stream()
                .sorted((a, b) -> b.getTimestamp() != null && a.getTimestamp() != null
                        ? b.getTimestamp().compareTo(a.getTimestamp()) : 0)
                .limit(5)
                .collect(Collectors.toList());
    }

    private String formatHealthDescription(HealthLog log) {
        if ("workout".equalsIgnoreCase(log.getType()) && log.getWorkoutType() != null) {
            return log.getWorkoutType();
        }
        return log.getType();
    }

    private BigDecimal formatHealthValue(HealthLog log) {
        if ("steps".equalsIgnoreCase(log.getType()) && log.getSteps() != null) {
            return BigDecimal.valueOf(log.getSteps());
        }
        if ("workout".equalsIgnoreCase(log.getType()) && log.getCalories() != null) {
            return BigDecimal.valueOf(log.getCalories());
        }
        if ("sleep".equalsIgnoreCase(log.getType()) && log.getHours() != null) {
            return BigDecimal.valueOf(log.getHours()).setScale(1, RoundingMode.HALF_UP);
        }
        if ("water".equalsIgnoreCase(log.getType()) && log.getWaterMl() != null) {
            return BigDecimal.valueOf(log.getWaterMl());
        }
        if ("weight".equalsIgnoreCase(log.getType()) && log.getWeight() != null) {
            return BigDecimal.valueOf(log.getWeight()).setScale(1, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO;
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

    private BigDecimal orZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
