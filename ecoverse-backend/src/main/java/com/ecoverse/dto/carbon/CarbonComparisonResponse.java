package com.ecoverse.dto.carbon;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CarbonComparisonResponse {
    private Double yourEmission;           // User's emission (kg)
    private List<Comparison> comparisons;   // Fun comparisons

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Comparison {
        private String icon;        // emoji or FA icon class
        private String item;        // "iPhone manufacturing"
        private Double equivalent;  // How many of that item
        private String unit;        // "phones", "flights", "trees"
        private String description; // "Your monthly carbon = 14 iPhones being made"
    }
}
