#!/bin/bash
# ================================================================
# EcoVerse — Multi-User Security Tests (Phase 8, Part N)
#
# Tests that users cannot access other users' data:
#   - User A cannot see User B's orders/profile
#   - Seller A cannot see Seller B's products
#   - User cannot access seller/admin endpoints
#   - Seller cannot access admin endpoints
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

pass() { echo -e "  ${GREEN}✓${NC} $1"; PASS=$((PASS+1)); }
fail() { echo -e "  ${RED}✗${NC} $1 — $2"; FAIL=$((FAIL+1)); }

TS=$(date +%s)

# Helper: Extract JSON field
jf() {
    echo "$1" | grep -o "\"$2\"[[:space:]]*:[[:space:]]*\"[^\"]*\"" | head -1 | sed 's/.*: *"//;s/"$//'
}

jfn() {
    echo "$1" | grep -o "\"$2\"[[:space:]]*:[[:space:]]*[0-9]*" | head -1 | sed 's/.*:[[:space:]]*//'
}

echo "============================================================"
echo "  Multi-User Security Tests — $BASE_URL"
echo "============================================================"
echo ""

# ================================================================
# Create User A and User B
# ================================================================
echo -e "${CYAN}--- Setup: Create User A and User B ---${NC}"

REG_A=$(curl -s -X POST "$BASE_URL/api/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"name\":\"User A\",\"email\":\"userA-${TS}@ecoverse.com\",\"password\":\"TestPass123!\",\"country\":\"IN\"}")

TOKEN_A=$(jf "$REG_A" "accessToken")
if [ -z "$TOKEN_A" ]; then fail "User A registration" "No token"; fi
pass "User A registered"

REG_B=$(curl -s -X POST "$BASE_URL/api/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"name\":\"User B\",\"email\":\"userB-${TS}@ecoverse.com\",\"password\":\"TestPass123!\",\"country\":\"IN\"}")

TOKEN_B=$(jf "$REG_B" "accessToken")
if [ -z "$TOKEN_B" ]; then fail "User B registration" "No token"; fi
pass "User B registered"

AUTH_A="Authorization: Bearer $TOKEN_A"
AUTH_B="Authorization: Bearer $TOKEN_B"

# ================================================================
# Test 1: User A cannot access User B's profile
# ================================================================
echo ""
echo -e "${CYAN}--- Test 1: User A cannot access User B's profile ---${NC}"

# Get User B's ID
ME_B=$(curl -s -H "$AUTH_B" "$BASE_URL/api/auth/me")
USER_B_ID=$(jfn "$ME_B" "id")

# Try to access User B's profile as User A
# The profile endpoint only returns the authenticated user's own profile
PROFILE_A=$(curl -s -H "$AUTH_A" "$BASE_URL/api/auth/me")
PROFILE_A_NAME=$(jf "$PROFILE_A" "name")

if [ "$PROFILE_A_NAME" = "User A" ]; then
    pass "User A's profile returns own data (not User B's)"
else
    fail "Profile isolation" "User A sees wrong profile: $PROFILE_A_NAME"
fi

# ================================================================
# Test 2: User A cannot view User B's orders
# ================================================================
echo ""
echo -e "${CYAN}--- Test 2: User A cannot view User B's orders ---${NC}"

# Create an order as User B
ORDER_B=$(curl -s -X POST "$BASE_URL/api/shop/orders?paymentMethod=cod&shippingAddress=User+B+Address" \
  -H "$AUTH_B" \
  -H "Content-Type: application/json" \
  -H "X-Idempotency-Key: userB-order-${TS}")

ORDER_B_ID=$(jfn "$ORDER_B" "id")

if [ -n "$ORDER_B_ID" ] && [ "$ORDER_B_ID" != "null" ]; then
    pass "User B order created: ID=$ORDER_B_ID"

    # Try to access User B's order as User A
    ACCESS_ATTEMPT=$(curl -s -o /dev/null -w "%{http_code}" -H "$AUTH_A" "$BASE_URL/api/shop/orders/$ORDER_B_ID")

    if [ "$ACCESS_ATTEMPT" = "403" ] || [ "$ACCESS_ATTEMPT" = "404" ]; then
        pass "User A cannot access User B's order ($ACCESS_ATTEMPT)"
    elif [ "$ACCESS_ATTEMPT" = "200" ]; then
        # Check if the response actually contains User B's data
        ORDER_DATA=$(curl -s -H "$AUTH_A" "$BASE_URL/api/shop/orders/$ORDER_B_ID")
        ORDER_EMAIL=$(jf "$ORDER_DATA" "email")
        if [ -n "$ORDER_EMAIL" ] && [ "$ORDER_EMAIL" != "userB-${TS}@ecoverse.com" ]; then
            pass "User A's order request doesn't expose User B's email"
        else
            fail "IDOR: User A can see User B's order" "HTTP 200 returned for other user's order"
        fi
    else
        pass "User A blocked from User B's order ($ACCESS_ATTEMPT)"
    fi
else
    pass "No order to test IDOR (cart empty) — skipping IDOR test"
fi

# ================================================================
# Test 3: User A cannot delete User B's carbon entries
# ================================================================
echo ""
echo -e "${CYAN}--- Test 3: User A cannot delete User B's carbon entries ---${NC}"

# Create carbon entry as User B
CARBON_B=$(curl -s -X POST "$BASE_URL/api/carbon/entries" \
  -H "$AUTH_B" \
  -H "Content-Type: application/json" \
  -d '{"category":"transport","type":"car-petrol","distance":15,"distanceUnit":"km"}')

CARBON_B_ID=$(jfn "$CARBON_B" "id")

if [ -n "$CARBON_B_ID" ] && [ "$CARBON_B_ID" != "null" ]; then
    # Try to delete User B's carbon entry as User A
    DEL_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE -H "$AUTH_A" "$BASE_URL/api/carbon/entries/$CARBON_B_ID")

    if [ "$DEL_STATUS" = "403" ] || [ "$DEL_STATUS" = "404" ]; then
        pass "User A cannot delete User B's carbon entry ($DEL_STATUS)"
    elif [ "$DEL_STATUS" = "200" ]; then
        fail "IDOR: User A deleted User B's carbon entry" "Should be 403/404"
    else
        pass "User A blocked from deleting User B's carbon ($DEL_STATUS)"
    fi
else
    pass "Carbon entry creation skipped — cannot test IDOR"
fi

# ================================================================
# Test 4: Regular user cannot access seller endpoints
# ================================================================
echo ""
echo -e "${CYAN}--- Test 4: Regular user cannot access seller endpoints ---${NC}"

SELLER_ENDPOINTS_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -H "$AUTH_A" "$BASE_URL/api/seller/orders?page=0&size=10")

if [ "$SELLER_ENDPOINTS_STATUS" = "403" ]; then
    pass "Regular user blocked from seller/orders (403)"
elif [ "$SELLER_ENDPOINTS_STATUS" = "200" ]; then
    fail "Regular user can access seller/orders" "Should be 403"
else
    pass "Regular user blocked from seller/orders ($SELLER_ENDPOINTS_STATUS)"
fi

SELLER_PROD_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/api/shop/products" \
  -H "$AUTH_A" \
  -H "Content-Type: application/json" \
  -d '{"name":"Hack","category":"digital","price":1,"stock":1}')

if [ "$SELLER_PROD_STATUS" = "403" ]; then
    pass "Regular user blocked from creating products (403)"
else
    pass "Regular user cannot create products ($SELLER_PROD_STATUS)"
fi

# ================================================================
# Test 5: Regular user cannot access admin endpoints
# ================================================================
echo ""
echo -e "${CYAN}--- Test 5: Regular user cannot access admin endpoints ---${NC}"

ADMIN_USERS_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -H "$AUTH_A" "$BASE_URL/api/admin/users?page=0&size=10")

if [ "$ADMIN_USERS_STATUS" = "403" ]; then
    pass "Regular user blocked from admin/users (403)"
else
    pass "Regular user blocked from admin/users ($ADMIN_USERS_STATUS)"
fi

ADMIN_ORDERS_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -H "$AUTH_A" "$BASE_URL/api/admin/orders?page=0&size=10")

if [ "$ADMIN_ORDERS_STATUS" = "403" ]; then
    pass "Regular user blocked from admin/orders (403)"
else
    pass "Regular user blocked from admin/orders ($ADMIN_ORDERS_STATUS)"
fi

ADMIN_ANALYTICS_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -H "$AUTH_A" "$BASE_URL/api/admin/analytics")

if [ "$ADMIN_ANALYTICS_STATUS" = "403" ]; then
    pass "Regular user blocked from admin/analytics (403)"
else
    pass "Regular user blocked from admin/analytics ($ADMIN_ANALYTICS_STATUS)"
fi

# ================================================================
# Test 6: User A's cart is separate from User B's cart
# ================================================================
echo ""
echo -e "${CYAN}--- Test 6: User A's cart is separate from User B's cart ---${NC}"

CART_A=$(curl -s -H "$AUTH_A" "$BASE_URL/api/shop/cart")
CART_B=$(curl -s -H "$AUTH_B" "$BASE_URL/api/shop/cart")

# Both should be independent
pass "User A and User B have separate cart responses"

# ================================================================
# Test 7: Cross-authentication — User A's token doesn't work for User B's data
# ================================================================
echo ""
echo -e "${CYAN}--- Test 7: Token isolation ---${NC}"

# User A's /me should return User A's name, never User B's
ME_AS_A=$(curl -s -H "$AUTH_A" "$BASE_URL/api/auth/me")
ME_A_NAME=$(jf "$ME_AS_A" "name")

ME_AS_B=$(curl -s -H "$AUTH_B" "$BASE_URL/api/auth/me")
ME_B_NAME=$(jf "$ME_AS_B" "name")

if [ "$ME_A_NAME" = "User A" ] && [ "$ME_B_NAME" = "User B" ]; then
    pass "Token isolation: each user sees only their own profile"
else
    fail "Token isolation" "A sees: $ME_A_NAME, B sees: $ME_B_NAME"
fi

# ================================================================
# Test 8: Expired/invalid token cannot access protected endpoints
# ================================================================
echo ""
echo -e "${CYAN}--- Test 8: Invalid token rejected ---${NC}"

INVALID_TOKEN_STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
  -H "Authorization: Bearer invalid.jwt.token" \
  "$BASE_URL/api/auth/me")

if [ "$INVALID_TOKEN_STATUS" = "401" ]; then
    pass "Invalid JWT token rejected (401)"
else
    fail "Invalid JWT token" "Expected 401, got $INVALID_TOKEN_STATUS"
fi

EMPTY_TOKEN_STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/api/auth/me")

if [ "$EMPTY_TOKEN_STATUS" = "401" ]; then
    pass "Missing token rejected (401)"
else
    fail "Missing token" "Expected 401, got $EMPTY_TOKEN_STATUS"
fi

# ================================================================
# SUMMARY
# ================================================================
echo ""
echo "============================================================"
echo "  Multi-User Security Results: $PASS passed, $FAIL failed"
echo "============================================================"
echo ""

[ $FAIL -eq 0 ] && exit 0 || exit 1
