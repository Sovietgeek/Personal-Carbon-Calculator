package com.ecoverse.model;

/**
 * Product lifecycle states.
 *
 * DRAFT:       Created by seller but not yet visible in shop
 * ACTIVE:      Visible and purchasable in the shop
 * INACTIVE:    Temporarily hidden by seller (not deleted)
 * OUT_OF_STOCK: Stock depleted — auto-set by system when stock reaches 0
 * ARCHIVED:    Permanently retired by seller (soft delete)
 *
 * Transitions:
 *   DRAFT → ACTIVE (seller publishes)
 *   ACTIVE → INACTIVE (seller pauses)
 *   ACTIVE → OUT_OF_STOCK (system auto-sets when stock=0)
 *   INACTIVE → ACTIVE (seller reactivates)
 *   OUT_OF_STOCK → ACTIVE (seller restocks — stock > 0)
 *   Any → ARCHIVED (seller retires product)
 *
 * Security: Only the seller who owns the product can change its status.
 * OUT_OF_STOCK is set automatically by the system when stock reaches 0.
 */
public enum ProductStatus {
    DRAFT,
    ACTIVE,
    INACTIVE,
    OUT_OF_STOCK,
    ARCHIVED
}
