-- ================================================================
-- ECOVERSE — V14: Payment Attempts
-- Tracks each payment attempt per order (supports retry).
-- One order can have multiple payment attempts (e.g., failed card → retry UPI).
-- ================================================================

CREATE TABLE IF NOT EXISTS payment_attempts (
    id              BIGSERIAL PRIMARY KEY,
    order_id        BIGINT       NOT NULL,
    provider        VARCHAR(50)  NOT NULL DEFAULT 'razorpay',
    provider_order_id   VARCHAR(255),
    provider_payment_id VARCHAR(255),
    amount          NUMERIC(12,2) NOT NULL,
    currency        VARCHAR(3)   NOT NULL DEFAULT 'INR',
    status          VARCHAR(30)  NOT NULL DEFAULT 'PENDING',
    failure_reason  TEXT,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_payment_attempt_order FOREIGN KEY (order_id)
        REFERENCES orders(id) ON DELETE CASCADE,
    CONSTRAINT chk_payment_attempt_status CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED')),
    CONSTRAINT chk_payment_attempt_amount CHECK (amount > 0)
);

-- Index for looking up attempts by order
CREATE INDEX idx_payment_attempts_order_id ON payment_attempts(order_id);

-- Unique provider order ID prevents duplicate Razorpay orders
CREATE UNIQUE INDEX idx_payment_attempts_provider_order_id ON payment_attempts(provider_order_id);

-- Index for finding the latest attempt for an order
CREATE INDEX idx_payment_attempts_order_created ON payment_attempts(order_id, created_at DESC);
