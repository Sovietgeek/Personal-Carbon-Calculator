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
public class RiskAssessmentResponse {

    private String level; // EXCELLENT/GOOD/MODERATE/HIGH/EXTREME
    private String title;
    private String description;
    private String color;
    private BigDecimal percentage;
    private BigDecimal youKg;
    private BigDecimal indiaAvgKg;
    private BigDecimal globalAvgKg;
}
