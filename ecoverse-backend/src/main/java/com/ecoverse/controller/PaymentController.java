package com.ecoverse.controller;

import com.ecoverse.dto.ApiResponse;
import com.ecoverse.dto.payment.CreateOrderRequest;
import com.ecoverse.dto.payment.OrderPaymentResponse;
import com.ecoverse.dto.payment.PaymentCallbackRequest;
import com.ecoverse.dto.payment.RefundRequest;
import com.ecoverse.service.PaymentService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    @Autowired
    private PaymentService paymentService;

    /**
     * Create a new payment order (Razorpay or COD).
     * This is the ONLY endpoint the frontend should call to create orders.
     *
     * POST /api/payments/create-order
     *
     * Server calculates the amount from the user's cart — NEVER trusts client amount.
     */
    @PostMapping("/create-order")
    public ResponseEntity<ApiResponse<OrderPaymentResponse>> createOrder(
            @Valid @RequestBody CreateOrderRequest request) {
        Long userId = getCurrentUserId();
        OrderPaymentResponse response = paymentService.createOrder(userId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Retry payment for an existing PENDING_PAYMENT order.
     * Creates a new PaymentAttempt (and new Razorpay order) without creating a new order.
     *
     * POST /api/payments/retry/{orderId}
     */
    @PostMapping("/retry/{orderId}")
    public ResponseEntity<ApiResponse<OrderPaymentResponse>> retryPayment(@PathVariable Long orderId) {
        Long userId = getCurrentUserId();
        OrderPaymentResponse response = paymentService.retryPayment(userId, orderId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Razorpay payment callback (after payment completion).
     * Server-side verification is AUTHORITATIVE — we do NOT trust the frontend.
     * The order must belong to the authenticated user.
     *
     * POST /api/payments/verify
     */
    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<OrderPaymentResponse>> verifyPayment(
            @Valid @RequestBody PaymentCallbackRequest request) {
        Long userId = getCurrentUserId();
        OrderPaymentResponse response = paymentService.verifyPayment(userId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Razorpay webhook endpoint.
     *
     * IMPORTANT:
     * - NO JWT authentication (Razorpay calls this, not users)
     * - Webhook signature is verified using RAZORPAY_WEBHOOK_SECRET
     * - Processing is idempotent (duplicate events are safely acknowledged)
     * - Returns 200 quickly
     *
     * POST /api/payments/webhook
     */
    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {
        try {
            paymentService.processWebhook(payload, signature);
        } catch (Exception e) {
            log.error("Webhook processing error: {}", e.getMessage());
            // Return 200 anyway for invalid payloads — don't reveal processing details
            // Razorpay will retry on 5xx errors
        }
        return ResponseEntity.ok("OK");
    }

    /**
     * Initiate a refund for an order.
     * ADMIN only, or SELLER (own products in the order — enforced in service).
     *
     * POST /api/payments/refund
     */
    @PostMapping("/refund")
    @PreAuthorize("hasAnyRole('ADMIN', 'SELLER')")
    public ResponseEntity<ApiResponse<OrderPaymentResponse>> initiateRefund(
            @Valid @RequestBody RefundRequest request) {
        Long userId = getCurrentUserId();
        OrderPaymentResponse response = paymentService.initiateRefund(
                userId, request.getOrderId(), request.getAmount(), request.getReason());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Get Razorpay key for frontend checkout initialization.
     * Only returns the public key ID — NEVER the secret.
     *
     * GET /api/payments/key
     */
    @GetMapping("/key")
    public ResponseEntity<ApiResponse<Map<String, String>>> getRazorpayKey() {
        String key = paymentService.getRazorpayKey();
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "key", key,
                "currency", "INR"
        )));
    }

    /**
     * Extract the authenticated user ID from the SecurityContext.
     * The JwtAuthenticationFilter sets the principal as a Long (userId).
     * NEVER use hardcoded fallbacks, request parameters, or frontend-supplied IDs.
     */
    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            throw new IllegalStateException("No authenticated user found in security context");
        }
        return Long.parseLong(auth.getPrincipal().toString());
    }
}
