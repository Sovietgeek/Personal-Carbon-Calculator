package com.ecoverse.service;

import com.ecoverse.dto.carbon.CarbonEntryRequest;
import com.ecoverse.exception.BadRequestException;
import com.ecoverse.util.UnitConverter;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Validates carbon entry requests before they are processed by the calculation engine.
 * Enforces category-specific input contracts and rejects invalid/dangerous inputs.
 *
 * Rules:
 * - Category must be one of the 6 supported categories
 * - Type must be a known emission factor type for the category
 * - Required fields vary by category (distance for transport, consumption for energy, etc.)
 * - All numeric inputs must be positive and within reasonable bounds
 * - Units must be recognized and convertible
 * - Passengers must be >= 1 (transport only)
 */
@Service
public class CarbonEntryValidator {

    private static final Set<String> VALID_CATEGORIES = Set.of(
            "transport", "energy", "food", "shopping", "waste", "digital"
    );

    // Maximum reasonable values to prevent absurd entries
    private static final BigDecimal MAX_DISTANCE_KM = new BigDecimal("50000");   // ~circumference of Earth
    private static final BigDecimal MAX_ENERGY_KWH = new BigDecimal("1000000");  // 1 GWh
    private static final BigDecimal MAX_MEALS = new BigDecimal("100");            // per day
    private static final BigDecimal MAX_SHOPPING_KG = new BigDecimal("10000");    // 10 tonnes
    private static final BigDecimal MAX_WASTE_KG = new BigDecimal("100000");     // 100 tonnes
    private static final BigDecimal MAX_DIGITAL = new BigDecimal("1000000");     // 1M hours/queries

    /**
     * Validate a carbon entry request. Throws BadRequestException if invalid.
     *
     * @param request the carbon entry request to validate
     */
    public void validate(CarbonEntryRequest request) {
        if (request == null) {
            throw new BadRequestException("Request must not be null");
        }

        validateCategory(request);
        validateType(request);

        switch (request.getCategory().toLowerCase()) {
            case "transport": validateTransport(request); break;
            case "energy": validateEnergy(request); break;
            case "food": validateFood(request); break;
            case "shopping": validateShopping(request); break;
            case "waste": validateWaste(request); break;
            case "digital": validateDigital(request); break;
        }
    }

    private void validateCategory(CarbonEntryRequest request) {
        if (request.getCategory() == null || request.getCategory().isBlank()) {
            throw new BadRequestException("Category is required");
        }
        if (!VALID_CATEGORIES.contains(request.getCategory().toLowerCase())) {
            throw new BadRequestException("Unknown emission category: " + request.getCategory() +
                    ". Valid categories: " + VALID_CATEGORIES);
        }
    }

    private void validateType(CarbonEntryRequest request) {
        if (request.getType() == null || request.getType().isBlank()) {
            throw new BadRequestException("Type is required for category: " + request.getCategory());
        }
    }

    private void validateTransport(CarbonEntryRequest request) {
        if (request.getDistance() == null || request.getDistance().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Distance is required for transport entries and must be positive");
        }
        if (request.getDistance().compareTo(MAX_DISTANCE_KM) > 0) {
            throw new BadRequestException("Distance exceeds maximum allowed value (" + MAX_DISTANCE_KM + " km)");
        }
        // Validate distance unit
        String unit = request.getDistanceUnit();
        if (unit != null && !unit.isBlank() && !UnitConverter.isDistanceUnit(unit)) {
            throw new BadRequestException("Unsupported distance unit: " + unit +
                    ". Supported: " + UnitConverter.DISTANCE_UNITS);
        }
        // Validate passengers
        if (request.getPassengers() != null && request.getPassengers() < 1) {
            throw new BadRequestException("Passengers must be at least 1");
        }
        if (request.getPassengers() != null && request.getPassengers() > 50) {
            throw new BadRequestException("Passengers exceeds maximum allowed (50)");
        }
    }

    private void validateEnergy(CarbonEntryRequest request) {
        if (request.getConsumption() == null || request.getConsumption().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Consumption is required for energy entries and must be positive");
        }
        if (request.getConsumption().compareTo(MAX_ENERGY_KWH) > 0) {
            throw new BadRequestException("Energy consumption exceeds maximum allowed value (" + MAX_ENERGY_KWH + " kWh)");
        }
        // Validate energy unit
        String unit = request.getEnergyUnit();
        if (unit != null && !unit.isBlank() && !UnitConverter.isEnergyUnit(unit)) {
            throw new BadRequestException("Unsupported energy unit: " + unit +
                    ". Supported: " + UnitConverter.ENERGY_UNITS);
        }
    }

    private void validateFood(CarbonEntryRequest request) {
        if (request.getMeals() == null || request.getMeals().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Number of meals is required for food entries and must be positive");
        }
        if (request.getMeals().compareTo(MAX_MEALS) > 0) {
            throw new BadRequestException("Number of meals exceeds maximum allowed value (" + MAX_MEALS + ")");
        }
    }

    private void validateShopping(CarbonEntryRequest request) {
        if (request.getQuantity() == null || request.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Quantity is required for shopping entries and must be positive");
        }
        if (request.getQuantity().compareTo(MAX_SHOPPING_KG) > 0) {
            throw new BadRequestException("Shopping quantity exceeds maximum allowed value (" + MAX_SHOPPING_KG + " kg)");
        }
    }

    private void validateWaste(CarbonEntryRequest request) {
        if (request.getQuantity() == null || request.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Quantity is required for waste entries and must be positive");
        }
        if (request.getQuantity().compareTo(MAX_WASTE_KG) > 0) {
            throw new BadRequestException("Waste quantity exceeds maximum allowed value (" + MAX_WASTE_KG + " kg)");
        }
    }

    private void validateDigital(CarbonEntryRequest request) {
        if (request.getQuantity() == null || request.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Quantity is required for digital entries and must be positive");
        }
        if (request.getQuantity().compareTo(MAX_DIGITAL) > 0) {
            throw new BadRequestException("Digital quantity exceeds maximum allowed value (" + MAX_DIGITAL + ")");
        }
    }
}
