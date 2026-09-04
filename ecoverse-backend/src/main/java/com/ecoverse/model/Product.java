package com.ecoverse.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "products")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "eco_rating")
    private Integer ecoRating;

    @Column(name = "is_secondhand")
    private Boolean isSecondhand;

    /** Brand / manufacturer name (e.g. "EcoSoul", "Bamboo India") */
    @Column(length = 100)
    private String brand;

    /** Original MRP before discount. Null = no discount shown. */
    @Column(name = "mrp", precision = 12, scale = 2)
    private BigDecimal mrp;

    /** Discount percentage (0-99). Computed from mrp vs price if null. */
    @Column(name = "discount_percent")
    private Integer discountPercent;

    /** JSON array of feature strings (e.g. ["100% Organic","BPA Free"]) */
    @Column(name = "features", columnDefinition = "TEXT")
    private String features;

    /** Comma-separated highlights for product card (e.g. "Free Delivery,Top Rated") */
    @Column(name = "highlights", length = 500)
    private String highlights;

    /** Comma-separated tags for search (e.g. "bamboo,toothbrush,eco,plastic-free") */
    @Column(name = "tags", length = 500)
    private String tags;

    /** Average customer rating 1.0-5.0 */
    @Column(name = "rating", precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal rating = BigDecimal.valueOf(4.0);

    /** Number of customer ratings */
    @Column(name = "rating_count")
    @Builder.Default
    private Integer ratingCount = 0;

    /** Estimated delivery days */
    @Column(name = "delivery_days")
    @Builder.Default
    private Integer deliveryDays = 5;

    /** Product weight in grams */
    @Column(name = "weight_grams")
    private Integer weightGrams;

    /**
     * @deprecated Use {@link #status} instead. Kept for backward compatibility
     * with existing queries and V8 FK constraints.
     * Derived from status: isAvailable = (status == ACTIVE)
     */
    @Deprecated
    @Builder.Default
    @Column(name = "is_available")
    private Boolean isAvailable = true;

    /**
     * Inventory stock quantity. Must be >= 0.
     * When stock reaches 0, the system auto-sets status to OUT_OF_STOCK.
     */
    @Builder.Default
    @Column(nullable = false)
    private Integer stock = 0;

    /**
     * Product lifecycle status. Replaces the boolean isAvailable.
     * Only ACTIVE products are visible in the shop and purchasable.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private ProductStatus status = ProductStatus.ACTIVE;

    /**
     * Optimistic locking version. Incremented automatically by JPA on each update.
     * Prevents lost updates when two transactions modify the same product concurrently.
     * Used alongside atomic stock decrement for concurrency safety.
     */
    @Version
    @Column(nullable = false)
    @Builder.Default
    private Integer version = 0;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        // Sync isAvailable with status on creation
        this.isAvailable = (this.status == ProductStatus.ACTIVE);
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
        // Keep isAvailable in sync with status on every update
        this.isAvailable = (this.status == ProductStatus.ACTIVE);
    }

    /**
     * Check if this product is purchasable.
     * A product is purchasable only if it is ACTIVE and has stock > 0.
     */
    public boolean isPurchasable() {
        return this.status == ProductStatus.ACTIVE && this.stock != null && this.stock > 0;
    }
}
