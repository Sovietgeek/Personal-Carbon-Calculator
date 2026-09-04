package com.ecoverse.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for rate limiting security:
 * - Login rate limiting (5/min)
 * - Password reset rate limiting (3/hour)
 * - Token refresh rate limiting (30/min)
 * - Resend verification rate limiting (5/min)
 * - OAuth exchange rate limiting (10/min)
 * - General API rate limiting (60/min)
 * - Different IPs have independent buckets
 */
class RateLimitSecurityTest {

    private RateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        // Create with default values
        rateLimitService = new RateLimitService(5, 60, 3, 30, 5, 10);
    }

    @Nested
    @DisplayName("Login Rate Limiting")
    class LoginRateLimit {

        @Test
        @DisplayName("Login allows up to 5 requests per minute per IP")
        void loginAllowsUpTo5PerMinute() {
            for (int i = 0; i < 5; i++) {
                assertThat(rateLimitService.allowLogin("192.168.1.1")).isTrue();
            }
            // 6th should be blocked
            assertThat(rateLimitService.allowLogin("192.168.1.1")).isFalse();
        }

        @Test
        @DisplayName("Login rate limit is per-IP (different IPs have separate buckets)")
        void loginRateLimitIsPerIp() {
            // Exhaust bucket for IP 1
            for (int i = 0; i < 5; i++) {
                assertThat(rateLimitService.allowLogin("192.168.1.1")).isTrue();
            }
            // IP 1 blocked
            assertThat(rateLimitService.allowLogin("192.168.1.1")).isFalse();

            // IP 2 still allowed
            assertThat(rateLimitService.allowLogin("10.0.0.1")).isTrue();
        }
    }

    @Nested
    @DisplayName("Password Reset Rate Limiting")
    class PasswordResetRateLimit {

        @Test
        @DisplayName("Password reset allows up to 3 per hour per IP")
        void passwordResetAllows3PerHour() {
            for (int i = 0; i < 3; i++) {
                assertThat(rateLimitService.allowPasswordReset("192.168.1.1")).isTrue();
            }
            assertThat(rateLimitService.allowPasswordReset("192.168.1.1")).isFalse();
        }
    }

    @Nested
    @DisplayName("Refresh Rate Limiting")
    class RefreshRateLimit {

        @Test
        @DisplayName("Token refresh allows up to 30 per minute per IP")
        void refreshAllows30PerMinute() {
            for (int i = 0; i < 30; i++) {
                assertThat(rateLimitService.allowRefresh("192.168.1.1")).isTrue();
            }
            assertThat(rateLimitService.allowRefresh("192.168.1.1")).isFalse();
        }
    }

    @Nested
    @DisplayName("Resend Verification Rate Limiting")
    class ResendVerificationRateLimit {

        @Test
        @DisplayName("Resend verification allows up to 5 per minute per IP")
        void resendAllows5PerMinute() {
            for (int i = 0; i < 5; i++) {
                assertThat(rateLimitService.allowResendVerification("192.168.1.1")).isTrue();
            }
            assertThat(rateLimitService.allowResendVerification("192.168.1.1")).isFalse();
        }
    }

    @Nested
    @DisplayName("OAuth Exchange Rate Limiting")
    class OAuthExchangeRateLimit {

        @Test
        @DisplayName("OAuth exchange allows up to 10 per minute per IP")
        void oauthExchangeAllows10PerMinute() {
            for (int i = 0; i < 10; i++) {
                assertThat(rateLimitService.allowOAuthExchange("192.168.1.1")).isTrue();
            }
            assertThat(rateLimitService.allowOAuthExchange("192.168.1.1")).isFalse();
        }
    }

    @Nested
    @DisplayName("General API Rate Limiting")
    class ApiRateLimit {

        @Test
        @DisplayName("General API allows up to 60 per minute per IP")
        void apiAllows60PerMinute() {
            for (int i = 0; i < 60; i++) {
                assertThat(rateLimitService.allowApiRequest("192.168.1.1")).isTrue();
            }
            assertThat(rateLimitService.allowApiRequest("192.168.1.1")).isFalse();
        }
    }
}
