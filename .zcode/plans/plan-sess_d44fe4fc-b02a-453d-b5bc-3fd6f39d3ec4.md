
# EcoVerse Admin Control Center — Implementation Plan

## Architecture Overview

**What already exists (reuse, don't duplicate):**
- `AdminController` + `AdminService` — 6 endpoints (users, products, orders, payments, analytics, user detail)
- `AuditLogService` — async audit logging with IP + user-agent
- `AuditLogRepository` — basic queries
- JWT filter checks `enabled` + `accountNonLocked` on every request — **blocking users already invalidates tokens immediately**
- `admin.js` — 557 lines, 5 tabs (analytics, users, products, orders, payments)
- `@PreAuthorize("hasRole('ADMIN')")` on all admin endpoints
- `AdminBootstrap` — promotes user to ADMIN via `ADMIN_EMAIL` env var
- Chart.js bundled and used in dashboard module

**What needs to be built:**
- 15 new backend endpoints
- 2 new database tables (reviews, ai_usage_logs) via Flyway migration V23
- Enhanced analytics with aggregate DB queries
- User 360° profile with carbon/health/shop/achievements sub-views
- Audit log browser with pagination + filtering
- System health checker
- Completely redesigned admin frontend with sidebar navigation, charts, and detail views

---

## Phase 1: Database Migration (V23)

**File:** `V23__Admin_Enhancements.sql`

### New tables:
1. **`reviews`** — id, user_id FK, product_id FK, rating (1-5), title, comment, status (PENDING/APPROVED/HIDDEN/FLAGGED), created_at, updated_at
2. **`ai_usage_logs`** — id, user_id FK, provider, model, input_tokens, output_tokens, success boolean, error_message, latency_ms, created_at

### New indexes:
- `idx_audit_logs_created_at` (already exists from V5)
- `idx_audit_logs_action_created` composite on (action, created_at)
- `idx_reviews_product_status` on (product_id, status)
- `idx_reviews_user_created` on (user_id, created_at)
- `idx_ai_usage_user_created` on (user_id, created_at)
- `idx_users_enabled_role` on (enabled, role)
- `idx_users_created_at` on (created_at) — may already exist

### H2 data.sql: Add sample reviews + AI usage logs

---

## Phase 2: Backend — New Entities + Repositories

### New entity: `Review.java`
- Fields: id, userId, productId, rating (1-5), title, comment, status (PENDING/APPROVED/HIDDEN/FLAGGED), createdAt, updatedAt
- JPA entity with `@Enumerated(EnumType.STRING)` for status

### New entity: `AiUsageLog.java`
- Fields: id, userId, provider, model, inputTokens, outputTokens, success, errorMessage, latencyMs, createdAt

### New repository: `ReviewRepository.java`
- `findByProductIdOrderByCreatedAtDesc(productId, pageable)`
- `findByProductIdAndStatus(productId, status, pageable)`
- `findByUserIdOrderByCreatedAtDesc(userId, pageable)`
- `countByProductIdAndStatus(productId, status)`
- `avgRatingByProductId(productId)` — aggregate query
- `findAllByOrderByCreatedAtDesc(pageable)` — admin
- `findByStatusOrderByCreatedAtDesc(status, pageable)` — admin
- `countByStatus(status)` — admin stats

### New repository: `AiUsageLogRepository.java`
- `findByUserIdOrderByCreatedAtDesc(userId, pageable)`
- `countByUserId(userId)`
- `countByUserIdAndSuccess(userId, success)`
- `findTopByUserIdOrderByCreatedAtDesc(userId)` — last AI request
- `findAllByOrderByCreatedAtDesc(pageable)` — admin
- `countBySuccess(boolean)` — admin stats
- `countByProvider(provider)` — admin stats
- `count()` — total

### Enhanced: `AuditLogRepository.java`
- Add: `findAllByOrderByCreatedAtDesc(pageable)` — paginated admin view
- Add: `findByActionOrderByCreatedAtDesc(action, pageable)` — filter by action
- Add: `findByUserIdOrderByCreatedAtDesc(userId, pageable)` — per-user audit
- Add: `countByAction(action)`

---

## Phase 3: Backend — Enhanced AdminService + AdminController

### AdminService enhancements:

**1. Enhanced `getAnalytics()`** — Replace the `orderRepository.findAll()` with aggregate queries:
```java
// Add to OrderRepository:
@Query("SELECT COALESCE(SUM(o.totalPrice), 0) FROM Order o WHERE o.status IN :statuses")
BigDecimal sumRevenueByStatuses(@Param("statuses") List<Order.OrderStatus> statuses);

@Query("SELECT COUNT(o) FROM Order o WHERE o.status = :status")
long countByStatus(@Param("status") Order.OrderStatus status);
```

Dashboard stats (all from DB aggregates, zero fake data):
- Total Users, Active Users (enabled=true), Blocked Users (enabled=false)
- New Users (last 30 days), Total Carbon Entries, Total CO₂
- Total Health Records, Total Products, Active Products, Out-of-stock Products
- Total Orders by status (pending, completed, cancelled), Total Revenue
- AI Requests, Failed AI Requests, Pending Reviews

**2. User 360° Profile** — `GET /api/admin/users/{id}/detail`
Returns: basic info + carbon summary + health summary + shop summary + achievements + AI usage + notes count + recent audit

**3. User Carbon Data** — `GET /api/admin/users/{id}/carbon?page=&size=`
Paginated carbon entries for a specific user + aggregates (total CO₂, category breakdown)

**4. User Health Data** — `GET /api/admin/users/{id}/health?page=&size=`
Paginated health logs + summary stats

**5. User Shop Data** — `GET /api/admin/users/{id}/orders?page=&size=`
User's orders with items + total spending + order status counts

**6. User Achievements** — `GET /api/admin/users/{id}/achievements`
Unlocked + locked achievements with progress

**7. Audit Logs** — `GET /api/admin/audit-logs?page=&size=&action=&userId=`
Paginated audit log browsing with filters

**8. System Health** — `GET /api/admin/system/health`
Check: database connection, AI provider status, email/SMTP config, payment config, weather API, news API
Show: CONFIGURED/NOT_CONFIGURED/HEALTHY/DEGRADED for each (never expose secrets)

**9. Reviews** — `GET /api/admin/reviews?page=&size=&status=`
Paginated review listing with status filter

**10. Review Moderation** — `PATCH /api/admin/reviews/{id}/status`
Approve/hide/flag review (with audit)

**11. AI Usage** — `GET /api/admin/ai-usage?page=&size=`
Paginated AI usage logs with success/failure counts

**12. Enhanced User List** — Add filters: `role`, `enabled`, `createdAfter`, `createdBefore`, `sortBy` (newest/oldest/most_active/highest_carbon)
Add to `UserRepository`:
- `findByRoleAndEnabledOrderByCreatedAtDesc(role, enabled, pageable)`
- `findByEnabledOrderByCreatedAtDesc(enabled, pageable)`
- `countByEnabled(boolean)`

**13. Role Change Restrictions** (CRITICAL SECURITY):
- Prevent any user from changing their own role
- Prevent promoting to ADMIN via the role endpoint (only AdminBootstrap can create admins)
- Prevent demoting the last admin
- Validate in `AdminService.updateUserRole()`:
  ```java
  if (newRole == Role.ADMIN) throw new BadRequestException("Cannot promote to ADMIN. Use ADMIN_EMAIL env var.");
  if (userId.equals(adminId)) throw new BadRequestException("Cannot change your own role.");
  ```

**14. Block/Unblock with session invalidation**:
- When admin blocks a user (`enabled=false`), also delete their refresh tokens: `refreshTokenRepository.deleteByUserId(userId)`
- This forces complete re-login when unblocked (JWT already rejected via filter check)

**15. Add Audit Logging for Auth Events**:
- In `AuthService`: Log REGISTER, LOGIN_SUCCESS, LOGIN_FAILURE, LOGOUT, PASSWORD_RESET_REQUEST, PASSWORD_RESET_SUCCESS
- In `CarbonController`: Log CARBON_ENTRY_CREATE, CARBON_ENTRY_DELETE
- In `HealthController`: Log HEALTH_LOG_CREATE, HEALTH_LOG_DELETE

### AdminController new endpoints summary:

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/admin/analytics` | Enhanced dashboard stats (already exists, enhance) |
| GET | `/api/admin/users` | Enhanced with filters (already exists, enhance) |
| GET | `/api/admin/users/{id}` | User basic info (already exists) |
| GET | `/api/admin/users/{id}/detail` | User 360° profile |
| GET | `/api/admin/users/{id}/carbon` | User carbon history |
| GET | `/api/admin/users/{id}/health` | User health data |
| GET | `/api/admin/users/{id}/orders` | User shop activity |
| GET | `/api/admin/users/{id}/achievements` | User achievements |
| PATCH | `/api/admin/users/{id}/status` | Block/unblock (already exists, enhance with refresh token deletion) |
| PATCH | `/api/admin/users/{id}/role` | Role change (already exists, add restrictions) |
| GET | `/api/admin/products` | (already exists) |
| PATCH | `/api/admin/products/{id}/status` | (already exists) |
| GET | `/api/admin/orders` | (already exists) |
| PATCH | `/api/admin/orders/{id}/status` | (already exists) |
| GET | `/api/admin/audit-logs` | Audit log browser |
| GET | `/api/admin/system/health` | System health check |
| GET | `/api/admin/reviews` | Review listing |
| PATCH | `/api/admin/reviews/{id}/status` | Review moderation |
| GET | `/api/admin/ai-usage` | AI usage logs |

---

## Phase 4: Frontend — Complete Admin Panel Redesign

### New admin.js structure (complete rewrite ~2000 lines):

The current admin panel is a single tab within the main app. The redesign will:
- Keep it as `#tab-admin` within the main app layout
- Add a secondary sidebar inside the admin section for sub-navigation
- Use the same CSS custom properties and Chart.js patterns as the rest of the app

### Admin sub-sections (secondary sidebar):

```
📊 Dashboard      → Stats cards + charts
👥 Users          → User table with search/filters/pagination
👤 User Detail    → 360° profile (slides in from right or replaces content)
📋 Audit Logs     → Paginated log viewer with filters
🛒 Products       → Product management (enhanced from current)
📦 Orders         → Order management (enhanced from current)
⭐ Reviews        → Review moderation
🤖 AI Usage       → AI usage logs + stats
💚 System Health  → Service status cards
📈 Analytics      → Full analytics charts (users, carbon, health, shop, AI)
```

### UI Components:

1. **Dashboard** — 16 stat cards in responsive grid (matching the 16 stats from the requirements) + 2 charts (user registrations over time, revenue trend)

2. **Users Table** — Enhanced with:
   - Search by name/email
   - Filter by role (USER/SELLER/ADMIN), status (active/blocked), verified
   - Sort by newest/oldest/most active/highest carbon
   - Columns: Name, Email, Role, Status, Created, Carbon, Health entries, Orders, Actions
   - Click row → User Detail view

3. **User 360° Profile** — Full-page detail view:
   - Header with name, email, role badge, status badge, action buttons (Block/Unblock, Change Role)
   - Tab bar: Overview | Carbon | Health | Shop | Achievements | AI | Activity
   - Overview tab: Join date, last activity, total carbon, total health logs, order count, spending
   - Carbon tab: Paginated entries table + total CO₂ + category breakdown chart
   - Health tab: Paginated entries table + summary stats
   - Shop tab: Order history + spending summary + order status counts
   - Achievements tab: Unlocked/locked grid
   - AI tab: Usage count, success/failure, last request
   - Activity tab: Recent audit log entries

4. **Audit Logs** — Paginated table with filters (action type, user, date range)

5. **Products** — Enhanced current table with image thumbnails, more columns

6. **Orders** — Enhanced current table with valid status transitions

7. **Reviews** — New table: Product, User, Rating, Comment, Status, Actions (approve/hide/flag)

8. **AI Usage** — New table with success/failure stats + provider breakdown

9. **System Health** — Status cards grid showing each service status

10. **Analytics** — 5 chart sections:
    - Users: registration trend (line), role distribution (doughnut)
    - Carbon: total CO₂, avg per user, category breakdown (bar)
    - Health: active users, logging frequency
    - Shop: revenue trend (line), popular categories (bar), popular products (bar)
    - AI: request volume (line), success/failure (doughnut)

### Confirmation Dialogs:
- Block/unblock user → "Are you sure you want to block [name]? They will be immediately logged out."
- Role change → "Change [name]'s role from USER to SELLER? This cannot promote to ADMIN."
- Archive/activate product → "Are you sure you want to archive [product]?"
- Review moderation → "Hide this review? It will no longer affect the product rating."

### CSS additions to styles.css:
- `.admin-sidebar` — secondary sidebar within admin section
- `.admin-detail` — user 360° profile layout
- `.admin-detail-header` — profile header with avatar, name, badges
- `.admin-detail-tabs` — sub-tabs within detail view
- `.admin-health-card`, `.admin-carbon-stat` — stat cards within detail
- `.admin-status-card` — system health status cards (green/red/yellow)
- `.admin-review-row` — review card with rating stars
- `.admin-chart-section` — analytics chart container
- Responsive breakpoints for mobile

---

## Phase 5: Add Audit Logging to Auth Events

**Files to modify:**
- `AuthService.java` — Add `auditLogService.log()` calls for: REGISTER, LOGIN_SUCCESS, LOGIN_FAILURE, LOGOUT, PASSWORD_RESET_REQUEST, PASSWORD_RESET_SUCCESS
- `CarbonController.java` — Log CARBON_ENTRY_CREATE, CARBON_ENTRY_DELETE
- `HealthController.java` — Log HEALTH_LOG_CREATE, HEALTH_LOG_DELETE

---

## Phase 6: Seed Sample Reviews + AI Usage Logs

**Files:** `data.sql` (H2), `V23` migration (PostgreSQL)

Add sample reviews for existing products (20-30 reviews across different products/users) and a few AI usage log entries so the admin panel has data to display.

---

## Phase 7: Docker Rebuild + Browser UAT

1. Rebuild Docker image
2. Login as USER → verify admin endpoints return 403
3. Login as ADMIN → verify full admin panel works
4. Test: Dashboard stats are real DB numbers
5. Test: User list with search/filter/sort/pagination
6. Test: User 360° profile with all sub-views
7. Test: Block user → verify user cannot login
8. Test: Unblock user → verify user can login again
9. Test: Role change restrictions (cannot promote to ADMIN, cannot change own role)
10. Test: Audit logs visible
11. Test: System health shows correct status
12. Test: Reviews moderation
13. Test: AI usage logs
14. Test: Analytics charts with real data

---

## Files Changed Summary

| # | File | Change |
|---|------|--------|
| 1 | `V23__Admin_Enhancements.sql` | NEW — reviews + ai_usage_logs tables, indexes |
| 2 | `Review.java` | NEW — entity |
| 3 | `AiUsageLog.java` | NEW — entity |
| 4 | `ReviewRepository.java` | NEW — repository with queries |
| 5 | `AiUsageLogRepository.java` | NEW — repository with queries |
| 6 | `OrderRepository.java` | MODIFY — add aggregate queries |
| 7 | `UserRepository.java` | MODIFY — add filter/sort queries + countByEnabled |
| 8 | `AuditLogRepository.java` | MODIFY — add pagination + filtering queries |
| 9 | `ProductRepository.java` | MODIFY — add countByStatus, aggregate queries |
| 10 | `CarbonEntryRepository.java` | MODIFY — add admin aggregate queries |
| 11 | `HealthLogRepository.java` | MODIFY — add admin aggregate queries |
| 12 | `AdminService.java` | MODIFY — 10+ new methods, enhanced analytics, role restrictions |
| 13 | `AdminController.java` | MODIFY — 12 new endpoints, enhanced toSafeUserResponse |
| 14 | `AuditLogService.java` | MODIFY — add convenience methods |
| 15 | `AuthService.java` | MODIFY — add audit logging for auth events |
| 16 | `CarbonController.java` | MODIFY — add audit logging |
| 17 | `HealthController.java` | MODIFY — add audit logging |
| 18 | `admin.js` | REWRITE — complete admin panel frontend (~2000 lines) |
| 19 | `styles.css` | MODIFY — add admin-specific CSS classes |
| 20 | `index.html` | MODIFY — update admin tab HTML structure |
| 21 | `data.sql` | MODIFY — add sample reviews + AI usage logs for H2 |
| 22 | `app.js` | MODIFY — add admin detail view state handling |
| 23 | `events.js` | MODIFY — add admin event handlers |

## Security Guarantees

1. ✅ Every admin endpoint has `@PreAuthorize("hasRole('ADMIN')")`
2. ✅ JWT filter checks `enabled` + `accountNonLocked` on every request — blocked users rejected immediately
3. ✅ Blocking deletes refresh tokens — forces complete re-authentication
4. ✅ Cannot promote to ADMIN via API — only `ADMIN_EMAIL` env var
5. ✅ Cannot change own role
6. ✅ Cannot demote the last admin
7. ✅ `toSafeUserResponse()` strips password, tokens, verification codes
8. ✅ System health shows CONFIGURED/NOT_CONFIGURED — never secrets
9. ✅ All destructive admin actions audited via AuditLogService
10. ✅ Review moderation properly excludes hidden/flagged reviews from rating calculations
