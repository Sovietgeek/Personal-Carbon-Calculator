package com.ecoverse.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate Limiting Service using Bucket4j (token bucket algorithm).
 * Maintains per-IP buckets for different endpoint types.
 */
@Service
public class RateLimitService {

    private final int loginPerMinute;
    private final int apiPerMinute;
    private final int passwordResetPerHour;
    private final int refreshPerMinute;
    private final int resendVerificationPerMinute;
    private final int oauthExchangePerMinute;

    private final ConcurrentHashMap<String, Bucket> loginBuckets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Bucket> apiBuckets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Bucket> passwordResetBuckets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Bucket> refreshBuckets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Bucket> resendVerificationBuckets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Bucket> oauthExchangeBuckets = new ConcurrentHashMap<>();

    // Phase 5: Payment rate limit buckets
    private final ConcurrentHashMap<String, Bucket> paymentCreateBuckets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Bucket> paymentVerifyBuckets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Bucket> refundRequestBuckets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Bucket> webhookBuckets = new ConcurrentHashMap<>();

    public RateLimitService(
            @Value("${app.rate-limit.login-per-minute:5}") int loginPerMinute,
            @Value("${app.rate-limit.api-per-minute:60}") int apiPerMinute,
            @Value("${app.rate-limit.password-reset-per-hour:3}") int passwordResetPerHour,
            @Value("${app.rate-limit.refresh-per-minute:30}") int refreshPerMinute,
            @Value("${app.rate-limit.resend-verification-per-minute:5}") int resendVerificationPerMinute,
            @Value("${app.rate-limit.oauth-exchange-per-minute:10}") int oauthExchangePerMinute
    ) {
        this.loginPerMinute = loginPerMinute;
        this.apiPerMinute = apiPerMinute;
        this.passwordResetPerHour = passwordResetPerHour;
        this.refreshPerMinute = refreshPerMinute;
        this.resendVerificationPerMinute = resendVerificationPerMinute;
        this.oauthExchangePerMinute = oauthExchangePerMinute;
    }

    public boolean allowLogin(String ipAddress) {
        Bucket bucket = loginBuckets.computeIfAbsent(ipAddress, this::createLoginBucket);
        return bucket.tryConsume(1);
    }

    public boolean allowApiRequest(String ipAddress) {
        Bucket bucket = apiBuckets.computeIfAbsent(ipAddress, this::createApiBucket);
        return bucket.tryConsume(1);
    }

    public boolean allowPasswordReset(String ipAddress) {
        Bucket bucket = passwordResetBuckets.computeIfAbsent(ipAddress, this::createPasswordResetBucket);
        return bucket.tryConsume(1);
    }

    public boolean allowRefresh(String ipAddress) {
        Bucket bucket = refreshBuckets.computeIfAbsent(ipAddress, this::createRefreshBucket);
        return bucket.tryConsume(1);
    }

    public boolean allowResendVerification(String ipAddress) {
        Bucket bucket = resendVerificationBuckets.computeIfAbsent(ipAddress, this::createResendVerificationBucket);
        return bucket.tryConsume(1);
    }

    public boolean allowOAuthExchange(String ipAddress) {
        Bucket bucket = oauthExchangeBuckets.computeIfAbsent(ipAddress, this::createOAuthExchangeBucket);
        return bucket.tryConsume(1);
    }

    public long getLoginRemaining(String ipAddress) {
        Bucket bucket = loginBuckets.get(ipAddress);
        return bucket != null ? bucket.getAvailableTokens() : loginPerMinute;
    }

    public long getApiRemaining(String ipAddress) {
        Bucket bucket = apiBuckets.get(ipAddress);
        return bucket != null ? bucket.getAvailableTokens() : apiPerMinute;
    }

    public long getRefreshRemaining(String ipAddress) {
        Bucket bucket = refreshBuckets.get(ipAddress);
        return bucket != null ? bucket.getAvailableTokens() : refreshPerMinute;
    }

    private Bucket createLoginBucket(String ipAddress) {
        Bandwidth limit = Bandwidth.classic(loginPerMinute, Refill.intervally(loginPerMinute, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limit).build();
    }

    private Bucket createApiBucket(String ipAddress) {
        Bandwidth limit = Bandwidth.classic(apiPerMinute, Refill.intervally(apiPerMinute, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limit).build();
    }

    private Bucket createPasswordResetBucket(String ipAddress) {
        Bandwidth limit = Bandwidth.classic(passwordResetPerHour, Refill.intervally(passwordResetPerHour, Duration.ofHours(1)));
        return Bucket.builder().addLimit(limit).build();
    }

    private Bucket createRefreshBucket(String ipAddress) {
        Bandwidth limit = Bandwidth.classic(refreshPerMinute, Refill.intervally(refreshPerMinute, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limit).build();
    }

    private Bucket createResendVerificationBucket(String ipAddress) {
        Bandwidth limit = Bandwidth.classic(resendVerificationPerMinute, Refill.intervally(resendVerificationPerMinute, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limit).build();
    }

    private Bucket createOAuthExchangeBucket(String ipAddress) {
        Bandwidth limit = Bandwidth.classic(oauthExchangePerMinute, Refill.intervally(oauthExchangePerMinute, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limit).build();
    }

    // ===== Phase 5: Payment Rate Limits =====

    public boolean allowPaymentCreate(String ipAddress) {
        Bucket bucket = paymentCreateBuckets.computeIfAbsent(ipAddress, this::createPaymentCreateBucket);
        return bucket.tryConsume(1);
    }

    public boolean allowPaymentVerify(String ipAddress) {
        Bucket bucket = paymentVerifyBuckets.computeIfAbsent(ipAddress, this::createPaymentVerifyBucket);
        return bucket.tryConsume(1);
    }

    public boolean allowRefundRequest(String ipAddress) {
        Bucket bucket = refundRequestBuckets.computeIfAbsent(ipAddress, this::createRefundRequestBucket);
        return bucket.tryConsume(1);
    }

    public boolean allowWebhook(String ipAddress) {
        Bucket bucket = webhookBuckets.computeIfAbsent(ipAddress, this::createWebhookBucket);
        return bucket.tryConsume(1);
    }

    // Payment order creation: 10/min per IP
    private Bucket createPaymentCreateBucket(String ipAddress) {
        Bandwidth limit = Bandwidth.classic(10, Refill.intervally(10, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limit).build();
    }

    // Payment verification: 10/min per IP
    private Bucket createPaymentVerifyBucket(String ipAddress) {
        Bandwidth limit = Bandwidth.classic(10, Refill.intervally(10, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limit).build();
    }

    // Refund requests: 5/min per IP
    private Bucket createRefundRequestBucket(String ipAddress) {
        Bandwidth limit = Bandwidth.classic(5, Refill.intervally(5, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limit).build();
    }

    // Webhook: 100/min per IP (generous for Razorpay retries)
    private Bucket createWebhookBucket(String ipAddress) {
        Bandwidth limit = Bandwidth.classic(100, Refill.intervally(100, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limit).build();
    }
}
