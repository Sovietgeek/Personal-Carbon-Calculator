package com.ecoverse.controller;

import com.ecoverse.dto.ApiResponse;
import com.ecoverse.dto.weather.WeatherResponse;
import com.ecoverse.service.WeatherService;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/weather")
@Validated
public class WeatherController {

    @Autowired
    private WeatherService weatherService;

    @GetMapping
    public ResponseEntity<ApiResponse<WeatherResponse>> getWeather(
            @RequestParam(defaultValue = "28.6139")
            @DecimalMin(value = "-90.0", message = "Latitude must be >= -90")
            @DecimalMax(value = "90.0", message = "Latitude must be <= 90") double lat,
            @RequestParam(defaultValue = "77.2090")
            @DecimalMin(value = "-180.0", message = "Longitude must be >= -180")
            @DecimalMax(value = "180.0", message = "Longitude must be <= 180") double lon) {
        WeatherResponse response = weatherService.getWeather(lat, lon);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
