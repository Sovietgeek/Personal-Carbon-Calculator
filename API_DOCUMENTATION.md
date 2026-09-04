# EcoVerse API Documentation

## Overview

EcoVerse is a Carbon Intelligence Platform with e-commerce, health tracking, carbon footprint management, and AI-powered insights.

**Base URL**: `http://localhost:8081`  
**Authentication**: Bearer JWT token in `Authorization: Bearer <token>` header  
**Content-Type**: `application/json`

---

## Authentication (`/api/auth`)

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| POST | `/register` | No | Register new user (returns no tokens until verified) |
| POST | `/login` | No | Login with email/password |
| POST | `/refresh` | No* | Refresh access token using httpOnly cookie |
| POST | `/logout` | No* | Invalidate refresh token, clear cookie |
| GET | `/verify?token=...` | No | Verify email with token |
| POST | `/forgot-password` | No | Request password reset email |
| POST | `/reset-password` | No | Reset password with token |
| POST | `/resend-verification` | No | Resend verification email |
| POST | `/oauth2/exchange` | No | Exchange Google OAuth code for tokens |
| POST | `/change-password` | Yes | Change password (requires current password) |
| GET | `/me` | Yes | Get current user profile |

\* Refresh/Logout use httpOnly SameSite=Lax cookie + Origin/Referer validation

---

## Carbon (`/api/carbon`)

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| POST | `/calculate` | Yes | Calculate carbon emission |
| POST | `/entries` | Yes | Create carbon entry |
| GET | `/entries` | Yes | List user's carbon entries |
| DELETE | `/entries/{id}` | Yes | Delete carbon entry |
| DELETE | `/entries/today/clear` | Yes | Clear today's carbon entries |
| GET | `/summary` | Yes | Carbon footprint summary |
| GET | `/risk` | Yes | Carbon risk assessment |
| GET | `/breakdown` | Yes | Carbon breakdown by category |
| GET | `/suggestions` | Yes | AI-powered carbon reduction suggestions |
| GET | `/factors` | Yes | List emission factors |

---

## Dashboard (`/api/dashboard`)

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| GET | `/` | Yes | Dashboard overview (aggregated stats) |
| GET | `/trend` | Yes | Carbon/health trend data |

---

## Health (`/api/health`)

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| POST | `/log` | Yes | Log health data |
| GET | `/logs` | Yes | List user's health logs |
| POST | `/bmi` | Yes | Calculate BMI |
| GET | `/score` | Yes | Health score |
| GET | `/streak` | Yes | Health activity streak |

---

## Weather (`/api/weather`)

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| GET | `/?lat=...&lon=...` | Yes | Get weather for coordinates |

---

## News (`/api/news`)

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| GET | `/` | Yes | Get eco news feed |

---

## Notes (`/api/notes`)

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| GET | `/` | Yes | List user's notes |
| POST | `/` | Yes | Create a note |
| DELETE | `/{id}` | Yes | Delete a note |
| GET | `/tip` | Yes | Get eco tip |
| GET | `/tips/history` | Yes | Get tip history |

---

## AI (`/api/ai`)

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| POST | `/chat` | Yes | AI chat (eco questions) |
| GET | `/carbon-suggestions` | Yes | Carbon reduction suggestions |
| GET | `/health-tips` | Yes | Health improvement tips |
| GET | `/eco-tip` | Yes | Random eco tip |

---

## Shop (`/api/shop`)

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| GET | `/products` | Yes | List products (paginated, filterable) |
| GET | `/products/{id}` | Yes | Get product details |
| GET | `/products/seller` | SELLER+ | List seller's own products |
| POST | `/products` | SELLER+ | Create product |
| PUT | `/products/{id}` | SELLER+ | Update product (ownership enforced) |
| DELETE | `/products/{id}` | SELLER+ | Soft-delete product (ownership enforced) |
| POST | `/cart` | Yes | Add item to cart |
| PUT | `/cart/{id}` | Yes | Update cart item quantity (ownership enforced) |
| DELETE | `/cart/{id}` | Yes | Remove cart item (ownership enforced) |
| GET | `/cart` | Yes | Get cart with product details |
| DELETE | `/cart` | Yes | Clear cart |
| POST | `/orders` | Yes | Place order (supports `X-Idempotency-Key` header) |
| GET | `/orders/{id}` | Yes | Get order (IDOR-protected) |
| GET | `/orders` | Yes | List user's orders |
| PATCH | `/orders/{id}/status` | Yes | Update order status |

---

## Payments (`/api/payments`)

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| POST | `/create-order` | Yes | Create Razorpay payment order |
| POST | `/retry/{orderId}` | Yes | Retry payment for pending order |
| POST | `/verify` | Yes | Verify payment callback (HMAC-SHA256) |
| POST | `/webhook` | No | Razorpay webhook endpoint (signature-verified) |
| POST | `/refund` | SELLER+ | Initiate refund |
| GET | `/key` | Yes | Get Razorpay public key |

### Payment Flow
1. `POST /create-order` → Creates Razorpay order, returns checkout details
2. Frontend opens Razorpay checkout
3. `POST /verify` → Server verifies HMAC-SHA256 signature, marks order PAID
4. Razorpay `POST /webhook` → Idempotent event processing (duplicate-safe)

### Security
- Payment amount calculated **server-side** from DB prices (never trusts frontend)
- HMAC-SHA256 signature verification on both callback and webhook
- Webhook idempotency via `provider_event_id` unique constraint
- Order status transitions enforced (cannot skip or go backward)
- Stock decremented atomically via `decrementStock()` SQL

---

## Seller (`/api/seller`) — Requires SELLER or ADMIN role

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| GET | `/orders` | SELLER+ | List orders containing seller's products |
| GET | `/orders/{id}` | SELLER+ | Get seller-scoped order detail |
| PATCH | `/orders/{id}/status` | SELLER+ | Update order status (PAID→PROCESSING→SHIPPED→DELIVERED only) |

### Seller Restrictions
- Can only see orders containing their products
- Can only transition forward: PAID → PROCESSING → SHIPPED → DELIVERED
- Cannot refund, cancel, or go backward
- Multi-seller orders: each seller sees only their items

---

## Admin (`/api/admin`) — Requires ADMIN role

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| GET | `/users` | ADMIN | List all users (paginated) |
| GET | `/users/{id}` | ADMIN | Get user details |
| PATCH | `/users/{id}/status` | ADMIN | Enable/disable user |
| PATCH | `/users/{id}/role` | ADMIN | Change user role |
| GET | `/products` | ADMIN | List all products (paginated) |
| PATCH | `/products/{id}/status` | ADMIN | Change product status |
| GET | `/orders` | ADMIN | List all orders (paginated) |
| PATCH | `/orders/{id}/status` | ADMIN | Update any order status |
| GET | `/payments/events` | ADMIN | List payment events (audit trail) |
| GET | `/analytics` | ADMIN | Platform analytics |

---

## Profile (`/api/profile`)

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| GET | `/` | Yes | Get profile |
| PUT | `/` | Yes | Update profile |
| DELETE | `/` | Yes | Delete account (requires password confirmation) |
| GET | `/export` | Yes | Export user data |

---

## Achievements (`/api/achievements`)

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| GET | `/` | Yes | List achievements |
| POST | `/check` | Yes | Check and unlock achievements |

---

## Actuator

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| GET | `/actuator/health` | No | Health check |
| GET | `/actuator/info` | No | Application info |

---

## Error Responses

All errors return JSON:

```json
{
  "error": "Human-readable error message",
  "status": 400
}
```

| HTTP Status | When |
|-------------|------|
| 400 | Bad request, validation error, invalid transition |
| 401 | Missing or invalid JWT token |
| 403 | Insufficient role, ownership violation, locked/disabled account |
| 404 | Resource not found |
| 413 | Upload too large |
| 429 | Rate limit exceeded (includes `Retry-After` header) |
| 500 | Internal error (generic message, no stack trace) |

### Anti-Enumeration
Login failures always return: `{"error":"Invalid email or password","status":400}`  
This applies to: wrong password, unverified email, locked account, non-existent email.

---

## Rate Limits

| Endpoint | Limit |
|----------|-------|
| Login / Register | 5/min per IP |
| Password Reset | 3/hour per IP |
| Token Refresh | 30/min per IP |
| Resend Verification | 5/min per IP |
| OAuth Exchange | 10/min per IP |
| Payment Create/Retry | 10/min per IP |
| Payment Verify | 10/min per IP |
| Payment Refund | 5/min per IP |
| Payment Webhook | 100/min per IP |
| All other API | 60/min per IP |

---

## Security Features

- **JWT Auth**: Bearer token + httpOnly SameSite=Lax refresh cookie
- **CSRF Defense**: SameSite=Lax cookies + Origin/Referer validation on cookie-relying endpoints
- **Security Headers**: CSP, X-Frame-Options DENY, HSTS, X-Content-Type-Options, Referrer-Policy, Permissions-Policy
- **Input Sanitization**: All user inputs sanitized via `InputSanitizer`
- **@Size Constraints**: All DTO string fields bounded (44 fields across 14 DTOs)
- **Rate Limiting**: Bucket4j token bucket per IP per endpoint
- **IDOR Protection**: All user-owned resources enforce `userId` ownership check
- **Payment Security**: Server-side amount calculation, HMAC-SHA256 verification, idempotent webhooks
- **Audit Logging**: All security-relevant actions logged via `AuditLogService`

---

*Generated for EcoVerse Phase 6 — Production Hardening*
