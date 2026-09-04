package com.ecoverse.dto.carbon;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmissionCalcResponse {

    /** Calculated CO2 in kg. Always non-negative. */
    private BigDecimal co2;
    /** Unit of the CO2 value */
    private String unit; // kg CO2
    /** Emission category */
    private String category;
    /** Emission type */
    private String type;
    /** Calculation type: EMISSION or AVOIDED_EMISSION */
    private String calculationType;
    /** Emission factor used (for transparency) */
    private BigDecimal factorUsed;
    /** Verification status of the factor */
    private String factorVerificationStatus;
    /** Uncertainty description of the factor */
    private String factorUncertainty;
}
