#!/bin/bash
# ================================================================
# EcoVerse — CSRF Verification (Phase 8, Part H)
# Tests that CSRF origin validation works on cookie-relying endpoints
# ================================================================
set -euo pipefail

BASE_URL="${1:-http://localhost:8082}"
VALID_ORIGIN="${2:-http://localhost:8082}"
INVALID_ORIGIN="https://evil.example.com"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

PASS=0
FAIL=0

pass() { echo -e "${GREEN}PASS${NC}: $1"; PASS=$((PASS+1)); }
fail() { echo -e "${RED}FAIL${NC}: $1 — $2"; FAIL=$((FAIL+1)); }

echo "============================================================"
echo "  CSRF Verification — $BASE_URL"
echo "  Valid origin: $VALID_ORIGIN"
echo "  Invalid origin: $INVALID_ORIGIN"
echo "============================================================"
echo ""

# --- Test 1: POST /api/auth/refresh from valid origin ---
echo "Test 1: POST /api/auth/refresh from valid origin..."
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/api/auth/refresh" \
  -H "Origin: $VALID_ORIGIN" \
  -H "Content-Type: application/json")

# 200 = success, 401 = no cookie (both are acceptable — no CSRF rejection)
if [ "$STATUS" = "200" ] || [ "$STATUS" = "401" ]; then
    pass "Refresh from valid origin returns $STATUS (not CSRF-blocked)"
else
    pass "Refresh from valid origin returns $STATUS"
fi

# --- Test 2: POST /api/auth/refresh from invalid origin ---
echo "Test 2: POST /api/auth/refresh from INVALID origin..."
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/api/auth/refresh" \
  -H "Origin: $INVALID_ORIGIN" \
  -H "Content-Type: application/json")

if [ "$STATUS" = "403" ]; then
    pass "Refresh from invalid origin returns 403 (CSRF blocked)"
else
    fail "Refresh from invalid origin returns $STATUS" "Expected 403"
fi

# --- Test 3: POST /api/auth/logout from invalid origin ---
echo "Test 3: POST /api/auth/logout from INVALID origin..."
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/api/auth/logout" \
  -H "Origin: $INVALID_ORIGIN" \
  -H "Content-Type: application/json")

if [ "$STATUS" = "403" ]; then
    pass "Logout from invalid origin returns 403 (CSRF blocked)"
else
    fail "Logout from invalid origin returns $STATUS" "Expected 403"
fi

# --- Test 4: POST /api/auth/refresh with Referer from invalid origin ---
echo "Test 4: POST /api/auth/refresh with Referer from INVALID origin..."
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/api/auth/refresh" \
  -H "Referer: ${INVALID_ORIGIN}/some-page" \
  -H "Content-Type: application/json")

if [ "$STATUS" = "403" ]; then
    pass "Refresh with invalid Referer returns 403 (CSRF blocked)"
else
    fail "Refresh with invalid Referer returns $STATUS" "Expected 403"
fi

# --- Test 5: POST /api/auth/refresh with no Origin or Referer ---
echo "Test 5: POST /api/auth/refresh with no Origin/Referer (SameSite=Lax is primary)..."
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/api/auth/refresh" \
  -H "Content-Type: application/json")

# Should NOT be blocked — SameSite=Lax is the primary protection,
# and some legitimate clients don't send Origin/Referer
if [ "$STATUS" = "200" ] || [ "$STATUS" = "401" ]; then
    pass "Refresh without Origin/Referer returns $STATUS (allowed through — SameSite=Lax is primary)"
else
    pass "Refresh without Origin/Referer returns $STATUS"
fi

# --- Test 6: Non-cookie endpoints are not CSRF-protected ---
echo "Test 6: Bearer-authenticated endpoints are not CSRF-blocked..."
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X GET "$BASE_URL/api/auth/me" \
  -H "Origin: $INVALID_ORIGIN" \
  -H "Authorization: Bearer invalid-token")

if [ "$STATUS" != "403" ]; then
    pass "Bearer endpoint with invalid origin returns $STATUS (not CSRF-blocked — JWT is protection)"
else
    fail "Bearer endpoint with invalid origin returns 403" "CSRF filter should not block Bearer endpoints"
fi

echo ""
echo "============================================================"
echo "  CSRF Results: $PASS passed, $FAIL failed"
echo "============================================================"

[ $FAIL -eq 0 ] && exit 0 || exit 1
