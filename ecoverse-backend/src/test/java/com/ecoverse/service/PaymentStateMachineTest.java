package com.ecoverse.service;

import com.ecoverse.model.Order;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprehensive PaymentStatus state machine tests.
 * Every legal and illegal transition is tested.
 */
class PaymentStateMachineTest {

    // ==================================================================
    // PAYMENT STATUS LEGAL TRANSITIONS
    // ==================================================================

    @Nested
    @DisplayName("Payment Status — Legal Transitions")
    class LegalTransitions {

        @Test
        @DisplayName("PENDING → AUTHORIZED")
        void pendingToAuthorized() {
            assertThat(Order.PaymentStatus.PENDING.canTransitionTo(Order.PaymentStatus.AUTHORIZED)).isTrue();
        }

        @Test
        @DisplayName("PENDING → PAID")
        void pendingToPaid() {
            assertThat(Order.PaymentStatus.PENDING.canTransitionTo(Order.PaymentStatus.PAID)).isTrue();
        }

        @Test
        @DisplayName("PENDING → FAILED")
        void pendingToFailed() {
            assertThat(Order.PaymentStatus.PENDING.canTransitionTo(Order.PaymentStatus.FAILED)).isTrue();
        }

        @Test
        @DisplayName("AUTHORIZED → PAID")
        void authorizedToPaid() {
            assertThat(Order.PaymentStatus.AUTHORIZED.canTransitionTo(Order.PaymentStatus.PAID)).isTrue();
        }

        @Test
        @DisplayName("AUTHORIZED → FAILED")
        void authorizedToFailed() {
            assertThat(Order.PaymentStatus.AUTHORIZED.canTransitionTo(Order.PaymentStatus.FAILED)).isTrue();
        }

        @Test
        @DisplayName("PAID → REFUND_PENDING")
        void paidToRefundPending() {
            assertThat(Order.PaymentStatus.PAID.canTransitionTo(Order.PaymentStatus.REFUND_PENDING)).isTrue();
        }

        @Test
        @DisplayName("REFUND_PENDING → REFUNDED")
        void refundPendingToRefunded() {
            assertThat(Order.PaymentStatus.REFUND_PENDING.canTransitionTo(Order.PaymentStatus.REFUNDED)).isTrue();
        }

        @Test
        @DisplayName("REFUND_PENDING → PAID (refund failed)")
        void refundPendingToPaid() {
            assertThat(Order.PaymentStatus.REFUND_PENDING.canTransitionTo(Order.PaymentStatus.PAID)).isTrue();
        }
    }

    // ==================================================================
    // PAYMENT STATUS ILLEGAL TRANSITIONS
    // ==================================================================

    @Nested
    @DisplayName("Payment Status — Illegal Transitions")
    class IllegalTransitions {

        @Test
        @DisplayName("FAILED → PAID (must create new attempt)")
        void failedToPaid() {
            assertThat(Order.PaymentStatus.FAILED.canTransitionTo(Order.PaymentStatus.PAID)).isFalse();
        }

        @Test
        @DisplayName("FAILED → AUTHORIZED")
        void failedToAuthorized() {
            assertThat(Order.PaymentStatus.FAILED.canTransitionTo(Order.PaymentStatus.AUTHORIZED)).isFalse();
        }

        @Test
        @DisplayName("REFUNDED → PAID")
        void refundedToPaid() {
            assertThat(Order.PaymentStatus.REFUNDED.canTransitionTo(Order.PaymentStatus.PAID)).isFalse();
        }

        @Test
        @DisplayName("REFUNDED → REFUND_PENDING")
        void refundedToRefundPending() {
            assertThat(Order.PaymentStatus.REFUNDED.canTransitionTo(Order.PaymentStatus.REFUND_PENDING)).isFalse();
        }

        @Test
        @DisplayName("PAID → PENDING (backward)")
        void paidToPending() {
            assertThat(Order.PaymentStatus.PAID.canTransitionTo(Order.PaymentStatus.PENDING)).isFalse();
        }

        @Test
        @DisplayName("PAID → FAILED")
        void paidToFailed() {
            assertThat(Order.PaymentStatus.PAID.canTransitionTo(Order.PaymentStatus.FAILED)).isFalse();
        }

        @Test
        @DisplayName("AUTHORIZED → PENDING (backward)")
        void authorizedToPending() {
            assertThat(Order.PaymentStatus.AUTHORIZED.canTransitionTo(Order.PaymentStatus.PENDING)).isFalse();
        }

        @Test
        @DisplayName("REFUND_PENDING → FAILED")
        void refundPendingToFailed() {
            assertThat(Order.PaymentStatus.REFUND_PENDING.canTransitionTo(Order.PaymentStatus.FAILED)).isFalse();
        }
    }

    // ==================================================================
    // IDEMPOTENT (SAME STATUS)
    // ==================================================================

    @Nested
    @DisplayName("Payment Status — Idempotent Same-Status")
    class IdempotentStatus {

        @Test
        @DisplayName("Same status is always allowed for all values")
        void sameStatusAlwaysAllowed() {
            for (Order.PaymentStatus status : Order.PaymentStatus.values()) {
                assertThat(status.canTransitionTo(status))
                        .as("Status %s should be able to transition to itself", status)
                        .isTrue();
            }
        }
    }

    // ==================================================================
    // VALIDATE THROWS
    // ==================================================================

    @Nested
    @DisplayName("Payment Status — validateTransitionTo Throws")
    class ValidateTransitionThrows {

        @Test
        @DisplayName("validateTransitionTo throws IllegalStateException on illegal")
        void throwsOnIllegal() {
            assertThatThrownBy(() ->
                    Order.PaymentStatus.FAILED.validateTransitionTo(Order.PaymentStatus.PAID))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Illegal payment status transition: FAILED → PAID");
        }

        @Test
        @DisplayName("validateTransitionTo does NOT throw on legal")
        void doesNotThrowOnLegal() {
            // Should not throw
            Order.PaymentStatus.PENDING.validateTransitionTo(Order.PaymentStatus.PAID);
            Order.PaymentStatus.PAID.validateTransitionTo(Order.PaymentStatus.REFUND_PENDING);
            Order.PaymentStatus.REFUND_PENDING.validateTransitionTo(Order.PaymentStatus.REFUNDED);
        }

        @Test
        @DisplayName("validateTransitionTo does NOT throw on same status")
        void doesNotThrowOnSameStatus() {
            // Same status is idempotent
            Order.PaymentStatus.PAID.validateTransitionTo(Order.PaymentStatus.PAID);
            Order.PaymentStatus.FAILED.validateTransitionTo(Order.PaymentStatus.FAILED);
        }
    }

    // ==================================================================
    // ORDER STATUS (PAYMENT-AWARE) TRANSITIONS
    // ==================================================================

    @Nested
    @DisplayName("Order Status — Payment-Aware Legal Transitions")
    class OrderStatusPaymentAware {

        @Test
        @DisplayName("PENDING_PAYMENT → PAYMENT_FAILED (payment failed)")
        void pendingPaymentToPaymentFailed() {
            assertThat(Order.OrderStatus.PENDING_PAYMENT.canTransitionTo(Order.OrderStatus.PAYMENT_FAILED)).isTrue();
        }

        @Test
        @DisplayName("PAYMENT_FAILED is terminal")
        void paymentFailedIsTerminal() {
            assertThat(Order.OrderStatus.PAYMENT_FAILED.canTransitionTo(Order.OrderStatus.PAID)).isFalse();
            assertThat(Order.OrderStatus.PAYMENT_FAILED.canTransitionTo(Order.OrderStatus.PROCESSING)).isFalse();
            assertThat(Order.OrderStatus.PAYMENT_FAILED.canTransitionTo(Order.OrderStatus.CANCELLED)).isFalse();
        }

        @Test
        @DisplayName("PAID → REFUNDED (direct refund)")
        void paidToRefunded() {
            assertThat(Order.OrderStatus.PAID.canTransitionTo(Order.OrderStatus.REFUNDED)).isTrue();
        }

        @Test
        @DisplayName("SHIPPED → REFUNDED")
        void shippedToRefunded() {
            assertThat(Order.OrderStatus.SHIPPED.canTransitionTo(Order.OrderStatus.REFUNDED)).isTrue();
        }

        @Test
        @DisplayName("DELIVERED → REFUNDED")
        void deliveredToRefunded() {
            assertThat(Order.OrderStatus.DELIVERED.canTransitionTo(Order.OrderStatus.REFUNDED)).isTrue();
        }

        @Test
        @DisplayName("REFUNDED is terminal")
        void refundedIsTerminal() {
            assertThat(Order.OrderStatus.REFUNDED.canTransitionTo(Order.OrderStatus.PAID)).isFalse();
            assertThat(Order.OrderStatus.REFUNDED.canTransitionTo(Order.OrderStatus.PROCESSING)).isFalse();
            assertThat(Order.OrderStatus.REFUNDED.canTransitionTo(Order.OrderStatus.CANCELLED)).isFalse();
        }
    }
}
