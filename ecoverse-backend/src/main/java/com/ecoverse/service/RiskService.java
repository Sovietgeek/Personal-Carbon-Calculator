package com.ecoverse.service;

import com.ecoverse.dto.carbon.RiskAssessmentResponse;
import com.ecoverse.model.EmissionFactor;
import com.ecoverse.repository.EmissionFactorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Single authoritative risk assessment service.
 * Deduplicates risk logic that was previously in both CarbonService and DashboardService.
 *
 * Risk levels are determined by comparing today's emissions to the user's daily budget:
 * - EXCELLENT: 0% (no emissions)
 * - GOOD: 0-25% of budget
 * - MODERATE: 25-60% of budget
 * - HIGH: 60-100% of budget
 * - EXTREME: >100% of budget
 *
 * Reference benchmarks (India avg, Global avg) are loaded from the _benchmark
 * category in emission_factors table, NOT hardcoded.
 */
@Service
public class RiskService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    @Autowired
    private EmissionFactorRepository emissionFactorRepository;

    /**
     * Get the India daily average benchmark from the database.
     * Falls back to 4.2 kg if not found in DB.
     *
     * @return India daily average CO2 in kg
     */
    public BigDecimal getIndiaDailyAverage() {
        return getBenchmark("india-daily-average", new BigDecimal("4.2"));
    }

    /**
     * Get the global daily average benchmark from the database.
     * Falls back to 8.5 kg if not found in DB.
     *
     * @return Global daily average CO2 in kg
     */
    public BigDecimal getGlobalDailyAverage() {
        return getBenchmark("global-daily-average", new BigDecimal("8.5"));
    }

    /**
     * Get the tree absorption annual benchmark from the database.
     * Falls back to 22.0 kg if not found in DB.
     *
     * @return Tree CO2 absorption in kg/year
     */
    public BigDecimal getTreeAbsorptionAnnual() {
        return getBenchmark("tree-absorption-annual", new BigDecimal("22.0"));
    }

    /**
     * Calculate the risk level based on emissions vs budget.
     *
     * @param todayEmissions today's CO2 emissions in kg
     * @param dailyBudget    user's daily carbon budget in kg
     * @return RiskAssessmentResponse with level, percentage, and comparison data
     */
    public RiskAssessmentResponse assess(BigDecimal todayEmissions, BigDecimal dailyBudget) {
        if (todayEmissions == null) todayEmissions = BigDecimal.ZERO;
        if (dailyBudget == null || dailyBudget.compareTo(BigDecimal.ZERO) <= 0) {
            dailyBudget = new BigDecimal("4.2");
        }

        BigDecimal percentage = todayEmissions.multiply(HUNDRED)
                .divide(dailyBudget, 2, RoundingMode.HALF_UP);

        String level, title, description, color;
        if (percentage.compareTo(BigDecimal.ZERO) == 0) {
            level = "EXCELLENT"; title = "Zero Emissions!";
            description = "You haven't logged any carbon emissions today. Keep it up!";
            color = "#10B981";
        } else if (percentage.compareTo(new BigDecimal("25")) <= 0) {
            level = "GOOD"; title = "Low Impact";
            description = "Your carbon footprint today is well within budget. Great job!";
            color = "#22C55E";
        } else if (percentage.compareTo(new BigDecimal("60")) <= 0) {
            level = "MODERATE"; title = "Moderate Impact";
            description = "You're using a moderate portion of your carbon budget.";
            color = "#F59E0B";
        } else if (percentage.compareTo(HUNDRED) <= 0) {
            level = "HIGH"; title = "High Impact";
            description = "You're close to exceeding your daily carbon budget.";
            color = "#EF4444";
        } else {
            level = "EXTREME"; title = "Extreme Impact";
            description = "You've exceeded your daily carbon budget!";
            color = "#DC2626";
        }

        BigDecimal indiaAvg = getIndiaDailyAverage();
        BigDecimal globalAvg = getGlobalDailyAverage();

        return RiskAssessmentResponse.builder()
                .level(level)
                .title(title)
                .description(description)
                .color(color)
                .percentage(percentage)
                .youKg(todayEmissions.setScale(2, RoundingMode.HALF_UP))
                .indiaAvgKg(indiaAvg)
                .globalAvgKg(globalAvg)
                .build();
    }

    /**
     * Get just the risk level string (for DashboardService).
     *
     * @param todayEmissions today's CO2 emissions in kg
     * @param dailyBudget    user's daily carbon budget in kg
     * @return risk level string
     */
    public String getRiskLevel(BigDecimal todayEmissions, BigDecimal dailyBudget) {
        if (todayEmissions == null) todayEmissions = BigDecimal.ZERO;
        if (dailyBudget == null || dailyBudget.compareTo(BigDecimal.ZERO) <= 0) {
            dailyBudget = new BigDecimal("4.2");
        }

        BigDecimal percentage = todayEmissions.multiply(HUNDRED)
                .divide(dailyBudget, 2, RoundingMode.HALF_UP);

        if (percentage.compareTo(BigDecimal.ZERO) == 0) return "EXCELLENT";
        if (percentage.compareTo(new BigDecimal("25")) <= 0) return "GOOD";
        if (percentage.compareTo(new BigDecimal("60")) <= 0) return "MODERATE";
        if (percentage.compareTo(HUNDRED) <= 0) return "HIGH";
        return "EXTREME";
    }

    /**
     * Calculate the number of trees needed to offset annual emissions.
     * Uses the tree absorption benchmark from the database.
     *
     * @param annualEmissions total annual CO2 emissions in kg
     * @return number of trees needed (rounded up)
     */
    public int calculateTreesNeeded(BigDecimal annualEmissions) {
        if (annualEmissions == null || annualEmissions.compareTo(BigDecimal.ZERO) <= 0) {
            return 0;
        }
        BigDecimal treeAbsorption = getTreeAbsorptionAnnual();
        return annualEmissions.divide(treeAbsorption, 0, RoundingMode.CEILING).intValue();
    }

    private BigDecimal getBenchmark(String type, BigDecimal fallback) {
        try {
            return emissionFactorRepository.findByCategoryAndTypeAndActiveTrue("_benchmark", type)
                    .map(EmissionFactor::getFactor)
                    .orElse(fallback);
        } catch (Exception e) {
            return fallback;
        }
    }
}
