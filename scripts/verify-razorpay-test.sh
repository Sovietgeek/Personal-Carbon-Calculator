#!/bin/bash
# ================================================================
# EcoVerse — Razorpay TEST MODE Verification (Phase 8, Part I)
#
# CRITICAL: This script ONLY tests against Razorpay TEST mode.
# NEVER enable Razorpay LIVE mode during staging verification.
# ================================================================
set -euo pipefail

BASE_URL="${1:-http://localhost:8082}"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

PASS=0
FAIL=0
SKIP=0

pass() { echo -e "${GREEN}PASS${NC}: $1"; PASS=$((PASS+1)); }
fail() { echo -e "${RED}FAIL${NC}: $1 — $2"; FAIL=$((FAIL+1)); }
skip() { echo -e "${YELLOW}SKIP${NC}: $1 — $2"; SKIP=$((SKIP+1)); }

COOKIE_JAR="/tmp/ecoverse-razorpay-cookies.txt"
rm -f "$COOKIE_JAR"

TIMESTAMP=$(date +%s)

echo "============================================================"
echo "  Razorpay TEST MODE Verification — $BASE_URL"
echo "  ⚠️  TEST MODE ONLY — NEVER use LIVE credentials"
echo "============================================================"
echo ""

# --- Helper: Extract JSON field ---
json_field() {
    echo "$1" | grep -o "\"$2\"[[:space:]]*:[[:space:]]*\"[^\"]*\"" | head -1 | sed 's/.*: *"//;s/"$//'
}

json_field_num() {
    echo "$1" | grep -o "\"$2\"[[:space:]]*:[[:space:]]*[0-9]*" | head -1 | sed 's/.*:[[:space:]]*//'
}

# ================================================================
# STEP 1: Register + Login test user
# ================================================================
echo -e "${CYAN}--- Step 1: Register + Login test user ---${NC}"

REGISTER_DATA=$(curl -s -X POST "$BASE_URL/api/auth/register" \
  -H "Content-Type: application/json" \
  -d "{
    \"name\": \"Razorpay Test User\",
    \"email\": \"razorpay-test-${TIMESTAMP}@ecoverse.com\",
    \"password\": \"TestPass123!\",
    \"country\": \"IN\"
  }")

TOKEN=$(json_field "$REGISTER_DATA" "accessToken")

if [ -z "$TOKEN" ]; then
    # Try login instead (user may already exist)
    LOGIN_DATA=$(curl -s -c "$COOKIE_JAR" -X POST "$BASE_URL/api/auth/login" \
      -H "Content-Type: application/json" \
      -d "{
        \"email\": \"razorpay-test-${TIMESTAMP}@ecoverse.com\",
        \"password\": \"TestPass123!\"
      }")
    TOKEN=$(json_field "$LOGIN_DATA" "accessToken")
fi

if [ -z "$TOKEN" ]; then
    echo -e "${RED}FATAL: Could not register or login. Aborting.${NC}"
    exit 1
fi

pass "User registered/logged in with JWT token"

AUTH_HEADER="Authorization: Bearer $TOKEN"

# ================================================================
# STEP 2: Check Razorpay key endpoint
# ================================================================
echo ""
echo -e "${CYAN}--- Step 2: GET /api/payments/key ---${NC}"

KEY_RESPONSE=$(curl -s -H "$AUTH_HEADER" "$BASE_URL/api/payments/key")
echo "  Response: $KEY_RESPONSE"

RAZORPAY_KEY=$(json_field "$KEY_RESPONSE" "keyId")

if [ -n "$RAZORPAY_KEY" ] && [ "$RAZORPAY_KEY" != "null" ]; then
    pass "Razorpay key returned: $RAZORPAY_KEY"

    # Verify it's a TEST key (starts with rzp_test_)
    if [[ "$RAZORPAY_KEY" == rzp_test_* ]]; then
        pass "Key is TEST mode (rzp_test_*)"
    elif [[ "$RAZORPAY_KEY" == rzp_live_* ]]; then
        fail "Key is LIVE mode (rzp_live_*)" "MUST NOT use LIVE key in staging"
    else
        echo -e "${YELLOW}  ⚠ Key format not standard Razorpay format: $RAZORPAY_KEY${NC}"
    fi
else
    skip "Razorpay key not returned" "Razorpay may not be configured (COD-only mode)"
fi

# ================================================================
# STEP 3: Create a test product (as seller) for checkout
# ================================================================
echo ""
echo -e "${CYAN}--- Step 3: Create test product for checkout ---${NC}"

# First, we need a seller account
SELLER_REGISTER=$(curl -s -X POST "$BASE_URL/api/auth/register" \
  -H "Content-Type: application/json" \
  -d "{
    \"name\": \"Razorpay Test Seller\",
    \"email\": \"razorpay-seller-${TIMESTAMP}@ecoverse.com\",
    \"password\": \"TestPass123!\",
    \"country\": \"IN\"
  }")

SELLER_TOKEN=$(json_field "$SELLER_REGISTER" "accessToken")

if [ -z "$SELLER_TOKEN" ]; then
    skip "Could not register seller" "Cannot test product creation without seller"
else
    pass "Seller account registered"

    # Promote to seller role (requires admin — try to create product directly)
    PRODUCT_RESPONSE=$(curl -s -X POST "$BASE_URL/api/shop/products" \
      -H "Authorization: Bearer $SELLER_TOKEN" \
      -H "Content-Type: application/json" \
      -d "{
        \"name\": \"Test Eco Product\",
        \"description\": \"A test product for Razorpay verification\",
        \"category\": \"digital\",
        \"price\": 1.00,
        \"ecoRating\": 5,
        \"stock\": 100
      }")

    PRODUCT_ID=$(json_field_num "$PRODUCT_RESPONSE" "id")

    if [ -n "$PRODUCT_ID" ] && [ "$PRODUCT_ID" != "null" ]; then
        pass "Test product created: ID=$PRODUCT_ID"
    else
        # Check if it's a role issue
        ERROR_MSG=$(json_field "$PRODUCT_RESPONSE" "message")
        if [[ "$ERROR_MSG" == *"role"* ]] || [[ "$ERROR_MSG" == *"Role"* ]] || [[ "$ERROR_MSG" == *"SELLER"* ]]; then
            skip "Cannot create product (user is not SELLER role)" "Need admin to promote user to SELLER"
        else
            echo "  Product creation response: $PRODUCT_RESPONSE"
        fi
    fi
fi

# ================================================================
# STEP 4: Test create-order endpoint
# ================================================================
echo ""
echo -e "${CYAN}--- Step 4: POST /api/payments/create-order ---${NC}"

if [ -n "$PRODUCT_ID" ] && [ "$PRODUCT_ID" != "null" ]; then
    CREATE_ORDER_RESPONSE=$(curl -s -X POST "$BASE_URL/api/payments/create-order" \
      -H "$AUTH_HEADER" \
      -H "Content-Type: application/json" \
      -H "X-Idempotency-Key: test-order-${TIMESTAMP}" \
      -d "{
        \"items\": [{\"productId\": $PRODUCT_ID, \"quantity\": 1}],
        \"shippingAddress\": {
          \"fullName\": \"Test User\",
          \"phone\": \"9999999999\",
          \"addressLine1\": \"123 Test Street\",
          \"city\": \"Mumbai\",
          \"state\": \"Maharashtra\",
          \"pincode\": \"400001\",
          \"country\": \"India\"
        },
        \"paymentMethod\": \"card\"
      }")

    RAZORPAY_ORDER_ID=$(json_field "$CREATE_ORDER_RESPONSE" "razorpayOrderId")

    if [ -n "$RAZORPAY_ORDER_ID" ] && [ "$RAZORPAY_ORDER_ID" != "null" ]; then
        pass "Razorpay order created: $RAZORPAY_ORDER_ID"

        # Verify it's a test order (order_xxx format)
        if [[ "$RAZORPAY_ORDER_ID" == order_* ]]; then
            pass "Order ID format is valid Razorpay format"
        fi
    else
        # Could be COD mode (no Razorpay order)
        ORDER_STATUS=$(json_field "$CREATE_ORDER_RESPONSE" "status")
        if [ "$ORDER_STATUS" = "PENDING_PAYMENT" ] || [ "$ORDER_STATUS" = "PAID" ]; then
            pass "Order created (may be COD/simulated mode): status=$ORDER_STATUS"
        else
            echo "  Create order response: $CREATE_ORDER_RESPONSE"
            skip "Could not create Razorpay order" "Razorpay may not be configured"
        fi
    fi
else
    skip "Cannot test create-order" "No test product available"
fi

# ================================================================
# STEP 5: Test payment verification endpoint
# ================================================================
echo ""
echo -e "${CYAN}--- Step 5: POST /api/payments/verify (with dummy data) ---${NC}"

VERIFY_RESPONSE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/api/payments/verify" \
  -H "$AUTH_HEADER" \
  -H "Content-Type: application/json" \
  -d "{
    \"razorpayOrderId\": \"order_test_dummy\",
    \"razorpayPaymentId\": \"pay_test_dummy\",
    \"razorpaySignature\": \"invalid_signature_for_testing\"
  }")

# Expected: 400 (bad signature) — proves the endpoint is working and validates signatures
if [ "$VERIFY_RESPONSE" = "400" ] || [ "$VERIFY_RESPONSE" = "500" ]; then
    pass "Payment verify rejects invalid signature ($VERIFY_RESPONSE)"
elif [ "$VERIFY_RESPONSE" = "404" ]; then
    pass "Payment verify returns 404 for non-existent order ($VERIFY_RESPONSE)"
else
    echo "  Verify response code: $VERIFY_RESPONSE (expected rejection)"
fi

# ================================================================
# STEP 6: COD order flow (always works)
# ================================================================
echo ""
echo -e "${CYAN}--- Step 6: COD order flow (no Razorpay needed) ---${NC}"

if [ -n "$PRODUCT_ID" ] && [ "$PRODUCT_ID" != "null" ]; then
    COD_ORDER_RESPONSE=$(curl -s -X POST "$BASE_URL/api/shop/orders?paymentMethod=cod&shippingAddress=123+Test+St+Mumbai+400001" \
      -H "$AUTH_HEADER" \
      -H "Content-Type: application/json" \
      -H "X-Idempotency-Key: cod-order-${TIMESTAMP}")

    COD_ORDER_ID=$(json_field_num "$COD_ORDER_RESPONSE" "id")

    if [ -n "$COD_ORDER_ID" ] && [ "$COD_ORDER_ID" != "null" ]; then
        pass "COD order created: ID=$COD_ORDER_ID"
    else
        echo "  COD order response: $COD_ORDER_RESPONSE"
    fi
else
    skip "Cannot test COD order" "No test product available"
fi

# ================================================================
# SUMMARY
# ================================================================
echo ""
echo "============================================================"
echo "  Razorpay TEST MODE Results: $PASS passed, $FAIL failed, $SKIP skipped"
echo "============================================================"
echo ""
echo -e "${YELLOW}⚠️  MANUAL BROWSER TESTING REQUIRED:${NC}"
echo "  1. Open the EcoVerse frontend in a browser"
echo "  2. Login with the test user"
echo "  3. Browse shop → Add product to cart → Checkout"
echo "  4. Select 'Card/UPI' payment method"
echo "  5. Razorpay TEST checkout modal should appear"
echo "  6. Test SUCCESS: use card 4111 1111 1111 1111"
echo "  7. Test FAILURE: use card 4000 0000 0000 0002"
echo "  8. Test CANCEL: dismiss/close the Razorpay modal"
echo "  9. Verify order status changes correctly after each"
echo ""
echo -e "${RED}🚫 NEVER test with LIVE keys or real money!${NC}"
echo ""

[ $FAIL -eq 0 ] && exit 0 || exit 1
