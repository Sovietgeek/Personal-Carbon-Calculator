package com.ecoverse.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardResponse {

    // ===== Carbon metrics =====
    private BigDecimal carbonToday;
    private BigDecimal todayAvoided;
    private BigDecimal monthEmissions;
    private BigDecimal yearEmissions;
    private BigDecimal budgetRemaining;
    private BigDecimal budgetUsedPercent;
    private Integer treesNeeded;
    private String riskLevel;
    private BigDecimal riskPercentage;

    // ===== Streak =====
    private Integer streakDays;
    private Integer bestStreak;

    // ===== Health =====
    private Integer healthScore;
    private Boolean stepsGoalMet;
    private Boolean workoutDone;
    private Boolean sleepGoalMet;
    private Boolean waterGoalMet;
    private Integer steps;
    private Integer calories;
    private Double sleep;
    private Double water;
    private Double weight;

    // ===== Breakdown & Activity =====
    private Map<String, BigDecimal> categoryBreakdown;
    private List<ActivityItem> recentActivity;

    // ===== Eco Tip =====
    private String ecoTip;
}
