-- ================================================================
-- ECOVERSE — V5: Audit Logs Table
-- Security audit trail — who did what, when
-- ================================================================

CREATE TABLE audit_logs (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT,
    action      VARCHAR(100) NOT NULL,
    resource    VARCHAR(500),
    ip_address  VARCHAR(50),
    user_agent  VARCHAR(500),
    details     TEXT,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_log_user_id ON audit_logs (user_id);
CREATE INDEX idx_audit_log_action ON audit_logs (action);
CREATE INDEX idx_audit_log_created_at ON audit_logs (created_at);
