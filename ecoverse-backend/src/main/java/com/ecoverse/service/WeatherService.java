package com.ecoverse.service;

import com.ecoverse.dto.weather.WeatherResponse;
import com.ecoverse.dto.weather.WeatherResponse.ForecastDay;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Production Weather Service — Phase 3.
 *
 * Key changes:
 * - @Cacheable with 10-minute TTL (weather changes slowly)
 * - Separate AQI fetch from air-quality API (forecast endpoint doesn't provide AQI)
 * - Null fields when data unavailable (NOT fake 0.0 values)
 * - slf4j logging (WARN on errors; no coordinate logging at INFO)
 * - Rain chance from daily precipitation_probability_max
 */
@Service
public class WeatherService {

    private static final Logger log = LoggerFactory.getLogger(WeatherService.class);

    private final WebClient webClient;

    @Value("${open-meteo.base-url:https://api.open-meteo.com/v1}")
    private String baseUrl;

    // WMO Weather code mapping to description
    private static final Map<Integer, String> WEATHER_DESCRIPTIONS = new HashMap<>();
    private static final Map<Integer, String> WEATHER_ICONS = new HashMap<>();

    static {
        WEATHER_DESCRIPTIONS.put(0, "Clear Sky");
        WEATHER_DESCRIPTIONS.put(1, "Mainly Clear");
        WEATHER_DESCRIPTIONS.put(2, "Partly Cloudy");
        WEATHER_DESCRIPTIONS.put(3, "Overcast");
        WEATHER_DESCRIPTIONS.put(45, "Foggy");
        WEATHER_DESCRIPTIONS.put(48, "Depositing Rime Fog");
        WEATHER_DESCRIPTIONS.put(51, "Light Drizzle");
        WEATHER_DESCRIPTIONS.put(53, "Moderate Drizzle");
        WEATHER_DESCRIPTIONS.put(55, "Dense Drizzle");
        WEATHER_DESCRIPTIONS.put(56, "Light Freezing Drizzle");
        WEATHER_DESCRIPTIONS.put(57, "Dense Freezing Drizzle");
        WEATHER_DESCRIPTIONS.put(61, "Slight Rain");
        WEATHER_DESCRIPTIONS.put(63, "Moderate Rain");
        WEATHER_DESCRIPTIONS.put(65, "Heavy Rain");
        WEATHER_DESCRIPTIONS.put(66, "Light Freezing Rain");
        WEATHER_DESCRIPTIONS.put(67, "Heavy Freezing Rain");
        WEATHER_DESCRIPTIONS.put(71, "Slight Snowfall");
        WEATHER_DESCRIPTIONS.put(73, "Moderate Snowfall");
        WEATHER_DESCRIPTIONS.put(75, "Heavy Snowfall");
        WEATHER_DESCRIPTIONS.put(77, "Snow Grains");
        WEATHER_DESCRIPTIONS.put(80, "Slight Rain Showers");
        WEATHER_DESCRIPTIONS.put(81, "Moderate Rain Showers");
        WEATHER_DESCRIPTIONS.put(82, "Violent Rain Showers");
        WEATHER_DESCRIPTIONS.put(85, "Slight Snow Showers");
        WEATHER_DESCRIPTIONS.put(86, "Heavy Snow Showers");
        WEATHER_DESCRIPTIONS.put(95, "Thunderstorm");
        WEATHER_DESCRIPTIONS.put(96, "Thunderstorm with Slight Hail");
        WEATHER_DESCRIPTIONS.put(99, "Thunderstorm with Heavy Hail");

        WEATHER_ICONS.put(0, "fa-sun");
        WEATHER_ICONS.put(1, "fa-cloud-sun");
        WEATHER_ICONS.put(2, "fa-cloud-sun");
        WEATHER_ICONS.put(3, "fa-cloud");
        WEATHER_ICONS.put(45, "fa-smog");
        WEATHER_ICONS.put(48, "fa-smog");
        WEATHER_ICONS.put(51, "fa-cloud-rain");
        WEATHER_ICONS.put(53, "fa-cloud-rain");
        WEATHER_ICONS.put(55, "fa-cloud-rain");
        WEATHER_ICONS.put(56, "fa-cloud-rain");
        WEATHER_ICONS.put(57, "fa-cloud-rain");
        WEATHER_ICONS.put(61, "fa-cloud-showers-heavy");
        WEATHER_ICONS.put(63, "fa-cloud-showers-heavy");
        WEATHER_ICONS.put(65, "fa-cloud-showers-heavy");
        WEATHER_ICONS.put(66, "fa-cloud-showers-heavy");
        WEATHER_ICONS.put(67, "fa-cloud-showers-heavy");
        WEATHER_ICONS.put(71, "fa-snowflake");
        WEATHER_ICONS.put(73, "fa-snowflake");
        WEATHER_ICONS.put(75, "fa-snowflake");
        WEATHER_ICONS.put(77, "fa-snowflake");
        WEATHER_ICONS.put(80, "fa-cloud-showers-heavy");
        WEATHER_ICONS.put(81, "fa-cloud-showers-heavy");
        WEATHER_ICONS.put(82, "fa-cloud-showers-heavy");
        WEATHER_ICONS.put(85, "fa-snowflake");
        WEATHER_ICONS.put(86, "fa-snowflake");
        WEATHER_ICONS.put(95, "fa-bolt");
        WEATHER_ICONS.put(96, "fa-bolt");
        WEATHER_ICONS.put(99, "fa-bolt");
    }

    public WeatherService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    /**
     * Get weather data for given coordinates.
     * Results are cached for 10 minutes (Caffeine TTL).
     * AQI is fetched from a separate air-quality API (non-fatal on failure).
     */
    @Cacheable(value = "weather", key = "#lat + ',' + #lon")
    @SuppressWarnings("unchecked")
    public WeatherResponse getWeather(double lat, double lon) {
        try {
            // 1. Fetch weather from forecast API
            Map<String, Object> response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host("api.open-meteo.com")
                            .path("/v1/forecast")
                            .queryParam("latitude", lat)
                            .queryParam("longitude", lon)
                            .queryParam("current", "temperature_2m,relative_humidity_2m,wind_speed_10m,surface_pressure,uv_index,weather_code,apparent_temperature,visibility")
                            .queryParam("daily", "weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,uv_index_max")
                            .queryParam("timezone", "auto")
                            .queryParam("forecast_days", 7)
                            .build())
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response == null) {
                return buildNullResponse();
            }

            // Parse current weather
            Map<String, Object> current = (Map<String, Object>) response.get("current");
            String timezone = (String) response.get("timezone");
            String location = timezone != null ? timezone.replace("/", ", ").replace("_", " ") : "Unknown";

            Double temperature = getDoubleValue(current, "temperature_2m");
            Double feelsLike = getDoubleValue(current, "apparent_temperature");
            Double humidity = getDoubleValue(current, "relative_humidity_2m");
            Double windSpeed = getDoubleValue(current, "wind_speed_10m");
            Double pressure = getDoubleValue(current, "surface_pressure");
            Double uvIndex = getDoubleValue(current, "uv_index");
            Double visibility = getDoubleValue(current, "visibility");
            Integer weatherCode = getIntegerValue(current, "weather_code");

            String description = WEATHER_DESCRIPTIONS.getOrDefault(weatherCode, "Unknown");
            String icon = WEATHER_ICONS.getOrDefault(weatherCode, "fa-question");

            // 2. Fetch AQI from separate air quality API (non-fatal)
            Double aqi = null;
            Double pm25 = null;
            try {
                Map<String, Object> aqiResponse = webClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .scheme("https")
                                .host("air-quality-api.open-meteo.com")
                                .path("/v1/air-quality")
                                .queryParam("latitude", lat)
                                .queryParam("longitude", lon)
                                .queryParam("current", "pm2_5,pm10,us_aqi,european_aqi")
                                .queryParam("timezone", "auto")
                                .queryParam("forecast_days", 1)
                                .build())
                        .retrieve()
                        .bodyToMono(Map.class)
                        .block();

                if (aqiResponse != null) {
                    Map<String, Object> aqiCurrent = (Map<String, Object>) aqiResponse.get("current");
                    aqi = getDoubleValue(aqiCurrent, "us_aqi");
                    if (aqi == null) {
                        aqi = getDoubleValue(aqiCurrent, "european_aqi");
                    }
                    pm25 = getDoubleValue(aqiCurrent, "pm2_5");
                }
            } catch (Exception e) {
                log.warn("AQI fetch failed: {}", e.getMessage());
                // AQI failure is non-fatal — weather data still returned
            }

            // AQI label
            String aqiLabel;
            if (aqi == null) {
                aqiLabel = "Unknown";
            } else if (aqi <= 50) {
                aqiLabel = "Good";
            } else if (aqi <= 100) {
                aqiLabel = "Moderate";
            } else if (aqi <= 150) {
                aqiLabel = "Unhealthy (Sensitive)";
            } else {
                aqiLabel = "Unhealthy";
            }

            // 3. Rain chance from daily precipitation_probability_max
            Integer rainChance = null;
            Map<String, Object> daily = (Map<String, Object>) response.get("daily");
            if (daily != null) {
                List<Number> precipProbs = (List<Number>) daily.get("precipitation_probability_max");
                if (precipProbs != null && !precipProbs.isEmpty() && precipProbs.get(0) != null) {
                    rainChance = precipProbs.get(0).intValue();
                }
            }

            // 4. Parse daily forecast
            List<ForecastDay> forecast = new ArrayList<>();
            if (daily != null) {
                List<String> dates = (List<String>) daily.get("time");
                List<Number> dailyWeatherCodes = (List<Number>) daily.get("weather_code");
                List<Number> maxTemps = (List<Number>) daily.get("temperature_2m_max");
                List<Number> minTemps = (List<Number>) daily.get("temperature_2m_min");
                List<Number> dailyRainChance = (List<Number>) daily.get("precipitation_probability_max");

                if (dates != null) {
                    for (int i = 0; i < dates.size(); i++) {
                        int wc = dailyWeatherCodes != null && i < dailyWeatherCodes.size()
                                ? dailyWeatherCodes.get(i).intValue() : 0;
                        Double highTemp = maxTemps != null && i < maxTemps.size()
                                ? maxTemps.get(i).doubleValue() : null;
                        Double lowTemp = minTemps != null && i < minTemps.size()
                                ? minTemps.get(i).doubleValue() : null;
                        Integer dayRainChance = null;
                        if (dailyRainChance != null && i < dailyRainChance.size() && dailyRainChance.get(i) != null) {
                            dayRainChance = dailyRainChance.get(i).intValue();
                        }

                        forecast.add(ForecastDay.builder()
                                .date(dates.get(i))
                                .icon(WEATHER_ICONS.getOrDefault(wc, "fa-question"))
                                .description(WEATHER_DESCRIPTIONS.getOrDefault(wc, "Unknown"))
                                .highTemp(highTemp)
                                .lowTemp(lowTemp)
                                .rainChance(dayRainChance)
                                .build());
                    }
                }
            }

            return WeatherResponse.builder()
                    .location(location)
                    .temperature(temperature)
                    .feelsLike(feelsLike)
                    .humidity(humidity)
                    .windSpeed(windSpeed)
                    .pressure(pressure)
                    .uvIndex(uvIndex)
                    .visibility(visibility)
                    .aqi(aqi)
                    .aqiLabel(aqiLabel)
                    .pm25(pm25)
                    .rainChance(rainChance)
                    .description(description)
                    .icon(icon)
                    .date(LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE))
                    .forecast(forecast)
                    .build();

        } catch (Exception e) {
            log.error("Weather fetch failed: {}", e.getMessage());
            return buildNullResponse();
        }
    }

    /**
     * Build a null-field response when weather data is unavailable.
     * Uses null (not 0.0) to distinguish "unavailable" from "zero".
     */
    private WeatherResponse buildNullResponse() {
        return WeatherResponse.builder()
                .location("Unavailable")
                .temperature(null)
                .feelsLike(null)
                .humidity(null)
                .windSpeed(null)
                .pressure(null)
                .uvIndex(null)
                .visibility(null)
                .aqi(null)
                .aqiLabel("Unknown")
                .pm25(null)
                .rainChance(null)
                .description("Unable to fetch weather data")
                .icon("fa-question")
                .date(LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE))
                .forecast(Collections.emptyList())
                .build();
    }

    private Double getDoubleValue(Map<String, Object> map, String key) {
        if (map == null || !map.containsKey(key)) {
            return null;
        }
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return null;
    }

    private Integer getIntegerValue(Map<String, Object> map, String key) {
        if (map == null || !map.containsKey(key)) {
            return null;
        }
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return null;
    }
}
