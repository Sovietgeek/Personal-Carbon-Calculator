# EcoVerse — Registration 400 Bug Investigation & Fix Report

**Date:** September 1, 2026
**Reported Issue:** `POST /api/auth/register` returns HTTP 400 with "Something went wrong. Please try again later." and login also fails
**Status:** ✅ Root cause found — 2 real bugs fixed, full flow verified end-to-end

---

## 1. Exact Root Cause of Registration 400

**There are TWO distinct findings here — one is the "Something went wrong" mystery, one is the real user-facing blocker.**

### Finding A — The "Something went wrong" message came from the SHOP SEARCH, not registration

`"Something went wrong. Please try again later."` is the **catch-all 500 handler** message (`GlobalExceptionHandler.java:123`). It can ONLY appear on HTTP 500, never HTTP 400. The browser Network tab showing "400 + Something went wrong" is inconsistent with the actual backend response, but the generic message itself was reproduced from a REAL bug:

**`GET /api/shop/products?keyword=&page=0&size=5` → HTTP 500 "Something went wrong"**

```
ERROR: function lower(bytea) does not exist
  Hint: No function matches the given name and argument types.
```

**Root cause:** In `ProductRepository.searchProducts()`, when `keyword` is null/empty, PostgreSQL cannot infer the type of the bound parameter inside `LOWER(CONCAT('%', :keyword, '%'))` and defaults it to `bytea`. `lower(bytea)` does not exist → SQLGrammarException → catch-all → generic 500.

### Finding B — Registration itself works (HTTP 201)

Registration logic, DTO, validation, schema, hashing, and role assignment are all **correct**. Verified with curl against the real Docker PostgreSQL:

| Scenario | Response | HTTP |
|----------|----------|------|
| Fresh registration | `success:true, "Registration successful..."` | **201** |
| Duplicate email | `"Email is already registered: ..."` (clean business message) | 400 |
| Weak password | `"Validation failed: {password=...}"` | 400 |
| Missing fields | `"Validation failed: {country=..., password=..., email=...}"` | 400 |
| Role escalation (`"role":"ADMIN"` in payload) | created with **role=USER** (ignored) | 201 |
| Wrong password | `"Invalid email or password"` (anti-enumeration) | 400 |

### Finding C — The REAL user-facing blocker: email verification lockout

```
Registration 201 → user created with enabled=false
→ verification email attempted → SMTP NOT configured in Docker → email never arrives
→ user can NEVER verify → login ALWAYS fails "Invalid email or password" (400)
```

In the Docker environment, `.env` had `MAIL_USERNAME=` / `MAIL_PASSWORD=` empty but `MAIL_HOST=smtp.gmail.com`, and `application.yml` hardcoded `mail.smtp.auth: true`. Every verification email failed with `AuthenticationFailedException`. **Users could register but could never log in** — which matches the manual test experience.

---

## 2. Actual Backend Exceptions

| # | Exception | Where | Impact |
|---|-----------|-------|--------|
| 1 | `org.postgresql.util.PSQLException: function lower(bytea) does not exist` | Product search (empty keyword) | HTTP 500 generic message |
| 2 | `jakarta.mail.AuthenticationFailedException: no password specified` | EmailService (SMTP not configured) | Verification email never sent → login lockout |
| 3 | `DataIntegrityViolationException: null value in "token" of refresh_tokens` | Login (PREVIOUSLY FIXED via V17 migration) | Login broken — already fixed |

---

## 3. Why Generic "Something went wrong" Was Returned

`GlobalExceptionHandler.handleGlobalException()` (line ~120):
```java
@ExceptionHandler(Exception.class)
public ResponseEntity<ApiResponse<Void>> handleGlobalException(Exception ex) {
    logger.error("Unhandled exception: ", ex);          // full stack logged server-side
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error("Something went wrong. Please try again later."));
}
```

This is the **correct, safe design** — full stack traces are logged server-side only, the client gets a generic message, no internals leak. **This was NOT removed or weakened.**

⚠️ **Note on status code:** The user reported HTTP 400 with this message, but this message is only produced by the 500 handler. The observed browser "400" likely came from either (a) the shop-search 500 being conflated with the registration flow, or (b) the login 400 "Invalid email or password" (which is correct anti-enumeration behavior for unverified users).

---

## 4. Exact Files Changed

| File | Change | Why |
|------|--------|-----|
| `repository/ProductRepository.java` | Added `CAST(:keyword AS string)` to all 3 keyword queries | Fixes `lower(bytea)` SQL error when keyword null/empty |
| `application.yml` | `mail.smtp.auth` → `${MAIL_SMTP_AUTH:false}`, `starttls.enable` → `${MAIL_SMTP_STARTTLS:false}` | MailHog (no auth) vs real SMTP (auth) now configurable |
| `docker-compose.yml` | Added **MailHog** service (SMTP:1025, Web UI:8025); backend mail env now defaults to MailHog | Local email capture — verification emails actually deliverable in dev |
| `.env` | `MAIL_HOST=mailhog`, `MAIL_PORT=1025`, `MAIL_SMTP_AUTH=false`, `MAIL_SMTP_STARTTLS=false` | Dev defaults for MailHog |
| `.env.example` | MailHog + production SMTP instructions | Onboarding |
| `.env.staging.example` | Added `MAIL_SMTP_AUTH` / `MAIL_SMTP_STARTTLS` | Staging parity |
| `test/.../AuthServiceTest.java` | Added 4 regression tests (see #9) | Prevent regressions |

---

## 5. Database / Schema Findings

| Check | Result |
|-------|--------|
| `users` table NOT NULL columns | All satisfied by registration flow (`name`, `email`, `password`, `created_at`, `updated_at`, `role`) |
| `users.email` UNIQUE | ✅ Enforced — duplicate returns clean 400 |
| `role` default + CHECK constraint | ✅ `'USER'` default, `chk_users_role_valid` — client cannot inject ADMIN/SELLER |
| Flyway V1→V17 | ✅ All applied (`flyway_schema_history` shows 1–17, all `success=true`) |
| `refresh_tokens.token` nullable | ✅ Fixed earlier via **V17** (was `NOT NULL` but obsolete — V9 made `token_hash` the lookup) |
| Entity ↔ schema mismatch | **No remaining mismatches** |

---

## 6. Email / SMTP Findings

| Check | Result |
|-------|--------|
| Verification email on register | ✅ Sent (async, non-blocking) |
| SMTP in Docker (before fix) | ❌ `smtp.gmail.com` with empty credentials → `AuthenticationFailedException` |
| MailHog (after fix) | ✅ Email captured at `http://localhost:8025` |
| Email failure handling | ✅ `EmailService` catches + logs WARN; **registration transaction unaffected** (defensible: user created, email attempted, failure logged) |
| No duplicate notifications | ✅ Idempotency verified in previous phases |

---

## 7. Security Findings

| Check | Result |
|-------|--------|
| Client-provided role | ✅ **Impossible** — `RegisterRequest` has NO role field; service always builds `Role.USER` |
| Role escalation via JSON `"role":"ADMIN"` | ✅ Verified — user created as USER (201, role=USER) |
| Unverified user login | ✅ Blocked with generic message (anti-enumeration) |
| Wrong password | ✅ Generic message, failed-attempt counter incremented |
| Account lockout (5 attempts) | ✅ Existing logic + tests |
| No stack traces to client | ✅ Catch-all returns generic message only |
| Email verification required | ✅ `enabled=false` until verified — NOT weakened |

---

## 8. Fix Implemented

1. **Product search `lower(bytea)` fix** — explicit `CAST(:keyword AS string)` in all 3 JPQL keyword queries so PostgreSQL always treats the parameter as text, even when null/empty.
2. **Email verification unlock** — MailHog in Docker + configurable SMTP auth/STARTTLS so verification emails are actually delivered in dev; production can still use authenticated SMTP (Gmail/SES/SendGrid) by setting `MAIL_SMTP_AUTH=true`.

---

## 9. New Regression Tests

Added to `AuthServiceTest` (now 25 tests in this class):

| Test | Verifies |
|------|----------|
| `registrationCannotEscalateRole` | Role is forced to USER; client escalation impossible |
| `registrationSucceedsWhenEmailFails` | SMTP down does NOT break registration (email failure logged, not fatal) |
| `weakPasswordRejected` | Password < 8 chars → BadRequestException, user NOT saved |
| `blacklistedPasswordRejected` | Common/blacklisted password → BadRequestException |

---

## 10. Docker Verification (live, Docker PostgreSQL + MailHog)

```
✅ 1. REGISTER fresh user            → 201 "Registration successful"
✅ 2. MailHog captures email          → "EcoVerse — Verify Your Email" received
✅ 3. VERIFY email (extracted token)  → 200 "Email verified successfully!"
✅ 4. LOGIN                           → 200 "Login successful" (accessToken issued)
✅ 5. /api/auth/me                    → 200 user profile
✅ 6. REFRESH (httpOnly cookie)       → 200 "Token refreshed successfully"
✅ 7. LOGOUT                          → 200 "Logged out successfully"
✅ 8. RE-LOGIN                        → 200 "Login successful"
✅ 9. Shop search (empty keyword)     → 200 (lower(bytea) FIXED — was 500)
✅10. Shop search (keyword=eco)       → 200
✅11. Duplicate email                 → 400 "Email is already registered: ..."
✅12. Role escalation                 → 201 but role=USER (rejected)
✅13. Wrong password                  → 400 "Invalid email or password"
```

---

## 11. Registration Result

**✅ FIXED AND VERIFIED.** A fresh registration against the live Docker PostgreSQL returns **HTTP 201** with `success:true`. The earlier confusion came from the shop-search 500 (generic message) and the email-verification lockout — both now fixed.

## 12. Login Result

**✅ VERIFIED.** After email verification (now possible via MailHog), login returns **HTTP 200** with a valid access token; refresh/logout/re-login all pass.

## 13. Tests Passed / Failed / Skipped

| Suite | Run | Failures | Errors | Skipped |
|-------|-----|----------|--------|---------|
| `mvn clean test` (full) | **665** | **0** | **0** | 0 |
| `AuthServiceTest` (with 4 new tests) | 25 | 0 | 0 | 0 |

## 14. Build Result

```
mvn clean package  →  BUILD SUCCESS
docker compose build →  Image built
docker compose up -d →  backend (healthy), db (healthy), mailhog (up)
```

## 15. Remaining Issues / Warnings

| Item | Severity | Notes |
|------|----------|-------|
| MailHog is dev-only | Info | Production MUST use real SMTP (`MAIL_SMTP_AUTH=true`, `MAIL_HOST=...`) — documented in `.env.example` |
| Audit-log FK noise in old logs | Low | Observed earlier (`audit_logs` FK violation from a deleted test user's scheduled task) — AuditLogService catches it; self-heals on data cleanup |
| Actuator health previously 503 | Fixed | Mail health indicator now passes with MailHog — backend reports **healthy** |
| Browser-side "400" reporting | Info | The reported "HTTP 400 + Something went wrong" combination is not producible by the current backend (that message is 500-only); the underlying generic-error bug (shop search) is fixed |

---

## Summary

| Question | Answer |
|----------|--------|
| Was registration logic broken? | **No** — always returned 201 with correct role/validation behavior |
| What generated "Something went wrong"? | **`lower(bytea)` SQL bug** in product search (empty keyword) → 500; **FIXED** |
| Why couldn't users log in after registering? | **Email verification lockout** — SMTP unconfigured in Docker; **FIXED with MailHog** |
| Security weakened? | **No** — role escalation blocked, anti-enumeration kept, generic 500 message kept, tests hardened |
| Verified? | ✅ Fresh registration succeeded against live Docker PostgreSQL, full auth lifecycle + shop search verified |
