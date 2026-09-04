-- ================================================================
-- ECOVERSE — V17: Fix Refresh Token Column Nullable
--
-- V4 created refresh_tokens.token as NOT NULL UNIQUE.
-- V9 added token_hash as the primary lookup field and dropped
-- the UNIQUE constraint on token, but never altered the column
-- to be nullable. New code only sets token_hash, leaving token
-- null, which violates the NOT NULL constraint and breaks login.
-- ================================================================

ALTER TABLE refresh_tokens ALTER COLUMN token DROP NOT NULL;