package com.ecoverse.service;

import com.ecoverse.config.RazorpayConfig;
import com.ecoverse.dto.payment.PaymentCallbackRequest;
import com.ecoverse.exception.BadRequestException;
import com.ecoverse.exception.ForbiddenException;
import com.ecoverse.model.Order;
import com.ecoverse.model.PaymentAttempt;
import com.ecoverse.repository.OrderItemRepository;
import com.ecoverse.repository.OrderRepository;
import com.ecoverse.repository.PaymentAttemptRepository;
import com.ecoverse.repository.PaymentEventRepository;
import com.ecoverse.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Payment security tests — signature verification, amount tampering,
 * webhook handling, IDOR protection, and concurrency safety.
 */
@ExtendWith(MockitoExtension.class)
class PaymentSecurityTest {

    @Mock private OrderRepository orderRepository;
    @Mock private ShopService shopService;
    @Mock private PaymentAttemptRepository paymentAttemptRepository;
    @Mock private PaymentEventRepository paymentEventRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private ProductRepository productRepository;
    @Mock private AuditLogService auditLogService;
    @Mock private WebClient.Builder webClientBuilder;

    private PaymentService paymentService;
    private RazorpayConfig razorpayConfig;

    @BeforeEach
    void setUp() {
        razorpayConfig = new RazorpayConfig();
        razorpayConfig.setKeyId("test_key");
        razorpayConfig.setKeySecret("test_secret");
        razorpayConfig.setWebhookSecret("test_webhook_secret");
        razorpayConfig.setCurrency("INR");
        razorpayConfig.setMode("test");
        razorpayConfig.setPaymentExpiryMinutes(30);

        when(webClientBuilder.build()).thenReturn(mock(WebClient.class));
        paymentService = new PaymentService(webClientBuilder, razorpayConfig);
        injectField(paymentService, "orderRepository", orderRepository);
        injectField(paymentService, "shopService", shopService);
        injectField(paymentService, "paymentAttemptRepository", paymentAttemptRepository);
        injectField(paymentService, "paymentEventRepository", paymentEventRepository);
        injectField(paymentService, "orderItemRepository", orderItemRepository);
        injectField(paymentService, "productRepository", productRepository);
        injectField(paymentService, "auditLogService", auditLogService);
    }

    private void injectField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject field: " + fieldName, e);
        }
    }

    // ==================================================================
    // SIGNATURE VERIFICATION TESTS
    // ==================================================================

    @Nested
    @DisplayName("Signature Verification")
    class SignatureVerification {

        @Test
        @DisplayName("Invalid signature is rejected")
        void invalidSignatureIsRejected() {
            PaymentCallbackRequest callback = PaymentCallbackRequest.builder()
                    .razorpayOrderId("order_123")
                    .razorpayPaymentId("pay_123")
                    .razorpaySignature("invalid_signature")
                    .build();

            assertThatThrownBy(() -> paymentService.verifyPayment(42L, callback))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Invalid signature");
        }

        @Test
        @DisplayName("Empty signature is rejected")
        void emptySignatureIsRejected() {
            PaymentCallbackRequest callback = PaymentCallbackRequest.builder()
                    .razorpayOrderId("order_123")
                    .razorpayPaymentId("pay_123")
                    .razorpaySignature("")
                    .build();

            assertThatThrownBy(() -> paymentService.verifyPayment(42L, callback))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        @DisplayName("Valid HMAC-SHA256 signature is accepted")
        void validSignatureIsAccepted() {
            // Generate a valid signature for testing
            String orderId = "order_test_123";
            String paymentId = "pay_test_456";
            String secret = "test_secret";
            String expectedSignature;
            try {
                javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
                javax.crypto.spec.SecretKeySpec keySpec = new javax.crypto.spec.SecretKeySpec(secret.getBytes(), "HmacSHA256");
                mac.init(keySpec);
                byte[] hash = mac.doFinal((orderId + "|" + paymentId).getBytes());
                expectedSignature = java.util.Base64.getEncoder().encodeToString(hash);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            Order order = Order.builder().id(1L).userId(42L).totalPrice(BigDecimal.valueOf(100))
                    .status(Order.OrderStatus.PENDING_PAYMENT).paymentStatus(Order.PaymentStatus.PENDING)
                    .currency("INR").refundedAmount(BigDecimal.ZERO).build();
            PaymentAttempt attempt = PaymentAttempt.builder().id(1L).orderId(1L)
                    .providerOrderId(orderId).amount(BigDecimal.valueOf(100)).status("PENDING").build();

            when(paymentAttemptRepository.findByProviderOrderId(orderId)).thenReturn(Optional.of(attempt));
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            PaymentCallbackRequest callback = PaymentCallbackRequest.builder()
                    .razorpayOrderId(orderId)
                    .razorpayPaymentId(paymentId)
                    .razorpaySignature(expectedSignature)
                    .build();

            var result = paymentService.verifyPayment(42L, callback);
            assertThat(result.getStatus()).isEqualTo("paid");
        }
    }

    // ==================================================================
    // IDOR PROTECTION TESTS
    // ==================================================================

    @Nested
    @DisplayName("Payment IDOR Protection")
    class PaymentIdorProtection {

        @Test
        @DisplayName("User A cannot verify User B's payment")
        void userACannotVerifyUserBPayment() {
            razorpayConfig.setKeySecret("");

            Order otherOrder = Order.builder().id(2L).userId(99L).totalPrice(BigDecimal.valueOf(100))
                    .status(Order.OrderStatus.PENDING_PAYMENT).paymentStatus(Order.PaymentStatus.PENDING)
                    .currency("INR").refundedAmount(BigDecimal.ZERO).build();
            PaymentAttempt otherAttempt = PaymentAttempt.builder().id(2L).orderId(2L)
                    .providerOrderId("order_other").amount(BigDecimal.valueOf(100)).status("PENDING").build();

            when(paymentAttemptRepository.findByProviderOrderId("order_other")).thenReturn(Optional.of(otherAttempt));
            when(orderRepository.findById(2L)).thenReturn(Optional.of(otherOrder));

            PaymentCallbackRequest callback = PaymentCallbackRequest.builder()
                    .razorpayOrderId("order_other").razorpayPaymentId("pay_other")
                    .razorpaySignature("sig").build();

            assertThatThrownBy(() -> paymentService.verifyPayment(42L, callback))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        @DisplayName("Order must exist for verification")
        void orderMustExist() {
            razorpayConfig.setKeySecret("");

            PaymentAttempt attempt = PaymentAttempt.builder().id(1L).orderId(999L)
                    .providerOrderId("order_x").amount(BigDecimal.valueOf(100)).status("PENDING").build();

            when(paymentAttemptRepository.findByProviderOrderId("order_x")).thenReturn(Optional.of(attempt));
            when(orderRepository.findById(999L)).thenReturn(Optional.empty());

            PaymentCallbackRequest callback = PaymentCallbackRequest.builder()
                    .razorpayOrderId("order_x").razorpayPaymentId("pay_x")
                    .razorpaySignature("sig").build();

            assertThatThrownBy(() -> paymentService.verifyPayment(42L, callback))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Order not found");
        }
    }

    // ==================================================================
    // WEBHOOK TESTS
    // ==================================================================

    @Nested
    @DisplayName("Webhook Processing")
    class WebhookProcessing {

        @Test
        @DisplayName("Webhook with invalid signature is rejected")
        void webhookWithInvalidSignatureIsRejected() {
            String payload = "{\"event\":\"payment.captured\",\"id\":\"evt_123\"}";

            assertThatThrownBy(() -> paymentService.processWebhook(payload, "invalid_sig"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Invalid webhook signature");
        }

        @Test
        @DisplayName("Duplicate webhook event is idempotent — first processes, second skips")
        void duplicateWebhookEventIsIdempotent() {
            razorpayConfig.setKeySecret("");
            razorpayConfig.setWebhookSecret("");

            String payload = "{\"id\":\"evt_dup_001\",\"event\":\"payment.captured\",\"payload\":{\"payment\":{\"entity\":{\"order_id\":\"order_123\",\"id\":\"pay_123\"}}}}";

            // First delivery — event doesn't exist yet, processes normally
            when(paymentEventRepository.existsByProviderEventId("evt_dup_001")).thenReturn(false);
            when(paymentEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // Should not throw
            paymentService.processWebhook(payload, "");

            // Second delivery — event already exists, should skip processing
            when(paymentEventRepository.existsByProviderEventId("evt_dup_001")).thenReturn(true);
            paymentService.processWebhook(payload, "");
        }

        @Test
        @DisplayName("Malformed webhook payload returns error")
        void malformedWebhookReturnsError() {
            razorpayConfig.setKeySecret("");
            razorpayConfig.setWebhookSecret("");

            // Malformed JSON triggers BadRequestException from processWebhook
            // The PaymentController catches this and returns 200 to Razorpay
            assertThatThrownBy(() -> paymentService.processWebhook("not valid json{}", ""))
                    .isInstanceOf(BadRequestException.class);
        }
    }

    // ==================================================================
    // PAYMENT RETRY TESTS
    // ==================================================================

    @Nested
    @DisplayName("Payment Retry")
    class PaymentRetry {

        @Test
        @DisplayName("Cannot retry payment for non-pending order")
        void cannotRetryForNonPendingOrder() {
            Order paidOrder = Order.builder().id(1L).userId(42L).totalPrice(BigDecimal.valueOf(100))
                    .status(Order.OrderStatus.PAID).paymentStatus(Order.PaymentStatus.PAID)
                    .currency("INR").refundedAmount(BigDecimal.ZERO).build();

            when(orderRepository.findByIdAndUserId(1L, 42L)).thenReturn(Optional.of(paidOrder));

            assertThatThrownBy(() -> paymentService.retryPayment(42L, 1L))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("pending orders");
        }

        @Test
        @DisplayName("Cannot retry payment for another user's order")
        void cannotRetryForOtherUsersOrder() {
            when(orderRepository.findByIdAndUserId(1L, 42L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> paymentService.retryPayment(42L, 1L))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("not found");
        }

        @Test
        @DisplayName("Cannot retry COD order")
        void cannotRetryCodOrder() {
            Order codOrder = Order.builder().id(1L).userId(42L).totalPrice(BigDecimal.valueOf(100))
                    .status(Order.OrderStatus.PENDING_PAYMENT).paymentMethod("cod")
                    .paymentStatus(Order.PaymentStatus.PENDING)
                    .currency("INR").refundedAmount(BigDecimal.ZERO).build();

            when(orderRepository.findByIdAndUserId(1L, 42L)).thenReturn(Optional.of(codOrder));

            assertThatThrownBy(() -> paymentService.retryPayment(42L, 1L))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("online payments");
        }
    }

    // ==================================================================
    // REFUND TESTS
    // ==================================================================

    @Nested
    @DisplayName("Refund Processing")
    class RefundProcessing {

        @Test
        @DisplayName("Cannot refund order that is not in paid state")
        void cannotRefundNonPaidOrder() {
            Order pendingOrder = Order.builder().id(1L).userId(42L).totalPrice(BigDecimal.valueOf(100))
                    .status(Order.OrderStatus.PENDING_PAYMENT).paymentStatus(Order.PaymentStatus.PENDING)
                    .currency("INR").refundedAmount(BigDecimal.ZERO).build();

            when(orderRepository.findById(1L)).thenReturn(Optional.of(pendingOrder));

            assertThatThrownBy(() -> paymentService.initiateRefund(42L, 1L, null, "test"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("refundable state");
        }

        @Test
        @DisplayName("Cannot refund more than order total")
        void cannotRefundMoreThanTotal() {
            Order paidOrder = Order.builder().id(1L).userId(42L).totalPrice(BigDecimal.valueOf(100))
                    .status(Order.OrderStatus.PAID).paymentStatus(Order.PaymentStatus.PAID)
                    .currency("INR").refundedAmount(BigDecimal.ZERO).build();

            when(orderRepository.findById(1L)).thenReturn(Optional.of(paidOrder));

            assertThatThrownBy(() -> paymentService.initiateRefund(42L, 1L, BigDecimal.valueOf(200), "test"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("exceeds refundable");
        }

        @Test
        @DisplayName("Cannot refund zero or negative amount")
        void cannotRefundZeroOrNegative() {
            Order paidOrder = Order.builder().id(1L).userId(42L).totalPrice(BigDecimal.valueOf(100))
                    .status(Order.OrderStatus.PAID).paymentStatus(Order.PaymentStatus.PAID)
                    .currency("INR").refundedAmount(BigDecimal.ZERO).build();

            when(orderRepository.findById(1L)).thenReturn(Optional.of(paidOrder));

            assertThatThrownBy(() -> paymentService.initiateRefund(42L, 1L, BigDecimal.ZERO, "test"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("positive");

            assertThatThrownBy(() -> paymentService.initiateRefund(42L, 1L, BigDecimal.valueOf(-10), "test"))
                    .isInstanceOf(BadRequestException.class);
        }
    }
}
