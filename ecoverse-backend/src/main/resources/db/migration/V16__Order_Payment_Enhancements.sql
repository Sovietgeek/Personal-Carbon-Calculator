-- ================================================================
-- ECOVERSE — V16: Order Payment Enhancements
-- Adds missing payment fields to orders for full Razorpay lifecycle support.
-- ================================================================

-- Payment failure tracking
ALTER TABLE orders ADD COLUMN IF NOT EXISTS payment_failure_reason TEXT;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS failed_at TIMESTAMP;

-- Refund tracking
ALTER TABLE orders ADD COLUMN IF NOT EXISTS refunded_amount NUMERIC(12,2) DEFAULT 0;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS refund_id VARCHAR(255);

-- Capture tracking
ALTER TABLE orders ADD COLUMN IF NOT EXISTS captured_at TIMESTAMP;

-- Currency (for multi-currency future support)
ALTER TABLE orders ADD COLUMN IF NOT EXISTS currency VARCHAR(3) DEFAULT 'INR';

-- Ensure refunded_amount is non-negative
ALTER TABLE orders ADD CONSTRAINT chk_orders_refunded_amount CHECK (refunded_amount >= 0);

-- Update existing payment_status CHECK to include AUTHORIZED
-- V6 created payment_status as VARCHAR, validated in Java enum.
-- AUTHORIZED is added to the Java PaymentStatus enum in this phase.
-- No SQL migration needed for VARCHAR columns — the Java enum enforces values.

-- Index for finding expired pending payments (payment expiry scheduler)
CREATE INDEX IF NOT EXISTS idx_orders_pending_payment_expiry
    ON orders(created_at) WHERE status = 'PENDING_PAYMENT';

-- Index for refund lookups
CREATE INDEX IF NOT EXISTS idx_orders_refund_id ON orders(refund_id) WHERE refund_id IS NOT NULL;
