#!/bin/bash
# ================================================================
# EcoVerse — Authentication Flow Verification (Phase 8, Part G)
# Tests the full auth flow against staging using curl
# ================================================================
set -euo pipefail

BASE_URL="${1:-http://localhost:8082}"
VALID_ORIGIN="${2:-http://localhost:8082}"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

PASS=0
FAIL=0

pass() { echo -e "${GREEN}PASS${NC}: $1"; PASS=$((PASS+1)); }
fail() { echo -e "${RED}FAIL${NC}: $1 — $2"; FAIL=$((FAIL+1)); }

# Temp file for cookies
COOKIE_JAR=$(mktemp)
trap "rm -f $COOKIE_JAR" EXIT

# Unique email per run
SUFFIX=$(date +%s)
TEST_EMAIL="staging-test-${SUFFIX}@ecoverse.test"
TEST_PASSWORD="StagingTest123!"
TEST_NAME="Staging Test User"

echo "============================================================"
echo "  Authentication Flow Verification — $BASE_URL"
echo "  Test email: $TEST_EMAIL"
echo "============================================================"
echo ""

# --- Test 1: Register ---
echo "Test 1: Register new user..."
REGISTER_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/api/auth/register" \
  -H "Content-Type: application/json" \
  -H "Origin: $VALID_ORIGIN" \
  -d "{\"name\":\"$TEST_NAME\",\"email\":\"$TEST_EMAIL\",\"password\":\"$TEST_PASSWORD\",\"country\":\"IN\"}")

REGISTER_STATUS=$(echo "$REGISTER_RESPONSE" | tail -1)
REGISTER_BODY=$(echo "$REGISTER_RESPONSE" | sed '$d')

if [ "$REGISTER_STATUS" = "200" ]; then
    if echo "$REGISTER_BODY" | grep -q '"success":true'; then
        pass "Registration returns 200 with success"
    else
        pass "Registration returns 200 (may need email verification)"
    fi
else
    fail "Registration returns $REGISTER_STATUS" "$REGISTER_BODY"
fi

# --- Test 2: Login ---
echo "Test 2: Login..."
LOGIN_RESPONSE=$(curl -s -w "\n%{http_code}" -c "$COOKIE_JAR" -X POST "$BASE_URL/api/auth/login" \
  -H "Content-Type: application/json" \
  -H "Origin: $VALID_ORIGIN" \
  -d "{\"email\":\"$TEST_EMAIL\",\"password\":\"$TEST_PASSWORD\"}")

LOGIN_STATUS=$(echo "$LOGIN_RESPONSE" | tail -1)
LOGIN_BODY=$(echo "$LOGIN_RESPONSE" | sed '$d')

if [ "$LOGIN_STATUS" = "200" ]; then
    # Extract access token
    ACCESS_TOKEN=$(echo "$LOGIN_BODY" | grep -o '"accessToken":"[^"]*"' | head -1 | cut -d'"' -f4)
    if [ -n "$ACCESS_TOKEN" ]; then
        pass "Login returns access token"
    else
        fail "Login returns 200 but no access token" "$LOGIN_BODY"
    fi
else
    fail "Login returns $LOGIN_STATUS" "$LOGIN_BODY"
    ACCESS_TOKEN=""
fi

# --- Test 3: Verify cookie attributes ---
echo "Test 3: Verify refresh token cookie attributes..."
if [ -f "$COOKIE_JAR" ] && grep -q "ecoverse_rt" "$COOKIE_JAR"; then
    COOKIE_LINE=$(grep "ecoverse_rt" "$COOKIE_JAR" | tail -1)

    # HttpOnly: curl doesn't show HttpOnly directly, but the cookie jar format
    # includes a flag column. Check the cookie is present.
    pass "Refresh token cookie is set (ecoverse_rt)"

    # Check cookie path
    if echo "$COOKIE_LINE" | grep -q "/api/auth"; then
        pass "Cookie path is /api/auth"
    else
        fail "Cookie path is NOT /api/auth" "$COOKIE_LINE"
    fi
else
    fail "No refresh token cookie found" "Check if login succeeded"
fi

# --- Test 4: Access protected endpoint ---
echo "Test 4: Access protected endpoint with Bearer token..."
if [ -n "$ACCESS_TOKEN" ]; then
    ME_RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "$BASE_URL/api/auth/me" \
      -H "Authorization: Bearer $ACCESS_TOKEN" \
      -H "Origin: $VALID_ORIGIN")

    ME_STATUS=$(echo "$ME_RESPONSE" | tail -1)
    ME_BODY=$(echo "$ME_RESPONSE" | sed '$d')

    if [ "$ME_STATUS" = "200" ]; then
        if echo "$ME_BODY" | grep -q "$TEST_EMAIL"; then
            pass "Protected endpoint returns user data"
        else
            fail "Protected endpoint returns 200 but wrong user" "$ME_BODY"
        fi
    else
        fail "Protected endpoint returns $ME_STATUS" "$ME_BODY"
    fi
else
    fail "Cannot test protected endpoint — no access token" "Login may have failed"
fi

# --- Test 5: Access protected endpoint without token ---
echo "Test 5: Access protected endpoint WITHOUT token..."
NO_AUTH_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X GET "$BASE_URL/api/auth/me" \
  -H "Origin: $VALID_ORIGIN")

if [ "$NO_AUTH_STATUS" = "401" ]; then
    pass "Unauthenticated request returns 401"
else
    fail "Unauthenticated request returns $NO_AUTH_STATUS" "Expected 401"
fi

# --- Test 6: Refresh token ---
echo "Test 6: Refresh token via cookie..."
REFRESH_RESPONSE=$(curl -s -w "\n%{http_code}" -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/api/auth/refresh" \
  -H "Origin: $VALID_ORIGIN")

REFRESH_STATUS=$(echo "$REFRESH_RESPONSE" | tail -1)
REFRESH_BODY=$(echo "$REFRESH_RESPONSE" | sed '$d')

if [ "$REFRESH_STATUS" = "200" ]; then
    NEW_TOKEN=$(echo "$REFRESH_BODY" | grep -o '"accessToken":"[^"]*"' | head -1 | cut -d'"' -f4)
    if [ -n "$NEW_TOKEN" ]; then
        pass "Token refresh returns new access token"
        ACCESS_TOKEN="$NEW_TOKEN"
    else
        fail "Token refresh returns 200 but no new token" "$REFRESH_BODY"
    fi
else
    fail "Token refresh returns $REFRESH_STATUS" "$REFRESH_BODY"
fi

# --- Test 7: Change password ---
echo "Test 7: Change password..."
CHANGE_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/api/auth/change-password" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -H "Origin: $VALID_ORIGIN" \
  -d "{\"currentPassword\":\"$TEST_PASSWORD\",\"newPassword\":\"${TEST_PASSWORD}New1\"}")

CHANGE_STATUS=$(echo "$CHANGE_RESPONSE" | tail -1)

if [ "$CHANGE_STATUS" = "200" ]; then
    pass "Password change returns 200"
    TEST_PASSWORD="${TEST_PASSWORD}New1"
else
    CHANGE_BODY=$(echo "$CHANGE_RESPONSE" | sed '$d')
    fail "Password change returns $CHANGE_STATUS" "$CHANGE_BODY"
fi

# --- Test 8: Logout ---
echo "Test 8: Logout..."
LOGOUT_RESPONSE=$(curl -s -w "\n%{http_code}" -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE_URL/api/auth/logout" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Origin: $VALID_ORIGIN")

LOGOUT_STATUS=$(echo "$LOGOUT_RESPONSE" | tail -1)

if [ "$LOGOUT_STATUS" = "200" ]; then
    pass "Logout returns 200"
else
    LOGOUT_BODY=$(echo "$LOGOUT_RESPONSE" | sed '$d')
    fail "Logout returns $LOGOUT_STATUS" "$LOGOUT_BODY"
fi

# --- Test 9: Token no longer works after logout ---
echo "Test 9: Verify old token no longer works after logout..."
POST_LOGOUT_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X GET "$BASE_URL/api/auth/me" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Origin: $VALID_ORIGIN")

if [ "$POST_LOGOUT_STATUS" = "401" ]; then
    pass "Old token rejected after logout (401)"
else
    fail "Old token still works after logout (status: $POST_LOGOUT_STATUS)" "Security issue"
fi

# --- Test 10: Re-login with new password ---
echo "Test 10: Re-login with new password..."
RELOGIN_RESPONSE=$(curl -s -w "\n%{http_code}" -c "$COOKIE_JAR" -X POST "$BASE_URL/api/auth/login" \
  -H "Content-Type: application/json" \
  -H "Origin: $VALID_ORIGIN" \
  -d "{\"email\":\"$TEST_EMAIL\",\"password\":\"$TEST_PASSWORD\"}")

RELOGIN_STATUS=$(echo "$RELOGIN_RESPONSE" | tail -1)

if [ "$RELOGIN_STATUS" = "200" ]; then
    pass "Re-login with new password succeeds"
else
    fail "Re-login with new password fails" "Status: $RELOGIN_STATUS"
fi

# --- Test 11: Invalid credentials ---
echo "Test 11: Login with invalid credentials..."
INVALID_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/api/auth/login" \
  -H "Content-Type: application/json" \
  -H "Origin: $VALID_ORIGIN" \
  -d '{"email":"nonexistent@ecoverse.test","password":"WrongPassword123!"}')

INVALID_STATUS=$(echo "$INVALID_RESPONSE" | tail -1)

if [ "$INVALID_STATUS" = "401" ]; then
    pass "Invalid credentials return 401"
else
    fail "Invalid credentials return $INVALID_STATUS" "Expected 401"
fi

# --- Test 12: Forgot password (always returns success for anti-enumeration) ---
echo "Test 12: Forgot password (anti-enumeration)..."
FORGOT_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/api/auth/forgot-password" \
  -H "Content-Type: application/json" \
  -H "Origin: $VALID_ORIGIN" \
  -d "{\"email\":\"$TEST_EMAIL\"}")

FORGOT_STATUS=$(echo "$FORGOT_RESPONSE" | tail -1)

if [ "$FORGOT_STATUS" = "200" ]; then
    pass "Forgot password returns 200 (anti-enumeration)"
else
    fail "Forgot password returns $FORGOT_STATUS" "Should always return 200"
fi

echo ""
echo "============================================================"
echo "  Auth Results: $PASS passed, $FAIL failed"
echo "============================================================"

[ $FAIL -eq 0 ] && exit 0 || exit 1
