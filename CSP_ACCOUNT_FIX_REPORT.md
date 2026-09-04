# EcoVerse — CSP Account Bug Fix Report

**Date:** September 1, 2026
**Bug:** Account UI unresponsive — CSP blocks inline event handlers
**Status:** ✅ FIXED AND VERIFIED

---

## 1. Exact Account Handler That Was Blocked

```html
<!-- BEFORE (blocked by CSP) -->
<div class="nav-item" data-tab="tab-profile" onclick="switchTab('tab-profile',this)">
    <i class="fa-solid fa-gear"></i><span>Settings</span>
</div>
```

The **Settings** nav item (the "Account" UI) used the inline `onclick` attribute. The strict CSP header (`script-src 'self'`) blocks ALL inline event handler attributes, so the browser refused to execute `switchTab('tab-profile', this)` — the Account page never opened.

---

## 2. Root Cause

| Factor | Detail |
|--------|--------|
| CSP directive | `script-src 'self'` (no `'unsafe-inline'`) — **correct and intentional** |
| Blocked mechanism | All 145 inline event handlers in `index.html` (`onclick=`, `onchange=`, `oninput=`, `onsubmit=`) |
| Symptom | Clicking Settings did nothing; console showed CSP violations |
| Why it happened | The frontend was written with inline handlers, which work without CSP but are silently blocked under strict CSP |

**Fix approach:** Keep the strict CSP. Replace inline handlers with an external event delegation system (`js/events.js`).

---

## 3. Exact Frontend Fix

### New architecture

```html
<!-- AFTER — data-action attribute, no inline JS -->
<div class="nav-item" data-tab="tab-profile" data-action="switchTab">
    <i class="fa-solid fa-gear"></i><span>Settings</span>
</div>
```

### New file: `js/events.js` (loaded LAST)

```js
const EcoActions = {
    switchTab: (el) => switchTab(el.dataset.tab, el),
    login: () => login(),
    logout: () => logout(),
    // ... 58 more actions
};

document.addEventListener('click', (e) => {
    const el = e.target.closest('[data-action]');
    if (!el) return;
    const action = EcoActions[el.dataset.action];
    if (typeof action === 'function') { e.preventDefault(); action(el, e); }
});
// change/input/submit delegation handled the same way
```

**Why delegation:** The page has 145 handlers and dynamic content. A single delegated `document` listener handles both static elements (sidebar nav) and dynamically generated ones (badges, notes, cart items) uniformly — no `unsafe-inline` needed.

---

## 4. Full Inline Handler Audit

### 4a. `index.html` — 145 inline handlers → ALL REMOVED

| Handler type | Count | Replacement |
|-------------|-------|-------------|
| `onclick="..."` | 138 | `data-action="..."` + `data-*` params |
| `onchange="..."` | 3 | `data-action-change="..."` |
| `oninput="..."` | 2 | `data-action-input="..."` |
| `onsubmit="return false;"` | 2 | `data-no-submit` + delegated submit guard |

### 4b. JS template literals — 3 dynamic handlers → ALL REPLACED

| File | Line | Handler | Replacement |
|------|------|---------|-------------|
| `js/achievements.js` | 94 | `onclick="Achievements.showBadgeDetail('${b.code||b.id}')"` | `data-action="badgeDetail" data-id="..."` |
| `js/app.js` | 591 | `onclick="deleteNote(${n.id})"` | `data-action="deleteNote" data-id="..."` |
| `js/carbon.js` | 382 | `onclick="Carbon.deleteEntry(${e.id})"` | `data-action="deleteCarbonEntry" data-id="..."` |

### 4c. Intentionally retained

| Item | Reason |
|------|--------|
| `ontent=` in `<meta content="...">` | Not an event handler — false positive (viewport meta) |
| `app.legacy.js` | Dead file — not referenced by index.html; left untouched |

---

## 5. CSP Changes

### `SecurityConfig.java` — header updated, STILL STRICT

```java
// BEFORE:
"script-src 'self' https://cdn.jsdelivr.net https://checkout.razorpay.com; " +
"connect-src 'self' https://api.open-meteo.com https://geocoding-api.open-meteo.com " +
    "https://api.rss2json.com https://api.razorpay.com https://checkout.razorpay.com; " +

// AFTER (verified in response header):
"script-src 'self' https://checkout.razorpay.com; " +
"connect-src 'self' https://geocoding-api.open-meteo.com " +
    "https://api.razorpay.com https://checkout.razorpay.com; " +
```

**Changes:**
- ❌ Removed `https://cdn.jsdelivr.net` from `script-src` — Chart.js now served locally
- ❌ Removed `https://api.open-meteo.com` from `connect-src` — weather goes through backend proxy, never direct frontend calls
- ❌ Removed `https://api.rss2json.com` from `connect-src` — news goes through backend proxy
- ✅ Kept `https://checkout.razorpay.com` — Razorpay checkout script is genuinely loaded client-side
- ✅ Kept `https://geocoding-api.open-meteo.com` — city search calls this directly from the frontend
- ✅ NO `'unsafe-inline'`, NO `'unsafe-eval'`, NO wildcard `*` added

**Verified in live response header:**
```
Content-Security-Policy: default-src 'self'; script-src 'self' https://checkout.razorpay.com;
style-src 'self' 'unsafe-inline' https://fonts.googleapis.com https://cdnjs.cloudflare.com;
font-src 'self' https://fonts.gstatic.com https://cdnjs.cloudflare.com;
img-src 'self' data: https:; frame-src https://api.razorpay.com;
connect-src 'self' https://geocoding-api.open-meteo.com https://api.razorpay.com https://checkout.razorpay.com;
object-src 'none'; base-uri 'self'; form-action 'self'
```

---

## 6. 404 Resource — Fixed

**Resource:** `/favicon.ico`
**Cause:** Browsers request `/favicon.ico` automatically; only `favicon.png` existed.
**Fix:** Added `favicon.ico` (copy of favicon.png) + `<link rel="icon" type="image/x-icon">`.

---

## 7. Chart.js Source Map — Fixed

| Item | Result |
|------|--------|
| Root cause | Chart.js loaded from `https://cdn.jsdelivr.net`; its `//# sourceMappingURL=chart.umd.js.map` triggered a connect-src CSP violation |
| Fix | Chart.js **vendored locally** (`js/chart.umd.min.js`, 205 KB, MIT license header kept) |
| Source map | `sourceMappingURL` line removed from the vendored copy (debug-only artifact, not required at runtime) |
| CDN | `cdn.jsdelivr.net` removed from CSP `script-src` entirely |
| Runtime dependency | **None** — the app no longer depends on any CDN for scripts |

---

## 8. Navigation Verification (real browser, Docker Compose app)

All verified in the running Docker Compose stack at `http://localhost:8081`:

| Test | Result |
|------|--------|
| Page loads, login form visible | ✅ |
| Register + login via UI | ✅ |
| **Click Settings (Account)** | ✅ **profile page opens: name, email, stats, settings** |
| Dashboard tab | ✅ |
| Carbon Tracker tab | ✅ |
| Health tab | ✅ |
| Weather tab | ✅ |
| News tab | ✅ |
| Tips & Notes tab | ✅ |
| Shop tab | ✅ |
| Achievements tab | ✅ |
| Logout | ✅ returns to login screen |
| Live DOM has inline handlers | **0** |
| Live DOM has data-action elements | **137** |
| events.js delegation system loaded | ✅ |

**No CSP runtime errors** for application functionality — the only console noise is the source-map request, which no longer exists since Chart.js is local.

---

## 9. Docker Verification

| Step | Result |
|------|--------|
| `docker compose build` | ✅ Image built |
| `docker compose up -d` | ✅ Backend + DB running |
| PostgreSQL connects | ✅ |
| Flyway migrations V1→V17 | ✅ (V17 added) |
| `GET /` serves frontend | ✅ HTTP 200 |
| `GET /js/chart.umd.min.js` | ✅ HTTP 200 |
| `GET /favicon.ico` | ✅ HTTP 200 |
| Strict CSP header on response | ✅ Verified |

---

## 10. Tests

| Suite | Result |
|-------|--------|
| `mvn clean test` (unit + integration, Testcontainers excluded on Windows) | ✅ **661 tests, 0 failures, 0 errors** |
| New: `InlineHandlerCspRegressionTest` | ✅ **19 tests, 0 failures** |
| `mvn clean package` | ✅ **BUILD SUCCESS** |

### New regression test: `InlineHandlerCspRegressionTest`

Prevents this bug from returning. Fails if:

1. `index.html` contains ANY inline event handler attribute (`onclick=`, `onchange=`, `oninput=`, `onsubmit=`, `onload=`, `onmouseover=`, `onfocus=`, `onblur=`, `onkeyup=`, ...)
2. Any JS module generates inline handlers via template literals (`onclick="${...}"`)
3. Chart.js is not served locally, or still references `cdn.jsdelivr.net`
4. Vendored Chart.js references `sourceMappingURL` (would trigger connect-src violation)
5. `events.js` is not loaded after `app.js`

---

## 11. Additional Bug Found & Fixed: Login Broken (V17 migration)

While verifying the fix end-to-end, **login was broken** in the Docker app:

```
ERROR: null value in column "token" of relation "refresh_tokens"
violates not-null constraint
```

**Root cause:** V4 created `refresh_tokens.token` as `NOT NULL`. V9 added `token_hash` (the new lookup field) but never altered the obsolete `token` column to be nullable. New code stores only the hash → every login/refresh failed.

**Fix:** `V17__Fix_Refresh_Token_Nullable.sql`:
```sql
ALTER TABLE refresh_tokens ALTER COLUMN token DROP NOT NULL;
```

**Verified:** login via UI + API now succeeds. **Note:** this was a pre-existing bug (not caused by this fix) that surfaced during end-to-end testing.

---

## 12. Remaining Warnings

| Warning | Severity | Status |
|---------|----------|--------|
| Actuator health returns DOWN | Low | Pre-existing — `MailHealthIndicator` fails because no SMTP credentials in `.env`. App itself runs fine. Configure SMTP or disable mail health indicator. |
| Notifications feature is a stub | Low | `openNotifications()` shows an empty panel — pre-existing. |

---

## 13. NOT VERIFIED Items

| Item | Why |
|------|-----|
| Account flow in production HTTPS | Requires ngrok/staging deployment (Phase 8 scripts ready) |
| Full checkout / Razorpay | Requires Razorpay TEST credentials |
| Email flows | Requires SMTP credentials |

---

## File Change Summary

| File | Change |
|------|--------|
| `static/js/events.js` | **NEW** — event delegation system (60 action handlers) |
| `static/index.html` | Rewritten — 145 inline handlers → `data-action` attributes; Chart.js now local |
| `static/js/app.js` | 1 dynamic template handler → `data-action` |
| `static/js/carbon.js` | 1 dynamic template handler → `data-action` |
| `static/js/achievements.js` | 1 dynamic template handler → `data-action` |
| `static/js/chart.umd.min.js` | **NEW** — Chart.js 4.4.0 vendored locally, source map ref removed |
| `static/favicon.ico` | **NEW** — fixes 404 |
| `SecurityConfig.java` | CSP tightened: jsdelivr removed, unused connect-src domains removed |
| `V17__Fix_Refresh_Token_Nullable.sql` | **NEW** — fixes pre-existing login bug |
| `InlineHandlerCspRegressionTest.java` | **NEW** — 19-test CSP regression guard |

---

*Strict CSP preserved: no `unsafe-inline`, no `unsafe-eval`, no wildcard `*`.*
*All 661 existing tests pass. Build succeeds. Docker deployment verified.*
