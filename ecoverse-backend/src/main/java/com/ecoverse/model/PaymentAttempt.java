package com.ecoverse.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Tracks each payment attempt per order.
 * One order can have multiple payment attempts (e.g., failed card → retry UPI).
 * Attempts are append-only — never overwritten.
 * Only the latest SUCCESS attempt matters for order state.
 */
@Entity
@Table(name = "payment_attempts", indexes = {
    @Index(name = "idx_payment_attempts_order_id", columnList = "order_id"),
    @Index(name = "idx_payment_attempts_provider_order_id", columnList = "provider_order_id"),
    @Index(name = "idx_payment_attempts_order_created", columnList = "order_id,created_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "provider", nullable = false, length = 50)
    @Builder.Default
    private String provider = "razorpay";

    @Column(name = "provider_order_id")
    private String providerOrderId;

    @Column(name = "provider_payment_id")
    private String providerPaymentId;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    @Builder.Default
    private String currency = "INR";

    /**
     * PENDING: Attempt created, awaiting payment
     * SUCCESS: Payment verified (signature or webhook confirmed)
     * FAILED:  Payment failed (signature invalid, payment declined, etc.)
     */
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "failure_reason")
    private String failureReason;

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
}
