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
public class CarbonEntryResponse {

    private Long id;
    private String category;
    private String type;
    /** CO2 value in kg. Always non-negative. Direction determined by calculationType. */
    private BigDecimal co2;
    /** Calculation type: EMISSION or AVOIDED_EMISSION */
    private String calculationType;
    /** Input value provided by the user */
    private BigDecimal inputValue;
    /** Unit of the input value */
    private String inputUnit;
    /** ID of the emission factor used for calculation */
    private Long factorId;
    /** Version of the emission factor at time of calculation */
    private Integer factorVersion;
    /** Modifier type applied (e.g., "secondhand") */
    private String modifierType;
    /** Modifier value applied (e.g., 0.5 for secondhand) */
    private BigDecimal modifierValue;
    /** Entry date in ISO-8601 format (UTC) */
    private String entryDate;
}
