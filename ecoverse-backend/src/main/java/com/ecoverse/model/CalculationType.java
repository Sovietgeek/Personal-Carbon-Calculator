package com.ecoverse.model;

/**
 * Defines the type of carbon calculation.
 * Replaces the old convention of negative CO2 values for savings/offsets.
 *
 * All CO2 values in CarbonEntry are non-negative. The direction is determined
 * by this enum:
 * - EMISSION: positive contribution to carbon footprint (e.g., driving, electricity)
 * - AVOIDED_EMISSION: avoided/offset amount (e.g., solar power, recycling, composting)
 * - CREDIT: purchased carbon credit
 */
public enum CalculationType {

    /**
     * A direct emission of CO2 (e.g., driving a car, using electricity).
     * Adds to the user's total emissions.
     */
    EMISSION,

    /**
     * An avoided emission / carbon saving (e.g., solar panels, recycling).
     * Counts as a positive contribution to the user's carbon savings.
     * The CO2 value represents the amount of emissions that were avoided.
     */
    AVOIDED_EMISSION,

    /**
     * A purchased carbon credit that offsets emissions.
     * Similar to AVOIDED_EMISSION but represents a financial transaction.
     */
    CREDIT;

    /**
     * Parse a string to CalculationType, with fallback to EMISSION.
     */
    public static CalculationType fromString(String value) {
        if (value == null) return EMISSION;
        try {
            return valueOf(value.toUpperCase().replace("-", "_"));
        } catch (IllegalArgumentException e) {
            return EMISSION;
        }
    }
}
