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
public class CarbonSummaryResponse {

    private BigDecimal todayEmissions;
    private BigDecimal todayAvoided;
    private BigDecimal monthEmissions;
    private BigDecimal yearEmissions;
    private BigDecimal totalEmitted;
    private BigDecimal totalSaved;
    private BigDecimal netImpact;
    private Integer treesNeeded;
    private BigDecimal budgetRemaining;
    private BigDecimal budgetUsedPercent;
}
