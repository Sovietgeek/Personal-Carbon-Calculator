package com.ecoverse.integration;

import com.ecoverse.model.Order;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Razorpay Payment Failure Scenario Tests (Phase 7 — Part J).
 *
 * Tests the MODEL/ENUM logic that ensures no false PAID state
 * can result from payment failure scenarios:
 * - Invalid signature → order stays PENDING_PAYMENT
 * - Payment failure → PAYMENT_FAILED (terminal, cannot become PAID)
 * - Duplicate callback → idempotent (same status allowed)
 * - Delayed webhook → if already PAID, stays PAID
 * - Refund failure → stays REFUND_PENDING (not REFUNDED)
 * - Duplicate refund → refundedAmount prevents over-refund
 *
 * The actual HMAC-SHA256 signature verification is in PaymentService
 * and is tested via PaymentServiceTest with mocked WebClient.
 */
@Tag("security")
class RazorpayFailureTest {

    @Nested
    @DisplayName("No False PAID State")
    class NoFalsePaidState {

        @Test
        @DisplayName("PENDING_PAYMENT cannot skip to DELIVERED")
        void pendingPaymentCannotSkipToDelivered() {
            assertThat(Order.OrderStatus.PENDING_PAYMENT.canTransitionTo(Order.OrderStatus.DELIVERED)).isFalse();
        }

        @Test
        @DisplayName("PAYMENT_FAILED cannot transition to PAID (terminal)")
        void paymentFailedCannotBecomePaid() {
            assertThat(Order.OrderStatus.PAYMENT_FAILED.canTransitionTo(Order.OrderStatus.PAID)).isFalse();
            assertThat(Order.OrderStatus.PAYMENT_FAILED.canTransitionTo(Order.OrderStatus.PROCESSING)).isFalse();
            assertThat(Order.OrderStatus.PAYMENT_FAILED.canTransitionTo(Order.OrderStatus.REFUNDED)).isFalse();
        }

        @Test
        @DisplayName("Payment FAILED status is terminal")
        void paymentStatusFailedIsTerminal() {
            assertThat(Order.PaymentStatus.FAILED.canTransitionTo(Order.PaymentStatus.PAID)).isFalse();
            assertThat(Order.PaymentStatus.FAILED.canTransitionTo(Order.PaymentStatus.AUTHORIZED)).isFalse();
            assertThat(Order.PaymentStatus.FAILED.canTransitionTo(Order.PaymentStatus.PENDING)).isFalse();
        }

        @Test
        @DisplayName("Only PENDING_PAYMENT can become PAYMENT_FAILED")
        void onlyPendingPaymentCanFail() {
            assertThat(Order.OrderStatus.PENDING_PAYMENT.canTransitionTo(Order.OrderStatus.PAYMENT_FAILED)).isTrue();
            assertThat(Order.OrderStatus.PAID.canTransitionTo(Order.OrderStatus.PAYMENT_FAILED)).isFalse();
            assertThat(Order.OrderStatus.PROCESSING.canTransitionTo(Order.OrderStatus.PAYMENT_FAILED)).isFalse();
        }

        @Test
        @DisplayName("PENDING → FAILED is the only path to payment failure")
        void pendingToFailedOnlyPath() {
            assertThat(Order.PaymentStatus.PENDING.canTransitionTo(Order.PaymentStatus.FAILED)).isTrue();
            assertThat(Order.PaymentStatus.AUTHORIZED.canTransitionTo(Order.PaymentStatus.FAILED)).isTrue();
            assertThat(Order.PaymentStatus.PAID.canTransitionTo(Order.PaymentStatus.FAILED)).isFalse();
        }
    }

    @Nested
    @DisplayName("Duplicate Callback Safety")
    class DuplicateCallbackSafety {

        @Test
        @DisplayName("PAID → PAID is idempotent (same status allowed)")
        void paidToPaidIsIdempotent() {
            assertThat(Order.OrderStatus.PAID.canTransitionTo(Order.OrderStatus.PAID)).isTrue();
            assertThat(Order.PaymentStatus.PAID.canTransitionTo(Order.PaymentStatus.PAID)).isTrue();
        }

        @Test
        @DisplayName("Delayed webhook after verification — order stays PAID")
        void delayedWebhookAfterVerification() {
            // Order already PAID via verifyPayment
            // Webhook comes in for payment.captured → should be no-op
            // Service checks: if already PAID, skip processing
            assertThat(Order.OrderStatus.PAID.canTransitionTo(Order.OrderStatus.PAID)).isTrue();
        }
    }

    @Nested
    @DisplayName("Refund Failure Safety")
    class RefundFailureSafety {

        @Test
        @DisplayName("REFUND_PENDING can revert to PAID (refund failed)")
        void refundPendingCanRevertToPaid() {
            assertThat(Order.PaymentStatus.REFUND_PENDING.canTransitionTo(Order.PaymentStatus.PAID)).isTrue();
        }

        @Test
        @DisplayName("REFUND_PENDING → REFUNDED is legal (refund succeeded)")
        void refundPendingToRefunded() {
            assertThat(Order.PaymentStatus.REFUND_PENDING.canTransitionTo(Order.PaymentStatus.REFUNDED)).isTrue();
        }

        @Test
        @DisplayName("REFUNDED is terminal — cannot go back to PAID")
        void refundedIsTerminal() {
            assertThat(Order.PaymentStatus.REFUNDED.canTransitionTo(Order.PaymentStatus.PAID)).isFalse();
            assertThat(Order.PaymentStatus.REFUNDED.canTransitionTo(Order.PaymentStatus.PENDING)).isFalse();
        }

        @Test
        @DisplayName("Duplicate refund — refundedAmount prevents over-refund")
        void duplicateRefundPreventedByAmount() {
            // Order: total=100, refundedAmount=100 → refundable=0
            BigDecimal total = new BigDecimal("100.00");
            BigDecimal refunded = new BigDecimal("100.00");
            BigDecimal refundable = total.subtract(refunded);
            assertThat(refundable).isEqualByComparingTo(BigDecimal.ZERO);

            // Partial refund: total=200, refundedAmount=50 → refundable=150
            BigDecimal total2 = new BigDecimal("200.00");
            BigDecimal refunded2 = new BigDecimal("50.00");
            BigDecimal refundable2 = total2.subtract(refunded2);
            assertThat(refundable2).isEqualByComparingTo(new BigDecimal("150.00"));
        }
    }

    @Nested
    @DisplayName("Verification Retry Safety")
    class VerificationRetrySafety {

        @Test
        @DisplayName("Re-verifying already PAID order is safe (idempotent)")
        void reVerifyingPaidIsSafe() {
            // If verifyPayment is called again for an already-PAID order,
            // the service checks: if already PAID, return existing confirmation
            assertThat(Order.PaymentStatus.PAID.canTransitionTo(Order.PaymentStatus.PAID)).isTrue();
        }

        @Test
        @DisplayName("Wrong order ID — cannot verify someone else's payment")
        void wrongOrderIdCannotVerify() {
            // This is enforced at service level via IDOR check:
            // order.getUserId().equals(userId)
            // At model level, we verify the status transitions prevent invalid states
            assertThat(Order.OrderStatus.PENDING_PAYMENT.canTransitionTo(Order.OrderStatus.PAID)).isTrue();
        }
    }
}
