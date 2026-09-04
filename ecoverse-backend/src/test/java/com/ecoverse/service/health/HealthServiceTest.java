package com.ecoverse.service.health;

import com.ecoverse.dto.health.*;
import com.ecoverse.exception.BadRequestException;
import com.ecoverse.model.HealthLog;
import com.ecoverse.model.User;
import com.ecoverse.repository.HealthLogRepository;
import com.ecoverse.repository.UserRepository;
import com.ecoverse.service.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * HealthService tests — Phase 3.
 *
 * Verifies:
 * - logHealth uses HealthEntryValidator
 * - getHealthLogs returns paginated results
 * - calculateBMI returns disclaimer
 * - getHealthScore correct computation
 * - calculateStreak delegates to repository
 * - getLatestWeight returns most recent
 */
@ExtendWith(MockitoExtension.class)
class HealthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private HealthLogRepository healthLogRepository;
    @Mock private TimezoneService timezoneService;
    @Mock private StreakService streakService;
    @Mock private HealthEntryValidator healthEntryValidator;

    @InjectMocks private HealthService healthService;

    private static final Long USER_ID = 1L;

    private User createTestUser() {
        User user = new User();
        user.setId(USER_ID);
        user.setTimezone("Asia/Kolkata");
        return user;
    }

    private ZoneId getZoneId() {
        return ZoneId.of("Asia/Kolkata");
    }

    private Instant[] getTodayRange() {
        ZoneId zoneId = getZoneId();
        return new Instant[]{
                java.time.LocalDate.now(zoneId).atStartOfDay(zoneId).toInstant(),
                java.time.LocalDate.now(zoneId).plusDays(1).atStartOfDay(zoneId).toInstant()
        };
    }

    private void stubUserTimezone() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(createTestUser()));
    }

    private void stubTimezoneNow() {
        when(timezoneService.now()).thenReturn(Instant.now());
    }

    private void stubTimezoneForLogs() {
        when(timezoneService.getUserZoneId("Asia/Kolkata")).thenReturn(getZoneId());
        when(timezoneService.getPeriodRange(eq("today"), eq(getZoneId()))).thenReturn(getTodayRange());
    }

    @Nested
    @DisplayName("logHealth")
    class LogHealth {

        @Test
        @DisplayName("Valid steps entry created successfully")
        void validStepsEntry() {
            stubUserTimezone();
            stubTimezoneNow();

            HealthLogRequest req = HealthLogRequest.builder().type("steps").steps(5000).build();
            doNothing().when(healthEntryValidator).validate(req);

            HealthLog savedLog = HealthLog.builder()
                    .id(1L).userId(USER_ID).type("steps").steps(5000)
                    .entryDate(Instant.now()).build();
            when(healthLogRepository.save(any(HealthLog.class))).thenReturn(savedLog);

            HealthLogResponse response = healthService.logHealth(USER_ID, req);

            assertThat(response).isNotNull();
            assertThat(response.getType()).isEqualTo("steps");
            assertThat(response.getSteps()).isEqualTo(5000);
            verify(healthEntryValidator).validate(req);
            verify(streakService).updateBestStreakIfNeeded(USER_ID);
        }

        @Test
        @DisplayName("Invalid type throws BadRequestException via validator")
        void invalidTypeThrowsViaValidator() {
            HealthLogRequest req = HealthLogRequest.builder().type("invalid").build();
            doThrow(new BadRequestException("Invalid health log type: invalid"))
                    .when(healthEntryValidator).validate(req);

            assertThatThrownBy(() -> healthService.logHealth(USER_ID, req))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Invalid health log type");

            verify(healthLogRepository, never()).save(any());
        }

        @Test
        @DisplayName("Workout calories auto-calculated when missing")
        void workoutCaloriesAutoCalculated() {
            stubUserTimezone();
            stubTimezoneNow();

            HealthLogRequest req = HealthLogRequest.builder()
                    .type("workout").duration(30).intensity("high").calories(0).build();

            doNothing().when(healthEntryValidator).validate(req);

            when(healthLogRepository.save(any(HealthLog.class))).thenAnswer(invocation -> {
                HealthLog log = invocation.getArgument(0);
                log.setId(2L);
                return log;
            });

            healthService.logHealth(USER_ID, req);

            verify(healthLogRepository).save(argThat(log ->
                    log.getCalories() != null && log.getCalories() == 300)); // 10 * 30 = 300 for high intensity
        }
    }

    @Nested
    @DisplayName("getHealthLogs — pagination")
    class GetHealthLogs {

        @Test
        @DisplayName("Returns paginated results")
        void returnsPaginatedResults() {
            stubUserTimezone();
            stubTimezoneForLogs();

            java.util.List<HealthLog> logs = Arrays.asList(
                    HealthLog.builder().id(1L).userId(USER_ID).type("steps").steps(5000).entryDate(Instant.now()).build(),
                    HealthLog.builder().id(2L).userId(USER_ID).type("water").waterMl(500).entryDate(Instant.now()).build()
            );
            Page<HealthLog> page = new PageImpl<>(logs);

            when(healthLogRepository.findByUserIdAndEntryDateBetween(
                    eq(USER_ID), any(Instant.class), any(Instant.class), any(Pageable.class)))
                    .thenReturn(page);

            Page<HealthLogResponse> result = healthService.getHealthLogs(USER_ID, null, "today", 0, 20);

            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getContent().get(0).getType()).isEqualTo("steps");
        }

        @Test
        @DisplayName("Filters by type and uses combined query")
        void filtersByType() {
            stubUserTimezone();
            stubTimezoneForLogs();

            java.util.List<HealthLog> logs = Collections.singletonList(
                    HealthLog.builder().id(1L).userId(USER_ID).type("steps").steps(5000).entryDate(Instant.now()).build()
            );
            Page<HealthLog> page = new PageImpl<>(logs);

            when(healthLogRepository.findByUserIdAndTypeAndEntryDateBetween(
                    eq(USER_ID), eq("steps"), any(Instant.class), any(Instant.class), any(Pageable.class)))
                    .thenReturn(page);

            Page<HealthLogResponse> result = healthService.getHealthLogs(USER_ID, "steps", "today", 0, 20);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getType()).isEqualTo("steps");
        }
    }

    @Nested
    @DisplayName("calculateBMI")
    class CalculateBMI {

        @Test
        @DisplayName("Correct BMI calculation with disclaimer")
        void correctBMIWithDisclaimer() {
            BMIRequest req = BMIRequest.builder().weight(70.0).height(175.0).build();

            BMIResponse response = healthService.calculateBMI(req);

            assertThat(response.getBmi()).isNotNull();
            assertThat(response.getBmi()).isCloseTo(22.9, org.assertj.core.data.Offset.offset(0.1));
            assertThat(response.getCategory()).isEqualTo("Normal");
            assertThat(response.getColor()).isEqualTo("#10B981");
            assertThat(response.getDisclaimer()).isNotBlank();
            assertThat(response.getDisclaimer()).contains("informational");
        }

        @Test
        @DisplayName("Zero height throws validation error")
        void zeroHeightThrows() {
            BMIRequest req = BMIRequest.builder().weight(70.0).height(0.0).build();

            assertThatThrownBy(() -> healthService.calculateBMI(req))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("positive");
        }

        @Test
        @DisplayName("Underweight category")
        void underweightCategory() {
            BMIRequest req = BMIRequest.builder().weight(45.0).height(170.0).build();

            BMIResponse response = healthService.calculateBMI(req);

            assertThat(response.getCategory()).isEqualTo("Underweight");
        }

        @Test
        @DisplayName("Obese category")
        void obeseCategory() {
            BMIRequest req = BMIRequest.builder().weight(120.0).height(170.0).build();

            BMIResponse response = healthService.calculateBMI(req);

            assertThat(response.getCategory()).isEqualTo("Obese");
        }
    }

    @Nested
    @DisplayName("getHealthScore")
    class GetHealthScore {

        @Test
        @DisplayName("Base score is 50 with no activity")
        void baseScoreNoActivity() {
            java.util.List<HealthLog> logs = Collections.emptyList();

            HealthScoreResponse response = healthService.getHealthScore(logs);

            assertThat(response.getScore()).isEqualTo(50);
            assertThat(response.getStepsGoalMet()).isFalse();
            assertThat(response.getWorkoutDone()).isFalse();
            assertThat(response.getSleepGoalMet()).isFalse();
            assertThat(response.getWaterGoalMet()).isFalse();
        }

        @Test
        @DisplayName("All goals met → score 100")
        void allGoalsMetScore100() {
            java.util.List<HealthLog> logs = Arrays.asList(
                    HealthLog.builder().type("steps").steps(10000).build(),
                    HealthLog.builder().type("workout").calories(300).build(),
                    HealthLog.builder().type("sleep").hours(8.0).build(),
                    HealthLog.builder().type("water").waterMl(3000).build()
            );

            HealthScoreResponse response = healthService.getHealthScore(logs);

            assertThat(response.getScore()).isEqualTo(100);
            assertThat(response.getStepsGoalMet()).isTrue();
            assertThat(response.getWorkoutDone()).isTrue();
            assertThat(response.getSleepGoalMet()).isTrue();
            assertThat(response.getWaterGoalMet()).isTrue();
        }

        @Test
        @DisplayName("Partial goals → score 80")
        void partialGoalsScore80() {
            java.util.List<HealthLog> logs = Arrays.asList(
                    HealthLog.builder().type("steps").steps(10000).build(),       // +15 → 65
                    HealthLog.builder().type("workout").calories(300).build(),     // +15 → 80
                    HealthLog.builder().type("sleep").hours(5.0).build(),         // < 7, no +
                    HealthLog.builder().type("water").waterMl(2000).build()       // < 3000, no +
            );

            HealthScoreResponse response = healthService.getHealthScore(logs);

            assertThat(response.getScore()).isEqualTo(80);
        }
    }

    @Nested
    @DisplayName("calculateStreak")
    class CalculateStreak {

        @Test
        @DisplayName("Delegates to repository")
        void delegatesToRepository() {
            stubUserTimezone();

            when(healthLogRepository.calculateCurrentStreak(USER_ID, "Asia/Kolkata")).thenReturn(7);

            Integer streak = healthService.calculateStreak(USER_ID);

            assertThat(streak).isEqualTo(7);
        }

        @Test
        @DisplayName("Returns 0 when repository returns null")
        void returnsZeroWhenNull() {
            stubUserTimezone();

            when(healthLogRepository.calculateCurrentStreak(USER_ID, "Asia/Kolkata")).thenReturn(null);

            Integer streak = healthService.calculateStreak(USER_ID);

            assertThat(streak).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("getLatestWeight")
    class GetLatestWeight {

        @Test
        @DisplayName("Returns most recent weight")
        void returnsMostRecent() {
            HealthLog log = HealthLog.builder().weight(72.5).build();
            when(healthLogRepository.findTopByUserIdAndTypeOrderByEntryDateDesc(USER_ID, "weight"))
                    .thenReturn(Optional.of(log));

            Double weight = healthService.getLatestWeight(USER_ID);

            assertThat(weight).isEqualTo(72.5);
        }

        @Test
        @DisplayName("Returns null when no weight logs")
        void returnsNullWhenNone() {
            when(healthLogRepository.findTopByUserIdAndTypeOrderByEntryDateDesc(USER_ID, "weight"))
                    .thenReturn(Optional.empty());

            Double weight = healthService.getLatestWeight(USER_ID);

            assertThat(weight).isNull();
        }
    }
}
