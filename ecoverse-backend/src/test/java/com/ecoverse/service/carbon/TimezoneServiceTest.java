package com.ecoverse.service.carbon;

import com.ecoverse.service.TimezoneService;
import org.junit.jupiter.api.*;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Timezone Service")
class TimezoneServiceTest {

    private TimezoneService service;

    @BeforeEach
    void setUp() {
        service = new TimezoneService();
    }

    // ===== ZoneId Resolution =====

    @Nested
    @DisplayName("ZoneId Resolution")
    class ZoneIdResolution {

        @Test
        @DisplayName("Valid timezone string returns correct ZoneId")
        void validTimezone() {
            ZoneId zone = service.getUserZoneId("America/New_York");
            assertThat(zone).isEqualTo(ZoneId.of("America/New_York"));
        }

        @Test
        @DisplayName("Invalid timezone returns default (Asia/Kolkata)")
        void invalidTimezoneFallsBack() {
            ZoneId zone = service.getUserZoneId("Invalid/Zone");
            assertThat(zone).isEqualTo(ZoneId.of("Asia/Kolkata"));
        }

        @Test
        @DisplayName("Null timezone returns default")
        void nullTimezoneFallsBack() {
            ZoneId zone = service.getUserZoneId(null);
            assertThat(zone).isEqualTo(ZoneId.of("Asia/Kolkata"));
        }

        @Test
        @DisplayName("Blank timezone returns default")
        void blankTimezoneFallsBack() {
            ZoneId zone = service.getUserZoneId("  ");
            assertThat(zone).isEqualTo(ZoneId.of("Asia/Kolkata"));
        }
    }

    // ===== Period Ranges =====

    @Nested
    @DisplayName("Today Range")
    class TodayRange {

        @Test
        @DisplayName("Today range starts at midnight")
        void startsAtMidnight() {
            ZoneId zone = ZoneId.of("Asia/Kolkata");
            Instant[] range = service.getTodayRange(zone);
            ZonedDateTime startZdt = ZonedDateTime.ofInstant(range[0], zone);
            assertThat(startZdt.getHour()).isEqualTo(0);
            assertThat(startZdt.getMinute()).isEqualTo(0);
        }

        @Test
        @DisplayName("Today range end is after start")
        void endAfterStart() {
            ZoneId zone = ZoneId.of("Asia/Kolkata");
            Instant[] range = service.getTodayRange(zone);
            assertThat(range[1]).isAfter(range[0]);
        }

        @Test
        @DisplayName("Today range spans exactly 24 hours")
        void exactly24Hours() {
            ZoneId zone = ZoneId.of("Asia/Kolkata");
            Instant[] range = service.getTodayRange(zone);
            long diffHours = java.time.Duration.between(range[0], range[1]).toHours();
            assertThat(diffHours).isEqualTo(24);
        }
    }

    @Nested
    @DisplayName("Week Range")
    class WeekRange {

        @Test
        @DisplayName("Week range starts on Monday")
        void startsOnMonday() {
            ZoneId zone = ZoneId.of("Asia/Kolkata");
            Instant[] range = service.getWeekRange(zone);
            ZonedDateTime startZdt = ZonedDateTime.ofInstant(range[0], zone);
            assertThat(startZdt.getDayOfWeek()).isEqualTo(java.time.DayOfWeek.MONDAY);
        }

        @Test
        @DisplayName("Week range spans 7 days")
        void spans7Days() {
            ZoneId zone = ZoneId.of("Asia/Kolkata");
            Instant[] range = service.getWeekRange(zone);
            long diffDays = java.time.Duration.between(range[0], range[1]).toDays();
            assertThat(diffDays).isEqualTo(7);
        }
    }

    @Nested
    @DisplayName("Month Range")
    class MonthRange {

        @Test
        @DisplayName("Month range starts on 1st")
        void startsOnFirst() {
            ZoneId zone = ZoneId.of("Asia/Kolkata");
            Instant[] range = service.getMonthRange(zone);
            ZonedDateTime startZdt = ZonedDateTime.ofInstant(range[0], zone);
            assertThat(startZdt.getDayOfMonth()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Year Range")
    class YearRange {

        @Test
        @DisplayName("Year range starts on Jan 1")
        void startsOnJan1() {
            ZoneId zone = ZoneId.of("Asia/Kolkata");
            Instant[] range = service.getYearRange(zone);
            ZonedDateTime startZdt = ZonedDateTime.ofInstant(range[0], zone);
            assertThat(startZdt.getDayOfYear()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Period Range (generic)")
    class PeriodRange {

        @Test
        @DisplayName("Unknown period defaults to today")
        void unknownDefaultsToToday() {
            ZoneId zone = ZoneId.of("Asia/Kolkata");
            Instant[] today = service.getTodayRange(zone);
            Instant[] unknown = service.getPeriodRange("unknown", zone);
            assertThat(unknown[0]).isEqualTo(today[0]);
            assertThat(unknown[1]).isEqualTo(today[1]);
        }

        @Test
        @DisplayName("Null period defaults to today")
        void nullDefaultsToToday() {
            ZoneId zone = ZoneId.of("Asia/Kolkata");
            Instant[] today = service.getTodayRange(zone);
            Instant[] result = service.getPeriodRange(null, zone);
            assertThat(result[0]).isEqualTo(today[0]);
        }
    }

    @Nested
    @DisplayName("Cross-Timezone Consistency")
    class CrossTimezone {

        @Test
        @DisplayName("Different timezones produce different 'today' boundaries")
        void differentTimezonesDifferentBoundaries() {
            ZoneId kolkata = ZoneId.of("Asia/Kolkata");
            ZoneId newYork = ZoneId.of("America/New_York");
            Instant[] kolkataToday = service.getTodayRange(kolkata);
            Instant[] newYorkToday = service.getTodayRange(newYork);
            // The ranges should be different (Kolkata is ~10.5h ahead of NY)
            assertThat(kolkataToday[0]).isNotEqualTo(newYorkToday[0]);
        }
    }
}
