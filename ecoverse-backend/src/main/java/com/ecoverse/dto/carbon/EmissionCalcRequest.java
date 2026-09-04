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
 * Request for preview/estimate calculation (POST /api/carbon/calculate).
 * This does NOT create an entry — it only returns the calculated CO2 value
 * for the user to review before submitting.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmissionCalcRequest {

    @NotBlank(message = "Category is required")
    @Size(min = 1, max = 50, message = "Category must be between 1 and 50 characters")
    private String category;

    @NotBlank(message = "Type is required")
    @Size(min = 1, max = 100, message = "Type must be between 1 and 100 characters")
    private String type;

    @NotNull(message = "Value is required")
    private BigDecimal value; // distance/consumption/etc in the given unit

    /** Unit of the value (e.g., "km", "kWh", "meal"). Default depends on category. */
    @Size(max = 20, message = "Unit must be at most 20 characters")
    private String unit;

    @Builder.Default
    private Integer passengers = 1;

    @Builder.Default
    private Boolean isSecondhand = false;
}
