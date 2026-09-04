package com.ecoverse.service.dashboard;

import com.ecoverse.dto.dashboard.ActivityItem;
import com.ecoverse.dto.dashboard.DashboardResponse;
import com.ecoverse.dto.dashboard.DashboardTrendResponse;
import com.ecoverse.dto.health.HealthScoreResponse;
import com.ecoverse.model.CarbonEntry;
import com.ecoverse.model.HealthLog;
import com.ecoverse.model.User;
import com.ecoverse.repository.CarbonEntryRepository;
import com.ecoverse.repository.HealthLogRepository;
import com.ecoverse.repository.UserRepository;
import com.ecoverse.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * DashboardService tests — Phase E.
 *
 * Verifies:
 * - All dashboard fields populated from server data
 * - No state mutation in GET (bestStreak not saved)
 * - Efficient streak via StreakService (no N+1)
 * - Health score from HealthService with pre-fetched logs
 * - Category breakdown from DB aggregate
 * - Trend data from DB aggregates
 */
@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock private CarbonEntryRepository carbonEntryRepository;
    @Mock private HealthLogRepository healthLogRepository;
    @Mock private UserRepository userRepository;
    @Mock private HealthService healthService;
    @Mock private RiskService riskService;
    @Mock private TimezoneService timezoneService;
    @Mock private StreakService streakService;

    @InjectMocks private DashboardService dashboardService;

    private User testUser;
    private static final Long USER_ID = 1L;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(USER_ID);
        testUser.setName("Test User");
        testUser.setTimezone("Asia/Kolkata");
        testUser.setCarbonBudget(new BigDecimal("4.20"));
        testUser.setBestStreak(5);
    }

    private void setupTimezoneMocks() {
        ZoneId zoneId = ZoneId.of("Asia/Kolkata");
        Instant now = Instant.now();
        Instant todayStart = LocalDate.now(zoneId).atStartOfDay(zoneId).toInstant();
        Instant todayEnd = LocalDate.now(zoneId).plusDays(1).atStartOfDay(zoneId).toInstant();
        Instant monthStart = LocalDate.now(zoneId).withDayOfMonth(1).atStartOfDay(zoneId).toInstant();
        Instant yearStart = LocalDate.now(zoneId).withDayOfYear(1).atStartOfDay(zoneId).toInstant();

        when(timezoneService.getUserZoneId("Asia/Kolkata")).thenReturn(zoneId);
        when(timezoneService.getTodayRange(zoneId)).thenReturn(new Instant[]{todayStart, todayEnd});
        when(timezoneService.getMonthRange(zoneId)).thenReturn(new Instant[]{monthStart, todayEnd});
        when(timezoneService.getYearRange(zoneId)).thenReturn(new Instant[]{yearStart, todayEnd});
    }

    @Nested
    @DisplayName("getDashboard — full data assembly")
    class GetDashboard {

        @Test
        @DisplayName("Returns all fields for user with data")
        void returnsAllFields() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(testUser));
            setupTimezoneMocks();

            // Carbon aggregates
            when(carbonEntryRepository.sumEmissionsByUserIdAndPeriod(eq(USER_ID), any(), any()))
                    .thenReturn(new BigDecimal("2.50"));
            when(carbonEntryRepository.sumAvoidedByUserIdAndPeriod(eq(USER_ID), any(), any()))
                    .thenReturn(new BigDecimal("0.30"));

            // Risk
            when(riskService.getRiskLevel(any(BigDecimal.class), any(BigDecimal.class))).thenReturn("GOOD");
            when(riskService.calculateTreesNeeded(any(BigDecimal.class))).thenReturn(3);

            // Streak
            when(streakService.getCurrentStreak(USER_ID, "Asia/Kolkata")).thenReturn(7);
            when(streakService.getBestStreak(USER_ID, "Asia/Kolkata")).thenReturn(10);

            // Health logs — empty today
            when(healthLogRepository.findByUserIdAndEntryDateBetween(eq(USER_ID), any(), any()))
                    .thenReturn(Collections.emptyList());
            when(healthService.getLatestWeight(USER_ID)).thenReturn(70.0);
            when(healthService.getHealthScore(anyList())).thenReturn(
                    new HealthScoreResponse(65, true, false, false, false));

            // Category breakdown
            List<Object[]> breakdown = new ArrayList<>();
            breakdown.add(new Object[]{"transport", new BigDecimal("1.50")});
            breakdown.add(new Object[]{"energy", new BigDecimal("1.00")});
            when(carbonEntryRepository.categoryEmissionBreakdownByUserId(USER_ID)).thenReturn(breakdown);

            DashboardResponse response = dashboardService.getDashboard(USER_ID);

            assertThat(response).isNotNull();
            assertThat(response.getCarbonToday()).isEqualByComparingTo("2.50");
            assertThat(response.getTodayAvoided()).isEqualByComparingTo("0.30");
            assertThat(response.getRiskLevel()).isEqualTo("GOOD");
            assertThat(response.getStreakDays()).isEqualTo(7);
            assertThat(response.getBestStreak()).isEqualTo(10);
            assertThat(response.getHealthScore()).isEqualTo(65);
            assertThat(response.getStepsGoalMet()).isTrue();
            assertThat(response.getWorkoutDone()).isFalse();
            assertThat(response.getTreesNeeded()).isEqualTo(3);
            assertThat(response.getCategoryBreakdown()).isNotNull();
            assertThat(response.getCategoryBreakdown()).containsEntry("transport", new BigDecimal("1.50"));
            assertThat(response.getRecentActivity()).isNotNull();
            assertThat(response.getEcoTip()).isNotBlank();
        }

        @Test
        @DisplayName("Zero emissions → risk level EXCELLENT")
        void zeroEmissions_riskExcellent() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(testUser));
            setupTimezoneMocks();

            when(carbonEntryRepository.sumEmissionsByUserIdAndPeriod(eq(USER_ID), any(), any()))
                    .thenReturn(null);
            when(carbonEntryRepository.sumAvoidedByUserIdAndPeriod(eq(USER_ID), any(), any()))
                    .thenReturn(null);
            when(riskService.getRiskLevel(BigDecimal.ZERO, new BigDecimal("4.20"))).thenReturn("EXCELLENT");
            when(riskService.calculateTreesNeeded(BigDecimal.ZERO)).thenReturn(0);
            when(streakService.getCurrentStreak(USER_ID, "Asia/Kolkata")).thenReturn(0);
            when(streakService.getBestStreak(USER_ID, "Asia/Kolkata")).thenReturn(0);
            when(healthLogRepository.findByUserIdAndEntryDateBetween(eq(USER_ID), any(), any()))
                    .thenReturn(Collections.emptyList());
            when(healthService.getLatestWeight(USER_ID)).thenReturn(null);
            when(healthService.getHealthScore(anyList())).thenReturn(
                    new HealthScoreResponse(50, false, false, false, false));
            when(carbonEntryRepository.categoryEmissionBreakdownByUserId(USER_ID))
                    .thenReturn(Collections.emptyList());

            DashboardResponse response = dashboardService.getDashboard(USER_ID);

            assertThat(response.getCarbonToday()).isEqualByComparingTo("0.00");
            assertThat(response.getRiskLevel()).isEqualTo("EXCELLENT");
            assertThat(response.getStreakDays()).isEqualTo(0);
        }

        @Test
        @DisplayName("NO state mutation — bestStreak NOT saved to user in GET")
        void noStateMutation_getDashboard() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(testUser));
            setupTimezoneMocks();

            when(carbonEntryRepository.sumEmissionsByUserIdAndPeriod(eq(USER_ID), any(), any()))
                    .thenReturn(BigDecimal.ZERO);
            when(carbonEntryRepository.sumAvoidedByUserIdAndPeriod(eq(USER_ID), any(), any()))
                    .thenReturn(BigDecimal.ZERO);
            when(riskService.getRiskLevel(any(), any())).thenReturn("EXCELLENT");
            when(riskService.calculateTreesNeeded(any())).thenReturn(0);
            when(streakService.getCurrentStreak(USER_ID, "Asia/Kolkata")).thenReturn(15);
            when(streakService.getBestStreak(USER_ID, "Asia/Kolkata")).thenReturn(15);
            when(healthLogRepository.findByUserIdAndEntryDateBetween(eq(USER_ID), any(), any()))
                    .thenReturn(Collections.emptyList());
            when(healthService.getLatestWeight(USER_ID)).thenReturn(null);
            when(healthService.getHealthScore(anyList())).thenReturn(
                    new HealthScoreResponse(50, false, false, false, false));
            when(carbonEntryRepository.categoryEmissionBreakdownByUserId(USER_ID))
                    .thenReturn(Collections.emptyList());

            dashboardService.getDashboard(USER_ID);

            // Verify userRepository.save() was NEVER called in GET
            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("Budget remaining calculated correctly")
        void budgetRemainingCalculated() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(testUser));
            setupTimezoneMocks();

            when(carbonEntryRepository.sumEmissionsByUserIdAndPeriod(eq(USER_ID), any(), any()))
                    .thenReturn(new BigDecimal("3.00"));
            when(carbonEntryRepository.sumAvoidedByUserIdAndPeriod(eq(USER_ID), any(), any()))
                    .thenReturn(BigDecimal.ZERO);
            when(riskService.getRiskLevel(any(), any())).thenReturn("MODERATE");
            when(riskService.calculateTreesNeeded(any())).thenReturn(0);
            when(streakService.getCurrentStreak(USER_ID, "Asia/Kolkata")).thenReturn(0);
            when(streakService.getBestStreak(USER_ID, "Asia/Kolkata")).thenReturn(0);
            when(healthLogRepository.findByUserIdAndEntryDateBetween(eq(USER_ID), any(), any()))
                    .thenReturn(Collections.emptyList());
            when(healthService.getLatestWeight(USER_ID)).thenReturn(null);
            when(healthService.getHealthScore(anyList())).thenReturn(
                    new HealthScoreResponse(50, false, false, false, false));
            when(carbonEntryRepository.categoryEmissionBreakdownByUserId(USER_ID))
                    .thenReturn(Collections.emptyList());

            DashboardResponse response = dashboardService.getDashboard(USER_ID);

            assertThat(response.getBudgetRemaining()).isEqualByComparingTo("1.20");
            assertThat(response.getBudgetUsedPercent()).isEqualByComparingTo("71.43");
        }

        @Test
        @DisplayName("Category breakdown populated from DB aggregate")
        void categoryBreakdownPopulated() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(testUser));
            setupTimezoneMocks();

            when(carbonEntryRepository.sumEmissionsByUserIdAndPeriod(eq(USER_ID), any(), any()))
                    .thenReturn(BigDecimal.ZERO);
            when(carbonEntryRepository.sumAvoidedByUserIdAndPeriod(eq(USER_ID), any(), any()))
                    .thenReturn(BigDecimal.ZERO);
            when(riskService.getRiskLevel(any(), any())).thenReturn("EXCELLENT");
            when(riskService.calculateTreesNeeded(any())).thenReturn(0);
            when(streakService.getCurrentStreak(USER_ID, "Asia/Kolkata")).thenReturn(0);
            when(streakService.getBestStreak(USER_ID, "Asia/Kolkata")).thenReturn(0);
            when(healthLogRepository.findByUserIdAndEntryDateBetween(eq(USER_ID), any(), any()))
                    .thenReturn(Collections.emptyList());
            when(healthService.getLatestWeight(USER_ID)).thenReturn(null);
            when(healthService.getHealthScore(anyList())).thenReturn(
                    new HealthScoreResponse(50, false, false, false, false));

            List<Object[]> breakdown = new ArrayList<>();
            breakdown.add(new Object[]{"transport", new BigDecimal("5.00")});
            breakdown.add(new Object[]{"food", new BigDecimal("3.00")});
            breakdown.add(new Object[]{"energy", new BigDecimal("2.00")});
            when(carbonEntryRepository.categoryEmissionBreakdownByUserId(USER_ID)).thenReturn(breakdown);

            DashboardResponse response = dashboardService.getDashboard(USER_ID);

            assertThat(response.getCategoryBreakdown()).hasSize(3);
            assertThat(response.getCategoryBreakdown().get("transport")).isEqualByComparingTo("5.00");
            assertThat(response.getCategoryBreakdown().get("food")).isEqualByComparingTo("3.00");
        }

        @Test
        @DisplayName("Eco tip rotates based on day of year")
        void ecoTipNotNull() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(testUser));
            setupTimezoneMocks();

            when(carbonEntryRepository.sumEmissionsByUserIdAndPeriod(eq(USER_ID), any(), any()))
                    .thenReturn(BigDecimal.ZERO);
            when(carbonEntryRepository.sumAvoidedByUserIdAndPeriod(eq(USER_ID), any(), any()))
                    .thenReturn(BigDecimal.ZERO);
            when(riskService.getRiskLevel(any(), any())).thenReturn("EXCELLENT");
            when(riskService.calculateTreesNeeded(any())).thenReturn(0);
            when(streakService.getCurrentStreak(USER_ID, "Asia/Kolkata")).thenReturn(0);
            when(streakService.getBestStreak(USER_ID, "Asia/Kolkata")).thenReturn(0);
            when(healthLogRepository.findByUserIdAndEntryDateBetween(eq(USER_ID), any(), any()))
                    .thenReturn(Collections.emptyList());
            when(healthService.getLatestWeight(USER_ID)).thenReturn(null);
            when(healthService.getHealthScore(anyList())).thenReturn(
                    new HealthScoreResponse(50, false, false, false, false));
            when(carbonEntryRepository.categoryEmissionBreakdownByUserId(USER_ID))
                    .thenReturn(Collections.emptyList());

            DashboardResponse response = dashboardService.getDashboard(USER_ID);

            assertThat(response.getEcoTip()).isNotBlank();
            assertThat(response.getEcoTip().length()).isGreaterThan(10);
        }

        @Test
        @DisplayName("Recent activity list populated")
        void recentActivityPopulated() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(testUser));
            setupTimezoneMocks();

            when(carbonEntryRepository.sumEmissionsByUserIdAndPeriod(eq(USER_ID), any(), any()))
                    .thenReturn(BigDecimal.ZERO);
            when(carbonEntryRepository.sumAvoidedByUserIdAndPeriod(eq(USER_ID), any(), any()))
                    .thenReturn(BigDecimal.ZERO);
            when(riskService.getRiskLevel(any(), any())).thenReturn("EXCELLENT");
            when(riskService.calculateTreesNeeded(any())).thenReturn(0);
            when(streakService.getCurrentStreak(USER_ID, "Asia/Kolkata")).thenReturn(0);
            when(streakService.getBestStreak(USER_ID, "Asia/Kolkata")).thenReturn(0);
            when(healthLogRepository.findByUserIdAndEntryDateBetween(eq(USER_ID), any(), any()))
                    .thenReturn(Collections.emptyList());
            when(healthService.getLatestWeight(USER_ID)).thenReturn(null);
            when(healthService.getHealthScore(anyList())).thenReturn(
                    new HealthScoreResponse(50, false, false, false, false));
            when(carbonEntryRepository.categoryEmissionBreakdownByUserId(USER_ID))
                    .thenReturn(Collections.emptyList());

            // Carbon entries for activity
            CarbonEntry entry = CarbonEntry.builder()
                    .id(1L).userId(USER_ID).category("transport").type("car-petrol")
                    .co2(new BigDecimal("1.50")).entryDate(Instant.now()).build();
            when(carbonEntryRepository.findByUserIdAndEntryDateBetween(eq(USER_ID), any(), any()))
                    .thenReturn(List.of(entry));

            DashboardResponse response = dashboardService.getDashboard(USER_ID);

            assertThat(response.getRecentActivity()).isNotNull();
            assertThat(response.getRecentActivity()).hasSize(1);
            assertThat(response.getRecentActivity().get(0).getType()).isEqualTo("carbon");
            assertThat(response.getRecentActivity().get(0).getCategory()).isEqualTo("transport");
        }
    }

    @Nested
    @DisplayName("getTrend — chart data from DB aggregates")
    class GetTrend {

        @Test
        @DisplayName("Trend data returns correct period range")
        void trendDataReturnsCorrectPeriod() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(testUser));
            ZoneId zoneId = ZoneId.of("Asia/Kolkata");
            when(timezoneService.getUserZoneId("Asia/Kolkata")).thenReturn(zoneId);
            when(timezoneService.getPeriodRange("week", zoneId))
                    .thenReturn(new Instant[]{Instant.now().minusSeconds(604800), Instant.now()});

            when(carbonEntryRepository.dailyEmissionsByUserIdAndPeriod(eq(USER_ID), any(), any()))
                    .thenReturn(Collections.emptyList());
            when(carbonEntryRepository.dailyAvoidedByUserIdAndPeriod(eq(USER_ID), any(), any()))
                    .thenReturn(Collections.emptyList());

            DashboardTrendResponse response = dashboardService.getTrend(USER_ID, "week");

            assertThat(response).isNotNull();
            assertThat(response.getPeriod()).isEqualTo("week");
            assertThat(response.getDataPoints()).isNotNull();
        }

        @Test
        @DisplayName("Trend data merges emission and avoided points")
        void trendDataMergesEmissionsAndAvoided() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(testUser));
            ZoneId zoneId = ZoneId.of("Asia/Kolkata");
            when(timezoneService.getUserZoneId("Asia/Kolkata")).thenReturn(zoneId);
            when(timezoneService.getPeriodRange("week", zoneId))
                    .thenReturn(new Instant[]{Instant.now().minusSeconds(604800), Instant.now()});

            // Emission data
            List<Object[]> emissionRows = new ArrayList<>();
            emissionRows.add(new Object[]{java.sql.Date.valueOf("2024-01-15"), new BigDecimal("2.50")});
            emissionRows.add(new Object[]{java.sql.Date.valueOf("2024-01-16"), new BigDecimal("1.80")});
            when(carbonEntryRepository.dailyEmissionsByUserIdAndPeriod(eq(USER_ID), any(), any()))
                    .thenReturn(emissionRows);

            // Avoided data
            List<Object[]> avoidedRows = new ArrayList<>();
            avoidedRows.add(new Object[]{java.sql.Date.valueOf("2024-01-15"), new BigDecimal("0.30")});
            when(carbonEntryRepository.dailyAvoidedByUserIdAndPeriod(eq(USER_ID), any(), any()))
                    .thenReturn(avoidedRows);

            DashboardTrendResponse response = dashboardService.getTrend(USER_ID, "week");

            assertThat(response.getDataPoints()).hasSize(2);
            assertThat(response.getDataPoints().get(0).getEmissions()).isEqualByComparingTo("2.5000");
            assertThat(response.getDataPoints().get(0).getAvoided()).isEqualByComparingTo("0.3000");
            assertThat(response.getDataPoints().get(1).getAvoided()).isEqualByComparingTo("0.0000");
        }
    }

    @Nested
    @DisplayName("Health data from today's logs")
    class HealthData {

        @Test
        @DisplayName("Steps, calories, sleep, water computed from today's logs")
        void healthMetricsFromLogs() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(testUser));
            setupTimezoneMocks();

            when(carbonEntryRepository.sumEmissionsByUserIdAndPeriod(eq(USER_ID), any(), any()))
                    .thenReturn(BigDecimal.ZERO);
            when(carbonEntryRepository.sumAvoidedByUserIdAndPeriod(eq(USER_ID), any(), any()))
                    .thenReturn(BigDecimal.ZERO);
            when(riskService.getRiskLevel(any(), any())).thenReturn("EXCELLENT");
            when(riskService.calculateTreesNeeded(any())).thenReturn(0);
            when(streakService.getCurrentStreak(USER_ID, "Asia/Kolkata")).thenReturn(0);
            when(streakService.getBestStreak(USER_ID, "Asia/Kolkata")).thenReturn(0);

            // Create health logs
            HealthLog stepsLog = HealthLog.builder().id(1L).userId(USER_ID).type("steps").steps(8000).entryDate(Instant.now()).build();
            HealthLog workoutLog = HealthLog.builder().id(2L).userId(USER_ID).type("workout").calories(350).entryDate(Instant.now()).build();
            HealthLog sleepLog = HealthLog.builder().id(3L).userId(USER_ID).type("sleep").hours(7.5).entryDate(Instant.now()).build();
            HealthLog waterLog = HealthLog.builder().id(4L).userId(USER_ID).type("water").waterMl(2500).entryDate(Instant.now()).build();
            List<HealthLog> logs = Arrays.asList(stepsLog, workoutLog, sleepLog, waterLog);

            when(healthLogRepository.findByUserIdAndEntryDateBetween(eq(USER_ID), any(), any()))
                    .thenReturn(logs);
            when(healthService.getLatestWeight(USER_ID)).thenReturn(72.0);
            when(healthService.getHealthScore(logs)).thenReturn(
                    new HealthScoreResponse(80, false, true, true, false));
            when(carbonEntryRepository.categoryEmissionBreakdownByUserId(USER_ID))
                    .thenReturn(Collections.emptyList());

            DashboardResponse response = dashboardService.getDashboard(USER_ID);

            assertThat(response.getSteps()).isEqualTo(8000);
            assertThat(response.getCalories()).isEqualTo(350);
            assertThat(response.getSleep()).isEqualTo(7.5);
            assertThat(response.getWater()).isEqualTo(2.5);
            assertThat(response.getWeight()).isEqualTo(72.0);
            assertThat(response.getHealthScore()).isEqualTo(80);
            assertThat(response.getWorkoutDone()).isTrue();
            assertThat(response.getSleepGoalMet()).isTrue();
        }
    }
}
