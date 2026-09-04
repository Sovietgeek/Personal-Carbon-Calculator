package com.ecoverse.dto.health;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BMIRequest {

    @NotNull(message = "Weight is required")
    @DecimalMin(value = "2.0", message = "Weight must be at least 2 kg")
    @DecimalMax(value = "300.0", message = "Weight must be at most 300 kg")
    private Double weight;

    @NotNull(message = "Height is required")
    @DecimalMin(value = "30.0", message = "Height must be at least 30 cm")
    @DecimalMax(value = "300.0", message = "Height must be at most 300 cm")
    private Double height;
}
