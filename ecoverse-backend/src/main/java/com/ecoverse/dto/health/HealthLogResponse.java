package com.ecoverse.dto.health;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HealthLogResponse {

    private Long id;
    private String type;
    private String entryDate;

    private Integer steps;
    private Double distance;
    private String workoutType;
    private Integer duration;
    private String intensity;
    private Integer calories;
    private Double weight;
    private Double height;
    private Double bodyFat;
    private Double hours;
    private String quality;
    private String bedtime;
    private String wakeTime;
    private Integer waterMl;
}
