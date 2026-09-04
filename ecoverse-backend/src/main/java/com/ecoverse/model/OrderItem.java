package com.ecoverse.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "order_items", indexes = {
    @Index(name = "idx_order_item_order_id", columnList = "order_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(nullable = false)
    private Integer quantity;

    /**
     * @deprecated Use {@link #unitPrice} instead. Kept for backward compatibility
     * with existing queries and frontend. Both price and unitPrice are populated
     * on order creation with the same value.
     */
    @Deprecated
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    /**
     * Purchase-time unit price snapshot. This is the authoritative price
     * at the time of purchase. Historical orders MUST NOT change when the
     * product price changes later.
     */
    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
