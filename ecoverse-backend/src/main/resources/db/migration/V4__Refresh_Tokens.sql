-- ================================================================
-- ECOVERSE — V4: Refresh Tokens Table
-- For JWT refresh token rotation
-- ================================================================

CREATE TABLE refresh_tokens (
    id              BIGSERIAL PRIMARY KEY,
    token           VARCHAR(255) NOT NULL UNIQUE,
    user_id         BIGINT NOT NULL,
    expiry_date     TIMESTAMP NOT NULL,
    revoked         BOOLEAN DEFAULT FALSE,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_refresh_token_token ON refresh_tokens (token);
CREATE INDEX idx_refresh_token_user_id ON refresh_tokens (user_id);
