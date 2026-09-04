package com.ecoverse.dto.profile;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProfileResponse {

    private Long id;
    private String name;
    private String email;
    private String country;
    private String city;
    private String state;
    private BigDecimal carbonBudget;
    private Boolean isPremium;
    private LocalDate joinedDate;
    private Integer bestStreak;
    private Integer goalsSteps;
    private Integer goalsSleep;
    private Integer goalsWater;
    private Integer goalsCalories;
    private String timezone;
}
