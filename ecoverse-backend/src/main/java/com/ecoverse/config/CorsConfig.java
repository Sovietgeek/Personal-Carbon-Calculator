package com.ecoverse.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS is now handled by SecurityConfig.java via CorsConfigurationSource.
 * This class is kept as a placeholder for any future WebMvc configurations.
 * Do NOT add CORS mappings here — Spring Security takes precedence.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {
    // CORS handled in SecurityConfig.java
}
