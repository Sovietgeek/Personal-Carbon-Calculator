package com.ecoverse.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for ProductionStartupValidator configuration validation.
 *
 * Phase A requirements verified:
 * - Production fails without JWT_SECRET
 * - Production fails with weak JWT_SECRET (less than 32 chars)
 * - Production fails with H2 database
 * - Production fails with wildcard CORS
 * - Development profile only warns (doesn't fail)
 */
@ExtendWith(MockitoExtension.class)
class ProductionStartupValidatorTest {

    @InjectMocks
    private ProductionStartupValidator validator;

    @BeforeEach
    void setUp() {
        // Set safe defaults
        ReflectionTestUtils.setField(validator, "jwtSecret", "a-very-long-secret-key-that-is-at-least-32-characters-long-for-security");
        ReflectionTestUtils.setField(validator, "datasourceUrl", "jdbc:postgresql://localhost:5432/ecoverse");
        ReflectionTestUtils.setField(validator, "corsOrigins", "https://ecoverse.app");
    }

    // ==================================================================
    // PRODUCTION PROFILE TESTS
    // ==================================================================

    @Test
    @DisplayName("Production profile fails without JWT_SECRET")
    void productionFailsWithoutJwtSecret() {
        ReflectionTestUtils.setField(validator, "jwtSecret", "");
        ReflectionTestUtils.setField(validator, "activeProfile", "prod");

        assertThatThrownBy(() -> validator.validateOnStartup())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    @DisplayName("Production profile fails with null JWT_SECRET")
    void productionFailsWithNullJwtSecret() {
        ReflectionTestUtils.setField(validator, "jwtSecret", null);
        ReflectionTestUtils.setField(validator, "activeProfile", "prod");

        assertThatThrownBy(() -> validator.validateOnStartup())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    @DisplayName("Production profile fails with weak JWT_SECRET (too short)")
    void productionFailsWithWeakJwtSecret() {
        ReflectionTestUtils.setField(validator, "jwtSecret", "too-short-key");
        ReflectionTestUtils.setField(validator, "activeProfile", "prod");

        assertThatThrownBy(() -> validator.validateOnStartup())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("too short");
    }

    @Test
    @DisplayName("Production profile fails with H2 database URL")
    void productionFailsWithH2Database() {
        ReflectionTestUtils.setField(validator, "datasourceUrl", "jdbc:h2:mem:ecoverse");
        ReflectionTestUtils.setField(validator, "activeProfile", "prod");

        assertThatThrownBy(() -> validator.validateOnStartup())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PostgreSQL");
    }

    @Test
    @DisplayName("Production profile fails with wildcard CORS origins")
    void productionFailsWithWildcardCors() {
        ReflectionTestUtils.setField(validator, "corsOrigins", "*");
        ReflectionTestUtils.setField(validator, "activeProfile", "prod");

        assertThatThrownBy(() -> validator.validateOnStartup())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CORS");
    }

    @Test
    @DisplayName("Production profile passes with valid configuration")
    void productionPassesWithValidConfig() {
        ReflectionTestUtils.setField(validator, "activeProfile", "prod");

        assertThatCode(() -> validator.validateOnStartup())
                .doesNotThrowAnyException();
    }

    // ==================================================================
    // STAGING PROFILE TESTS
    // ==================================================================

    @Test
    @DisplayName("Staging profile also fails without JWT_SECRET")
    void stagingFailsWithoutJwtSecret() {
        ReflectionTestUtils.setField(validator, "jwtSecret", "");
        ReflectionTestUtils.setField(validator, "activeProfile", "staging");

        assertThatThrownBy(() -> validator.validateOnStartup())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    // ==================================================================
    // DEVELOPMENT PROFILE TESTS
    // ==================================================================

    @Test
    @DisplayName("Development profile does NOT fail without JWT_SECRET (only warns)")
    void developmentDoesNotFailWithoutJwtSecret() {
        ReflectionTestUtils.setField(validator, "jwtSecret", "");
        ReflectionTestUtils.setField(validator, "activeProfile", "default");

        assertThatCode(() -> validator.validateOnStartup())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Development profile with H2 does not fail")
    void developmentDoesNotFailWithH2() {
        ReflectionTestUtils.setField(validator, "datasourceUrl", "jdbc:h2:mem:ecoverse");
        ReflectionTestUtils.setField(validator, "activeProfile", "dev");

        assertThatCode(() -> validator.validateOnStartup())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Development profile with wildcard CORS does not fail")
    void developmentDoesNotFailWithWildcardCors() {
        ReflectionTestUtils.setField(validator, "corsOrigins", "*");
        ReflectionTestUtils.setField(validator, "activeProfile", "default");

        assertThatCode(() -> validator.validateOnStartup())
                .doesNotThrowAnyException();
    }
}
