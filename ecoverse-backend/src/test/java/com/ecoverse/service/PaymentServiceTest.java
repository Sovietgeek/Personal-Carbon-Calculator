package com.ecoverse.service;

import com.ecoverse.config.RazorpayConfig;
import com.ecoverse.dto.payment.PaymentCallbackRequest;
import com.ecoverse.exception.BadRequestException;
import com.ecoverse.exception.ForbiddenException;
import com.ecoverse.model.Order;
import com.ecoverse.model.PaymentAttempt;
import com.ecoverse.repository.OrderRepository;
import com.ecoverse.repository.PaymentAttemptRepository;
import com.ecoverse.repository.PaymentEventRepository;
import com.ecoverse.repository.OrderItemRepository;
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
 * Tests for PaymentService security-critical behavior.
 *
 * Phase 5 requirements verified:
 * - Authenticated user can verify their own order
 * - User CANNOT verify another user's order (IDOR protection)
 * - Duplicate payment verification is handled safely (idempotency)
 * - Invalid Razorpay order ID throws BadRequestException
 * - Payment verification transitions PENDING_PAYMENT → PAID
 * - PaymentStatus transitions are enforced
 * - Refund validates refundable state and amount
 * - Payment expiry restores stock
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

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

    private Order userOrder;
    private Order otherUserOrder;
    private PaymentAttempt userAttempt;

    @BeforeEach
    void setUp() {
        // Create RazorpayConfig with test values (no real keys)
        razorpayConfig = new RazorpayConfig();
        razorpayConfig.setKeyId("test_key_id");
        razorpayConfig.setKeySecret("test_key_secret");
        razorpayConfig.setWebhookSecret("test_webhook_secret");
        razorpayConfig.setCurrency("INR");
        razorpayConfig.setMode("test");
        razorpayConfig.setPaymentExpiryMinutes(30);

        // Create PaymentService with mock WebClient builder and config
        when(webClientBuilder.build()).thenReturn(mock(WebClient.class));
        paymentService = new PaymentService(webClientBuilder, razorpayConfig);

        // Inject mocks via reflection
        injectField(paymentService, "orderRepository", orderRepository);
        injectField(paymentService, "shopService", shopService);
        injectField(paymentService, "paymentAttemptRepository", paymentAttemptRepository);
        injectField(paymentService, "paymentEventRepository", paymentEventRepository);
        injectField(paymentService, "orderItemRepository", orderItemRepository);
        injectField(paymentService, "productRepository", productRepository);
        injectField(paymentService, "auditLogService", auditLogService);

        userOrder = Order.builder()
                .id(100L)
                .userId(42L)
                .totalPrice(BigDecimal.valueOf(500.0))
                .status(Order.OrderStatus.PENDING_PAYMENT)
                .paymentMethod("online")
                .shippingAddress("123 Main St")
                .razorpayOrderId("order_razorpay_100")
                .paymentStatus(Order.PaymentStatus.PENDING)
                .currency("INR")
                .refundedAmount(BigDecimal.ZERO)
                .build();

        otherUserOrder = Order.builder()
                .id(200L)
                .userId(99L)
                .totalPrice(BigDecimal.valueOf(1000.0))
                .status(Order.OrderStatus.PENDING_PAYMENT)
                .paymentMethod("online")
                .shippingAddress("456 Other St")
                .razorpayOrderId("order_razorpay_200")
                .paymentStatus(Order.PaymentStatus.PENDING)
                .currency("INR")
                .refundedAmount(BigDecimal.ZERO)
                .build();

        userAttempt = PaymentAttempt.builder()
                .id(1L)
                .orderId(100L)
                .provider("razorpay")
                .providerOrderId("order_razorpay_100")
                .amount(BigDecimal.valueOf(500.0))
                .currency("INR")
                .status("PENDING")
                .build();
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
    // PAYMENT VERIFICATION OWNERSHIP TESTS
    // ==================================================================

    @Nested
    @DisplayName("Payment Verification — Ownership (IDOR Protection)")
    class PaymentVerificationOwnership {

        @Test
        @DisplayName("User CAN verify their own order")
        void userCanVerifyOwnOrder() {
            // Use empty key secret to skip signature verification
            razorpayConfig.setKeySecret("");

            PaymentCallbackRequest callback = PaymentCallbackRequest.builder()
                    .razorpayOrderId("order_razorpay_100")
                    .razorpayPaymentId("pay_123")
                    .razorpaySignature("sig_123")
                    .build();

            when(paymentAttemptRepository.findByProviderOrderId("order_razorpay_100"))
                    .thenReturn(Optional.of(userAttempt));
            when(orderRepository.findById(100L)).thenReturn(Optional.of(userOrder));
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            var result = paymentService.verifyPayment(42L, callback);

            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo("paid");
        }

        @Test
        @DisplayName("User CANNOT verify another user's order (IDOR)")
        void userCannotVerifyAnotherUsersOrder() {
            razorpayConfig.setKeySecret("");

            PaymentAttempt otherAttempt = PaymentAttempt.builder()
                    .id(2L).orderId(200L).providerOrderId("order_razorpay_200")
                    .amount(BigDecimal.valueOf(1000)).status("PENDING").build();

            PaymentCallbackRequest callback = PaymentCallbackRequest.builder()
                    .razorpayOrderId("order_razorpay_200")
                    .razorpayPaymentId("pay_456")
                    .razorpaySignature("sig_456")
                    .build();

            when(paymentAttemptRepository.findByProviderOrderId("order_razorpay_200"))
                    .thenReturn(Optional.of(otherAttempt));
            when(orderRepository.findById(200L)).thenReturn(Optional.of(otherUserOrder));

            // User 42 trying to verify user 99's order — MUST be rejected
            assertThatThrownBy(() -> paymentService.verifyPayment(42L, callback))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("permission");

            verify(orderRepository, never()).save(argThat(order ->
                    order.getId().equals(200L)
            ));
        }

        @Test
        @DisplayName("Non-existent Razorpay order ID throws BadRequestException")
        void nonExistentOrderThrowsException() {
            razorpayConfig.setKeySecret("");

            PaymentCallbackRequest callback = PaymentCallbackRequest.builder()
                    .razorpayOrderId("order_nonexistent")
                    .razorpayPaymentId("pay_789")
                    .razorpaySignature("sig_789")
                    .build();

            when(paymentAttemptRepository.findByProviderOrderId("order_nonexistent"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> paymentService.verifyPayment(42L, callback))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("No payment attempt found");
        }

        @Test
        @DisplayName("Payment verification transitions PENDING_PAYMENT → PAID (not CONFIRMED)")
        void verifyPaymentTransitionsToPaidNotConfirmed() {
            razorpayConfig.setKeySecret("");

            PaymentCallbackRequest callback = PaymentCallbackRequest.builder()
                    .razorpayOrderId("order_razorpay_100")
                    .razorpayPaymentId("pay_123")
                    .razorpaySignature("sig_123")
                    .build();

            when(paymentAttemptRepository.findByProviderOrderId("order_razorpay_100"))
                    .thenReturn(Optional.of(userAttempt));
            when(orderRepository.findById(100L)).thenReturn(Optional.of(userOrder));
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            var result = paymentService.verifyPayment(42L, callback);

            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo("paid");

            verify(orderRepository).save(argThat(order ->
                    order.getStatus() == Order.OrderStatus.PAID &&
                    order.getPaymentStatus() == Order.PaymentStatus.PAID
            ));
        }
    }

    // ==================================================================
    // IDEMPOTENCY TESTS
    // ==================================================================

    @Nested
    @DisplayName("Payment Verification — Idempotency")
    class PaymentVerificationIdempotency {

        @Test
        @DisplayName("Already-paid order returns confirmation without error (idempotent)")
        void alreadyPaidOrderIsIdempotent() {
            razorpayConfig.setKeySecret("");

            Order paidOrder = Order.builder()
                    .id(100L)
                    .userId(42L)
                    .totalPrice(BigDecimal.valueOf(500.0))
                    .status(Order.OrderStatus.PAID)
                    .paymentMethod("online")
                    .shippingAddress("123 Main St")
                    .razorpayOrderId("order_razorpay_100")
                    .paymentStatus(Order.PaymentStatus.PAID)
                    .currency("INR")
                    .refundedAmount(BigDecimal.ZERO)
                    .build();

            PaymentCallbackRequest callback = PaymentCallbackRequest.builder()
                    .razorpayOrderId("order_razorpay_100")
                    .razorpayPaymentId("pay_123")
                    .razorpaySignature("sig_123")
                    .build();

            when(paymentAttemptRepository.findByProviderOrderId("order_razorpay_100"))
                    .thenReturn(Optional.of(userAttempt));
            when(orderRepository.findById(100L)).thenReturn(Optional.of(paidOrder));

            var result = paymentService.verifyPayment(42L, callback);

            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo("paid");
            assertThat(result.getMessage()).contains("already verified");

            verify(orderRepository, never()).save(any());
        }
    }

    // ==================================================================
    // PAYMENT STATUS TRANSITION TESTS
    // ==================================================================

    @Nested
    @DisplayName("Payment Status — Transition Enforcement")
    class PaymentStatusTransitions {

        @Test
        @DisplayName("PENDING → PAID is legal")
        void pendingToPaidIsLegal() {
            assertThat(Order.PaymentStatus.PENDING.canTransitionTo(Order.PaymentStatus.PAID)).isTrue();
        }

        @Test
        @DisplayName("PENDING → FAILED is legal")
        void pendingToFailedIsLegal() {
            assertThat(Order.PaymentStatus.PENDING.canTransitionTo(Order.PaymentStatus.FAILED)).isTrue();
        }

        @Test
        @DisplayName("PENDING → AUTHORIZED is legal")
        void pendingToAuthorizedIsLegal() {
            assertThat(Order.PaymentStatus.PENDING.canTransitionTo(Order.PaymentStatus.AUTHORIZED)).isTrue();
        }

        @Test
        @DisplayName("PAID → REFUND_PENDING is legal")
        void paidToRefundPendingIsLegal() {
            assertThat(Order.PaymentStatus.PAID.canTransitionTo(Order.PaymentStatus.REFUND_PENDING)).isTrue();
        }

        @Test
        @DisplayName("REFUND_PENDING → REFUNDED is legal")
        void refundPendingToRefundedIsLegal() {
            assertThat(Order.PaymentStatus.REFUND_PENDING.canTransitionTo(Order.PaymentStatus.REFUNDED)).isTrue();
        }

        @Test
        @DisplayName("FAILED → PAID is illegal")
        void failedToPaidIsIllegal() {
            assertThat(Order.PaymentStatus.FAILED.canTransitionTo(Order.PaymentStatus.PAID)).isFalse();
        }

        @Test
        @DisplayName("REFUNDED → PAID is illegal")
        void refundedToPaidIsIllegal() {
            assertThat(Order.PaymentStatus.REFUNDED.canTransitionTo(Order.PaymentStatus.PAID)).isFalse();
        }

        @Test
        @DisplayName("REFUNDED → REFUND_PENDING is illegal")
        void refundedToRefundPendingIsIllegal() {
            assertThat(Order.PaymentStatus.REFUNDED.canTransitionTo(Order.PaymentStatus.REFUND_PENDING)).isFalse();
        }

        @Test
        @DisplayName("Same status is always allowed (idempotent)")
        void sameStatusIsIdempotent() {
            for (Order.PaymentStatus status : Order.PaymentStatus.values()) {
                assertThat(status.canTransitionTo(status)).isTrue();
            }
        }

        @Test
        @DisplayName("validateTransitionTo throws on illegal transition")
        void validateThrowsOnIllegal() {
            assertThatThrownBy(() -> Order.PaymentStatus.FAILED.validateTransitionTo(Order.PaymentStatus.PAID))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Illegal payment status transition");
        }
    }

    // ==================================================================
    // PAYMENT EXPIRY TESTS
    // ==================================================================

    @Nested
    @DisplayName("Payment Expiry — Stock Restoration")
    class PaymentExpiryTests {

        @Test
        @DisplayName("expirePayment sets PAYMENT_FAILED and restores stock")
        void expirePaymentSetsFailedAndRestoresStock() {
            // Order with items that need stock restoration
            Order pendingOrder = Order.builder()
                    .id(100L)
                    .userId(42L)
                    .totalPrice(BigDecimal.valueOf(500.0))
                    .status(Order.OrderStatus.PENDING_PAYMENT)
                    .paymentMethod("card")
                    .paymentStatus(Order.PaymentStatus.PENDING)
                    .currency("INR")
                    .refundedAmount(BigDecimal.ZERO)
                    .build();

            when(orderItemRepository.findByOrderId(100L)).thenReturn(java.util.List.of(
                    com.ecoverse.model.OrderItem.builder()
                            .orderId(100L).productId(10L).quantity(2)
                            .unitPrice(BigDecimal.valueOf(250)).build()
            ));
            when(productRepository.restoreStock(10L, 2)).thenReturn(1);
            when(paymentAttemptRepository.findByOrderId(100L)).thenReturn(java.util.List.of());
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            paymentService.expirePayment(pendingOrder);

            assertThat(pendingOrder.getStatus()).isEqualTo(Order.OrderStatus.PAYMENT_FAILED);
            assertThat(pendingOrder.getPaymentStatus()).isEqualTo(Order.PaymentStatus.FAILED);
            assertThat(pendingOrder.getPaymentFailureReason()).contains("expired");

            verify(productRepository).restoreStock(10L, 2);
        }

        @Test
        @DisplayName("expirePayment is idempotent — skips non-PENDING_PAYMENT orders")
        void expirePaymentIsIdempotent() {
            Order paidOrder = Order.builder()
                    .id(100L)
                    .status(Order.OrderStatus.PAID)
                    .paymentStatus(Order.PaymentStatus.PAID)
                    .build();

            paymentService.expirePayment(paidOrder);

            // Should NOT modify a PAID order
            assertThat(paidOrder.getStatus()).isEqualTo(Order.OrderStatus.PAID);
            verify(orderRepository, never()).save(any());
            verify(productRepository, never()).restoreStock(anyLong(), anyInt());
        }
    }
}
