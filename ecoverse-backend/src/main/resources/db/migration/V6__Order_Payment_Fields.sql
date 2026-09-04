-- ================================================================
-- ECOVERSE — V6: Order Payment Fields
-- Adds Razorpay integration fields and payment status tracking
-- ================================================================

-- Add Razorpay payment fields to orders table
ALTER TABLE orders ADD COLUMN IF NOT EXISTS razorpay_order_id VARCHAR(255);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS razorpay_payment_id VARCHAR(255);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS payment_status VARCHAR(50) DEFAULT 'PENDING';
ALTER TABLE orders ADD COLUMN IF NOT EXISTS payment_provider VARCHAR(50);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS payment_verified_at TIMESTAMP;

-- Create index on razorpay_order_id for fast lookups during webhook/verification
CREATE UNIQUE INDEX IF NOT EXISTS idx_order_razorpay_order_id ON orders (razorpay_order_id);

-- Extend order status enum to include new states
-- Note: PostgreSQL ENUM types are not easily altered. We use VARCHAR for status
-- which is already the case from V1 (VARCHAR(255)). The Java enum handles validation.
-- New valid values: PENDING, CONFIRMED, SHIPPED, DELIVERED, CANCELLED, REFUNDED, PAYMENT_FAILED
