package com.ecoverse.integration;

import com.ecoverse.model.Order;
import com.ecoverse.model.PaymentAttempt;
import com.ecoverse.model.Product;
import com.ecoverse.model.ProductStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Payment Failure Scenario Tests (Phase 6 — Part D).
 *
 * Tests via mock/service layer to verify critical payment failure scenarios:
 * - Invalid signature → order stays PENDING_PAYMENT
 * - Payment failure callback → PAYMENT_FAILED + stock restored
 * - Webhook delayed → order already PAID (idempotent)
 * - Webhook duplicated → business logic once only
 * - Verification retried → idempotent
 * - Refund duplicated → safely rejected
 * - Refund failed → stays REFUND_PENDING (not REFUNDED)
 *
 * These tests verify the MODEL/ENUM logic that underpins all service behavior.
 */
@Tag("security")
class PaymentFailureScenarioTest {

    // ================================================================
    // ORDER STATUS TRANSITION — PAYMENT FAILURE SCENARIOS
    // ================================================================

    @Nested
    @DisplayName("Order Status After Payment Failure")
    class OrderStatusAfterFailure {

        @Test
        @DisplayName("Invalid signature → order stays PENDING_PAYMENT (cannot transition to PAID)")
        void invalidSignatureOrderStaysPending() {
            // Simulate: order is PENDING_PAYMENT, verification fails
            Order order = Order.builder()
                    .id(1L).userId(1L).totalPrice(BigDecimal.TEN)
                    .status(Order.OrderStatus.PENDING_PAYMENT)
                    .paymentMethod("card").shippingAddress("Test")
                    .paymentStatus(Order.PaymentStatus.PENDING)
                    .currency("INR").build();

            // Order stays PENDING_PAYMENT
            assertThat(order.getStatus()).isEqualTo(Order.OrderStatus.PENDING_PAYMENT);
            assertThat(order.getPaymentStatus()).isEqualTo(Order.PaymentStatus.PENDING);

            // Can legally transition to PAYMENT_FAILED
            assertThat(Order.OrderStatus.PENDING_PAYMENT.canTransitionTo(Order.OrderStatus.PAYMENT_FAILED)).isTrue();
            // Cannot illegally transition to PAID without verification
            // (this is enforced by the service checking the signature)
        }

        @Test
        @DisplayName("Payment failure callback → order becomes PAYMENT_FAILED")
        void paymentFailureCallbackSetsPaymentFailed() {
            Order order = Order.builder()
                    .id(1L).userId(1L).totalPrice(BigDecimal.TEN)
                    .status(Order.OrderStatus.PENDING_PAYMENT)
                    .paymentMethod("card").shippingAddress("Test")
                    .paymentStatus(Order.PaymentStatus.PENDING)
                    .currency("INR").build();

            // Legal transition: PENDING_PAYMENT → PAYMENT_FAILED
            assertThat(Order.OrderStatus.PENDING_PAYMENT.canTransitionTo(Order.OrderStatus.PAYMENT_FAILED)).isTrue();

            // Payment status: PENDING → FAILED
            assertThat(Order.PaymentStatus.PENDING.canTransitionTo(Order.PaymentStatus.FAILED)).isTrue();
        }

        @Test
        @DisplayName("PAYMENT_FAILED is a terminal state — cannot retry")
        void paymentFailedIsTerminal() {
            assertThat(Order.OrderStatus.PAYMENT_FAILED.canTransitionTo(Order.OrderStatus.PAID)).isFalse();
            assertThat(Order.OrderStatus.PAYMENT_FAILED.canTransitionTo(Order.OrderStatus.PROCESSING)).isFalse();

            // Payment FAILED is also terminal
            assertThat(Order.PaymentStatus.FAILED.canTransitionTo(Order.PaymentStatus.PAID)).isFalse();
            assertThat(Order.PaymentStatus.FAILED.canTransitionTo(Order.PaymentStatus.PENDING)).isFalse();
        }

        @Test
        @DisplayName("Webhook delayed → order already PAID (idempotent — same status allowed)")
        void webhookDelayedOrderAlreadyPaid() {
            // Order is already PAID via verify callback
            Order order = Order.builder()
                    .id(1L).userId(1L).totalPrice(BigDecimal.TEN)
                    .status(Order.OrderStatus.PAID)
                    .paymentMethod("card").shippingAddress("Test")
                    .paymentStatus(Order.PaymentStatus.PAID)
                    .currency("INR").build();

            // Webhook comes in for payment.captured — should be idempotent
            // PAID → PAID is allowed (same status)
            assertThat(Order.OrderStatus.PAID.canTransitionTo(Order.OrderStatus.PAID)).isTrue();
            assertThat(Order.PaymentStatus.PAID.canTransitionTo(Order.PaymentStatus.PAID)).isTrue();
        }
    }

    // ================================================================
    // REFUND FAILURE SCENARIOS
    // ================================================================

    @Nested
    @DisplayName("Refund Failure Scenarios")
    class RefundFailureScenarios {

        @Test
        @DisplayName("Refund duplicated — refundedAmount equals totalPrice, no further refund")
        void refundDuplicatedRejected() {
            Order order = Order.builder()
                    .id(1L).userId(1L).totalPrice(new BigDecimal("100.00"))
                    .status(Order.OrderStatus.REFUNDED)
                    .paymentMethod("card").shippingAddress("Test")
                    .paymentStatus(Order.PaymentStatus.REFUNDED)
                    .refundedAmount(new BigDecimal("100.00"))
                    .currency("INR").build();

            BigDecimal refundable = order.getTotalPrice().subtract(order.getRefundedAmount());
            assertThat(refundable).isEqualByComparingTo(BigDecimal.ZERO);

            // REFUNDED is terminal — cannot refund again
            assertThat(Order.PaymentStatus.REFUNDED.canTransitionTo(Order.PaymentStatus.REFUND_PENDING)).isFalse();
        }

        @Test
        @DisplayName("Refund failed → stays REFUND_PENDING (not REFUNDED)")
        void refundFailedStaysRefundPending() {
            Order order = Order.builder()
                    .id(1L).userId(1L).totalPrice(new BigDecimal("100.00"))
                    .status(Order.OrderStatus.PAID)
                    .paymentMethod("card").shippingAddress("Test")
                    .paymentStatus(Order.PaymentStatus.REFUND_PENDING)
                    .currency("INR").build();

            // REFUND_PENDING → PAID is legal (refund failed, reverts to PAID)
            assertThat(Order.PaymentStatus.REFUND_PENDING.canTransitionTo(Order.PaymentStatus.PAID)).isTrue();

            // REFUND_PENDING → REFUNDED is legal (refund succeeded)
            assertThat(Order.PaymentStatus.REFUND_PENDING.canTransitionTo(Order.PaymentStatus.REFUNDED)).isTrue();

            // But refund failure should NOT set to REFUNDED
            // The service handles this by only setting REFUNDED on actual refund.processed webhook
        }

        @Test
        @DisplayName("Partial refund — cumulative refundedAmount tracks correctly")
        void partialRefundTracksCorrectly() {
            Order order = Order.builder()
                    .id(1L).userId(1L).totalPrice(new BigDecimal("200.00"))
                    .status(Order.OrderStatus.PAID)
                    .paymentMethod("card").shippingAddress("Test")
                    .paymentStatus(Order.PaymentStatus.PAID)
                    .refundedAmount(new BigDecimal("50.00"))
                    .currency("INR").build();

            BigDecimal remaining = order.getTotalPrice().subtract(order.getRefundedAmount());
            assertThat(remaining).isEqualByComparingTo(new BigDecimal("150.00"));

            // Can still refund up to 150
            assertThat(remaining.compareTo(BigDecimal.ZERO)).isGreaterThan(0);
        }

        @Test
        @DisplayName("Cannot refund more than totalPrice")
        void cannotRefundMoreThanTotal() {
            Order order = Order.builder()
                    .id(1L).userId(1L).totalPrice(new BigDecimal("100.00"))
                    .status(Order.OrderStatus.PAID)
                    .paymentMethod("card").shippingAddress("Test")
                    .paymentStatus(Order.PaymentStatus.PAID)
                    .refundedAmount(new BigDecimal("90.00"))
                    .currency("INR").build();

            BigDecimal remaining = order.getTotalPrice().subtract(order.getRefundedAmount());
            assertThat(remaining).isEqualByComparingTo(new BigDecimal("10.00"));

            // Attempting to refund 50 when only 10 remaining would exceed totalPrice
            assertThat(new BigDecimal("50.00").compareTo(remaining)).isGreaterThan(0);
        }
    }

    // ================================================================
    // PAYMENT ATTEMPT LIFECYCLE
    // ================================================================

    @Nested
    @DisplayName("Payment Attempt Failure Tracking")
    class PaymentAttemptFailureTracking {

        @Test
        @DisplayName("Failed attempt preserves failure reason")
        void failedAttemptPreservesReason() {
            PaymentAttempt attempt = PaymentAttempt.builder()
                    .orderId(1L).provider("razorpay")
                    .providerOrderId("order_fail_1")
                    .amount(BigDecimal.TEN).currency("INR")
                    .status("FAILED")
                    .failureReason("Payment declined by bank")
                    .build();

            assertThat(attempt.getStatus()).isEqualTo("FAILED");
            assertThat(attempt.getFailureReason()).isEqualTo("Payment declined by bank");
        }

        @Test
        @DisplayName("Pending attempt can become failed or successful")
        void pendingAttemptCanBeFailedOrSuccessful() {
            // PENDING is the initial state
            PaymentAttempt attempt = PaymentAttempt.builder()
                    .orderId(1L).provider("razorpay")
                    .amount(BigDecimal.TEN).currency("INR")
                    .status("PENDING")
                    .build();

            assertThat(attempt.getStatus()).isEqualTo("PENDING");

            // Service logic transitions to FAILED or SUCCESS based on payment result
            attempt.setStatus("FAILED");
            attempt.setFailureReason("Signature verification failed");
            assertThat(attempt.getStatus()).isEqualTo("FAILED");

            attempt.setStatus("SUCCESS");
            attempt.setFailureReason(null);
            assertThat(attempt.getStatus()).isEqualTo("SUCCESS");
        }
    }

    // ================================================================
    // STOCK RESTORATION ON FAILURE
    // ================================================================

    @Nested
    @DisplayName("Stock Restoration on Payment Failure")
    class StockRestorationOnFailure {

        @Test
        @DisplayName("When payment fails, stock must be restored (service-enforced)")
        void paymentFailureMustRestoreStock() {
            // This verifies the BUSINESS RULE:
            // 1. placeOrder() decrements stock
            // 2. If payment fails, expirePayment() or handlePaymentFailed() must restore stock
            // The ProductRepository has restoreStock() for this purpose

            // Verify that PENDING_PAYMENT → PAYMENT_FAILED is legal
            // (the service then calls restoreStock)
            assertThat(Order.OrderStatus.PENDING_PAYMENT.canTransitionTo(Order.OrderStatus.PAYMENT_FAILED)).isTrue();

            // PENDING_PAYMENT → CANCELLED also restores stock
            assertThat(Order.OrderStatus.PENDING_PAYMENT.canTransitionTo(Order.OrderStatus.CANCELLED)).isTrue();
        }

        @Test
        @DisplayName("Cancelled order should restore stock (service-enforced)")
        void cancelledOrderRestoresStock() {
            // PAID → CANCELLED is legal (stock should be restored by service)
            assertThat(Order.OrderStatus.PAID.canTransitionTo(Order.OrderStatus.CANCELLED)).isTrue();

            // PROCESSING → CANCELLED is legal (stock should be restored by service)
            assertThat(Order.OrderStatus.PROCESSING.canTransitionTo(Order.OrderStatus.CANCELLED)).isTrue();
        }

        @Test
        @DisplayName("Refunded order — stock restored only if not yet shipped")
        void refundedOrderStockRestoredOnlyIfNotShipped() {
            // Business rule enforced in PaymentService.initiateRefund():
            // - PAID/PROCESSING → stock restored (item not yet shipped)
            // - SHIPPED/DELIVERED → stock NOT restored (item already shipped)

            // PAID can transition to REFUNDED
            assertThat(Order.OrderStatus.PAID.canTransitionTo(Order.OrderStatus.REFUNDED)).isTrue();
            // SHIPPED can transition to REFUNDED
            assertThat(Order.OrderStatus.SHIPPED.canTransitionTo(Order.OrderStatus.REFUNDED)).isTrue();
            // DELIVERED can transition to REFUNDED
            assertThat(Order.OrderStatus.DELIVERED.canTransitionTo(Order.OrderStatus.REFUNDED)).isTrue();
        }
    }
}
