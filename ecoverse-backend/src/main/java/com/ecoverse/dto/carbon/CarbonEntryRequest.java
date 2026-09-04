package com.ecoverse.dto.carbon;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Carbon entry request — client NEVER sends CO2 value.
 * The server calculates CO2 authoritatively from the emission factor + input values.
 *
 * Category-specific required fields:
 * - Transport: distance (required), distanceUnit (optional, default "km"), passengers (optional, default 1)
 * - Energy: consumption (required), energyUnit (optional, default "kWh")
 * - Food: meals (required, default 1)
 * - Shopping: quantity (required), quantityUnit (optional, default "kg"), isSecondhand (optional)
 * - Waste: quantity (required), quantityUnit (optional, default "kg")
 * - Digital: quantity (required), quantityUnit (optional, default "hr")
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CarbonEntryRequest {

    @NotBlank(message = "Category is required")
    @Size(min = 1, max = 50, message = "Category must be between 1 and 50 characters")
    private String category; // transport/energy/food/shopping/waste/digital

    @NotBlank(message = "Type is required")
    @Size(min = 1, max = 100, message = "Type must be between 1 and 100 characters")
    private String type; // specific type like "car-petrol", "electricity", etc.

    // ===== Transport fields =====
    /** Distance traveled (in distanceUnit) */
    private BigDecimal distance;
    /** Distance unit: km (default), mi, m */
    @Size(max = 20, message = "Distance unit must be at most 20 characters")
    private String distanceUnit;
    /** Number of passengers (transport only, default 1) */
    private Integer passengers;

    // ===== Energy fields =====
    /** Energy consumption (in energyUnit) */
    private BigDecimal consumption;
    /** Energy unit: kWh (default), Wh, MWh */
    @Size(max = 20, message = "Energy unit must be at most 20 characters")
    private String energyUnit;

    // ===== Food fields =====
    /** Number of meals (default 1) */
    private BigDecimal meals;

    // ===== Shopping fields =====
    /** Product quantity (in quantityUnit) */
    private BigDecimal quantity;
    /** Quantity unit: kg (default), g, item */
    @Size(max = 20, message = "Quantity unit must be at most 20 characters")
    private String quantityUnit;
    /** Whether the item is secondhand (applies 0.5 modifier) */
    private Boolean isSecondhand;

    // ===== Waste fields =====
    // Reuses `quantity` and `quantityUnit` (default "kg")

    // ===== Digital fields =====
    // Reuses `quantity` and `quantityUnit` (default "hr")

    // ===== User timezone =====
    /** User's IANA timezone for period calculations (optional, uses profile default) */
    @Size(max = 50, message = "Timezone must be at most 50 characters")
    private String timezone;
}
