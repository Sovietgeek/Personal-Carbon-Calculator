package com.ecoverse.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;

/**
 * Validates that all required secrets and configuration are present on startup.
 * Fails fast if required values are missing in production/staging profiles.
 *
 * This prevents the application from starting in a dangerously misconfigured state.
 */
@Configuration
public class ProductionStartupValidator {

    private static final Logger log = LoggerFactory.getLogger(ProductionStartupValidator.class);

    @Value("${jwt.secret:}")
    private String jwtSecret;

    @Value("${spring.datasource.url:}")
    private String datasourceUrl;

    @Value("${spring.profiles.active:default}")
    private String activeProfile;

    @Value("${app.cors.allowed-origins:}")
    private String corsOrigins;

    @EventListener(ContextRefreshedEvent.class)
    public void validateOnStartup() {
        boolean isProductionLike = isActiveProfile("prod") || isActiveProfile("staging");

        if (!isProductionLike) {
            // In development, just warn about missing secrets
            if (jwtSecret == null || jwtSecret.isBlank()) {
                log.warn("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                log.warn("⚠️  JWT_SECRET is not set. Using insecure configuration.");
                log.warn("   Set JWT_SECRET in your .env file for secure operation.");
                log.warn("   Generate one with: openssl rand -base64 64 | tr -d '\\n'");
                log.warn("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            }
            return;
        }

        // Production/staging: fail fast on missing required configuration
        log.info("Validating production configuration for profile: {}", activeProfile);

        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException(
                "FATAL: JWT_SECRET is not set. " +
                "Set the JWT_SECRET environment variable before starting the application. " +
                "Generate one with: openssl rand -base64 64 | tr -d '\\n'"
            );
        }

        if (jwtSecret.length() < 32) {
            throw new IllegalStateException(
                "FATAL: JWT_SECRET is too short (" + jwtSecret.length() + " chars). " +
                "It must be at least 256 bits (32 bytes / ~43 base64 chars). " +
                "Generate one with: openssl rand -base64 64 | tr -d '\\n'"
            );
        }

        if (datasourceUrl == null || datasourceUrl.isBlank() || datasourceUrl.contains("h2")) {
            throw new IllegalStateException(
                "FATAL: Production must use PostgreSQL, not H2. " +
                "Set DATABASE_URL environment variable to a PostgreSQL connection string."
            );
        }

        if (corsOrigins == null || corsOrigins.isBlank() || corsOrigins.contains("*")) {
            throw new IllegalStateException(
                "FATAL: CORS_ORIGINS must be explicitly set in production. " +
                "Wildcard (*) origins are not allowed."
            );
        }

        log.info("✅ Production configuration validation passed");
    }

    private boolean isActiveProfile(String profile) {
        return activeProfile != null && activeProfile.contains(profile);
    }
}
