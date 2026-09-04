# EcoVerse Carbon Methodology

## Overview

This document describes the carbon calculation methodology used in EcoVerse's Production Carbon Engine (Phase D). All calculations are performed server-side; the frontend never calculates or sends CO2 values.

## Calculation Formula

```
CO₂ (kg) = emission_factor × input_value / passengers × modifier
```

- **emission_factor**: Looked up from `emission_factors` database table (never hardcoded)
- **input_value**: User's raw input, converted to canonical unit (km, kWh, kg, meal, hr)
- **passengers**: For transport only; divides total emissions among passengers (minimum 1)
- **modifier**: Optional reduction factor (e.g., 0.5 for secondhand items)

### Rounding

All CO₂ values are rounded to **4 decimal places** (0.1 gram precision) using `RoundingMode.HALF_UP`.

## Data Types

| Field | Type | Precision | Notes |
|-------|------|-----------|-------|
| CO₂ values | `BigDecimal` | NUMERIC(12,4) | Always non-negative |
| Emission factors | `BigDecimal` | NUMERIC(10,6) | Always non-negative |
| Carbon budget | `BigDecimal` | NUMERIC(8,2) | User's daily budget in kg |
| Monetary values | `BigDecimal` | NUMERIC(12,2) | For shop/orders |

## Calculation Types

CO₂ values are always stored as non-negative numbers. The direction (emission vs. avoidance) is determined by the `calculation_type` field:

| Type | Meaning | Examples |
|------|---------|---------|
| `EMISSION` | Direct CO₂ emission | Driving, electricity, beef, landfill |
| `AVOIDED_EMISSION` | CO₂ that was NOT emitted | Solar power, recycling, composting |
| `CREDIT` | Purchased carbon credit | (future) |

**Before Phase D**, avoided emissions were stored as negative numbers (e.g., solar: -0.05). This created ambiguity. **After Phase D**, the CO₂ value is always positive and the `calculation_type` field tells you the direction.

## Categories & Input Contracts

### Transport (kg CO₂ per km)
- **Required**: `distance` (positive number), `type` (e.g., car-petrol, bus, train)
- **Optional**: `distanceUnit` (km/mi/m; default: km), `passengers` (default: 1)
- **Canonical unit**: km
- **Calculation**: `factor × distance_km / passengers`

### Energy (kg CO₂ per kWh/unit)
- **Required**: `consumption` (positive number), `type` (e.g., electricity, solar)
- **Optional**: `energyUnit` (kWh/Wh/MWh; default: kWh)
- **Canonical unit**: kWh
- **Calculation**: `factor × consumption_kWh`
- **Special**: `solar` → `AVOIDED_EMISSION` type

### Food (kg CO₂ per meal)
- **Required**: `meals` (positive number), `type` (e.g., beef, vegan)
- **Canonical unit**: meal (no conversion)
- **Calculation**: `factor × meals`

### Shopping (kg CO₂ per kg or per item)
- **Required**: `quantity` (positive number), `type` (e.g., clothing-kg, electronics-item)
- **Optional**: `quantityUnit` (kg/g/item; default: kg), `isSecondhand` (boolean; applies ×0.5 modifier)
- **Canonical unit**: kg (for weight-based) or item (for per-item factors)
- **Calculation**: `factor × quantity × (0.5 if secondhand)`

**Phase D redesign**: Shopping was previously calculated per Rupee spent (kg/₹), which is scientifically indefensible. It now uses product-weight-based factors (kg CO₂ per kg of product) or per-item factors.

### Waste (kg CO₂ per kg)
- **Required**: `quantity` (positive number), `type` (e.g., landfill, recycled)
- **Optional**: `quantityUnit` (kg/g/tonnes; default: kg)
- **Canonical unit**: kg
- **Calculation**: `factor × quantity_kg`
- **Special**: `recycled`, `composted` → `AVOIDED_EMISSION` type

### Digital (kg CO₂ per unit)
- **Required**: `quantity` (positive number), `type` (e.g., streaming-hd, crypto-transaction)
- **Optional**: `quantityUnit` (hr/GB/txn/query; default: hr)
- **Calculation**: `factor × quantity`

## Unit Conversions

All conversions use explicit documented multipliers with `BigDecimal` arithmetic:

| From | To | Multiplier |
|------|----|-----------|
| mile (mi) | km | 1.60934 |
| meter (m) | km | 0.001 |
| watt-hour (Wh) | kWh | 0.001 |
| megawatt-hour (MWh) | kWh | 1000 |
| gram (g) | kg | 0.001 |
| tonne (tonnes) | kg | 1000 |

Unsupported units throw `IllegalArgumentException` with a message listing valid options.

## Emission Factor Sources & Verification

Every emission factor in the database has:

| Field | Purpose |
|-------|---------|
| `source_name` | Human-readable name of the data source |
| `source_url` | URL to the source document (if available) |
| `verification_status` | `VERIFIED`, `ESTIMATED`, or `NOT_VERIFIED` |
| `uncertainty` | Human-readable uncertainty range (e.g., "±30%") |
| `region` | Geographic applicability (e.g., `IN` for India, `GLOBAL`) |
| `effective_from` | Date the factor value became effective |
| `effective_to` | Date the factor value was superseded (NULL = current) |
| `version` | Incremented when the factor is updated |

### Verification Status Definitions

- **VERIFIED**: Peer-reviewed or from an authoritative government/international source (e.g., IPCC, DEFRA, CEA India)
- **ESTIMATED**: Derived from general data, adapted for Indian conditions, but not directly from India-specific peer-reviewed studies
- **NOT_VERIFIED**: No documented source; approximate value that needs validation

### Source References (Transport)
- IPCC AR6 / Ministry of Road Transport and Highways (MoRTH) India averages
- Status: **ESTIMATED** — values are per-passenger averages, vary significantly by vehicle age/load/conditions

### Source References (Energy)
- India grid emission factor: CEA (Central Electricity Authority) 2022-23 report
- Status: **ESTIMATED** for grid electricity; **NOT_VERIFIED** for natural gas, LPG, diesel generator, solar

### Source References (Food)
- Poore & Nemecek (2018), "Reducing food's environmental impacts through producers and consumers", *Science*
- Status: **ESTIMATED** — adapted for Indian meal portions; per-meal values, not per-kg-of-food

### Source References (Waste)
- IPCC 2006 Guidelines for National Greenhouse Gas Inventories
- Status: **ESTIMATED** for landfill; **NOT_VERIFIED** for recycled, composted, incinerated, e-waste

### Source References (Digital)
- The Shift Project (2019), "Lean ICT" report + IEA data center estimates
- Status: **ESTIMATED** — varies enormously by data center location and energy mix

## Reference Benchmarks

Stored in the `emission_factors` table under category `_benchmark`:

| Benchmark | Value | Source |
|-----------|-------|--------|
| India daily average | 4.2 kg CO₂/person/day | MOEFCC India estimates |
| Global daily average | 8.5 kg CO₂/person/day | Global Carbon Project 2023 |
| Tree absorption | 22.0 kg CO₂/tree/year | Various forestry studies |

All benchmarks are marked **ESTIMATED** with documented uncertainty ranges.

## Timezone Handling

- All timestamps in the database are stored as `TIMESTAMPTZ` (UTC)
- Each user has a `timezone` field (IANA timezone string, default: `Asia/Kolkata`)
- Period calculations (today, week, month, year) use the user's timezone to determine boundaries
- Example: A user in `America/New_York` sees "today" as midnight EST to midnight EST, even though the server is in UTC

## Factor Versioning & Historical Immutability

When an emission factor is updated:
1. The old factor row is set `active = false` with `effective_to = CURRENT_DATE`
2. A new row is created with `version = old_version + 1` and `active = true`
3. New calculations use the latest active factor
4. Historical entries retain their `factor_id` and `factor_version`, so old calculations remain reproducible

## API Contract

### Preview Calculation (no entry created)
```
POST /api/carbon/calculate
{
  "category": "transport",
  "type": "car-petrol",
  "value": 100,
  "unit": "km",
  "passengers": 1,
  "isSecondhand": false
}
→ { "co2": 21.0, "calculationType": "EMISSION", "factorUsed": 0.21, "factorVerificationStatus": "ESTIMATED" }
```

### Add Entry (server calculates CO₂)
```
POST /api/carbon/entries
{
  "category": "transport",
  "type": "car-petrol",
  "distance": 100,
  "distanceUnit": "km",
  "passengers": 1,
  "timezone": "Asia/Kolkata"
}
→ { "id": 42, "co2": 21.0, "calculationType": "EMISSION", "factorId": 1, "factorVersion": 1 }
```

**The client NEVER sends `co2`** — this field was removed from `CarbonEntryRequest` in Phase D.

### Get Available Factor Types
```
GET /api/carbon/factors?category=transport
→ [{ "type": "car-petrol", "factor": 0.21, "unit": "kg/km", "verificationStatus": "ESTIMATED" }, ...]
```

## Migration History

| Version | Description |
|---------|-------------|
| V10 | BigDecimal for carbon, new calculation fields, TIMESTAMPTZ, user timezone |
| V11 | Emission factor metadata, verification, product-based shopping, reference benchmarks |

## Important Notes

1. **Do not assume existing emission factors are correct merely because they exist** — many are estimates
2. **Do not invent scientific values** — every production emission factor must have a documented source or be marked `NOT_VERIFIED`
3. **The backend is the authoritative source for carbon calculations** — the frontend must never be trusted for final CO₂ values
4. **Negative CO₂ values are no longer stored** — use `calculationType = AVOIDED_EMISSION` instead
5. **Shopping is now product-based** (kg CO₂ per kg of product) — currency-based factors have been deactivated
