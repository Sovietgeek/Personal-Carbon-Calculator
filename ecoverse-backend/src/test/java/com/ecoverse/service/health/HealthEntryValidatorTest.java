package com.ecoverse.service.health;

import com.ecoverse.dto.health.HealthLogRequest;
import com.ecoverse.exception.BadRequestException;
import com.ecoverse.service.HealthEntryValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * HealthEntryValidator tests — Phase 3.
 *
 * Verifies all validation boundaries for health log inputs:
 * - Steps: 1–100000
 * - Workout: duration 1–600 min
 * - Weight: 2–300 kg
 * - Height: 30–300 cm
 * - Sleep: 0.1–24 hours
 * - Water: 1–20000 ml
 * - Body fat: 0–70%
 * - Distance: 0–200 km
 * - Type whitelist, intensity whitelist, quality whitelist
 */
@ExtendWith(MockitoExtension.class)
class HealthEntryValidatorTest {

    private HealthEntryValidator validator;

    @BeforeEach
    void setUp() {
        validator = new HealthEntryValidator();
    }

    @Nested
    @DisplayName("Steps validation")
    class StepsValidation {
        @Test
        @DisplayName("Valid steps entry passes")
        void validSteps() {
            HealthLogRequest req = HealthLogRequest.builder().type("steps").steps(5000).build();
            validator.validate(req); // no exception
        }

        @Test
        @DisplayName("Null steps throws")
        void nullSteps() {
            HealthLogRequest req = HealthLogRequest.builder().type("steps").steps(null).build();
            assertThatThrownBy(() -> validator.validate(req))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("required");
        }

        @Test
        @DisplayName("Zero steps throws")
        void zeroSteps() {
            HealthLogRequest req = HealthLogRequest.builder().type("steps").steps(0).build();
            assertThatThrownBy(() -> validator.validate(req))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("positive");
        }

        @Test
        @DisplayName("Negative steps throws")
        void negativeSteps() {
            HealthLogRequest req = HealthLogRequest.builder().type("steps").steps(-100).build();
            assertThatThrownBy(() -> validator.validate(req))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        @DisplayName("Max steps (100000) passes")
        void maxSteps() {
            HealthLogRequest req = HealthLogRequest.builder().type("steps").steps(100000).build();
            validator.validate(req);
        }

        @Test
        @DisplayName("Steps exceeding max throws")
        void exceedsMaxSteps() {
            HealthLogRequest req = HealthLogRequest.builder().type("steps").steps(100001).build();
            assertThatThrownBy(() -> validator.validate(req))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("maximum");
        }
    }

    @Nested
    @DisplayName("Workout validation")
    class WorkoutValidation {
        @Test
        @DisplayName("Valid workout entry passes")
        void validWorkout() {
            HealthLogRequest req = HealthLogRequest.builder().type("workout").duration(30).intensity("moderate").build();
            validator.validate(req);
        }

        @Test
        @DisplayName("Null duration throws")
        void nullDuration() {
            HealthLogRequest req = HealthLogRequest.builder().type("workout").duration(null).build();
            assertThatThrownBy(() -> validator.validate(req))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("required");
        }

        @Test
        @DisplayName("Zero duration throws")
        void zeroDuration() {
            HealthLogRequest req = HealthLogRequest.builder().type("workout").duration(0).build();
            assertThatThrownBy(() -> validator.validate(req))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        @DisplayName("Duration exceeding max (600 min) throws")
        void exceedsMaxDuration() {
            HealthLogRequest req = HealthLogRequest.builder().type("workout").duration(601).build();
            assertThatThrownBy(() -> validator.validate(req))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("maximum");
        }

        @Test
        @DisplayName("Invalid intensity throws")
        void invalidIntensity() {
            HealthLogRequest req = HealthLogRequest.builder().type("workout").duration(30).intensity("superhuman").build();
            assertThatThrownBy(() -> validator.validate(req))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Invalid intensity");
        }
    }

    @Nested
    @DisplayName("Weight validation")
    class WeightValidation {
        @Test
        @DisplayName("Valid weight entry passes")
        void validWeight() {
            HealthLogRequest req = HealthLogRequest.builder().type("weight").weight(70.0).height(175.0).build();
            validator.validate(req);
        }

        @Test
        @DisplayName("Null weight throws")
        void nullWeight() {
            HealthLogRequest req = HealthLogRequest.builder().type("weight").weight(null).build();
            assertThatThrownBy(() -> validator.validate(req))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("required");
        }

        @Test
        @DisplayName("Weight below minimum (2 kg) throws")
        void belowMinWeight() {
            HealthLogRequest req = HealthLogRequest.builder().type("weight").weight(1.0).build();
            assertThatThrownBy(() -> validator.validate(req))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("at least 2");
        }

        @Test
        @DisplayName("Weight above maximum (300 kg) throws")
        void aboveMaxWeight() {
            HealthLogRequest req = HealthLogRequest.builder().type("weight").weight(301.0).build();
            assertThatThrownBy(() -> validator.validate(req))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("maximum");
        }

        @Test
        @DisplayName("Min weight (2 kg) passes")
        void minWeight() {
            HealthLogRequest req = HealthLogRequest.builder().type("weight").weight(2.0).build();
            validator.validate(req);
        }

        @Test
        @DisplayName("Max weight (300 kg) passes")
        void maxWeight() {
            HealthLogRequest req = HealthLogRequest.builder().type("weight").weight(300.0).build();
            validator.validate(req);
        }
    }

    @Nested
    @DisplayName("Sleep validation")
    class SleepValidation {
        @Test
        @DisplayName("Valid sleep entry passes")
        void validSleep() {
            HealthLogRequest req = HealthLogRequest.builder().type("sleep").hours(7.5).quality("good").build();
            validator.validate(req);
        }

        @Test
        @DisplayName("Null hours throws")
        void nullHours() {
            HealthLogRequest req = HealthLogRequest.builder().type("sleep").hours(null).build();
            assertThatThrownBy(() -> validator.validate(req))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("required");
        }

        @Test
        @DisplayName("Zero hours throws")
        void zeroHours() {
            HealthLogRequest req = HealthLogRequest.builder().type("sleep").hours(0.0).build();
            assertThatThrownBy(() -> validator.validate(req))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        @DisplayName("Hours exceeding 24 throws")
        void exceedsMaxHours() {
            HealthLogRequest req = HealthLogRequest.builder().type("sleep").hours(25.0).build();
            assertThatThrownBy(() -> validator.validate(req))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("24");
        }

        @Test
        @DisplayName("Invalid quality throws")
        void invalidQuality() {
            HealthLogRequest req = HealthLogRequest.builder().type("sleep").hours(7.0).quality("amazing").build();
            assertThatThrownBy(() -> validator.validate(req))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Invalid sleep quality");
        }
    }

    @Nested
    @DisplayName("Water validation")
    class WaterValidation {
        @Test
        @DisplayName("Valid water entry passes")
        void validWater() {
            HealthLogRequest req = HealthLogRequest.builder().type("water").waterMl(500).build();
            validator.validate(req);
        }

        @Test
        @DisplayName("Null water throws")
        void nullWater() {
            HealthLogRequest req = HealthLogRequest.builder().type("water").waterMl(null).build();
            assertThatThrownBy(() -> validator.validate(req))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("required");
        }

        @Test
        @DisplayName("Zero water throws")
        void zeroWater() {
            HealthLogRequest req = HealthLogRequest.builder().type("water").waterMl(0).build();
            assertThatThrownBy(() -> validator.validate(req))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        @DisplayName("Water exceeding max (20000 ml) throws")
        void exceedsMaxWater() {
            HealthLogRequest req = HealthLogRequest.builder().type("water").waterMl(21000).build();
            assertThatThrownBy(() -> validator.validate(req))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("maximum");
        }
    }

    @Nested
    @DisplayName("Cross-field validation")
    class CrossFieldValidation {
        @Test
        @DisplayName("Invalid type throws")
        void invalidType() {
            HealthLogRequest req = HealthLogRequest.builder().type("meditation").build();
            assertThatThrownBy(() -> validator.validate(req))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Invalid health log type");
        }

        @Test
        @DisplayName("Null type throws")
        void nullType() {
            HealthLogRequest req = HealthLogRequest.builder().type(null).build();
            assertThatThrownBy(() -> validator.validate(req))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        @DisplayName("Null request throws")
        void nullRequest() {
            assertThatThrownBy(() -> validator.validate(null))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("null");
        }

        @Test
        @DisplayName("Negative body fat throws")
        void negativeBodyFat() {
            HealthLogRequest req = HealthLogRequest.builder().type("weight").weight(70.0).bodyFat(-1.0).build();
            assertThatThrownBy(() -> validator.validate(req))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("negative");
        }

        @Test
        @DisplayName("Body fat exceeding 70% throws")
        void exceedsMaxBodyFat() {
            HealthLogRequest req = HealthLogRequest.builder().type("weight").weight(70.0).bodyFat(71.0).build();
            assertThatThrownBy(() -> validator.validate(req))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("maximum");
        }

        @Test
        @DisplayName("Negative distance throws")
        void negativeDistance() {
            HealthLogRequest req = HealthLogRequest.builder().type("steps").steps(5000).distance(-5.0).build();
            assertThatThrownBy(() -> validator.validate(req))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("negative");
        }

        @Test
        @DisplayName("Height below minimum (30 cm) throws as cross-field")
        void heightBelowMinCrossField() {
            HealthLogRequest req = HealthLogRequest.builder().type("weight").weight(70.0).height(10.0).build();
            assertThatThrownBy(() -> validator.validate(req))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        @DisplayName("Negative calories throws")
        void negativeCalories() {
            HealthLogRequest req = HealthLogRequest.builder().type("workout").duration(30).calories(-100).build();
            assertThatThrownBy(() -> validator.validate(req))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("negative");
        }
    }
}
