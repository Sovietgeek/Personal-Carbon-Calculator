package com.ecoverse.service;

import org.springframework.stereotype.Service;

import java.time.*;
import java.time.temporal.TemporalAdjusters;

/**
 * Timezone service for carbon period calculations.
 * All dates are stored as Instant (UTC) in the database.
 * Period boundaries (start of day, start of week, etc.) are calculated
 * relative to the user's preferred timezone so that "today" means
 * the user's today, not the server's today.
 */
@Service
public class TimezoneService {

    /**
     * Get the user's ZoneId from their timezone string.
     * Falls back to UTC if the timezone is invalid or null.
     *
     * @param timezoneString IANA timezone string (e.g., "Asia/Kolkata")
     * @return ZoneId for the user's timezone
     */
    public ZoneId getUserZoneId(String timezoneString) {
        if (timezoneString == null || timezoneString.isBlank()) {
            return ZoneId.of("Asia/Kolkata"); // Default: IST
        }
        try {
            return ZoneId.of(timezoneString);
        } catch (DateTimeException e) {
            return ZoneId.of("Asia/Kolkata"); // Fallback to default
        }
    }

    /**
     * Get the Instant range for "today" in the user's timezone.
     * Returns [startOfDay, startOfNextDay) as Instant pair.
     *
     * @param userZoneId the user's timezone
     * @return array of two Instants: [start, end)
     */
    public Instant[] getTodayRange(ZoneId userZoneId) {
        LocalDate today = LocalDate.now(userZoneId);
        Instant start = today.atStartOfDay(userZoneId).toInstant();
        Instant end = today.plusDays(1).atStartOfDay(userZoneId).toInstant();
        return new Instant[]{start, end};
    }

    /**
     * Get the Instant range for "this week" (Monday to Sunday) in the user's timezone.
     *
     * @param userZoneId the user's timezone
     * @return array of two Instants: [start, end)
     */
    public Instant[] getWeekRange(ZoneId userZoneId) {
        LocalDate today = LocalDate.now(userZoneId);
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Instant start = monday.atStartOfDay(userZoneId).toInstant();
        Instant end = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
                .plusDays(1).atStartOfDay(userZoneId).toInstant();
        return new Instant[]{start, end};
    }

    /**
     * Get the Instant range for "this month" in the user's timezone.
     *
     * @param userZoneId the user's timezone
     * @return array of two Instants: [start, end)
     */
    public Instant[] getMonthRange(ZoneId userZoneId) {
        LocalDate today = LocalDate.now(userZoneId);
        LocalDate firstOfMonth = today.withDayOfMonth(1);
        Instant start = firstOfMonth.atStartOfDay(userZoneId).toInstant();
        Instant end = today.with(TemporalAdjusters.firstDayOfNextMonth())
                .atStartOfDay(userZoneId).toInstant();
        return new Instant[]{start, end};
    }

    /**
     * Get the Instant range for "this year" in the user's timezone.
     *
     * @param userZoneId the user's timezone
     * @return array of two Instants: [start, end)
     */
    public Instant[] getYearRange(ZoneId userZoneId) {
        LocalDate today = LocalDate.now(userZoneId);
        LocalDate firstOfYear = today.withDayOfYear(1);
        Instant start = firstOfYear.atStartOfDay(userZoneId).toInstant();
        Instant end = today.with(TemporalAdjusters.firstDayOfNextYear())
                .atStartOfDay(userZoneId).toInstant();
        return new Instant[]{start, end};
    }

    /**
     * Get the Instant range for a given period string in the user's timezone.
     *
     * @param period "today", "week", "month", or "year"
     * @param userZoneId the user's timezone
     * @return array of two Instants: [start, end)
     */
    public Instant[] getPeriodRange(String period, ZoneId userZoneId) {
        if (period == null) period = "today";
        switch (period.toLowerCase()) {
            case "week": return getWeekRange(userZoneId);
            case "month": return getMonthRange(userZoneId);
            case "year": return getYearRange(userZoneId);
            default: return getTodayRange(userZoneId);
        }
    }

    /**
     * Get the current Instant (UTC). Used as the entry date for new carbon entries.
     *
     * @return current Instant in UTC
     */
    public Instant now() {
        return Instant.now();
    }
}
