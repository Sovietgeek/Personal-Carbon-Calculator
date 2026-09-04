-- ================================================================
-- V12: Dashboard Performance — Composite Indexes + Health Logs Instant
-- ================================================================
-- Phase E migration: Performance indexes, health_logs TIMESTAMPTZ conversion
--
-- Changes:
--   1. Composite indexes for dashboard query performance
--   2. health_logs.entry_date: TIMESTAMP → TIMESTAMPTZ (timezone-aware)
--   3. health_logs.created_at: TIMESTAMP → TIMESTAMPTZ
--   4. health_logs.user_timezone: new column for per-entry timezone
-- ================================================================

-- 1. Composite indexes for carbon_entries (dashboard aggregates, streak, trend)
CREATE INDEX IF NOT EXISTS idx_carbon_user_date ON carbon_entries(user_id, entry_date);
CREATE INDEX IF NOT EXISTS idx_carbon_user_date_type ON carbon_entries(user_id, entry_date, calculation_type);

-- 2. Composite index for health_logs (dashboard aggregates, streak)
CREATE INDEX IF NOT EXISTS idx_health_user_date ON health_logs(user_id, entry_date);

-- 3. Convert health_logs.entry_date from TIMESTAMP to TIMESTAMPTZ
-- Existing data assumed to be in Asia/Kolkata (IST, UTC+5:30)
ALTER TABLE health_logs ALTER COLUMN entry_date TYPE TIMESTAMPTZ
    USING entry_date AT TIME ZONE 'Asia/Kolkata';

-- 4. Convert health_logs.created_at from TIMESTAMP to TIMESTAMPTZ
ALTER TABLE health_logs ALTER COLUMN created_at TYPE TIMESTAMPTZ
    USING created_at AT TIME ZONE 'Asia/Kolkata';

-- 5. Add user_timezone column to health_logs for per-entry timezone tracking
ALTER TABLE health_logs ADD COLUMN IF NOT EXISTS user_timezone VARCHAR(50) DEFAULT 'Asia/Kolkata';

-- 6. Drop legacy single-column indexes if they exist (composite indexes are superior)
-- These may or may not exist depending on prior migrations; IF EXISTS handles both cases
DROP INDEX IF EXISTS idx_health_log_user_id;
