package com.ecoverse.service.dashboard;

import com.ecoverse.model.User;
import com.ecoverse.repository.HealthLogRepository;
import com.ecoverse.repository.UserRepository;
import com.ecoverse.service.StreakService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * StreakService tests — Phase E.
 *
 * Verifies:
 * - Current streak from SQL window function (delegated to repository)
 * - Best streak from SQL window function
 * - Best streak update on mutation (only when current > best)
 * - No best streak update when current <= best
 */
@ExtendWith(MockitoExtension.class)
class StreakServiceTest {

    @Mock private HealthLogRepository healthLogRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks private StreakService streakService;

    private static final Long USER_ID = 1L;

    @Nested
    @DisplayName("getCurrentStreak")
    class GetCurrentStreak {

        @Test
        @DisplayName("Returns streak from repository")
        void returnsStreakFromRepository() {
            when(healthLogRepository.calculateCurrentStreak(USER_ID, "Asia/Kolkata")).thenReturn(7);

            Integer streak = streakService.getCurrentStreak(USER_ID, "Asia/Kolkata");

            assertThat(streak).isEqualTo(7);
        }

        @Test
        @DisplayName("Returns 0 when repository returns null")
        void returnsZeroWhenNull() {
            when(healthLogRepository.calculateCurrentStreak(USER_ID, "Asia/Kolkata")).thenReturn(null);

            Integer streak = streakService.getCurrentStreak(USER_ID, "Asia/Kolkata");

            assertThat(streak).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("getBestStreak")
    class GetBestStreak {

        @Test
        @DisplayName("Returns best streak from repository")
        void returnsBestStreakFromRepository() {
            when(healthLogRepository.calculateBestStreak(USER_ID, "Asia/Kolkata")).thenReturn(14);

            Integer best = streakService.getBestStreak(USER_ID, "Asia/Kolkata");

            assertThat(best).isEqualTo(14);
        }

        @Test
        @DisplayName("Returns 0 when repository returns null")
        void returnsZeroWhenNull() {
            when(healthLogRepository.calculateBestStreak(USER_ID, "Asia/Kolkata")).thenReturn(null);

            Integer best = streakService.getBestStreak(USER_ID, "Asia/Kolkata");

            assertThat(best).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("updateBestStreakIfNeeded")
    class UpdateBestStreak {

        @Test
        @DisplayName("Updates best streak when current exceeds it")
        void updatesBestStreakWhenCurrentExceeds() {
            User user = createUser("Asia/Kolkata");
            user.setBestStreak(5);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(healthLogRepository.calculateCurrentStreak(USER_ID, "Asia/Kolkata")).thenReturn(10);

            streakService.updateBestStreakIfNeeded(USER_ID);

            assertThat(user.getBestStreak()).isEqualTo(10);
            verify(userRepository).save(user);
        }

        @Test
        @DisplayName("Does NOT update when current streak equals best")
        void doesNotUpdateWhenEqual() {
            User user = createUser("Asia/Kolkata");
            user.setBestStreak(7);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(healthLogRepository.calculateCurrentStreak(USER_ID, "Asia/Kolkata")).thenReturn(7);

            streakService.updateBestStreakIfNeeded(USER_ID);

            assertThat(user.getBestStreak()).isEqualTo(7);
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Does NOT update when current streak is less than best")
        void doesNotUpdateWhenLess() {
            User user = createUser("Asia/Kolkata");
            user.setBestStreak(15);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(healthLogRepository.calculateCurrentStreak(USER_ID, "Asia/Kolkata")).thenReturn(3);

            streakService.updateBestStreakIfNeeded(USER_ID);

            assertThat(user.getBestStreak()).isEqualTo(15);
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Handles null bestStreak on user (defaults to 0)")
        void handlesNullBestStreak() {
            User user = createUser("Asia/Kolkata");
            user.setBestStreak(null);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(healthLogRepository.calculateCurrentStreak(USER_ID, "Asia/Kolkata")).thenReturn(1);

            streakService.updateBestStreakIfNeeded(USER_ID);

            assertThat(user.getBestStreak()).isEqualTo(1);
            verify(userRepository).save(user);
        }
    }

    private User createUser(String timezone) {
        User user = new User();
        user.setId(USER_ID);
        user.setTimezone(timezone);
        user.setBestStreak(0);
        return user;
    }
}
