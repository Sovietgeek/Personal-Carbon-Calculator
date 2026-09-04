-- ============================================================
-- V13: Product Inventory + Order Improvements
-- Phase 4: Production Shop + Orders + Inventory
-- ============================================================

-- ============================================================
-- PRODUCT: Add stock, status, and version columns
-- ============================================================

-- Stock quantity (default 0 for existing products, seed data updated below)
ALTER TABLE products ADD COLUMN stock INTEGER NOT NULL DEFAULT 0;

-- Product status enum (VARCHAR to match existing pattern — Java enum validates)
-- Valid values: DRAFT, ACTIVE, INACTIVE, OUT_OF_STOCK, ARCHIVED
ALTER TABLE products ADD COLUMN status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE';

-- Version for optimistic locking (prevents concurrent modification conflicts)
ALTER TABLE products ADD COLUMN version INTEGER NOT NULL DEFAULT 0;

-- Stock must be non-negative
ALTER TABLE products ADD CONSTRAINT chk_products_stock_non_negative CHECK (stock >= 0);

-- Status must be a valid enum value
ALTER TABLE products ADD CONSTRAINT chk_products_status_valid
    CHECK (status IN ('DRAFT', 'ACTIVE', 'INACTIVE', 'OUT_OF_STOCK', 'ARCHIVED'));

-- Index for status-based queries (shop listing filters by ACTIVE)
CREATE INDEX IF NOT EXISTS idx_products_status ON products (status);

-- Composite index for category + status (common shop query pattern)
CREATE INDEX IF NOT EXISTS idx_products_category_status ON products (category, status);

-- ============================================================
-- ORDER ITEM: Add unit_price column (purchase-time price snapshot)
-- The existing 'price' column is kept for backward compatibility.
-- 'unit_price' is the authoritative snapshot of the product price
-- at the time of purchase. Both are populated on order creation.
-- ============================================================

ALTER TABLE order_items ADD COLUMN unit_price NUMERIC(12,2);

-- Backfill unit_price from existing price column
UPDATE order_items SET unit_price = price WHERE unit_price IS NULL;

-- Make unit_price NOT NULL after backfill
ALTER TABLE order_items ALTER COLUMN unit_price SET NOT NULL;

-- ============================================================
-- ORDER: Add idempotency key for duplicate order prevention
-- ============================================================

ALTER TABLE orders ADD COLUMN idempotency_key VARCHAR(255);

-- Unique partial index: only enforces uniqueness for non-null keys
-- Null keys (orders placed without idempotency key) are allowed duplicates
CREATE UNIQUE INDEX IF NOT EXISTS idx_orders_idempotency_key
    ON orders (idempotency_key) WHERE idempotency_key IS NOT NULL;

-- ============================================================
-- CART: Add max quantity constraint
-- ============================================================

-- Cart item quantity must not exceed 100 (prevents absurd quantities)
ALTER TABLE cart_items ADD CONSTRAINT chk_cart_items_quantity_max CHECK (quantity <= 100);

-- ============================================================
-- SEED DATA: Update existing products with stock and status
-- V2 created 10 sample products with seller_id=1
-- Give them reasonable stock and ensure status is ACTIVE
-- ============================================================

UPDATE products SET stock = 50, status = 'ACTIVE' WHERE id <= 10 AND stock = 0;

-- ============================================================
-- ORDER STATUS: Update existing orders from PENDING/CONFIRMED
-- to PENDING_PAYMENT/PAID to match new OrderStatus enum.
-- This aligns existing data with the new status values.
-- ============================================================

-- PENDING → PENDING_PAYMENT (order created but payment not yet verified)
UPDATE orders SET status = 'PENDING_PAYMENT' WHERE status = 'PENDING';

-- CONFIRMED → PAID (payment was verified, order is being processed)
UPDATE orders SET status = 'PAID' WHERE status = 'CONFIRMED';

-- Add CHECK constraint for valid order statuses
ALTER TABLE orders ADD CONSTRAINT chk_orders_status_valid
    CHECK (status IN ('PENDING_PAYMENT', 'PAID', 'PROCESSING', 'SHIPPED',
                      'DELIVERED', 'CANCELLED', 'REFUNDED', 'PAYMENT_FAILED'));
