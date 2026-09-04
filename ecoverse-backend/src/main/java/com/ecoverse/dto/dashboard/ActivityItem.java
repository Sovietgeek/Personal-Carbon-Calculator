package com.ecoverse.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivityItem {

    private Long id;
    /** "carbon" or "health" */
    private String type;
    /** Carbon category (transport, energy, etc.) or health type (steps, workout, etc.) */
    private String category;
    /** Human-readable description (e.g., "car-petrol", "Running") */
    private String description;
    /** CO2 in kg for carbon entries, or step count / calories etc. for health */
    private BigDecimal value;
    /** ISO-8601 timestamp */
    private String timestamp;
}
