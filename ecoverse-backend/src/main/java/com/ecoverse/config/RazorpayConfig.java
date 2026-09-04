package com.ecoverse.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Razorpay configuration properties.
 *
 * SECURITY:
 * - key-secret is NEVER exposed to the frontend
 * - webhook-secret is NEVER exposed to the frontend
 * - Live mode REQUIRES both key-id and key-secret to be set (startup fails otherwise)
 * - Test mode allows empty keys (falls back to COD-only)
 *
 * Environment variables:
 *   RAZORPAY_KEY_ID       — Razorpay API key ID (public, safe for frontend)
 *   RAZORPAY_KEY_SECRET   — Razorpay API key secret (NEVER expose to frontend)
 *   RAZORPAY_WEBHOOK_SECRET — Secret for verifying webhook signatures
 *   RAZORPAY_MODE         — "test" or "live" (default: test)
 *   RAZORPAY_CURRENCY     — Currency code (default: INR)
 *   RAZORPAY_PAYMENT_EXPIRY_MINUTES — Minutes before abandoning unpaid orders (default: 30)
 */
@Component
@ConfigurationProperties(prefix = "razorpay")
public class RazorpayConfig {

    private static final Logger log = LoggerFactory.getLogger(RazorpayConfig.class);

    private String keyId = "";
    private String keySecret = "";
    private String webhookSecret = "";
    private String currency = "INR";
    private String mode = "test";
    private int paymentExpiryMinutes = 30;

    @PostConstruct
    public void validate() {
        if ("live".equalsIgnoreCase(mode)) {
            if (keyId == null || keyId.isBlank()) {
                throw new IllegalStateException(
                    "RAZORPAY_KEY_ID is REQUIRED when RAZORPAY_MODE=live. " +
                    "Set the RAZORPAY_KEY_ID environment variable.");
            }
            if (keySecret == null || keySecret.isBlank()) {
                throw new IllegalStateException(
                    "RAZORPAY_KEY_SECRET is REQUIRED when RAZORPAY_MODE=live. " +
                    "Set the RAZORPAY_KEY_SECRET environment variable.");
            }
            log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            log.info("⚠️  RAZORPAY MODE: LIVE — Real money transactions enabled!");
            log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        } else {
            if (keyId == null || keyId.isBlank() || keySecret == null || keySecret.isBlank()) {
                log.info("Razorpay keys not configured — online payments disabled. COD-only mode.");
            } else {
                log.info("Razorpay TEST MODE configured — online payments enabled with test keys.");
            }
        }

        if (paymentExpiryMinutes < 5) {
            log.warn("RAZORPAY_PAYMENT_EXPIRY_MINUTES={} is too low. Minimum is 5. Setting to 5.", paymentExpiryMinutes);
            paymentExpiryMinutes = 5;
        }
    }

    /**
     * Returns true if Razorpay is configured with valid keys.
     * Used by PaymentService to decide whether to create Razorpay orders.
     */
    public boolean isConfigured() {
        return keyId != null && !keyId.isBlank()
            && keySecret != null && !keySecret.isBlank();
    }

    /**
     * Returns true if webhook verification is configured.
     * Without webhook secret, webhooks cannot be verified.
     */
    public boolean isWebhookConfigured() {
        return webhookSecret != null && !webhookSecret.isBlank();
    }

    public boolean isLiveMode() {
        return "live".equalsIgnoreCase(mode);
    }

    // ===== Getters and Setters =====

    public String getKeyId() { return keyId; }
    public void setKeyId(String keyId) { this.keyId = keyId; }

    public String getKeySecret() { return keySecret; }
    public void setKeySecret(String keySecret) { this.keySecret = keySecret; }

    public String getWebhookSecret() { return webhookSecret; }
    public void setWebhookSecret(String webhookSecret) { this.webhookSecret = webhookSecret; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public int getPaymentExpiryMinutes() { return paymentExpiryMinutes; }
    public void setPaymentExpiryMinutes(int paymentExpiryMinutes) { this.paymentExpiryMinutes = paymentExpiryMinutes; }
}
