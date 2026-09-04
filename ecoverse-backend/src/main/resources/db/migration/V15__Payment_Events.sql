-- ================================================================
-- ECOVERSE — V15: Payment Events
-- Webhook idempotency + payment audit trail.
-- Each provider event is stored exactly once (UNIQUE on provider_event_id).
-- Duplicate webhooks are detected and safely acknowledged.
-- ================================================================

CREATE TABLE IF NOT EXISTS payment_events (
    id                  BIGSERIAL PRIMARY KEY,
    provider_event_id   VARCHAR(255) NOT NULL,
    event_type          VARCHAR(100) NOT NULL,
    order_id            BIGINT,
    payment_attempt_id  BIGINT,
    payload             TEXT,
    processed           BOOLEAN      NOT NULL DEFAULT FALSE,
    processed_at        TIMESTAMP,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_payment_event_order FOREIGN KEY (order_id)
        REFERENCES orders(id) ON DELETE SET NULL,
    CONSTRAINT fk_payment_event_attempt FOREIGN KEY (payment_attempt_id)
        REFERENCES payment_attempts(id) ON DELETE SET NULL
);

-- Unique provider event ID ensures idempotent webhook processing
CREATE UNIQUE INDEX idx_payment_events_provider_event_id ON payment_events(provider_event_id);

-- Index for looking up events by order
CREATE INDEX idx_payment_events_order_id ON payment_events(order_id);

-- Index for looking up events by type
CREATE INDEX idx_payment_events_event_type ON payment_events(event_type);

-- Index for finding unprocessed events
CREATE INDEX idx_payment_events_processed ON payment_events(processed) WHERE NOT processed;
