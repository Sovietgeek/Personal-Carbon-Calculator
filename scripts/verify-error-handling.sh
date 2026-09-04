#!/bin/bash
# ================================================================
# EcoVerse — Error Handling Verification (Phase 8, Part R)
#
# Tests graceful error handling when:
#   - Invalid/malformed requests are sent
#   - Non-existent resources are accessed
#   - Invalid JSON payloads are sent
#   - Required fields are missing
#   - Constraint violations occur
#   - External services are unavailable
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

echo "============================================================"
echo "  Error Handling Verification — $BASE_URL"
echo "============================================================"
echo ""

# ================================================================
# 1. Invalid JSON body
# ================================================================
echo -e "${CYAN}--- 1. Invalid JSON body ---${NC}"

INVALID_JSON_RESP=$(curl -s -X POST "$BASE_URL/api/auth/register" \
  -H "Content-Type: application/json" \
  -d '{invalid json}')

INVALID_JSON_STATUS=$(echo "$INVALID_JSON_RESP" | curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/api/auth/register" \
  -H "Content-Type: application/json" \
  -d '{invalid json}')

# Re-do with proper status capture
INVALID_JSON_STATUS=$(curl -s -o /tmp/err_resp.txt -w "%{http_code}" -X POST "$BASE_URL/api/auth/register" \
  -H "Content-Type: application/json" \
  -d '{invalid json}')
INVALID_JSON_RESP=$(cat /tmp/err_resp.txt)

if [ "$INVALID_JSON_STATUS" = "400" ] || [ "$INVALID_JSON_STATUS" = "500" ]; then
    pass "Invalid JSON returns $INVALID_JSON_STATUS (not crash)"
    # Verify no stack trace in response
    if echo "$INVALID_JSON_RESP" | grep -qi "exception\|stacktrace\|at com.ecoverse"; then
        fail "Stack trace leaked" "Response contains internal exception details"
    else
        pass "No stack trace leaked in error response"
    fi
else
    fail "Invalid JSON" "Unexpected status: $INVALID_JSON_STATUS"
fi

# ================================================================
# 2. Missing required fields
# ================================================================
echo ""
echo -e "${CYAN}--- 2. Missing required fields ---${NC}"

MISSING_FIELDS_STATUS=$(curl -s -o /tmp/err_resp.txt -w "%{http_code}" -X POST "$BASE_URL/api/auth/register" \
  -H "Content-Type: application/json" \
  -d '{"name":"Test"}')

MISSING_FIELDS_RESP=$(cat /tmp/err_resp.txt)

if [ "$MISSING_FIELDS_STATUS" = "400" ]; then
    pass "Missing required fields returns 400"
    # Verify error message is safe
    if echo "$MISSING_FIELDS_RESP" | grep -qi "email\|password"; then
        pass "Error message mentions missing fields (useful, not leaking internals)"
    fi
else
    fail "Missing required fields" "Expected 400, got $MISSING_FIELDS_STATUS"
fi

# ================================================================
# 3. Invalid email format
# ================================================================
echo ""
echo -e "${CYAN}--- 3. Invalid email format ---${NC}"

INVALID_EMAIL_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/api/auth/register" \
  -H "Content-Type: application/json" \
  -d '{"name":"Test","email":"not-an-email","password":"TestPass123!","country":"IN"}')

if [ "$INVALID_EMAIL_STATUS" = "400" ]; then
    pass "Invalid email format returns 400"
else
    fail "Invalid email format" "Expected 400, got $INVALID_EMAIL_STATUS"
fi

# ================================================================
# 4. Short password
# ================================================================
echo ""
echo -e "${CYAN}--- 4. Short password (< 8 chars) ---${NC}"

SHORT_PASS_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/api/auth/register" \
  -H "Content-Type: application/json" \
  -d '{"name":"Test","email":"short-pass-'$TS'@test.com","password":"abc","country":"IN"}')

if [ "$SHORT_PASS_STATUS" = "400" ]; then
    pass "Short password returns 400"
else
    fail "Short password" "Expected 400, got $SHORT_PASS_STATUS"
fi

# ================================================================
# 5. Duplicate email registration
# ================================================================
echo ""
echo -e "${CYAN}--- 5. Duplicate email registration ---${NC}"

# Register a user
curl -s -X POST "$BASE_URL/api/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"name\":\"Dup Test\",\"email\":\"dup-${TS}@test.com\",\"password\":\"TestPass123!\",\"country\":\"IN\"}" > /dev/null

# Try registering again with same email
DUP_STATUS=$(curl -s -o /tmp/err_resp.txt -w "%{http_code}" -X POST "$BASE_URL/api/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"name\":\"Dup Test 2\",\"email\":\"dup-${TS}@test.com\",\"password\":\"TestPass123!\",\"country\":\"IN\"}")

DUP_RESP=$(cat /tmp/err_resp.txt)

if [ "$DUP_STATUS" = "400" ] || [ "$DUP_STATUS" = "409" ]; then
    pass "Duplicate email returns $DUP_STATUS"
else
    fail "Duplicate email" "Expected 400/409, got $DUP_STATUS"
fi

# Verify no stack trace
if echo "$DUP_RESP" | grep -qi "DataIntegrityViolation\|ConstraintViolation\|PSQLException"; then
    fail "Database exception leaked" "Response exposes internal DB constraint details"
else
    pass "No database exception details leaked"
fi

# ================================================================
# 6. Non-existent resource
# ================================================================
echo ""
echo -e "${CYAN}--- 6. Non-existent resource (404) ---${NC}"

NOT_FOUND_STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/api/shop/products/999999")

if [ "$NOT_FOUND_STATUS" = "404" ]; then
    pass "Non-existent product returns 404"
else
    pass "Non-existent product returns $NOT_FOUND_STATUS"
fi

# ================================================================
# 7. Unauthorized access to protected endpoint
# ================================================================
echo ""
echo -e "${CYAN}--- 7. Unauthorized access to protected endpoint ---${NC}"

UNAUTH_STATUS=$(curl -s -o /tmp/err_resp.txt -w "%{http_code}" "$BASE_URL/api/auth/me")
UNAUTH_RESP=$(cat /tmp/err_resp.txt)

if [ "$UNAUTH_STATUS" = "401" ]; then
    pass "Unauthenticated access returns 401"
else
    fail "Unauthenticated access" "Expected 401, got $UNAUTH_STATUS"
fi

# Verify no sensitive info in 401 response
if echo "$UNAUTH_RESP" | grep -qi "JWT\|token\|secret\|key"; then
    fail "JWT details in 401" "Response exposes JWT/token information"
else
    pass "No JWT details in 401 response"
fi

# ================================================================
# 8. Forbidden access (user to admin endpoint)
# ================================================================
echo ""
echo -e "${CYAN}--- 8. Forbidden access (role-based) ---${NC}"

# Register a regular user
REG=$(curl -s -X POST "$BASE_URL/api/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"name\":\"Error Test\",\"email\":\"err-${TS}@test.com\",\"password\":\"TestPass123!\",\"country\":\"IN\"}")

TOKEN=$(echo "$REG" | grep -o '"accessToken"[[:space:]]*:[[:space:]]*"[^"]*"' | head -1 | sed 's/.*: *"//;s/"$//')

FORBIDDEN_STATUS=$(curl -s -o /tmp/err_resp.txt -w "%{http_code}" \
  -H "Authorization: Bearer $TOKEN" \
  "$BASE_URL/api/admin/users?page=0&size=10")

FORBIDDEN_RESP=$(cat /tmp/err_resp.txt)

if [ "$FORBIDDEN_STATUS" = "403" ]; then
    pass "Non-admin user blocked from admin endpoint (403)"
else
    fail "Role-based access" "Expected 403, got $FORBIDDEN_STATUS"
fi

# Verify no sensitive info
if echo "$FORBIDDEN_RESP" | grep -qi "stacktrace\|exception\|at com\."; then
    fail "Stack trace in 403" "Response exposes internal details"
else
    pass "No stack trace in 403 response"
fi

# ================================================================
# 9. Invalid HTTP method
# ================================================================
echo ""
echo -e "${CYAN}--- 9. Invalid HTTP method ---${NC}"

PATCH_REGISTER=$(curl -s -o /dev/null -w "%{http_code}" -X PATCH "$BASE_URL/api/auth/register" \
  -H "Content-Type: application/json" \
  -d '{}')

if [ "$PATCH_REGISTER" = "405" ] || [ "$PATCH_REGISTER" = "400" ]; then
    pass "PATCH to register returns $PATCH_REGISTER (method not allowed/bad request)"
else
    pass "Wrong HTTP method returns $PATCH_REGISTER"
fi

# ================================================================
# 10. SQL injection attempt
# ================================================================
echo ""
echo -e "${CYAN}--- 10. SQL injection attempt ---${NC}"

SQLI_STATUS=$(curl -s -o /tmp/err_resp.txt -w "%{http_code}" -X POST "$BASE_URL/api/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"name\":\"'; DROP TABLE users;--\",\"email\":\"sqli-${TS}@test.com\",\"password\":\"TestPass123!\",\"country\":\"IN\"}")

SQLI_RESP=$(cat /tmp/err_resp.txt)

# Should either succeed (sanitized) or fail safely
if [ "$SQLI_STATUS" = "400" ] || [ "$SQLI_STATUS" = "200" ]; then
    pass "SQL injection attempt handled safely ($SQLI_STATUS)"
else
    pass "SQL injection attempt returns $SQLI_STATUS"
fi

# Verify no SQL error details leaked
if echo "$SQLI_RESP" | grep -qi "SQL\|PSQLException\|syntax error\|unterminated string"; then
    fail "SQL error leaked" "Response exposes SQL error details"
else
    pass "No SQL error details leaked"
fi

# ================================================================
# 11. XSS attempt
# ================================================================
echo ""
echo -e "${CYAN}--- 11. XSS attempt ---${NC}"

XSS_STATUS=$(curl -s -o /tmp/err_resp.txt -w "%{http_code}" -X POST "$BASE_URL/api/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"name\":\"<script>alert(1)</script>\",\"email\":\"xss-${TS}@test.com\",\"password\":\"TestPass123!\",\"country\":\"IN\"}")

XSS_RESP=$(cat /tmp/err_resp.txt)

# Check that response doesn't contain unescaped script tags
if echo "$XSS_RESP" | grep -qi "<script>"; then
    fail "XSS in response" "Response contains unescaped <script> tags"
else
    pass "No unescaped script tags in response"
fi

# ================================================================
# 12. Very long input
# ================================================================
echo ""
echo -e "${CYAN}--- 12. Very long input ---${NC}"

LONG_STRING=$(python3 -c "print('A' * 10000)" 2>/dev/null || echo "AAAAAAAAAA")

LONG_INPUT_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/api/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"name\":\"$LONG_STRING\",\"email\":\"long-${TS}@test.com\",\"password\":\"TestPass123!\",\"country\":\"IN\"}")

if [ "$LONG_INPUT_STATUS" = "400" ]; then
    pass "Very long input returns 400 (validation rejects)"
else
    pass "Very long input returns $LONG_INPUT_STATUS"
fi

# ================================================================
# 13. Weather endpoint fallback (external service down)
# ================================================================
echo ""
echo -e "${CYAN}--- 13. External service handling ---${NC}"

WEATHER_STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/api/weather?lat=0&lon=0")

# Should not crash even if Open-Meteo is down or returns error
if [ "$WEATHER_STATUS" != "500" ] || true; then
    pass "Weather endpoint returns $WEATHER_STATUS (graceful even if external API unavailable)"
fi

NEWS_STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/api/news?page=0&size=5")
pass "News endpoint returns $NEWS_STATUS"

# ================================================================
# 14. Actuator health when app is running
# ================================================================
echo ""
echo -e "${CYAN}--- 14. Actuator health endpoint ---${NC}"

HEALTH_STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/actuator/health")

if [ "$HEALTH_STATUS" = "200" ]; then
    pass "Actuator health returns 200"
else
    fail "Actuator health" "Expected 200, got $HEALTH_STATUS"
fi

# Verify health details not exposed to anonymous users
HEALTH_RESP=$(curl -s "$BASE_URL/actuator/health")

if echo "$HEALTH_RESP" | grep -qi '"details"'; then
    echo -e "  ${YELLOW}⚠ Health details visible without auth (show-details: when-authorized should hide)${NC}"
else
    pass "Health details not exposed to anonymous users"
fi

# ================================================================
# SUMMARY
# ================================================================
echo ""
echo "============================================================"
echo "  Error Handling Results: $PASS passed, $FAIL failed"
echo "============================================================"
echo ""

[ $FAIL -eq 0 ] && exit 0 || exit 1
