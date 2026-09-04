package com.ecoverse.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for RazorpayConfig validation and security behavior.
 */
class RazorpayConfigTest {

    @Test
    @DisplayName("Test mode with empty keys allows startup (COD-only)")
    void testModeWithEmptyKeysAllowsStartup() {
        RazorpayConfig config = new RazorpayConfig();
        config.setKeyId("");
        config.setKeySecret("");
        config.setMode("test");

        // Should NOT throw
        config.validate();
        assertThat(config.isConfigured()).isFalse();
        assertThat(config.isLiveMode()).isFalse();
    }

    @Test
    @DisplayName("Test mode with keys is configured")
    void testModeWithKeysIsConfigured() {
        RazorpayConfig config = new RazorpayConfig();
        config.setKeyId("rzp_test_123");
        config.setKeySecret("secret_123");
        config.setMode("test");

        config.validate();
        assertThat(config.isConfigured()).isTrue();
        assertThat(config.isLiveMode()).isFalse();
    }

    @Test
    @DisplayName("Live mode without key-id fails startup")
    void liveModeWithoutKeyIdFailsStartup() {
        RazorpayConfig config = new RazorpayConfig();
        config.setKeyId("");
        config.setKeySecret("secret_123");
        config.setMode("live");

        assertThatThrownBy(() -> config.validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RAZORPAY_KEY_ID is REQUIRED");
    }

    @Test
    @DisplayName("Live mode without key-secret fails startup")
    void liveModeWithoutKeySecretFailsStartup() {
        RazorpayConfig config = new RazorpayConfig();
        config.setKeyId("rzp_live_123");
        config.setKeySecret("");
        config.setMode("live");

        assertThatThrownBy(() -> config.validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RAZORPAY_KEY_SECRET is REQUIRED");
    }

    @Test
    @DisplayName("Webhook configured only when secret is non-empty")
    void webhookConfiguredOnlyWhenSecretSet() {
        RazorpayConfig config = new RazorpayConfig();
        config.setMode("test");
        config.setKeyId("test");
        config.setKeySecret("test");

        config.setWebhookSecret("");
        assertThat(config.isWebhookConfigured()).isFalse();

        config.setWebhookSecret("wh_secret_123");
        assertThat(config.isWebhookConfigured()).isTrue();
    }

    @Test
    @DisplayName("Payment expiry minimum is 5 minutes")
    void paymentExpiryMinimumIsFiveMinutes() {
        RazorpayConfig config = new RazorpayConfig();
        config.setMode("test");
        config.setPaymentExpiryMinutes(1); // Too low

        config.validate();
        assertThat(config.getPaymentExpiryMinutes()).isEqualTo(5);
    }

    @Test
    @DisplayName("Null keys are not configured")
    void nullKeysAreNotConfigured() {
        RazorpayConfig config = new RazorpayConfig();
        config.setKeyId(null);
        config.setKeySecret(null);
        config.setMode("test");

        config.validate();
        assertThat(config.isConfigured()).isFalse();
    }

    @Test
    @DisplayName("Default values are correct")
    void defaultValuesAreCorrect() {
        RazorpayConfig config = new RazorpayConfig();
        assertThat(config.getCurrency()).isEqualTo("INR");
        assertThat(config.getMode()).isEqualTo("test");
        assertThat(config.getPaymentExpiryMinutes()).isEqualTo(30);
    }
}
