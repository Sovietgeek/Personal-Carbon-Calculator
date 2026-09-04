package com.ecoverse.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for Order.OrderStatus transition enforcement.
 * Ensures that only legal status transitions are allowed.
 */
class OrderStatusTransitionTest {

    // ==================================================================
    // LEGAL TRANSITIONS
    // ==================================================================

    @Nested
    @DisplayName("Legal Transitions")
    class LegalTransitions {

        @Test
        @DisplayName("PENDING_PAYMENT → PAID")
        void pendingPaymentToPaid() {
            assertThat(Order.OrderStatus.PENDING_PAYMENT.canTransitionTo(Order.OrderStatus.PAID)).isTrue();
        }

        @Test
        @DisplayName("PENDING_PAYMENT → CANCELLED")
        void pendingPaymentToCancelled() {
            assertThat(Order.OrderStatus.PENDING_PAYMENT.canTransitionTo(Order.OrderStatus.CANCELLED)).isTrue();
        }

        @Test
        @DisplayName("PENDING_PAYMENT → PAYMENT_FAILED")
        void pendingPaymentToPaymentFailed() {
            assertThat(Order.OrderStatus.PENDING_PAYMENT.canTransitionTo(Order.OrderStatus.PAYMENT_FAILED)).isTrue();
        }

        @Test
        @DisplayName("PAID → PROCESSING")
        void paidToProcessing() {
            assertThat(Order.OrderStatus.PAID.canTransitionTo(Order.OrderStatus.PROCESSING)).isTrue();
        }

        @Test
        @DisplayName("PAID → CANCELLED")
        void paidToCancelled() {
            assertThat(Order.OrderStatus.PAID.canTransitionTo(Order.OrderStatus.CANCELLED)).isTrue();
        }

        @Test
        @DisplayName("PAID → REFUNDED")
        void paidToRefunded() {
            assertThat(Order.OrderStatus.PAID.canTransitionTo(Order.OrderStatus.REFUNDED)).isTrue();
        }

        @Test
        @DisplayName("PROCESSING → SHIPPED")
        void processingToShipped() {
            assertThat(Order.OrderStatus.PROCESSING.canTransitionTo(Order.OrderStatus.SHIPPED)).isTrue();
        }

        @Test
        @DisplayName("PROCESSING → CANCELLED")
        void processingToCancelled() {
            assertThat(Order.OrderStatus.PROCESSING.canTransitionTo(Order.OrderStatus.CANCELLED)).isTrue();
        }

        @Test
        @DisplayName("SHIPPED → DELIVERED")
        void shippedToDelivered() {
            assertThat(Order.OrderStatus.SHIPPED.canTransitionTo(Order.OrderStatus.DELIVERED)).isTrue();
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
        @DisplayName("Same status transition is always allowed (idempotent)")
        void sameStatusIsAlwaysAllowed() {
            for (Order.OrderStatus status : Order.OrderStatus.values()) {
                assertThat(status.canTransitionTo(status))
                        .as(status + " → " + status + " should be allowed (idempotent)")
                        .isTrue();
            }
        }
    }

    // ==================================================================
    // ILLEGAL TRANSITIONS
    // ==================================================================

    @Nested
    @DisplayName("Illegal Transitions")
    class IllegalTransitions {

        @Test
        @DisplayName("DELIVERED → PROCESSING is illegal")
        void deliveredToProcessing() {
            assertThat(Order.OrderStatus.DELIVERED.canTransitionTo(Order.OrderStatus.PROCESSING)).isFalse();
        }

        @Test
        @DisplayName("DELIVERED → PAID is illegal")
        void deliveredToPaid() {
            assertThat(Order.OrderStatus.DELIVERED.canTransitionTo(Order.OrderStatus.PAID)).isFalse();
        }

        @Test
        @DisplayName("CANCELLED → PAID is illegal")
        void cancelledToPaid() {
            assertThat(Order.OrderStatus.CANCELLED.canTransitionTo(Order.OrderStatus.PAID)).isFalse();
        }

        @Test
        @DisplayName("CANCELLED → PROCESSING is illegal")
        void cancelledToProcessing() {
            assertThat(Order.OrderStatus.CANCELLED.canTransitionTo(Order.OrderStatus.PROCESSING)).isFalse();
        }

        @Test
        @DisplayName("CANCELLED → SHIPPED is illegal")
        void cancelledToShipped() {
            assertThat(Order.OrderStatus.CANCELLED.canTransitionTo(Order.OrderStatus.SHIPPED)).isFalse();
        }

        @Test
        @DisplayName("PAYMENT_FAILED → PAID is illegal")
        void paymentFailedToPaid() {
            assertThat(Order.OrderStatus.PAYMENT_FAILED.canTransitionTo(Order.OrderStatus.PAID)).isFalse();
        }

        @Test
        @DisplayName("PAYMENT_FAILED → PROCESSING is illegal")
        void paymentFailedToProcessing() {
            assertThat(Order.OrderStatus.PAYMENT_FAILED.canTransitionTo(Order.OrderStatus.PROCESSING)).isFalse();
        }

        @Test
        @DisplayName("REFUNDED → any is illegal (terminal state)")
        void refundedIsTerminal() {
            for (Order.OrderStatus target : Order.OrderStatus.values()) {
                if (target == Order.OrderStatus.REFUNDED) continue; // same-status is idempotent
                assertThat(Order.OrderStatus.REFUNDED.canTransitionTo(target))
                        .as("REFUNDED → " + target + " should be illegal")
                        .isFalse();
            }
        }
    }

    // ==================================================================
    // VALIDATE TRANSITION THROWS
    // ==================================================================

    @Nested
    @DisplayName("validateTransitionTo throws on illegal transition")
    class ValidateTransitionThrows {

        @Test
        @DisplayName("Illegal transition throws IllegalStateException")
        void illegalTransitionThrows() {
            assertThatThrownBy(() -> Order.OrderStatus.DELIVERED.validateTransitionTo(Order.OrderStatus.PROCESSING))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Illegal order status transition")
                    .hasMessageContaining("DELIVERED")
                    .hasMessageContaining("PROCESSING");
        }

        @Test
        @DisplayName("Legal transition does not throw")
        void legalTransitionDoesNotThrow() {
            // Should not throw
            Order.OrderStatus.PENDING_PAYMENT.validateTransitionTo(Order.OrderStatus.PAID);
        }
    }
}
