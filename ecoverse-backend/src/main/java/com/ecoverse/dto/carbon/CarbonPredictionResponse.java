package com.ecoverse.dto.carbon;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CarbonPredictionResponse {
    private Double todayEmissions;        // Today's CO2 so far
    private Double predictedMonthTotal;     // Predicted month total
    private Double predictedYearTotal;      // Predicted year total
    private Double averageDailyEmission;   // Avg daily emission
    private String trend;                   // "increasing", "decreasing", "stable"
    private Double trendPercent;            // % change from last period
    private String projectionMessage;       // "At this rate, you'll emit X kg this month"
    private Integer treesNeededToOffset;    // Trees needed to offset
    private Double comparedToAverage;       // % compared to India avg (4.2 kg/day)
    private String comparedToAverageLabel;  // "Below average", "Above average"
}
