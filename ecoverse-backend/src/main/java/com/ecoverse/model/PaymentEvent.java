package com.ecoverse.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Webhook idempotency + payment audit trail.
 * Each provider event is stored exactly once (UNIQUE on providerEventId).
 * Duplicate webhooks are detected and safely acknowledged.
 *
 * Stores safe metadata only — NEVER secrets, signatures, or tokens.
 */
@Entity
@Table(name = "payment_events", indexes = {
    @Index(name = "idx_payment_events_order_id", columnList = "order_id"),
    @Index(name = "idx_payment_events_event_type", columnList = "event_type")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Provider's unique event ID (e.g., Razorpay event ID: evt_xxx).
     * Used for idempotent webhook processing — if we've seen this ID before,
     * we skip processing and return success.
     */
    @Column(name = "provider_event_id", nullable = false, unique = true)
    private String providerEventId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "payment_attempt_id")
    private Long paymentAttemptId;

    /**
     * Safe metadata JSON payload from the webhook.
     * Contains order IDs, amounts, statuses — NO secrets or signatures.
     */
    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;

    @Column(name = "processed", nullable = false)
    @Builder.Default
    private Boolean processed = false;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
