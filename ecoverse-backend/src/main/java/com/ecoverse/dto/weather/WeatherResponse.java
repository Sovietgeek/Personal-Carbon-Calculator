package com.ecoverse.dto.weather;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WeatherResponse {

    private String location;
    private Double temperature;
    private Double feelsLike;
    private Double humidity;
    private Double windSpeed;
    private Double pressure;
    private Double uvIndex;
    private Double visibility;
    private Double aqi;
    private String aqiLabel;
    private Double pm25;
    private Integer rainChance;  // from daily precipitation_probability_max (null if unavailable)
    private String description;
    private String icon;
    private String date;
    private List<ForecastDay> forecast;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ForecastDay {
        private String date;
        private String icon;
        private String description;
        private Double highTemp;
        private Double lowTemp;
        private Integer rainChance;
    }
}
