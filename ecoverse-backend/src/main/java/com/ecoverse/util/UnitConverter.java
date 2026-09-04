package com.ecoverse.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Set;

/**
 * Unit conversion utility for carbon calculations.
 * Converts user-supplied units to the canonical units expected by emission factors.
 * All conversions are explicit and documented.
 */
public final class UnitConverter {

    private UnitConverter() {
        // Utility class — no instantiation
    }

    // ===== Distance conversions → km =====
    private static final Map<String, BigDecimal> DISTANCE_TO_KM = Map.of(
        "km", BigDecimal.ONE,
        "mi", new BigDecimal("1.60934"),
        "m", new BigDecimal("0.001")
    );

    // ===== Energy conversions → kWh =====
    private static final Map<String, BigDecimal> ENERGY_TO_KWH = Map.of(
        "kwh", BigDecimal.ONE,
        "wh", new BigDecimal("0.001"),
        "mwh", new BigDecimal("1000")
    );

    // ===== Mass conversions → kg =====
    private static final Map<String, BigDecimal> MASS_TO_KG = Map.of(
        "kg", BigDecimal.ONE,
        "g", new BigDecimal("0.001"),
        "tonnes", new BigDecimal("1000")
    );

    // ===== Valid units per category =====
    public static final Set<String> DISTANCE_UNITS = Set.of("km", "mi", "m");
    public static final Set<String> ENERGY_UNITS = Set.of("kwh", "wh", "mwh");
    public static final Set<String> MASS_UNITS = Set.of("kg", "g", "tonnes");
    public static final Set<String> COUNT_UNITS = Set.of("meal", "item", "txn", "query", "100 emails");

    /**
     * Convert a distance value to kilometers.
     *
     * @param value the input value
     * @param unit  the input unit ("km", "mi", "m")
     * @return value in km
     * @throws IllegalArgumentException if the unit is not supported
     */
    public static BigDecimal toKm(BigDecimal value, String unit) {
        if (value == null || unit == null) return value;
        BigDecimal multiplier = DISTANCE_TO_KM.get(unit.toLowerCase());
        if (multiplier == null) {
            throw new IllegalArgumentException("Unsupported distance unit: " + unit +
                    ". Supported: " + DISTANCE_UNITS);
        }
        return value.multiply(multiplier).setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * Convert an energy value to kilowatt-hours.
     *
     * @param value the input value
     * @param unit  the input unit ("kwh", "wh", "mwh")
     * @return value in kWh
     * @throws IllegalArgumentException if the unit is not supported
     */
    public static BigDecimal toKwh(BigDecimal value, String unit) {
        if (value == null || unit == null) return value;
        BigDecimal multiplier = ENERGY_TO_KWH.get(unit.toLowerCase());
        if (multiplier == null) {
            throw new IllegalArgumentException("Unsupported energy unit: " + unit +
                    ". Supported: " + ENERGY_UNITS);
        }
        return value.multiply(multiplier).setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * Convert a mass value to kilograms.
     *
     * @param value the input value
     * @param unit  the input unit ("kg", "g", "tonnes")
     * @return value in kg
     * @throws IllegalArgumentException if the unit is not supported
     */
    public static BigDecimal toKg(BigDecimal value, String unit) {
        if (value == null || unit == null) return value;
        BigDecimal multiplier = MASS_TO_KG.get(unit.toLowerCase());
        if (multiplier == null) {
            throw new IllegalArgumentException("Unsupported mass unit: " + unit +
                    ". Supported: " + MASS_UNITS);
        }
        return value.multiply(multiplier).setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * Check if a unit string is a recognized distance unit.
     */
    public static boolean isDistanceUnit(String unit) {
        return unit != null && DISTANCE_UNITS.contains(unit.toLowerCase());
    }

    /**
     * Check if a unit string is a recognized energy unit.
     */
    public static boolean isEnergyUnit(String unit) {
        return unit != null && ENERGY_UNITS.contains(unit.toLowerCase());
    }

    /**
     * Check if a unit string is a recognized mass unit.
     */
    public static boolean isMassUnit(String unit) {
        return unit != null && MASS_UNITS.contains(unit.toLowerCase());
    }

    /**
     * Check if a unit string is a recognized count unit (meal, item, etc.).
     */
    public static boolean isCountUnit(String unit) {
        return unit != null && COUNT_UNITS.contains(unit.toLowerCase());
    }

    /**
     * Get the canonical unit for a category (the unit emission factors expect).
     *
     * @param category the emission category
     * @return canonical unit string
     */
    public static String getCanonicalUnit(String category) {
        if (category == null) return null;
        switch (category.toLowerCase()) {
            case "transport": return "km";
            case "energy": return "kwh";
            case "food": return "meal";
            case "shopping": return "kg";
            case "waste": return "kg";
            case "digital": return "hr";
            default: return null;
        }
    }
}
