-- ============================================================
-- V10: Carbon Engine — BigDecimal, Calculation Fields, Timezone
-- Phase D: Production Carbon Engine
-- ============================================================
-- Changes:
--   1. carbon_entries.co2: DOUBLE → NUMERIC(12,4)
--   2. New carbon_entries columns: activity_type, input_value, input_unit,
--      factor_id, factor_version, calculation_type, modifier_type,
--      modifier_value, user_timezone
--   3. carbon_entries.entry_date + created_at: TIMESTAMP → TIMESTAMPTZ
--   4. emission_factors.factor: DOUBLE → NUMERIC(10,6)
--   5. users.carbon_budget: DOUBLE → NUMERIC(8,2)
--   6. users.timezone: new column
-- ============================================================

-- ============================================================
-- 1. carbon_entries.co2: DOUBLE PRECISION → NUMERIC(12,4)
-- ============================================================
ALTER TABLE carbon_entries ALTER COLUMN co2 TYPE NUMERIC(12,4) USING co2::NUMERIC(12,4);

-- ============================================================
-- 2. New carbon_entries columns
-- ============================================================

-- Activity type: more specific sub-type (e.g. "car-petrol", "electricity")
ALTER TABLE carbon_entries ADD COLUMN activity_type VARCHAR(100);
-- Backfill activity_type from existing type column
UPDATE carbon_entries SET activity_type = type WHERE activity_type IS NULL;

-- Input value: the raw number the user entered (distance, kWh, meals, etc.)
ALTER TABLE carbon_entries ADD COLUMN input_value NUMERIC(12,4);

-- Input unit: the unit of the input (km, kWh, meal, kg, hr, etc.)
ALTER TABLE carbon_entries ADD COLUMN input_unit VARCHAR(20);

-- Factor ID: which emission factor row was used for this calculation
ALTER TABLE carbon_entries ADD COLUMN factor_id BIGINT;

-- Factor version: which version of the factor was used (for immutability)
ALTER TABLE carbon_entries ADD COLUMN factor_version INTEGER DEFAULT 1;

-- Calculation type: EMISSION, AVOIDED_EMISSION, or CREDIT
-- Existing entries: positive co2 = EMISSION, negative co2 = AVOIDED_EMISSION
ALTER TABLE carbon_entries ADD COLUMN calculation_type VARCHAR(20) DEFAULT 'EMISSION';

-- Backfill calculation_type from co2 sign
UPDATE carbon_entries SET calculation_type = 'AVOIDED_EMISSION' WHERE co2 < 0;

-- Make negative co2 values positive with AVOIDED_EMISSION type
-- (semantic: the CO2 value is now always non-negative; the type tells you direction)
UPDATE carbon_entries SET co2 = ABS(co2) WHERE co2 < 0;

-- Modifier type: e.g. "secondhand", "carpool"
ALTER TABLE carbon_entries ADD COLUMN modifier_type VARCHAR(50);

-- Modifier value: the multiplier applied (e.g. 0.5 for secondhand)
ALTER TABLE carbon_entries ADD COLUMN modifier_value NUMERIC(8,4);

-- User timezone at time of entry (IANA timezone string, e.g. "Asia/Kolkata")
ALTER TABLE carbon_entries ADD COLUMN user_timezone VARCHAR(50);

-- ============================================================
-- 3. carbon_entries timestamps: TIMESTAMP → TIMESTAMPTZ (UTC storage)
-- ============================================================
-- PostgreSQL allows ALTER COLUMN TYPE from TIMESTAMP to TIMESTAMPTZ
-- Existing data is treated as local time and converted to UTC with session timezone
-- We set timezone to UTC first so existing data is preserved correctly
SET timezone = 'UTC';

ALTER TABLE carbon_entries ALTER COLUMN entry_date TYPE TIMESTAMPTZ USING entry_date AT TIME ZONE 'UTC';
ALTER TABLE carbon_entries ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';

-- ============================================================
-- 4. emission_factors.factor: DOUBLE PRECISION → NUMERIC(10,6)
-- ============================================================
ALTER TABLE emission_factors ALTER COLUMN factor TYPE NUMERIC(10,6) USING factor::NUMERIC(10,6);

-- Make negative factors positive (AVOIDED_EMISSION semantics now handled by calculation_type)
-- "solar" was -0.05, "recycled" was -0.2, "composted" was -0.1
-- These will be marked as AVOIDED_EMISSION type in application logic
UPDATE emission_factors SET factor = ABS(factor) WHERE factor < 0;

-- ============================================================
-- 5. users.carbon_budget: DOUBLE PRECISION → NUMERIC(8,2)
-- ============================================================
ALTER TABLE users ALTER COLUMN carbon_budget TYPE NUMERIC(8,2) USING carbon_budget::NUMERIC(8,2);

-- ============================================================
-- 6. users.timezone: new column (default India for existing users)
-- ============================================================
ALTER TABLE users ADD COLUMN timezone VARCHAR(50) DEFAULT 'Asia/Kolkata';

-- ============================================================
-- 7. Foreign key: carbon_entries.factor_id → emission_factors.id
-- Not adding FK constraint here because factor_id may be NULL for
-- entries created before this migration. Application will enforce
-- that new entries always have a factor_id.
-- ============================================================

-- ============================================================
-- 8. Indexes for new columns
-- ============================================================
CREATE INDEX IF NOT EXISTS idx_carbon_entries_factor_id ON carbon_entries (factor_id);
CREATE INDEX IF NOT EXISTS idx_carbon_entries_calculation_type ON carbon_entries (user_id, calculation_type);

-- Reset session timezone
RESET timezone;
