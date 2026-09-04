-- ================================================================
-- ECOVERSE — V9: Refresh Token Hash Storage
-- Stores SHA-256 hash of refresh tokens instead of plaintext.
-- This prevents an attacker with DB access from impersonating users.
-- ================================================================

-- Enable pgcrypto extension for digest() function
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Add token_hash column (nullable initially for backfill)
ALTER TABLE refresh_tokens ADD COLUMN IF NOT EXISTS token_hash VARCHAR(64);

-- Backfill: hash existing plaintext token values
-- SHA-256 produces exactly 64 hex characters
UPDATE refresh_tokens SET token_hash = encode(digest(token, 'SHA-256'), 'hex')
WHERE token_hash IS NULL;

-- Now make token_hash NOT NULL and UNIQUE
ALTER TABLE refresh_tokens ALTER COLUMN token_hash SET NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS idx_refresh_tokens_token_hash ON refresh_tokens (token_hash);

-- Add last_used_at for audit tracking
ALTER TABLE refresh_tokens ADD COLUMN IF NOT EXISTS last_used_at TIMESTAMP;

-- Drop old unique constraint on plaintext token column
-- We keep the token column for transition compatibility (code reads it during backfill)
-- but it will no longer be used for lookups.
ALTER TABLE refresh_tokens DROP CONSTRAINT IF EXISTS refresh_tokens_token_key;

-- Add index on user_id for token revocation queries
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_id ON refresh_tokens (user_id);
