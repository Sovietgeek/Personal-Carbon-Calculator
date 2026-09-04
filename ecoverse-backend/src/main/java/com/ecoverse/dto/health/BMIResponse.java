package com.ecoverse.dto.health;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BMIResponse {

    private Double bmi;
    private String category;
    private String color;
    private String advice;
    private String disclaimer;
}
