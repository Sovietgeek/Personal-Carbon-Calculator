# Razorpay TEST MODE Verification

## Status: NOT VERIFIED — TEST CREDENTIALS REQUIRED

Razorpay TEST MODE browser checkout has NOT been verified because it requires:
1. Real Razorpay TEST MODE keys
2. A running browser with access to the Razorpay checkout popup
3. Manual interaction with the test payment flow

## What HAS Been Verified (Server-Side)

The following server-side payment logic IS verified via automated tests:

| Check | Status | Evidence |
|-------|--------|----------|
| HMAC-SHA256 signature verification | ✅ | PaymentServiceTest with mocked WebClient |
| Payment idempotency (same order → no double-PAID) | ✅ | PostgreSQLIntegrationTest |
| Webhook idempotency (same event_id → processed once) | ✅ | WebhookSecurityTest |
| Order status transition enforcement | ✅ | OrderStatusTransitionTest, ErrorHandlingTest |
| PAYMENT_FAILED is terminal (cannot become PAID) | ✅ | RazorpayFailureTest |
| Payment amount calculated server-side | ✅ | ShopServiceOrderTest, ShopIntegrationTest |
| Stock decremented atomically | ✅ | RealConcurrencyTest |
| Stock restored on payment failure | ✅ | PaymentInventoryConsistencyTest |
| Refund tracking (refundedAmount prevents over-refund) | ✅ | PaymentInventoryConsistencyTest |

## Manual Verification Steps

To verify Razorpay TEST MODE checkout manually:

### Prerequisites
1. Razorpay account (free at https://dashboard.razorpay.com)
2. TEST MODE keys from the dashboard
3. Docker running locally

### Setup
```bash
# 1. Copy .env.example to .env
cp .env.example .env

# 2. Fill in Razorpay TEST keys (NOT live keys!)
# In .env, set:
RAZORPAY_KEY_ID=rzp_test_XXXXXX
RAZORPAY_KEY_SECRET=XXXXXX
RAZORPAY_WEBHOOK_SECRET=XXXXXX  # From Razorpay dashboard → Webhooks
RAZORPAY_MODE=test

# 3. Generate JWT secret
echo "JWT_SECRET=$(openssl rand -base64 64 | tr -d '\n')" >> .env

# 4. Set a strong PostgreSQL password
# Change POSTGRES_PASSWORD in .env

# 5. Start the application
docker-compose up -d
```

### Test Flow
1. **Login**: Open http://localhost:8081, register a new user, verify email
2. **Browse Products**: Navigate to the Shop tab
3. **Add to Cart**: Add a product to the cart
4. **Checkout**: Click "Checkout" — backend creates a Razorpay order
5. **Razorpay Popup**: The Razorpay checkout modal should appear
6. **Test Payment**: Use Razorpay test card numbers:
   - Success: `4111 1111 1111 1111`, any future expiry, any CVV
   - Failure: `4000 0000 0000 0002`, any future expiry, any CVV
7. **Verify Callback**: After payment, the frontend sends the callback to backend
8. **Verify Signature**: Backend verifies HMAC-SHA256 signature (this is the critical security check)
9. **Verify Order Status**: Order should be PAID, payment status PAID
10. **Seller View**: Login as seller, verify order appears in seller dashboard
11. **Test Refund**: From admin/seller panel, initiate a refund
12. **Verify Refund**: Check order status becomes REFUNDED, refundedAmount equals order total

### Webhook Verification
1. Use ngrok or similar to expose localhost:8081 to the internet
2. Configure the webhook URL in Razorpay dashboard: `https://your-ngrok-url/api/payments/webhook`
3. Perform a test payment
4. Verify the webhook is received and processed (check application logs)
5. Verify duplicate webhooks are handled idempotently

## CRITICAL RULES

- **NEVER use LIVE keys** — always use TEST keys
- **NEVER set RAZORPAY_MODE=live** — keep it as `test`
- **NEVER commit real keys to Git** — use .env (which is .gitignored)
- **NEVER use real money** — test payments use Razorpay's test card numbers

## Failure Scenarios to Test Manually

| Scenario | Expected Behavior |
|----------|-------------------|
| Invalid card number | Razorpay shows error, order stays PENDING_PAYMENT |
| Payment cancelled by user | Order stays PENDING_PAYMENT, stock NOT consumed |
| Network timeout during checkout | Order stays PENDING_PAYMENT, can retry |
| Payment succeeds but callback fails | Webhook eventually marks order PAID |
| Duplicate callback submitted | Second callback is idempotent, no double-PAID |
| Refund initiated on shipped order | Refund processed, stock NOT restored |
| Refund initiated on paid order | Refund processed, stock restored |

---

*This document will be updated to VERIFIED once manual browser testing is completed with real test keys.*
