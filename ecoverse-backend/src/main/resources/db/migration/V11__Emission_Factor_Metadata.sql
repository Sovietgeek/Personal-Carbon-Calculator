-- ============================================================
-- V11: Emission Factor Metadata, Verification, Product-Based Shopping
-- Phase D: Production Carbon Engine
-- ============================================================
-- Changes:
--   1. New emission_factors columns: source_name, source_url,
--      verification_date, version, active, effective_from,
--      effective_to, region, uncertainty, verification_status, input_unit
--   2. Backfill metadata for existing factors with documented sources
--   3. Deactivate old currency-based shopping factors (active=false)
--   4. Insert product-based shopping factors
--   5. Insert reference benchmarks (India avg, Global avg, Tree absorption)
--   6. Update unique constraint to include version for factor versioning
-- ============================================================

-- ============================================================
-- 1. New emission_factors columns
-- ============================================================

ALTER TABLE emission_factors ADD COLUMN source_name VARCHAR(255);
ALTER TABLE emission_factors ADD COLUMN source_url TEXT;
ALTER TABLE emission_factors ADD COLUMN verification_date DATE;
ALTER TABLE emission_factors ADD COLUMN version INTEGER DEFAULT 1;
ALTER TABLE emission_factors ADD COLUMN active BOOLEAN DEFAULT true;
ALTER TABLE emission_factors ADD COLUMN effective_from DATE;
ALTER TABLE emission_factors ADD COLUMN effective_to DATE;
ALTER TABLE emission_factors ADD COLUMN region VARCHAR(100) DEFAULT 'IN';
ALTER TABLE emission_factors ADD COLUMN uncertainty VARCHAR(255);
ALTER TABLE emission_factors ADD COLUMN verification_status VARCHAR(20) DEFAULT 'NOT_VERIFIED';
ALTER TABLE emission_factors ADD COLUMN input_unit VARCHAR(20);

-- Set effective_from for all existing factors to their creation date
UPDATE emission_factors SET effective_from = '2024-01-01' WHERE effective_from IS NULL;

-- ============================================================
-- 2. Backfill metadata for existing factors with documented sources
-- ============================================================

-- TRANSPORT FACTORS
-- Source: Indian Central Electricity Authority / IPCC AR6 / DEFRA 2023
-- Car petrol 0.21 kg/km: based on average Indian passenger car (~140 gCO2/km per passenger)
-- These are ESTIMATED values for Indian conditions, not peer-reviewed per-type
UPDATE emission_factors SET
    source_name = 'IPCC AR6 + MoRTH India averages (estimated)',
    source_url = 'https://www.ipcc-nggip.iges.or.jp/public/2019rf/',
    verification_status = 'ESTIMATED',
    uncertainty = '±30% (varies by vehicle age, load, driving conditions)',
    input_unit = 'km',
    region = 'IN'
WHERE category = 'transport';

-- ENERGY FACTORS
-- Electricity 0.82 kg/kWh: India grid emission factor (CEA 2022-23)
-- Natural gas 2.0 kg/m³: approximate
-- LPG 2.98 kg/kg: approximate
-- Diesel generator 0.9 kg/kWh: approximate
-- Solar: now positive (AVOIDED_EMISSION handled by calculation_type)
UPDATE emission_factors SET
    source_name = 'CEA India Grid Emission Factor 2022-23 + DEFRA conversion factors (estimated)',
    source_url = 'https://cea.nic.in/wp-content/uploads/2023/04/CO2_Base_Line_User_Guide_Book_2022_23.pdf',
    verification_status = 'ESTIMATED',
    uncertainty = '±25% (grid mix varies by state and season)',
    input_unit = 'kWh',
    region = 'IN'
WHERE category = 'energy' AND type = 'electricity';

UPDATE emission_factors SET
    source_name = 'Approximate (estimated)',
    verification_status = 'NOT_VERIFIED',
    uncertainty = '±40%',
    input_unit = 'm³',
    region = 'IN'
WHERE category = 'energy' AND type = 'natural-gas';

UPDATE emission_factors SET
    source_name = 'Approximate (estimated)',
    verification_status = 'NOT_VERIFIED',
    uncertainty = '±40%',
    input_unit = 'kg',
    region = 'IN'
WHERE category = 'energy' AND type = 'lpg';

UPDATE emission_factors SET
    source_name = 'Approximate (estimated)',
    verification_status = 'NOT_VERIFIED',
    uncertainty = '±40%',
    input_unit = 'kWh',
    region = 'IN'
WHERE category = 'energy' AND type = 'diesel-generator';

UPDATE emission_factors SET
    source_name = 'Approximate avoided emission (estimated)',
    verification_status = 'NOT_VERIFIED',
    uncertainty = '±50% (depends on grid displaced)',
    input_unit = 'kWh',
    region = 'IN'
WHERE category = 'energy' AND type = 'solar';

-- FOOD FACTORS
-- Source: Poore & Nemecek (2018) Science, "Reducing food's environmental impacts"
-- These are per-meal estimates, not per-kg-of-food
UPDATE emission_factors SET
    source_name = 'Poore & Nemecek 2018 (Science) — per-meal Indian estimates (estimated)',
    source_url = 'https://doi.org/10.1126/science.aaq0216',
    verification_status = 'ESTIMATED',
    uncertainty = '±50% (varies by source, preparation, portion)',
    input_unit = 'meal',
    region = 'IN'
WHERE category = 'food';

-- WASTE FACTORS
-- Source: IPCC Guidelines + Indian MSW characteristics
-- Landfill 2.5 kg/kg: methane emissions from organic waste decomposition
-- Recycled: avoided emission (now positive value, AVOIDED_EMISSION type)
-- Composted: avoided emission (now positive value, AVOIDED_EMISSION type)
UPDATE emission_factors SET
    source_name = 'IPCC 2006 Guidelines + Indian CPCB data (estimated)',
    source_url = 'https://www.ipcc-nggip.iges.or.jp/public/2006gl/',
    verification_status = 'ESTIMATED',
    uncertainty = '±50% (varies by waste composition, landfill practices)',
    input_unit = 'kg',
    region = 'IN'
WHERE category = 'waste' AND type = 'landfill';

UPDATE emission_factors SET
    source_name = 'Estimated avoided emission vs landfill',
    verification_status = 'NOT_VERIFIED',
    uncertainty = '±60%',
    input_unit = 'kg',
    region = 'IN'
WHERE category = 'waste' AND type = 'recycled';

UPDATE emission_factors SET
    source_name = 'Estimated avoided emission vs landfill',
    verification_status = 'NOT_VERIFIED',
    uncertainty = '±60%',
    input_unit = 'kg',
    region = 'IN'
WHERE category = 'waste' AND type = 'composted';

UPDATE emission_factors SET
    source_name = 'Estimated (open burning in Indian conditions)',
    verification_status = 'NOT_VERIFIED',
    uncertainty = '±50%',
    input_unit = 'kg',
    region = 'IN'
WHERE category = 'waste' AND type = 'incinerated';

UPDATE emission_factors SET
    source_name = 'Estimated (informal e-waste recycling in India)',
    verification_status = 'NOT_VERIFIED',
    uncertainty = '±60%',
    input_unit = 'kg',
    region = 'IN'
WHERE category = 'waste' AND type = 'e-waste';

-- DIGITAL FACTORS
-- Source: The Shift Project + IEA data center estimates
UPDATE emission_factors SET
    source_name = 'The Shift Project 2019 + IEA data (estimated)',
    source_url = 'https://theshiftproject.org/en/article/lean-ict-why-ict-must-contribute-to-the-1-5c-trajectory/',
    verification_status = 'ESTIMATED',
    uncertainty = '±40% (varies by grid, device, bitrate)',
    input_unit = 'hr',
    region = 'IN'
WHERE category = 'digital' AND type IN ('streaming-hd', 'streaming-4k', 'video-call', 'web-browsing', 'gaming-online');

UPDATE emission_factors SET
    source_name = 'The Shift Project 2019 (estimated)',
    verification_status = 'ESTIMATED',
    uncertainty = '±40%',
    input_unit = 'GB',
    region = 'IN'
WHERE category = 'digital' AND type = 'cloud-storage';

UPDATE emission_factors SET
    source_name = 'de Vries 2018 + Digiconomist (estimated)',
    source_url = 'https://doi.org/10.1016/j.joule.2018.07.013',
    verification_status = 'ESTIMATED',
    uncertainty = '±100% (varies enormously by cryptocurrency and consensus mechanism)',
    input_unit = 'txn',
    region = 'IN'
WHERE category = 'digital' AND type = 'crypto-transaction';

UPDATE emission_factors SET
    source_name = 'McAfee/IEA carbon footprint of email (estimated)',
    verification_status = 'NOT_VERIFIED',
    uncertainty = '±60% (varies by attachment size, spam filtering)',
    input_unit = '100 emails',
    region = 'IN'
WHERE category = 'digital' AND type = 'email';

UPDATE emission_factors SET
    source_name = 'Estimated from LLM training energy (estimated)',
    verification_status = 'NOT_VERIFIED',
    uncertainty = '±80% (varies by model, hardware, data center)',
    input_unit = 'query',
    region = 'IN'
WHERE category = 'digital' AND type = 'ai-query';

-- ============================================================
-- 3. Deactivate old currency-based shopping factors
-- These used kg/₹ (per Rupee) which is scientifically indefensible
-- ============================================================
UPDATE emission_factors SET active = false, effective_to = CURRENT_DATE
WHERE category = 'shopping' AND type IN ('clothing', 'electronics', 'furniture', 'books', 'beauty', 'sports', 'other');

-- ============================================================
-- 4. Insert product-based shopping factors
-- These use kg CO2 per kg of product or per item
-- Sources: MIT Material Flows, DEFRA, various LCA studies
-- All marked ESTIMATED or NOT_VERIFIED — no peer-reviewed India-specific data exists
-- ============================================================

INSERT INTO emission_factors (category, type, factor, unit, source_name, source_url, verification_status, uncertainty, input_unit, region, version, active, effective_from)
VALUES
-- Per-kg factors (kg CO2 per kg of product purchased)
('shopping', 'clothing-kg', 15.0, 'kg CO2/kg', 'Various LCA studies (estimated)', NULL, 'ESTIMATED', '±50% (varies by material: cotton vs polyester, fast fashion vs sustainable)', 'kg', 'IN', 1, true, CURRENT_DATE),
('shopping', 'electronics-item', 80.0, 'kg CO2/item', 'Apple Environmental Report 2022 + UNU study (estimated)', 'https://www.apple.com/environment/', 'ESTIMATED', '±80% (varies enormously by device type: phone vs laptop vs TV)', 'item', 'IN', 1, true, CURRENT_DATE),
('shopping', 'furniture-kg', 2.5, 'kg CO2/kg', 'DEFRA 2023 conversion factors (estimated)', 'https://www.gov.uk/government/collections/government-conversion-factors-for-company-reporting', 'ESTIMATED', '±60% (varies by material: wood vs MDF vs metal)', 'kg', 'IN', 1, true, CURRENT_DATE),
('shopping', 'books-kg', 1.2, 'kg CO2/kg', 'Various LCA studies (estimated)', NULL, 'NOT_VERIFIED', '±50%', 'kg', 'IN', 1, true, CURRENT_DATE),
('shopping', 'beauty-kg', 5.0, 'kg CO2/kg', 'Various LCA studies (estimated)', NULL, 'NOT_VERIFIED', '±60%', 'kg', 'IN', 1, true, CURRENT_DATE),
('shopping', 'sports-kg', 8.0, 'kg CO2/kg', 'Various LCA studies (estimated)', NULL, 'NOT_VERIFIED', '±60%', 'kg', 'IN', 1, true, CURRENT_DATE),
('shopping', 'other-kg', 5.0, 'kg CO2/kg', 'Default average for mixed consumer goods (estimated)', NULL, 'NOT_VERIFIED', '±70% (catch-all category)', 'kg', 'IN', 1, true, CURRENT_DATE)
ON CONFLICT (category, type) DO NOTHING;

-- ============================================================
-- 5. Insert reference benchmarks
-- These are used for risk assessment and comparisons
-- Stored as emission factors with special category "_benchmark"
-- ============================================================

INSERT INTO emission_factors (category, type, factor, unit, source_name, source_url, verification_status, uncertainty, input_unit, region, version, active, effective_from)
VALUES
('_benchmark', 'india-daily-average', 4.2, 'kg CO2/person/day', 'MOEFCC India + various estimates (estimated)', NULL, 'ESTIMATED', '±30% (per-person estimate varies by methodology)', 'person-day', 'IN', 1, true, CURRENT_DATE),
('_benchmark', 'global-daily-average', 8.5, 'kg CO2/person/day', 'Global Carbon Project 2023 + World Bank population (estimated)', 'https://www.globalcarbonproject.org/', 'ESTIMATED', '±25% (per-person estimate varies by methodology)', 'person-day', 'GLOBAL', 1, true, CURRENT_DATE),
('_benchmark', 'tree-absorption-annual', 22.0, 'kg CO2/tree/year', 'Various forestry studies (estimated)', NULL, 'ESTIMATED', '±50% (varies by species, age, climate, health)', 'tree-year', 'IN', 1, true, CURRENT_DATE)
ON CONFLICT (category, type) DO NOTHING;

-- ============================================================
-- 6. Update unique constraint to include version
-- This allows multiple versions of the same (category, type) factor
-- Drop old constraint, add new one with version
-- ============================================================

-- First, update the existing unique constraint to include version
-- We need to drop the old one and create a new one
ALTER TABLE emission_factors DROP CONSTRAINT uk_emission_factor_category_type;
ALTER TABLE emission_factors ADD CONSTRAINT uk_emission_factor_category_type_version
    UNIQUE (category, type, version);

-- ============================================================
-- 7. Index for active factor lookups
-- ============================================================
CREATE INDEX IF NOT EXISTS idx_emission_factors_category_type_active
    ON emission_factors (category, type, active) WHERE active = true;

CREATE INDEX IF NOT EXISTS idx_emission_factors_benchmark
    ON emission_factors (category) WHERE category = '_benchmark';

-- Reset sequence after inserts
SELECT setval('emission_factors_id_seq', (SELECT COALESCE(MAX(id), 1) FROM emission_factors));
