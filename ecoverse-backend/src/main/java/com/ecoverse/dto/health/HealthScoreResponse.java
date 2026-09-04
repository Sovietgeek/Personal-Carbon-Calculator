package com.ecoverse.dto.health;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HealthScoreResponse {

    private Integer score; // out of 100
    private Boolean stepsGoalMet;
    private Boolean workoutDone;
    private Boolean sleepGoalMet;
    private Boolean waterGoalMet;
}
