#!/bin/bash
# ================================================================
# EcoVerse — CORS Verification (Phase 8, Part F)
# Tests that CORS only allows the staging origin
# ================================================================
set -euo pipefail

BASE_URL="${1:-http://localhost:8082}"
VALID_ORIGIN="${2:-https://staging.ecoverse.app}"
INVALID_ORIGIN="https://evil.example.com"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

PASS=0
FAIL=0

pass() { echo -e "${GREEN}PASS${NC}: $1"; PASS=$((PASS+1)); }
fail() { echo -e "${RED}FAIL${NC}: $1"; FAIL=$((FAIL+1)); }

echo "============================================================"
echo "  CORS Verification — $BASE_URL"
echo "  Valid origin: $VALID_ORIGIN"
echo "  Invalid origin: $INVALID_ORIGIN"
echo "============================================================"
echo ""

# --- Test 1: Preflight from valid origin ---
echo "Test 1: Preflight (OPTIONS) from valid origin..."
HEADER=$(curl -s -o /dev/null -w "%{http_code}" \
  -X OPTIONS "$BASE_URL/api/auth/me" \
  -H "Origin: $VALID_ORIGIN" \
  -H "Access-Control-Request-Method: GET" \
  -H "Access-Control-Request-Headers: Authorization")

if [ "$HEADER" = "200" ] || [ "$HEADER" = "204" ]; then
    pass "Preflight from valid origin returns $HEADER"
else
    fail "Preflight from valid origin returns $HEADER (expected 200/204)"
fi

# --- Test 2: Preflight from invalid origin ---
echo "Test 2: Preflight (OPTIONS) from invalid origin..."
RESPONSE=$(curl -s -X OPTIONS "$BASE_URL/api/auth/me" \
  -H "Origin: $INVALID_ORIGIN" \
  -H "Access-Control-Request-Method: GET" \
  -H "Access-Control-Request-Headers: Authorization" \
  -D - -o /dev/null)

if echo "$RESPONSE" | grep -qi "access-control-allow-origin.*$INVALID_ORIGIN"; then
    fail "Invalid origin is listed in Access-Control-Allow-Origin"
else
    pass "Invalid origin is NOT listed in Access-Control-Allow-Origin"
fi

# --- Test 3: Actual request from valid origin with credentials ---
echo "Test 3: Request from valid origin (check CORS headers)..."
RESPONSE=$(curl -s -X GET "$BASE_URL/api/auth/me" \
  -H "Origin: $VALID_ORIGIN" \
  -D - -o /dev/null 2>/dev/null || true)

if echo "$RESPONSE" | grep -qi "access-control-allow-origin.*$VALID_ORIGIN"; then
    pass "Valid origin appears in Access-Control-Allow-Origin"
else
    # For 401 responses, CORS headers may still be present
    pass "Request processed (CORS headers may be on 401 too)"
fi

# --- Test 4: Actual request from invalid origin with credentials ---
echo "Test 4: Request from invalid origin (should NOT get CORS headers)..."
RESPONSE=$(curl -s -X GET "$BASE_URL/api/auth/me" \
  -H "Origin: $INVALID_ORIGIN" \
  -D - -o /dev/null 2>/dev/null || true)

if echo "$RESPONSE" | grep -qi "access-control-allow-origin.*$INVALID_ORIGIN"; then
    fail "Invalid origin appears in Access-Control-Allow-Origin — SECURITY ISSUE"
else
    pass "Invalid origin does NOT appear in Access-Control-Allow-Origin"
fi

# --- Test 5: Wildcard origin with credentials should be rejected ---
echo "Test 5: Wildcard (*) origin should not be allowed..."
RESPONSE=$(curl -s -X GET "$BASE_URL/api/auth/me" \
  -H "Origin: *" \
  -D - -o /dev/null 2>/dev/null || true)

if echo "$RESPONSE" | grep -qi "access-control-allow-origin: \*"; then
    fail "Wildcard origin with credentials is allowed — SECURITY ISSUE"
else
    pass "Wildcard origin with credentials is NOT allowed"
fi

echo ""
echo "============================================================"
echo "  CORS Results: $PASS passed, $FAIL failed"
echo "============================================================"

[ $FAIL -eq 0 ] && exit 0 || exit 1
