package com.ecoverse.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

@Entity
@Table(name = "orders", indexes = {
    @Index(name = "idx_order_user_id", columnList = "user_id"),
    @Index(name = "idx_order_razorpay_order_id", columnList = "razorpay_order_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "total_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(name = "payment_method", nullable = false)
    private String paymentMethod;

    @Column(name = "shipping_address", nullable = false)
    private String shippingAddress;

    // ===== RAZORPAY PAYMENT FIELDS (V6 Migration) =====

    @Column(name = "razorpay_order_id", unique = true)
    private String razorpayOrderId;

    @Column(name = "razorpay_payment_id")
    private String razorpayPaymentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status")
    @Builder.Default
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    @Column(name = "payment_provider", length = 50)
    private String paymentProvider;

    @Column(name = "payment_verified_at")
    private LocalDateTime paymentVerifiedAt;

    // ===== IDEMPOTENCY (V13 Migration) =====

    /**
     * Idempotency key for preventing duplicate orders.
     * Client generates a UUID and sends it with the checkout request.
     * If the same key is seen again, the existing order is returned.
     * Null keys are allowed (orders placed without idempotency protection).
     */
    @Column(name = "idempotency_key")
    private String idempotencyKey;

    // ===== PAYMENT ENHANCEMENT FIELDS (V16 Migration) =====

    @Column(name = "payment_failure_reason")
    private String paymentFailureReason;

    @Column(name = "refunded_amount", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal refundedAmount = BigDecimal.ZERO;

    @Column(name = "refund_id")
    private String refundId;

    @Column(name = "captured_at")
    private LocalDateTime capturedAt;

    @Column(name = "failed_at")
    private LocalDateTime failedAt;

    @Column(name = "currency", length = 3)
    @Builder.Default
    private String currency = "INR";

    // ===== TIMESTAMPS =====

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Order lifecycle states with legal transition enforcement.
     *
     * Transitions:
     *   PENDING_PAYMENT → PAID             (online payment verified)
     *   PENDING_PAYMENT → CANCELLED       (user cancels before paying, stock restored)
     *   PENDING_PAYMENT → PAYMENT_FAILED  (payment attempt failed)
     *   PAID → PROCESSING                  (seller starts preparing order)
     *   PAID → CANCELLED                   (cancelled after payment, stock restored)
     *   PAID → REFUNDED                    (via refund process)
     *   PROCESSING → SHIPPED              (order shipped by seller)
     *   PROCESSING → CANCELLED            (cancelled during processing, stock restored)
     *   SHIPPED → DELIVERED               (order delivered)
     *   SHIPPED → REFUNDED                (via refund process)
     *   DELIVERED → REFUNDED               (via refund process)
     *
     * Terminal states: CANCELLED, REFUNDED, PAYMENT_FAILED
     */
    public enum OrderStatus {
        PENDING_PAYMENT,
        PAID,
        PROCESSING,
        SHIPPED,
        DELIVERED,
        CANCELLED,
        REFUNDED,
        PAYMENT_FAILED;

        // Legal transitions: source → set of allowed targets
        private static final Map<OrderStatus, Set<OrderStatus>> TRANSITIONS = Map.of(
            PENDING_PAYMENT, Set.of(PAID, CANCELLED, PAYMENT_FAILED),
            PAID, Set.of(PROCESSING, CANCELLED, REFUNDED),
            PROCESSING, Set.of(SHIPPED, CANCELLED),
            SHIPPED, Set.of(DELIVERED, REFUNDED),
            DELIVERED, Set.of(REFUNDED),
            CANCELLED, Set.of(),          // Terminal state
            REFUNDED, Set.of(),           // Terminal state
            PAYMENT_FAILED, Set.of()      // Terminal state
        );

        /**
         * Check if transitioning from this status to the target is legal.
         */
        public boolean canTransitionTo(OrderStatus target) {
            if (this == target) return true; // Idempotent: same status is always ok
            return TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
        }

        /**
         * Validate transition and throw if illegal.
         * @throws IllegalStateException if the transition is not allowed
         */
        public void validateTransitionTo(OrderStatus target) {
            if (!canTransitionTo(target)) {
                throw new IllegalStateException(
                    "Illegal order status transition: " + this + " → " + target);
            }
        }
    }

    /**
     * Payment-specific status, separate from order fulfillment status.
     * Tracks the payment lifecycle independently from order fulfillment.
     *
     * Legal transitions:
     *   PENDING → AUTHORIZED  (Razorpay auth without capture)
     *   PENDING → PAID        (direct capture or COD collected)
     *   PENDING → FAILED      (payment attempt failed)
     *   AUTHORIZED → PAID     (capture authorized payment)
     *   AUTHORIZED → FAILED   (authorization expired/cancelled)
     *   PAID → REFUND_PENDING (refund requested)
     *   REFUND_PENDING → REFUNDED (refund confirmed)
     *   REFUND_PENDING → PAID  (refund failed, stays PAID)
     *
     * Terminal states: FAILED, REFUNDED
     */
    public enum PaymentStatus {
        PENDING,
        AUTHORIZED,
        PAID,
        FAILED,
        REFUND_PENDING,
        REFUNDED;

        private static final Map<PaymentStatus, Set<PaymentStatus>> TRANSITIONS = Map.of(
            PENDING, Set.of(AUTHORIZED, PAID, FAILED),
            AUTHORIZED, Set.of(PAID, FAILED),
            PAID, Set.of(REFUND_PENDING),
            REFUND_PENDING, Set.of(REFUNDED, PAID),
            FAILED, Set.of(),       // Terminal
            REFUNDED, Set.of()      // Terminal
        );

        public boolean canTransitionTo(PaymentStatus target) {
            if (this == target) return true;
            return TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
        }

        public void validateTransitionTo(PaymentStatus target) {
            if (!canTransitionTo(target)) {
                throw new IllegalStateException(
                    "Illegal payment status transition: " + this + " → " + target);
            }
        }
    }
}
