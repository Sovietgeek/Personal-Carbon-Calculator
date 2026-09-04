# EcoVerse Phase B — Verification Report

## Methodology

Each verification item is labeled with one of:
- **VERIFIED** — Actually executed and confirmed against real PostgreSQL + HTTP
- **UNIT TESTED** — Covered by automated unit/mock test only (not executed against real DB/HTTP)
- **PARTIAL** — Partly verified
- **NOT VERIFIED** — Not actually executed

---

## 1. PostgreSQL Migration Result

### Fresh V1→V8 Migration: **VERIFIED**

Executed via `spring-boot:run -Dspring-boot.run.profiles=dev` against PostgreSQL 16 container.

| Item | Result |
|---|---|
| Flyway V1→V8 all succeed | ✅ VERIFIED |
| Schema creation (14 tables) | ✅ VERIFIED |
| Column types: `numeric(12,2)` for monetary | ✅ VERIFIED — products.price, orders.total_price, order_items.price all `numeric(12,2)` |
| Foreign keys (13 constraints) | ✅ VERIFIED — all CASCADE/RESTRICT/SET NULL as designed |
| CHECK constraints (7 business) | ✅ VERIFIED — price >= 0, quantity > 0, eco_rating range, role validity |
| Indexes (12 V8 + pre-existing) | ✅ VERIFIED — 26 indexes total |
| Unique constraints | ✅ VERIFIED — users.email, refresh_tokens.token, products.razorpay_order_id |
| Enum/role storage | ✅ VERIFIED — `varchar(20), default 'USER', CHECK(role IN ('USER','SELLER','ADMIN'))` |
| Orphaned seed data cleaned | ✅ VERIFIED — 10 orphaned products (seller_id=1) deleted by V8 cleanup |
| Hibernate `ddl-auto: validate` passes | ✅ VERIFIED |

### Existing V6→V8 Upgrade: **VERIFIED**

Applied V1–V6 manually, inserted representative data, then V7+V8.

| Item | Result |
|---|---|
| No data loss | ✅ VERIFIED — 2 users, 12 products, 1 order, 2 order items, etc. all preserved |
| Monetary values preserved exactly | ✅ VERIFIED — 19.99→19.99, 449.50→449.50, 469.49→469.49 |
| Double → NUMERIC(12,2) conversion | ✅ VERIFIED — implicit cast via `price::NUMERIC(12,2)` |
| FK constraints added successfully | ✅ VERIFIED — 13 FKs created |
| Orphan detection/cleanup | ✅ VERIFIED — 0 orphans found (data was clean) |

**BUG FIXED during verification:** V8 originally failed on fresh DB because V2 seed data creates products with `seller_id=1` but no user with `id=1`. **Fix:** Added orphan cleanup DELETE statements to V8 before FK creation.

---

## 2. Data Migration Result: **VERIFIED**

See V6→V8 upgrade above. All data preserved, all schema changes applied correctly.

---

## 3. Role + Authorization Result: **VERIFIED** (HTTP-level)

| Test | HTTP Status | Result |
|---|---|---|
| USER role cannot create product (`POST /api/shop/products`) | 403 | ✅ "Access denied" |
| USER role (new SELLER) cannot create product before promotion | 403 | ✅ "Access denied" |
| SELLER role CAN create product after DB promotion | 201 | ✅ Product created |
| Registration returns `role: "USER"` | 200 | ✅ Default role |
| `role=ADMIN` in request body ignored | 200 | ✅ Created with USER role |
| Unverified user cannot login | 400 | ✅ "verify your email" |
| Admin bootstrap via ADMIN_EMAIL env var only | — | ✅ UNIT TESTED (6 tests) — no HTTP endpoint |

**Key finding:** Role changes require DB-level promotion (AdminBootstrap or direct DB update). No API endpoint exists to change roles.

---

## 4. IDOR Result: **VERIFIED** (HTTP-level)

| Test | HTTP Status | Result |
|---|---|---|
| Seller deletes User1's cart item | 403 | ✅ "You don't have access to this cart item" |
| Seller deletes User1's carbon entry | 403 | ✅ "You don't have access to this carbon entry" |
| Seller deletes User1's note | 403 | ✅ "You don't have access to this note" |
| Non-existent resource deletion | 404 | ✅ ResourceNotFoundException |
| User-scoped data (cart, orders, carbon, notes, health) | 200 | ✅ Only returns own data |
| Payment verification ownership | — | ✅ UNIT TESTED — ForbiddenException for wrong user |

All 3 DELETE endpoints with resource IDs enforce ownership at HTTP level.

---

## 5. BigDecimal Result: **VERIFIED** (HTTP + PostgreSQL-level)

| Test | Stored Value | API Response | Result |
|---|---|---|---|
| Price 19.99 | 19.99 | 19.99 | ✅ No floating-point error |
| Price 0.01 | 0.01 | 0.01 | ✅ Smallest valid price |
| Price 999999.99 | 999999.99 | 999999.99 | ✅ NUMERIC(12,2) max range |
| Order total: 799.99×2 + 19.99×1 + 0.01×3 | 1620.00 | 1620.0 | ✅ Correct arithmetic |
| Order item prices | 799.99, 19.99, 0.01 | Same | ✅ Prices snapshotted correctly |

---

## 6. Pagination Result: **VERIFIED** (HTTP-level)

| Test | Result |
|---|---|
| Default: page=0, size=20 | ✅ Returns correct content, totalElements, totalPages, last |
| size=1000 → capped to 100 | ✅ Max page size enforced |
| size=0 → corrected to 1 | ✅ Minimum page size enforced |
| page=-1 → corrected to 0 | ✅ Negative page handled |
| Paginated response format | ✅ {content, page, size, totalElements, totalPages, last} |
| Orders endpoint also paginated | ✅ Same format |

---

## 7. Transaction Result: **UNIT TESTED** (not HTTP-verified for failure rollback)

| Test | Result |
|---|---|
| Duplicate email registration → no partial user | ✅ VERIFIED via HTTP — count=1 after failed duplicate |
| Registration `@Transactional` | ✅ UNIT TESTED |
| Login `@Transactional` | ✅ UNIT TESTED |
| `processOAuthLogin` `@Transactional` | ✅ UNIT TESTED |
| Payment verify `@Transactional` | ✅ UNIT TESTED |
| Order place `@Transactional` | ✅ UNIT TESTED |
| Mid-transaction failure rollback | ⚠️ NOT VERIFIED — would require integration test with controlled failure injection |

**Remaining gap:** No integration test verifies that partial writes are rolled back when an exception occurs mid-transaction.

---

## 8. Production Configuration Result: **VERIFIED**

| Test | Result |
|---|---|
| Missing JWT_SECRET in prod → startup fails | ✅ VERIFIED — `IllegalStateException: FATAL: JWT_SECRET is not set` |
| Weak JWT_SECRET (too short) → startup fails | ✅ VERIFIED — `FATAL: JWT_SECRET is too short (5 chars)` |
| Valid JWT_SECRET + PostgreSQL → startup succeeds | ✅ VERIFIED — `Started EcoVerseApplication` |
| Swagger disabled in prod | ✅ VERIFIED — `/api-docs` returns 404, `/swagger-ui.html` returns 404 |
| H2 URL in prod → startup fails | ✅ VERIFIED — PostgreSQL driver rejects H2 JDBC URL |
| Wildcard CORS → startup fails | ✅ UNIT TESTED (10 tests in ProductionStartupValidatorTest) |

---

## 9. Bugs Found and Fixed During Verification

| # | Bug | Impact | Fix |
|---|---|---|---|
| 1 | **V8 FK migration fails on fresh DB** — V2 seed data creates products with `seller_id=1` but no user with `id=1` exists. FK `fk_products_seller_id` violates referential integrity. | Fresh V1→V8 migration impossible | Added orphan cleanup DELETE statements to V8 before FK constraints |
| 2 | **`Order.paymentStatus` missing `@Enumerated(EnumType.STRING)`** — JPA defaults to `EnumType.ORDINAL` (maps to `smallint`), but V6 created the column as `VARCHAR(50)`. Hibernate `validate` fails with `wrong column type`. | App won't start with PostgreSQL + `ddl-auto: validate` | Added `@Enumerated(EnumType.STRING)` to `paymentStatus` field |
| 3 | **Duplicate FK on `refresh_tokens.user_id`** — V4 created `fk_refresh_token_user`, V8 tries to create `fk_refresh_tokens_user_id` on the same column. | V8 migration fails on existing DB | Added DO block in V8 to drop V4's FK first, then create the new one |
| 4 | **`deleteByIdAndUserId` returns `void`** — Test mock used `when().thenReturn(1)` which doesn't compile for void methods. | Test compilation error | Removed stub; void mocks do nothing by default |

---

## 10. Remaining Risks

| # | Risk | Severity | Details |
|---|---|---|---|
| 1 | **Carbon emission values use `Double`** | Medium | `CarbonEntry.co2`, `User.carbonBudget`, `EmissionFactor.factor` remain `Double`. Not "money" but domain-critical quantitative values. Aggregations and budget comparisons could have floating-point precision errors. |
| 2 | **Unbounded list queries** | Medium | Several `findByUserId` methods return unbounded `List<T>` without pagination: CarbonEntry, HealthLog, Note, AuditLog. Most concerning: `AuditLogRepository.findByUserIdOrderByCreatedAtDesc()` and `CarbonEntryRepository.findByUserId()` could grow unbounded over time. |
| 3 | **Missing `@Transactional` on some multi-write methods** | Low | `CarbonService.clearTodayEntries`, `AchievementService.checkAndUnlockBadges`, `AchievementService.initAchievements`, `DashboardService.getDashboard` (has write side-effect), `ProfileController` update/delete methods. Risk of partial writes on failure. |
| 4 | **Role promotion requires app restart** | Low | After promoting a user's role in the DB, the user must re-login to get a new JWT with the updated role. The old JWT still contains the previous role until it expires (15 min access token). This is by design (JWTs are stateless) but could confuse during role changes. |
| 5 | **No integration test for transaction rollback** | Low | No test verifies that partial writes are rolled back when an exception occurs mid-transaction. Only unit tests confirm `@Transactional` is present. |
| 6 | **Duplicate indexes from V1 and V8** | Informational | Some indexes created in V1 (e.g., `idx_carbon_entry_user_id`) overlap with V8 indexes (e.g., `idx_carbon_entries_user_date`). Not harmful but redundant. |

---

## 11. Exact Recommendation for Phase C

**Phase B is VERIFIED for production readiness with documented caveats.**

The following should be addressed before or during Phase C:

1. **Convert `CarbonEntry.co2` and `User.carbonBudget` to `BigDecimal`** (new V9 migration) — these are domain-critical quantitative values that should have the same precision guarantees as monetary values. This is a Phase C candidate.

2. **Add pagination to remaining unbounded endpoints** — CarbonEntry, HealthLog, Note, AuditLog list endpoints should support `Pageable`. The repository methods already have `Page<T>` overloads (added in Phase B); they just need controller wiring. Low effort.

3. **Add `@Transactional` to remaining multi-write service methods** — `clearTodayEntries`, `checkAndUnlockBadges`, `initAchievements`. Low effort, high safety.

4. **Add integration tests for transaction rollback** — Create tests that inject failures mid-transaction and verify no partial writes remain. Medium effort.

**Phase C can proceed.** The three bugs found during verification have been fixed, all 98 unit tests pass, PostgreSQL migration is verified for both fresh and upgrade paths, and all HTTP-level security tests pass.
