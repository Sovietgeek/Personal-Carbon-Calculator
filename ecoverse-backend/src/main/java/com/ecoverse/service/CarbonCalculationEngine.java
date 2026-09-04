package com.ecoverse.service;

import com.ecoverse.model.CalculationType;
import com.ecoverse.model.EmissionFactor;
import com.ecoverse.util.UnitConverter;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Single authoritative carbon calculation engine.
 * All CO2 calculations MUST go through this class — never trust client-sent values.
 *
 * Calculation formula: co2 = factor × inputValue / passengers × modifier
 *
 * Rules:
 * - All arithmetic uses BigDecimal with HALF_UP rounding
 * - Input values are converted to canonical units before calculation
 * - The factor is always looked up from the database (never hardcoded)
 * - CO2 values are rounded to 4 decimal places (0.0001 kg = 0.1 g precision)
 * - Passengers must be >= 1 (default: 1)
 * - Modifier values are in (0, 1] range (e.g., 0.5 for secondhand)
 */
@Service
public class CarbonCalculationEngine {

    /**
     * Scale for CO2 output: 4 decimal places (0.1 gram precision)
     */
    private static final int CO2_SCALE = 4;

    /**
     * Calculate CO2 from an emission factor and user input.
     * This is the single authoritative calculation method.
     *
     * @param factor      the emission factor from the database
     * @param inputValue  the raw input value from the user (already in canonical units)
     * @param passengers  number of passengers (for transport), must be >= 1
     * @param modifierType  type of modifier applied (e.g., "secondhand"), null if none
     * @param modifierValue  modifier multiplier (e.g., 0.5 for secondhand), null if none
     * @return calculated CO2 in kg, rounded to 4 decimal places
     */
    public BigDecimal calculate(EmissionFactor factor, BigDecimal inputValue,
                                Integer passengers, String modifierType,
                                BigDecimal modifierValue) {
        if (factor == null) {
            throw new IllegalArgumentException("Emission factor must not be null");
        }
        if (inputValue == null || inputValue.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Input value must be positive, got: " + inputValue);
        }

        // Start with factor × input value
        BigDecimal co2 = factor.getFactor().multiply(inputValue);

        // Divide by passengers (transport)
        if (passengers != null && passengers > 1) {
            co2 = co2.divide(BigDecimal.valueOf(passengers), CO2_SCALE + 4, RoundingMode.HALF_UP);
        }

        // Apply modifier (e.g., secondhand × 0.5)
        // Only apply if modifier is in (0, 1] range — values > 1 are invalid
        if (modifierValue != null && modifierValue.compareTo(BigDecimal.ZERO) > 0
                && modifierValue.compareTo(BigDecimal.ONE) <= 0) {
            co2 = co2.multiply(modifierValue);
        }

        // Round to 4 decimal places
        return co2.setScale(CO2_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Calculate CO2 for transport category.
     * Input is distance in the user's unit, converted to km.
     *
     * @param factor     emission factor (expected unit: kg/km)
     * @param distance   distance value
     * @param distanceUnit distance unit ("km", "mi", "m")
     * @param passengers number of passengers (default: 1)
     * @return calculated CO2 in kg
     */
    public BigDecimal calculateTransport(EmissionFactor factor, BigDecimal distance,
                                          String distanceUnit, Integer passengers) {
        BigDecimal distanceKm = UnitConverter.toKm(distance, distanceUnit);
        return calculate(factor, distanceKm, passengers, null, null);
    }

    /**
     * Calculate CO2 for energy category.
     * Input is consumption in the user's unit, converted to kWh.
     *
     * @param factor       emission factor (expected unit: kg/kWh)
     * @param consumption  energy consumption value
     * @param energyUnit   energy unit ("kwh", "wh", "mwh")
     * @return calculated CO2 in kg
     */
    public BigDecimal calculateEnergy(EmissionFactor factor, BigDecimal consumption,
                                       String energyUnit) {
        BigDecimal consumptionKwh = UnitConverter.toKwh(consumption, energyUnit);
        return calculate(factor, consumptionKwh, null, null, null);
    }

    /**
     * Calculate CO2 for food category.
     * Input is number of meals.
     *
     * @param factor emission factor (expected unit: kg/meal)
     * @param meals  number of meals
     * @return calculated CO2 in kg
     */
    public BigDecimal calculateFood(EmissionFactor factor, BigDecimal meals) {
        return calculate(factor, meals, null, null, null);
    }

    /**
     * Calculate CO2 for shopping category.
     * Input is quantity in the user's unit, converted to kg.
     * Supports secondhand modifier.
     *
     * @param factor          emission factor (expected unit: kg CO2/kg or kg CO2/item)
     * @param quantity        product quantity
     * @param quantityUnit    quantity unit ("kg", "g", "item")
     * @param isSecondhand    whether the item is secondhand
     * @return calculated CO2 in kg
     */
    public BigDecimal calculateShopping(EmissionFactor factor, BigDecimal quantity,
                                         String quantityUnit, boolean isSecondhand) {
        BigDecimal quantityKg;
        if ("item".equalsIgnoreCase(quantityUnit)) {
            // Per-item factor — use quantity directly (it's a count)
            quantityKg = quantity;
        } else {
            // Per-kg factor — convert to kg
            quantityKg = UnitConverter.toKg(quantity, quantityUnit);
        }

        String modifierType = isSecondhand ? "secondhand" : null;
        BigDecimal modifierValue = isSecondhand ? new BigDecimal("0.5") : null;
        return calculate(factor, quantityKg, null, modifierType, modifierValue);
    }

    /**
     * Calculate CO2 for waste category.
     * Input is weight in the user's unit, converted to kg.
     *
     * @param factor     emission factor (expected unit: kg/kg)
     * @param weight     waste weight
     * @param weightUnit weight unit ("kg", "g", "tonnes")
     * @return calculated CO2 in kg (may be AVOIDED_EMISSION type for recycling/composting)
     */
    public BigDecimal calculateWaste(EmissionFactor factor, BigDecimal weight,
                                      String weightUnit) {
        BigDecimal weightKg = UnitConverter.toKg(weight, weightUnit);
        return calculate(factor, weightKg, null, null, null);
    }

    /**
     * Calculate CO2 for digital category.
     * Input is usage quantity in the user's unit.
     *
     * @param factor        emission factor
     * @param quantity      usage quantity
     * @param quantityUnit  quantity unit ("hr", "GB", "txn", "query", "100 emails")
     * @return calculated CO2 in kg
     */
    public BigDecimal calculateDigital(EmissionFactor factor, BigDecimal quantity,
                                        String quantityUnit) {
        return calculate(factor, quantity, null, null, null);
    }

    /**
     * Determine the CalculationType for a given emission factor.
     * Factors for solar, recycled, and composted are AVOIDED_EMISSION;
     * everything else is EMISSION.
     *
     * @param category the emission category
     * @param type     the emission type within the category
     * @return the appropriate CalculationType
     */
    public CalculationType determineCalculationType(String category, String type) {
        if (category == null || type == null) return CalculationType.EMISSION;

        // Energy: solar is avoided emission
        if ("energy".equalsIgnoreCase(category) && "solar".equalsIgnoreCase(type)) {
            return CalculationType.AVOIDED_EMISSION;
        }

        // Waste: recycled and composted are avoided emissions
        if ("waste".equalsIgnoreCase(category) &&
                ("recycled".equalsIgnoreCase(type) || "composted".equalsIgnoreCase(type))) {
            return CalculationType.AVOIDED_EMISSION;
        }

        return CalculationType.EMISSION;
    }
}
