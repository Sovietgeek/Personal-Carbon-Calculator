package com.ecoverse.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrendDataPoint {

    /** Date string in ISO format (YYYY-MM-DD) */
    private String date;
    /** Emissions in kg CO2 */
    private BigDecimal emissions;
    /** Avoided emissions in kg CO2 */
    private BigDecimal avoided;
}
