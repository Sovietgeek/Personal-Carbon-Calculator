-- ============================================================
-- V8: BigDecimal for Money, Foreign Keys, Constraints, Indexes
-- Phase B: Data Integrity & Foundation
-- ============================================================

-- ============================================================
-- B5: Change monetary columns from DOUBLE PRECISION to NUMERIC(12,2)
-- This prevents floating-point rounding errors in financial data.
-- Existing data is preserved; values are implicitly cast.
-- ============================================================

ALTER TABLE products ALTER COLUMN price TYPE NUMERIC(12,2) USING price::NUMERIC(12,2);
ALTER TABLE orders ALTER COLUMN total_price TYPE NUMERIC(12,2) USING total_price::NUMERIC(12,2);
ALTER TABLE order_items ALTER COLUMN price TYPE NUMERIC(12,2) USING price::NUMERIC(12,2);

-- Add CHECK constraints: monetary values must be non-negative
ALTER TABLE products ADD CONSTRAINT chk_products_price_positive CHECK (price >= 0);
ALTER TABLE orders ADD CONSTRAINT chk_orders_total_price_positive CHECK (total_price >= 0);
ALTER TABLE order_items ADD CONSTRAINT chk_order_items_price_positive CHECK (price >= 0);

-- ============================================================
-- B7: Business Constraints
-- ============================================================

-- Quantity must be positive
ALTER TABLE order_items ADD CONSTRAINT chk_order_items_quantity_positive CHECK (quantity > 0);
ALTER TABLE cart_items ADD CONSTRAINT chk_cart_items_quantity_positive CHECK (quantity > 0);

-- CO2 values should be reasonable (allow negative for "savings")
-- No constraint on carbon_entries.co2 — negative values are valid (carbon savings)

-- Email uniqueness already exists via unique constraint on users.email
-- Refresh token uniqueness already exists via unique constraint on refresh_tokens.token

-- Razorpay order ID uniqueness already exists via unique index from V6

-- Products must have a name and category (already NOT NULL in V1)

-- Eco rating between 1 and 5 if set
ALTER TABLE products ADD CONSTRAINT chk_products_eco_rating_range CHECK (eco_rating IS NULL OR (eco_rating >= 1 AND eco_rating <= 5));

-- ============================================================
-- B6: Foreign Keys
-- 
-- DELETE STRATEGY for each FK:
-- - user_id FKs: ON DELETE CASCADE — when a user is deleted, all their data goes too
--   (The ProfileController.deleteAccount() already deletes by userId)
-- - product_id FKs: ON DELETE RESTRICT — don't delete products that are in active orders/carts
-- - order_id FK: ON DELETE CASCADE — order items are part of the order
-- - achievement_id FK: ON DELETE CASCADE — achievements unlocked for a deleted achievement go too
--
-- ORPHAN CLEANUP:
-- Before adding FKs, we MUST remove orphaned records that reference non-existent parents.
-- In a fresh DB, the V2 seed data creates products with seller_id=1 but no user with id=1 exists.
-- In existing DBs, users may have been deleted manually leaving orphaned records.
-- These orphaned records are demo/seed data or dangling references — safe to remove.
-- ============================================================

-- STEP 1: Remove orphaned records that would prevent FK creation
-- Products whose seller doesn't exist (seed data with seller_id=1 on fresh DB)
DELETE FROM order_items WHERE product_id IN (SELECT id FROM products WHERE seller_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM users WHERE id = products.seller_id));
DELETE FROM cart_items WHERE product_id IN (SELECT id FROM products WHERE seller_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM users WHERE id = products.seller_id));
DELETE FROM products WHERE seller_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM users WHERE id = products.seller_id);

-- Carbon entries whose user doesn't exist
DELETE FROM carbon_entries WHERE user_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM users WHERE id = carbon_entries.user_id);

-- Health logs whose user doesn't exist
DELETE FROM health_logs WHERE user_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM users WHERE id = health_logs.user_id);

-- Notes whose user doesn't exist
DELETE FROM notes WHERE user_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM users WHERE id = notes.user_id);

-- Cart items whose user doesn't exist
DELETE FROM cart_items WHERE user_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM users WHERE id = cart_items.user_id);

-- Orders whose user doesn't exist (and their items)
DELETE FROM order_items WHERE order_id IN (SELECT id FROM orders WHERE user_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM users WHERE id = orders.user_id));
DELETE FROM orders WHERE user_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM users WHERE id = orders.user_id);

-- User achievements whose user or achievement doesn't exist
DELETE FROM user_achievements WHERE user_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM users WHERE id = user_achievements.user_id);
DELETE FROM user_achievements WHERE achievement_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM achievements WHERE id = user_achievements.achievement_id);

-- Refresh tokens whose user doesn't exist
DELETE FROM refresh_tokens WHERE user_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM users WHERE id = refresh_tokens.user_id);

-- Audit logs whose user doesn't exist (SET NULL instead of delete, but clean up for FK creation)
UPDATE audit_logs SET user_id = NULL WHERE user_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM users WHERE id = audit_logs.user_id);

-- Carbon entries → users
ALTER TABLE carbon_entries ADD CONSTRAINT fk_carbon_entries_user_id
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

-- Health logs → users
ALTER TABLE health_logs ADD CONSTRAINT fk_health_logs_user_id
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

-- Notes → users
ALTER TABLE notes ADD CONSTRAINT fk_notes_user_id
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

-- Cart items → users
ALTER TABLE cart_items ADD CONSTRAINT fk_cart_items_user_id
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

-- Cart items → products (restrict: can't delete a product that's in someone's cart)
ALTER TABLE cart_items ADD CONSTRAINT fk_cart_items_product_id
    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE RESTRICT;

-- Orders → users
ALTER TABLE orders ADD CONSTRAINT fk_orders_user_id
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

-- Order items → orders (cascade: order items are part of the order)
ALTER TABLE order_items ADD CONSTRAINT fk_order_items_order_id
    FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE;

-- Order items → products (restrict: can't delete a product that's in an order)
ALTER TABLE order_items ADD CONSTRAINT fk_order_items_product_id
    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE RESTRICT;

-- Products → users (seller_id, cascade: delete seller's products when seller deleted)
ALTER TABLE products ADD CONSTRAINT fk_products_seller_id
    FOREIGN KEY (seller_id) REFERENCES users(id) ON DELETE CASCADE;

-- User achievements → users
ALTER TABLE user_achievements ADD CONSTRAINT fk_user_achievements_user_id
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

-- User achievements → achievements
ALTER TABLE user_achievements ADD CONSTRAINT fk_user_achievements_achievement_id
    FOREIGN KEY (achievement_id) REFERENCES achievements(id) ON DELETE CASCADE;

-- Refresh tokens → users
-- V4 already created fk_refresh_token_user on this column; drop it first to avoid duplicate
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_name = 'fk_refresh_token_user' AND table_name = 'refresh_tokens') THEN
        ALTER TABLE refresh_tokens DROP CONSTRAINT fk_refresh_token_user;
    END IF;
END $$;
ALTER TABLE refresh_tokens ADD CONSTRAINT fk_refresh_tokens_user_id
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

-- Audit logs → users (SET NULL: keep audit trail even if user is deleted)
ALTER TABLE audit_logs ADD CONSTRAINT fk_audit_logs_user_id
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL;

-- ============================================================
-- B8: Performance Indexes
-- Based on actual query patterns found in repositories
-- ============================================================

-- Carbon entries: queried by (user_id, entry_date) and (user_id, category)
CREATE INDEX IF NOT EXISTS idx_carbon_entries_user_date ON carbon_entries (user_id, entry_date);
CREATE INDEX IF NOT EXISTS idx_carbon_entries_user_category ON carbon_entries (user_id, category);

-- Health logs: queried by (user_id, entry_date) and (user_id, type)
CREATE INDEX IF NOT EXISTS idx_health_logs_user_date ON health_logs (user_id, entry_date);
CREATE INDEX IF NOT EXISTS idx_health_logs_user_type ON health_logs (user_id, type);

-- Products: queried by (category, is_available) and by seller_id
CREATE INDEX IF NOT EXISTS idx_products_category_available ON products (category, is_available);
CREATE INDEX IF NOT EXISTS idx_products_seller_id ON products (seller_id);

-- Orders: queried by (user_id, created_at)
CREATE INDEX IF NOT EXISTS idx_orders_user_created ON orders (user_id, created_at DESC);

-- Audit logs: queried by (action, created_at) and by user_id
CREATE INDEX IF NOT EXISTS idx_audit_logs_action_date ON audit_logs (action, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_logs_user_id ON audit_logs (user_id);

-- Notes: queried by user_id (already has index from entity)
CREATE INDEX IF NOT EXISTS idx_notes_user_id ON notes (user_id);

-- Cart items: queried by user_id (already has index from entity)
CREATE INDEX IF NOT EXISTS idx_cart_items_product_id ON cart_items (product_id);

-- Order items: FK index on product_id for ON DELETE RESTRICT lookups
CREATE INDEX IF NOT EXISTS idx_order_items_product_id ON order_items (product_id);
