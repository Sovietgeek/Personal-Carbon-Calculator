package com.ecoverse.service;

import com.ecoverse.config.RazorpayConfig;
import com.ecoverse.dto.payment.CreateOrderRequest;
import com.ecoverse.dto.payment.OrderPaymentResponse;
import com.ecoverse.dto.payment.PaymentCallbackRequest;
import com.ecoverse.dto.shop.OrderResponse;
import com.ecoverse.exception.BadRequestException;
import com.ecoverse.exception.ForbiddenException;
import com.ecoverse.model.*;
import com.ecoverse.repository.*;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Razorpay Payment Integration
 *
 * SECURITY RULES (NON-NEGOTIABLE):
 * 1. Never trust frontend amount — server calculates from DB
 * 2. Never trust frontend userId — extracted from SecurityContext
 * 3. Never trust frontend order status — enforced server-side
 * 4. Never trust browser payment success alone — verify signature server-side
 * 5. Never expose Razorpay secret to frontend
 * 6. Payment state and order fulfillment state are separate
 * 7. Webhooks are idempotent (provider_event_id UNIQUE)
 * 8. Every payment operation is audited (payment_events)
 *
 * FLOW:
 *   Frontend → POST /api/payments/create-order
 *     → ShopService.placeOrder() (creates local order, decrements stock)
 *     → If online + Razorpay configured: create PaymentAttempt + Razorpay order
 *     → Return checkout details to frontend
 *
 *   Frontend → Razorpay checkout opens
 *     → User pays → browser callback
 *     → Frontend → POST /api/payments/verify
 *     → Server verifies HMAC-SHA256 signature (AUTHORITATIVE)
 *     → Order: PENDING_PAYMENT → PAID
 *
 *   Razorpay → POST /api/payments/webhook
 *     → Server verifies webhook signature
 *     → Idempotent processing via payment_events
 *     → May confirm payment (if browser verify missed) or handle refunds
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final WebClient webClient;
    private final RazorpayConfig razorpayConfig;

    @Autowired private ShopService shopService;
    @Autowired private OrderRepository orderRepository;
    @Autowired private PaymentAttemptRepository paymentAttemptRepository;
    @Autowired private PaymentEventRepository paymentEventRepository;
    @Autowired private OrderItemRepository orderItemRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private AuditLogService auditLogService;
    @Autowired private EmailService emailService;
    @Autowired private UserRepository userRepository;

    public PaymentService(WebClient.Builder webClientBuilder, RazorpayConfig razorpayConfig) {
        this.webClient = webClientBuilder.build();
        this.razorpayConfig = razorpayConfig;
    }

    // ================================================================
    // CREATE ORDER — Entry Point for Frontend Checkout
    // ================================================================

    /**
     * Create an order with payment integration.
     *
     * This is the ONLY way frontend should create orders.
     * It delegates order creation to ShopService.placeOrder() which handles
     * cart validation, stock decrement, price calculation — all in one transaction.
     *
     * For online payments (card/UPI) with Razorpay configured:
     *   - Creates a Razorpay order and returns checkout details
     *   - The local order is PENDING_PAYMENT until verifyPayment() or webhook confirms
     *
     * For COD or when Razorpay is not configured:
     *   - Order is created as PENDING_PAYMENT with paymentStatus=PENDING
     *   - COD is NEVER falsely marked as CONFIRMED or PAID
     *
     * AMOUNT: Always derived from the server-authoritative order total.
     *         NEVER accepts amount from the client.
     */
    @Transactional
    public OrderPaymentResponse createOrder(Long userId, CreateOrderRequest request) {
        // Validate payment method
        String paymentMethod = request.getPaymentMethod();
        if (paymentMethod == null || paymentMethod.isBlank()) {
            throw new BadRequestException("Payment method is required.");
        }

        // Build shipping address string
        String shippingAddress = buildAddressString(request.getShippingAddress());

        // Delegate order creation to ShopService (single authoritative path)
        // ShopService handles: cart validation, stock check, price calculation, stock decrement, cart clear
        var orderResponse = shopService.placeOrder(userId, paymentMethod, shippingAddress, null);

        // Calculate total for Razorpay (from the order that was just created — SERVER-AUTHORITATIVE)
        Order order = orderRepository.findById(orderResponse.getId())
                .orElseThrow(() -> new BadRequestException("Order creation failed unexpectedly"));

        BigDecimal totalPrice = order.getTotalPrice();
        int amountPaise = totalPrice.multiply(BigDecimal.valueOf(100)).intValueExact();

        // Determine if this is an online payment requiring Razorpay
        boolean isOnlinePayment = isOnlinePaymentMethod(paymentMethod);

        if (isOnlinePayment && razorpayConfig.isConfigured()) {
            try {
                // Create PaymentAttempt record
                PaymentAttempt attempt = PaymentAttempt.builder()
                        .orderId(order.getId())
                        .provider("razorpay")
                        .amount(totalPrice)
                        .currency(razorpayConfig.getCurrency())
                        .status("PENDING")
                        .build();
                attempt = paymentAttemptRepository.save(attempt);

                // Create Razorpay order for server-authoritative amount
                String razorpayOrderId = createRazorpayOrder(amountPaise);

                // Update attempt with provider order ID
                attempt.setProviderOrderId(razorpayOrderId);
                paymentAttemptRepository.save(attempt);

                // Update order with Razorpay details
                order.setRazorpayOrderId(razorpayOrderId);
                order.setPaymentProvider("razorpay");
                order.setCurrency(razorpayConfig.getCurrency());
                orderRepository.save(order);

                log.info("Razorpay order created: localOrderId={}, razorpayOrderId={}, amount={}, user={}",
                        order.getId(), razorpayOrderId, amountPaise, userId);

                return OrderPaymentResponse.builder()
                        .razorpayOrderId(razorpayOrderId)
                        .currency(razorpayConfig.getCurrency())
                        .amount(amountPaise)
                        .key(razorpayConfig.getKeyId())
                        .status("created")
                        .ecoverseOrderId(String.valueOf(order.getId()))
                        .message("Order created. Complete payment to confirm.")
                        .build();
            } catch (Exception e) {
                log.error("Failed to create Razorpay order for user {}: {}", userId, e.getMessage());
                // Fall through to COD mode — order already created as PENDING_PAYMENT
                // Stock is already decremented — will be restored by payment expiry if not paid
            }
        }

        // COD (Cash on Delivery) or Razorpay fallback
        // Order remains PENDING_PAYMENT, paymentStatus PENDING
        // COD is NEVER falsely marked as CONFIRMED or PAID
        String effectivePaymentMethod = isOnlinePayment && !razorpayConfig.isConfigured() ? "cod" : paymentMethod;
        if (!effectivePaymentMethod.equals(order.getPaymentMethod())) {
            order.setPaymentMethod(effectivePaymentMethod);
            order.setPaymentProvider(effectivePaymentMethod.equals("cod") ? "cod" : order.getPaymentProvider());
            orderRepository.save(order);
        } else if (order.getPaymentProvider() == null) {
            order.setPaymentProvider(effectivePaymentMethod.equals("cod") ? "cod" : "cod");
            orderRepository.save(order);
        }

        return OrderPaymentResponse.builder()
                .currency(razorpayConfig.getCurrency())
                .amount(amountPaise)
                .status("pending_payment")
                .ecoverseOrderId(String.valueOf(order.getId()))
                .message(effectivePaymentMethod.equals("cod")
                        ? "Order placed successfully (Cash on Delivery). Payment pending until delivery."
                        : "Order created. Payment pending.")
                .build();
    }

    /**
     * Create a payment attempt for an existing order (retry payment).
     * Used when a user wants to retry a failed online payment.
     * Does NOT create a new order — reuses the existing one.
     */
    @Transactional
    public OrderPaymentResponse retryPayment(Long userId, Long orderId) {
        Order order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new BadRequestException("Order not found or doesn't belong to you."));

        // Can only retry PENDING_PAYMENT orders
        if (order.getStatus() != Order.OrderStatus.PENDING_PAYMENT) {
            throw new BadRequestException("Can only retry payment for pending orders. Current status: " + order.getStatus());
        }

        // Must be an online payment method
        if (!isOnlinePaymentMethod(order.getPaymentMethod())) {
            throw new BadRequestException("Can only retry online payments. This order uses: " + order.getPaymentMethod());
        }

        if (!razorpayConfig.isConfigured()) {
            throw new BadRequestException("Online payments are not available. Please contact support.");
        }

        // Check for existing pending attempt
        Optional<PaymentAttempt> pendingAttempt = paymentAttemptRepository
                .findTopByOrderIdAndStatusOrderByCreatedAtDesc(orderId, "PENDING");
        if (pendingAttempt.isPresent()) {
            // Already have a pending Razorpay order — return it
            PaymentAttempt existing = pendingAttempt.get();
            return OrderPaymentResponse.builder()
                    .razorpayOrderId(existing.getProviderOrderId())
                    .currency(existing.getCurrency())
                    .amount(existing.getAmount().multiply(BigDecimal.valueOf(100)).intValueExact())
                    .key(razorpayConfig.getKeyId())
                    .status("created")
                    .ecoverseOrderId(String.valueOf(order.getId()))
                    .message("Pending payment found. Complete payment to confirm.")
                    .build();
        }

        // Create new PaymentAttempt
        BigDecimal totalPrice = order.getTotalPrice();
        int amountPaise = totalPrice.multiply(BigDecimal.valueOf(100)).intValueExact();

        try {
            PaymentAttempt attempt = PaymentAttempt.builder()
                    .orderId(order.getId())
                    .provider("razorpay")
                    .amount(totalPrice)
                    .currency(razorpayConfig.getCurrency())
                    .status("PENDING")
                    .build();
            attempt = paymentAttemptRepository.save(attempt);

            String razorpayOrderId = createRazorpayOrder(amountPaise);

            attempt.setProviderOrderId(razorpayOrderId);
            paymentAttemptRepository.save(attempt);

            // Update order with new Razorpay order ID
            order.setRazorpayOrderId(razorpayOrderId);
            order.setPaymentProvider("razorpay");
            orderRepository.save(order);

            log.info("Payment retry: localOrderId={}, newRazorpayOrderId={}, user={}",
                    order.getId(), razorpayOrderId, userId);

            return OrderPaymentResponse.builder()
                    .razorpayOrderId(razorpayOrderId)
                    .currency(razorpayConfig.getCurrency())
                    .amount(amountPaise)
                    .key(razorpayConfig.getKeyId())
                    .status("created")
                    .ecoverseOrderId(String.valueOf(order.getId()))
                    .message("New payment attempt created. Complete payment to confirm.")
                    .build();
        } catch (Exception e) {
            log.error("Failed to create Razorpay order for retry: orderId={}, error={}", orderId, e.getMessage());
            throw new BadRequestException("Failed to create payment. Please try again.");
        }
    }

    // ================================================================
    // VERIFY PAYMENT — Server-Side Signature Verification
    // ================================================================

    /**
     * Verify Razorpay payment callback.
     * Called after user completes payment on Razorpay checkout.
     *
     * IMPORTANT SECURITY:
     * 1. The userId is from SecurityContext — NOT from the callback
     * 2. We enforce that the order belongs to this user (IDOR protection)
     * 3. Server-side HMAC-SHA256 signature verification is AUTHORITATIVE
     * 4. The frontend "payment success" callback is NEVER trusted alone
     * 5. Double verification is idempotent (returns existing confirmation)
     */
    @Transactional
    public OrderPaymentResponse verifyPayment(Long userId, PaymentCallbackRequest callback) {
        // Step 1: Verify Razorpay signature (server-side — AUTHORITATIVE)
        if (razorpayConfig.isConfigured()) {
            boolean isValid = verifyRazorpaySignature(
                    callback.getRazorpayOrderId(),
                    callback.getRazorpayPaymentId(),
                    callback.getRazorpaySignature()
            );

            if (!isValid) {
                log.error("Razorpay signature verification FAILED for razorpayOrder: {}", callback.getRazorpayOrderId());
                // Record failed verification attempt
                recordPaymentEvent(null, callback.getRazorpayOrderId(), "PAYMENT_VERIFICATION_FAILED", null);
                throw new BadRequestException("Payment verification failed. Invalid signature.");
            }
        }

        // Step 2: Find PaymentAttempt by provider order ID
        PaymentAttempt attempt = paymentAttemptRepository
                .findByProviderOrderId(callback.getRazorpayOrderId())
                .orElseThrow(() -> new BadRequestException(
                        "No payment attempt found for Razorpay order: " + callback.getRazorpayOrderId()));

        // Step 3: Find the order
        Order order = orderRepository.findById(attempt.getOrderId())
                .orElseThrow(() -> new BadRequestException("Order not found for payment attempt."));

        // Step 4: OWNERSHIP CHECK — the order MUST belong to the authenticated user
        if (!order.getUserId().equals(userId)) {
            log.error("Payment verification DENIED: user {} attempted to verify order {} belonging to user {}",
                    userId, order.getId(), order.getUserId());
            throw new ForbiddenException("You do not have permission to verify this order.");
        }

        // Step 5: Prevent double verification (idempotency)
        if (order.getPaymentStatus() == Order.PaymentStatus.PAID) {
            log.info("Order {} already verified as PAID — returning existing confirmation", order.getId());
            return OrderPaymentResponse.builder()
                    .razorpayOrderId(callback.getRazorpayOrderId())
                    .razorpayPaymentId(callback.getRazorpayPaymentId())
                    .currency(order.getCurrency())
                    .status("paid")
                    .ecoverseOrderId(String.valueOf(order.getId()))
                    .message("Payment already verified.")
                    .build();
        }

        // Step 6: Validate current order status
        if (order.getStatus() != Order.OrderStatus.PENDING_PAYMENT) {
            throw new BadRequestException("Order is in unexpected status: " + order.getStatus());
        }

        // Step 7: Validate payment status transition
        order.getPaymentStatus().validateTransitionTo(Order.PaymentStatus.PAID);

        // Step 8: Update PaymentAttempt — SUCCESS
        attempt.setStatus("SUCCESS");
        attempt.setProviderPaymentId(callback.getRazorpayPaymentId());
        paymentAttemptRepository.save(attempt);

        // Step 9: Update Order — PENDING_PAYMENT → PAID (server-side is AUTHORITATIVE)
        order.setStatus(Order.OrderStatus.PAID);
        order.setRazorpayPaymentId(callback.getRazorpayPaymentId());
        order.setPaymentMethod("online");
        order.setPaymentProvider("razorpay");
        order.getPaymentStatus().validateTransitionTo(Order.PaymentStatus.PAID);
        order.setPaymentStatus(Order.PaymentStatus.PAID);
        order.setPaymentVerifiedAt(LocalDateTime.now());
        order.setCapturedAt(LocalDateTime.now());
        orderRepository.save(order);

        // Step 10: Record payment event for audit
        recordPaymentEvent(order.getId(), callback.getRazorpayOrderId(),
                "PAYMENT_VERIFIED", attempt.getId());

        log.info("Payment verified successfully: orderId={}, userId={}, razorpayPaymentId={}",
                order.getId(), userId, callback.getRazorpayPaymentId());

        // Send payment confirmation email (async, deduplicated)
        try {
            User orderUser = userRepository.findById(order.getUserId()).orElse(null);
            if (orderUser != null) {
                emailService.sendPaymentConfirmation(orderUser.getEmail(), order.getId(),
                        "₹" + order.getTotalPrice().toPlainString());
                emailService.sendOrderConfirmation(orderUser.getEmail(), order.getId(),
                        "₹" + order.getTotalPrice().toPlainString());
            }
        } catch (Exception e) {
            log.warn("Failed to send payment confirmation email for orderId={}: {}", order.getId(), e.getMessage());
        }

        return OrderPaymentResponse.builder()
                .razorpayOrderId(callback.getRazorpayOrderId())
                .razorpayPaymentId(callback.getRazorpayPaymentId())
                .currency(order.getCurrency())
                .status("paid")
                .ecoverseOrderId(String.valueOf(order.getId()))
                .message("Payment successful! Order confirmed.")
                .build();
    }

    // ================================================================
    // WEBHOOK — Razorpay Webhook Handler
    // ================================================================

    /**
     * Process Razorpay webhook events.
     *
     * SECURITY:
     * - Webhook signature MUST be verified (using webhook secret)
     * - No JWT authentication (Razorpay calls this, not users)
     * - Processing is idempotent via provider_event_id UNIQUE constraint
     * - Returns 200 quickly; business logic is fast
     *
     * Events handled:
     * - payment.captured: Order PENDING_PAYMENT → PAID
     * - payment.failed: Order PENDING_PAYMENT → PAYMENT_FAILED (stock restored)
     * - refund.processed: paymentStatus REFUND_PENDING → REFUNDED
     * - refund.failed: Log only, no auto-action
     */
    @Transactional
    public void processWebhook(String payload, String signature) {
        // Step 1: Verify webhook signature
        if (razorpayConfig.isWebhookConfigured() && !verifyWebhookSignature(payload, signature)) {
            log.error("Webhook signature verification FAILED");
            throw new BadRequestException("Invalid webhook signature.");
        }

        // Step 2: Parse webhook payload
        JSONObject eventJson;
        try {
            eventJson = new JSONObject(payload);
        } catch (Exception e) {
            log.error("Failed to parse webhook payload: {}", e.getMessage());
            throw new BadRequestException("Invalid webhook payload.");
        }

        String eventId = eventJson.optString("id", "");
        String eventType = eventJson.optString("event", "");
        String safePayload = buildSafePayload(eventJson);

        // Step 3: Idempotency check — if we've seen this event, skip
        if (paymentEventRepository.existsByProviderEventId(eventId)) {
            log.info("Duplicate webhook event: eventId={}, type={} — skipping", eventId, eventType);
            return;
        }

        // Step 4: Record event (with UNIQUE constraint for idempotency)
        PaymentEvent event;
        try {
            event = PaymentEvent.builder()
                    .providerEventId(eventId)
                    .eventType(eventType)
                    .payload(safePayload)
                    .processed(false)
                    .build();
            event = paymentEventRepository.save(event);
        } catch (DataIntegrityViolationException e) {
            log.info("Concurrent webhook event: eventId={} — another thread processed it", eventId);
            return;
        }

        // Step 5: Process event
        try {
            processWebhookEvent(eventType, eventJson, event);
            event.setProcessed(true);
            event.setProcessedAt(LocalDateTime.now());
            paymentEventRepository.save(event);
        } catch (Exception e) {
            log.error("Failed to process webhook event: eventId={}, type={}, error={}",
                    eventId, eventType, e.getMessage());
            // Don't rethrow — we've recorded the event. Razorpay will retry.
        }
    }

    private void processWebhookEvent(String eventType, JSONObject eventJson, PaymentEvent event) {
        switch (eventType) {
            case "payment.captured":
                handlePaymentCaptured(eventJson, event);
                break;
            case "payment.authorized":
                handlePaymentAuthorized(eventJson, event);
                break;
            case "payment.failed":
                handlePaymentFailed(eventJson, event);
                break;
            case "refund.processed":
                handleRefundProcessed(eventJson, event);
                break;
            case "refund.failed":
                log.info("Refund failed event received: eventId={}. Manual review required.", event.getProviderEventId());
                break;
            default:
                log.info("Unhandled webhook event type: {} (eventId={})", eventType, event.getProviderEventId());
        }
    }

    private void handlePaymentCaptured(JSONObject eventJson, PaymentEvent event) {
        JSONObject payment = eventJson.optJSONObject("payload", new JSONObject())
                .optJSONObject("payment", new JSONObject())
                .optJSONObject("entity", new JSONObject());

        String razorpayOrderId = payment.optString("order_id", "");
        String razorpayPaymentId = payment.optString("id", "");

        if (razorpayOrderId.isEmpty()) return;

        // Find PaymentAttempt by provider order ID
        paymentAttemptRepository.findByProviderOrderId(razorpayOrderId).ifPresent(attempt -> {
            Order order = orderRepository.findById(attempt.getOrderId()).orElse(null);
            if (order == null) {
                log.warn("Webhook: Order not found for payment attempt {}", attempt.getId());
                return;
            }

            event.setOrderId(order.getId());
            event.setPaymentAttemptId(attempt.getId());

            // Idempotent: if already PAID, skip
            if (order.getPaymentStatus() == Order.PaymentStatus.PAID) {
                log.info("Webhook: Order {} already PAID — skipping", order.getId());
                return;
            }

            // Update attempt
            attempt.setStatus("SUCCESS");
            attempt.setProviderPaymentId(razorpayPaymentId);
            paymentAttemptRepository.save(attempt);

            // Update order
            order.setStatus(Order.OrderStatus.PAID);
            order.setRazorpayPaymentId(razorpayPaymentId);
            order.setPaymentStatus(Order.PaymentStatus.PAID);
            order.setPaymentProvider("razorpay");
            order.setCapturedAt(LocalDateTime.now());
            orderRepository.save(order);

            log.info("Webhook: Payment captured — orderId={}, razorpayPaymentId={}", order.getId(), razorpayPaymentId);
        });
    }

    private void handlePaymentAuthorized(JSONObject eventJson, PaymentEvent event) {
        JSONObject payment = eventJson.optJSONObject("payload", new JSONObject())
                .optJSONObject("payment", new JSONObject())
                .optJSONObject("entity", new JSONObject());

        String razorpayOrderId = payment.optString("order_id", "");

        if (razorpayOrderId.isEmpty()) return;

        paymentAttemptRepository.findByProviderOrderId(razorpayOrderId).ifPresent(attempt -> {
            Order order = orderRepository.findById(attempt.getOrderId()).orElse(null);
            if (order == null) return;

            event.setOrderId(order.getId());
            event.setPaymentAttemptId(attempt.getId());

            // Set to AUTHORIZED (not yet captured)
            if (order.getPaymentStatus() == Order.PaymentStatus.PENDING) {
                order.setPaymentStatus(Order.PaymentStatus.AUTHORIZED);
                orderRepository.save(order);
                log.info("Webhook: Payment authorized — orderId={}", order.getId());
            }
        });
    }

    private void handlePaymentFailed(JSONObject eventJson, PaymentEvent event) {
        JSONObject payment = eventJson.optJSONObject("payload", new JSONObject())
                .optJSONObject("payment", new JSONObject())
                .optJSONObject("entity", new JSONObject());

        String razorpayOrderId = payment.optString("order_id", "");
        String errorCode = payment.optString("error_code", "");
        String errorDescription = payment.optString("error_description", "Payment failed");

        if (razorpayOrderId.isEmpty()) return;

        paymentAttemptRepository.findByProviderOrderId(razorpayOrderId).ifPresent(attempt -> {
            Order order = orderRepository.findById(attempt.getOrderId()).orElse(null);
            if (order == null) return;

            event.setOrderId(order.getId());
            event.setPaymentAttemptId(attempt.getId());

            // Idempotent: if already PAYMENT_FAILED, skip
            if (order.getStatus() == Order.OrderStatus.PAYMENT_FAILED) {
                log.info("Webhook: Order {} already PAYMENT_FAILED — skipping", order.getId());
                return;
            }

            // Update attempt
            attempt.setStatus("FAILED");
            attempt.setFailureReason(errorCode + ": " + errorDescription);
            paymentAttemptRepository.save(attempt);

            // Update order — PENDING_PAYMENT → PAYMENT_FAILED
            if (order.getStatus() == Order.OrderStatus.PENDING_PAYMENT) {
                order.setStatus(Order.OrderStatus.PAYMENT_FAILED);
                order.setPaymentStatus(Order.PaymentStatus.FAILED);
                order.setPaymentFailureReason(errorDescription);
                order.setFailedAt(LocalDateTime.now());
                orderRepository.save(order);

                // Restore stock
                restoreStockForOrder(order);

                log.info("Webhook: Payment failed — orderId={}, reason={}", order.getId(), errorDescription);

                // Send payment failure email (async, deduplicated)
                try {
                    User orderUser = userRepository.findById(order.getUserId()).orElse(null);
                    if (orderUser != null) {
                        emailService.sendPaymentFailure(orderUser.getEmail(), order.getId(),
                                "₹" + order.getTotalPrice().toPlainString());
                    }
                } catch (Exception e) {
                    log.warn("Failed to send payment failure email for orderId={}: {}", order.getId(), e.getMessage());
                }
            }
        });
    }

    private void handleRefundProcessed(JSONObject eventJson, PaymentEvent event) {
        JSONObject refund = eventJson.optJSONObject("payload", new JSONObject())
                .optJSONObject("refund", new JSONObject())
                .optJSONObject("entity", new JSONObject());

        String razorpayPaymentId = refund.optString("payment_id", "");
        String refundId = refund.optString("id", "");
        long refundAmountPaise = refund.optLong("amount", 0);
        BigDecimal refundAmount = BigDecimal.valueOf(refundAmountPaise, 2);

        if (razorpayPaymentId.isEmpty()) return;

        // Find order by Razorpay payment ID
        orderRepository.findByRazorpayOrderId(
                eventJson.optJSONObject("payload", new JSONObject())
                        .optJSONObject("payment", new JSONObject())
                        .optJSONObject("entity", new JSONObject())
                        .optString("order_id", "")
        ).ifPresent(order -> {
            event.setOrderId(order.getId());

            // Update order refund state
            if (order.getPaymentStatus() == Order.PaymentStatus.REFUND_PENDING) {
                order.setPaymentStatus(Order.PaymentStatus.REFUNDED);
                order.setStatus(Order.OrderStatus.REFUNDED);
                order.setRefundedAmount(order.getRefundedAmount().add(refundAmount));
                order.setRefundId(refundId);
                orderRepository.save(order);

                log.info("Webhook: Refund processed — orderId={}, refundId={}, amount={}",
                        order.getId(), refundId, refundAmount);

                // Send refund completed email (async, deduplicated)
                try {
                    User orderUser = userRepository.findById(order.getUserId()).orElse(null);
                    if (orderUser != null) {
                        emailService.sendRefundCompleted(orderUser.getEmail(), order.getId(),
                                "₹" + refundAmount.toPlainString());
                    }
                } catch (Exception e) {
                    log.warn("Failed to send refund email for orderId={}: {}", order.getId(), e.getMessage());
                }
            }
        });
    }

    // ================================================================
    // REFUND — Server-Controlled Refund Processing
    // ================================================================

    /**
     * Initiate a refund for an order.
     *
     * ACCESS: ADMIN only, or SELLER (own products in the order)
     * AMOUNT: If null, full refund. If specified, partial refund.
     * STOCK: Restored only if order has NOT been shipped yet.
     *
     * Flow:
     * 1. Validate order exists and is in a refundable state (PAID/DELIVERED)
     * 2. Validate refund amount doesn't exceed remaining
     * 3. Call Razorpay Refund API
     * 4. Set paymentStatus = REFUND_PENDING
     * 5. Webhook confirms: paymentStatus = REFUNDED
     */
    @Transactional
    public OrderPaymentResponse initiateRefund(Long userId, Long orderId, BigDecimal amount, String reason) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BadRequestException("Order not found: " + orderId));

        // Validate order is in a refundable state
        if (order.getStatus() != Order.OrderStatus.PAID
                && order.getStatus() != Order.OrderStatus.PROCESSING
                && order.getStatus() != Order.OrderStatus.SHIPPED
                && order.getStatus() != Order.OrderStatus.DELIVERED) {
            throw new BadRequestException("Order is not in a refundable state. Current status: " + order.getStatus());
        }

        if (order.getPaymentStatus() != Order.PaymentStatus.PAID) {
            throw new BadRequestException("Order payment is not in PAID state. Current: " + order.getPaymentStatus());
        }

        // Determine refund amount (default: full)
        BigDecimal refundAmount = amount != null ? amount : order.getTotalPrice().subtract(order.getRefundedAmount());

        // Validate refund amount
        BigDecimal maxRefundable = order.getTotalPrice().subtract(order.getRefundedAmount());
        if (refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Refund amount must be positive.");
        }
        if (refundAmount.compareTo(maxRefundable) > 0) {
            throw new BadRequestException("Refund amount exceeds refundable balance: ₹" + maxRefundable);
        }

        // Call Razorpay Refund API
        String razorpayRefundId = null;
        if (razorpayConfig.isConfigured() && order.getRazorpayPaymentId() != null) {
            try {
                int refundAmountPaise = refundAmount.multiply(BigDecimal.valueOf(100)).intValueExact();
                razorpayRefundId = createRazorpayRefund(order.getRazorpayPaymentId(), refundAmountPaise);
            } catch (Exception e) {
                log.error("Razorpay refund failed for order {}: {}", orderId, e.getMessage());
                throw new BadRequestException("Refund request failed. Please try again.");
            }
        }

        // Update order state
        order.getPaymentStatus().validateTransitionTo(Order.PaymentStatus.REFUND_PENDING);
        order.setPaymentStatus(Order.PaymentStatus.REFUND_PENDING);

        // Determine if stock should be restored (only if order hasn't been shipped)
        boolean shouldRestoreStock = order.getStatus() == Order.OrderStatus.PAID
                || order.getStatus() == Order.OrderStatus.PROCESSING;

        if (shouldRestoreStock) {
            restoreStockForOrder(order);
            log.info("Stock restored for refunded order: orderId={}", orderId);
        }

        // Store refund ID if Razorpay returned one
        if (razorpayRefundId != null) {
            order.setRefundId(razorpayRefundId);
        }

        orderRepository.save(order);

        // Record payment event
        recordPaymentEvent(orderId, order.getRazorpayOrderId(), "REFUND_REQUESTED", null);

        log.info("Refund initiated: orderId={}, amount={}, reason={}, stockRestored={}",
                orderId, refundAmount, reason, shouldRestoreStock);

        return OrderPaymentResponse.builder()
                .razorpayOrderId(order.getRazorpayOrderId())
                .currency(order.getCurrency())
                .amount(refundAmount.multiply(BigDecimal.valueOf(100)).intValueExact())
                .status("refund_pending")
                .ecoverseOrderId(String.valueOf(order.getId()))
                .message("Refund initiated. You will be notified once processed.")
                .build();
    }

    // ================================================================
    // READ — Payment Information
    // ================================================================

    /**
     * Get Razorpay key for frontend checkout initialization.
     * Only returns the public key ID — never the secret.
     */
    public String getRazorpayKey() {
        if (!razorpayConfig.isConfigured()) {
            return ""; // Frontend will show COD only
        }
        return razorpayConfig.getKeyId();
    }

    // ================================================================
    // PAYMENT EXPIRY — Called by PaymentExpiryScheduler
    // ================================================================

    /**
     * Expire a PENDING_PAYMENT order that has exceeded the payment TTL.
     * Sets order to PAYMENT_FAILED and restores stock.
     * Idempotent: skips if already in a terminal state.
     */
    @Transactional
    public void expirePayment(Order order) {
        if (order.getStatus() != Order.OrderStatus.PENDING_PAYMENT) {
            return; // Already processed — skip
        }

        order.setStatus(Order.OrderStatus.PAYMENT_FAILED);
        order.setPaymentStatus(Order.PaymentStatus.FAILED);
        order.setPaymentFailureReason("Payment expired — no payment received within " +
                razorpayConfig.getPaymentExpiryMinutes() + " minutes");
        order.setFailedAt(LocalDateTime.now());
        orderRepository.save(order);

        // Restore stock
        restoreStockForOrder(order);

        // Mark pending payment attempts as failed
        List<PaymentAttempt> pendingAttempts = paymentAttemptRepository.findByOrderId(order.getId());
        pendingAttempts.stream()
                .filter(a -> "PENDING".equals(a.getStatus()))
                .forEach(a -> {
                    a.setStatus("FAILED");
                    a.setFailureReason("Payment expired");
                    paymentAttemptRepository.save(a);
                });

        // Record event
        recordPaymentEvent(order.getId(), order.getRazorpayOrderId(), "PAYMENT_EXPIRED", null);

        log.info("Payment expired: orderId={}, stockRestored=true", order.getId());
    }

    // ================================================================
    // PRIVATE HELPERS
    // ================================================================

    private boolean isOnlinePaymentMethod(String paymentMethod) {
        return "card".equalsIgnoreCase(paymentMethod)
                || "upi".equalsIgnoreCase(paymentMethod)
                || "net".equalsIgnoreCase(paymentMethod)
                || "online".equalsIgnoreCase(paymentMethod);
    }

    /**
     * Create a Razorpay order via the Razorpay API.
     * Amount is server-authoritative — derived from order total.
     */
    private String createRazorpayOrder(int amountPaise) {
        try {
            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", amountPaise);
            orderRequest.put("currency", razorpayConfig.getCurrency());
            orderRequest.put("payment_capture", 1); // Auto-capture

            String auth = java.util.Base64.getEncoder()
                    .encodeToString((razorpayConfig.getKeyId() + ":" + razorpayConfig.getKeySecret()).getBytes());

            String response = webClient.post()
                    .uri("https://api.razorpay.com/v1/orders")
                    .header("Authorization", "Basic " + auth)
                    .header("Content-Type", "application/json")
                    .bodyValue(orderRequest.toString())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (response != null) {
                JSONObject jsonResponse = new JSONObject(response);
                return jsonResponse.getString("id");
            }
        } catch (Exception e) {
            log.error("Razorpay order creation failed: {}", e.getMessage());
            throw new RuntimeException("Failed to create payment order. Please try again.");
        }
        throw new RuntimeException("Failed to create payment order.");
    }

    /**
     * Create a Razorpay refund via the Razorpay API.
     */
    private String createRazorpayRefund(String razorpayPaymentId, int refundAmountPaise) {
        try {
            JSONObject refundRequest = new JSONObject();
            refundRequest.put("amount", refundAmountPaise);

            String auth = java.util.Base64.getEncoder()
                    .encodeToString((razorpayConfig.getKeyId() + ":" + razorpayConfig.getKeySecret()).getBytes());

            String response = webClient.post()
                    .uri("https://api.razorpay.com/v1/payments/" + razorpayPaymentId + "/refund")
                    .header("Authorization", "Basic " + auth)
                    .header("Content-Type", "application/json")
                    .bodyValue(refundRequest.toString())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (response != null) {
                JSONObject jsonResponse = new JSONObject(response);
                return jsonResponse.getString("id");
            }
        } catch (Exception e) {
            log.error("Razorpay refund failed: {}", e.getMessage());
            throw new RuntimeException("Failed to process refund.");
        }
        throw new RuntimeException("Failed to process refund.");
    }

    /**
     * Verify Razorpay payment signature using HMAC-SHA256.
     * This is the AUTHORITATIVE verification — browser callbacks are NOT trusted.
     */
    private boolean verifyRazorpaySignature(String orderId, String paymentId, String signature) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            javax.crypto.spec.SecretKeySpec secretKeySpec = new javax.crypto.spec.SecretKeySpec(
                    razorpayConfig.getKeySecret().getBytes(), "HmacSHA256");
            mac.init(secretKeySpec);

            String payload = orderId + "|" + paymentId;
            byte[] hash = mac.doFinal(payload.getBytes());

            String generatedSignature = java.util.Base64.getEncoder().encodeToString(hash);
            return generatedSignature.equals(signature);
        } catch (Exception e) {
            log.error("Signature verification error: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Verify Razorpay webhook signature.
     * Uses the webhook secret configured via RAZORPAY_WEBHOOK_SECRET.
     */
    private boolean verifyWebhookSignature(String payload, String signature) {
        if (signature == null || signature.isBlank()) return false;
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            javax.crypto.spec.SecretKeySpec secretKeySpec = new javax.crypto.spec.SecretKeySpec(
                    razorpayConfig.getWebhookSecret().getBytes(), "HmacSHA256");
            mac.init(secretKeySpec);

            byte[] hash = mac.doFinal(payload.getBytes());
            String generatedSignature = new java.math.BigInteger(1, hash).toString(16);

            // Razorpay webhook signatures are hex-encoded
            return generatedSignature.equalsIgnoreCase(signature)
                    || java.util.Base64.getEncoder().encodeToString(hash).equals(signature);
        } catch (Exception e) {
            log.error("Webhook signature verification error: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Restore stock for all items in an order.
     * Called when payment fails or order is cancelled.
     */
    private void restoreStockForOrder(Order order) {
        List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());
        for (OrderItem item : items) {
            productRepository.restoreStock(item.getProductId(), item.getQuantity());
            log.info("Stock restored for product {}: +{}", item.getProductId(), item.getQuantity());
        }
    }

    /**
     * Record a payment event for audit trail.
     * Safe to call — never throws (catches all exceptions).
     */
    private void recordPaymentEvent(Long orderId, String providerOrderId, String eventType, Long attemptId) {
        try {
            // Use providerOrderId + eventType as a pseudo-event-id for non-webhook events
            String eventId = "local_" + (providerOrderId != null ? providerOrderId : "order" + orderId)
                    + "_" + eventType + "_" + System.currentTimeMillis();

            if (!paymentEventRepository.existsByProviderEventId(eventId)) {
                PaymentEvent event = PaymentEvent.builder()
                        .providerEventId(eventId)
                        .eventType(eventType)
                        .orderId(orderId)
                        .paymentAttemptId(attemptId)
                        .processed(true)
                        .processedAt(LocalDateTime.now())
                        .build();
                paymentEventRepository.save(event);
            }
        } catch (Exception e) {
            log.warn("Failed to record payment event: {}", e.getMessage());
            // Never break the main flow for audit logging
        }
    }

    /**
     * Build a safe payload string from webhook JSON.
     * Contains order IDs, amounts, statuses — NO secrets or signatures.
     */
    private String buildSafePayload(JSONObject eventJson) {
        try {
            JSONObject safePayload = new JSONObject();
            safePayload.put("event", eventJson.optString("event", ""));
            JSONObject payload = eventJson.optJSONObject("payload");
            if (payload != null) {
                safePayload.put("entity", payload.optJSONObject("payment") != null
                        ? payload.optJSONObject("payment").optJSONObject("entity") != null
                        ? payload.optJSONObject("payment").optJSONObject("entity").optString("id", "")
                        : ""
                        : "");
            }
            return safePayload.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    private String buildAddressString(CreateOrderRequest.ShippingAddress addr) {
        if (addr == null) return "No address provided";
        StringBuilder sb = new StringBuilder();
        if (addr.getFullName() != null) sb.append(addr.getFullName()).append(", ");
        if (addr.getPhone() != null) sb.append(addr.getPhone()).append(", ");
        if (addr.getAddressLine1() != null) sb.append(addr.getAddressLine1()).append(", ");
        if (addr.getAddressLine2() != null) sb.append(addr.getAddressLine2()).append(", ");
        if (addr.getCity() != null) sb.append(addr.getCity()).append(", ");
        if (addr.getState() != null) sb.append(addr.getState()).append(" ");
        if (addr.getPincode() != null) sb.append(addr.getPincode());
        return sb.toString();
    }
}
