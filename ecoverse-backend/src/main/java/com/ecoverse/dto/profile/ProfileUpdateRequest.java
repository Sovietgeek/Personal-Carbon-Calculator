package com.ecoverse.dto.profile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProfileUpdateRequest {

    @Size(min = 1, max = 100, message = "Name must be between 1 and 100 characters")
    private String name;

    private BigDecimal carbonBudget;
    private Integer goalsSteps;
    private Integer goalsSleep;
    private Integer goalsWater;
    private Integer goalsCalories;

    @Size(max = 50, message = "Timezone must be at most 50 characters")
    private String timezone;

    // Location fields (updated via PUT /api/profile/location)
    private String city;
    private String state;
    private Double latitude;
    private Double longitude;
}
