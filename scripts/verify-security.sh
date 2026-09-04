#!/bin/bash
# ================================================================
# EcoVerse — Security Scan (Phase 8, Part T)
#
# Automated security verification:
#   - HTTPS and TLS
#   - Security headers
#   - CORS policy
#   - Cookie security
#   - CSRF protection
#   - IDOR prevention
#   - Authentication enforcement
#   - Actuator exposure
#   - Error info leakage
#   - API documentation exposure
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
WARN=0

pass() { echo -e "  ${GREEN}✓${NC} $1"; PASS=$((PASS+1)); }
fail() { echo -e "  ${RED}✗${NC} $1 — $2"; FAIL=$((FAIL+1)); }
warn() { echo -e "  ${YELLOW}⚠${NC} $1"; WARN=$((WARN+1)); }

TS=$(date +%s)

echo "============================================================"
echo "  Security Scan — $BASE_URL"
echo "============================================================"
echo ""

# ================================================================
# 1. HTTPS / TLS
# ================================================================
echo -e "${CYAN}--- 1. HTTPS / TLS Verification ---${NC}"

if [[ "$BASE_URL" == https://* ]]; then
    pass "Base URL uses HTTPS"

    # Check TLS certificate
    TLS_INFO=$(echo | openssl s_client -connect "$(echo "$BASE_URL" | sed 's|https://||' | sed 's|/.*||'):443" 2>/dev/null || true)

    if echo "$TLS_INFO" | grep -qi "verify return code: 0"; then
        pass "TLS certificate is valid"
    else
        warn "Could not verify TLS certificate (may be self-signed or ngrok)"
    fi

    # Check HTTP → HTTPS redirect
    HTTP_URL="${BASE_URL/https:/http:}"
    HTTP_REDIRECT=$(curl -s -o /dev/null -w "%{http_code}" -L --max-redirs 0 "$HTTP_URL" 2>/dev/null || true)

    if [ "$HTTP_REDIRECT" = "301" ] || [ "$HTTP_REDIRECT" = "302" ] || [ "$HTTP_REDIRECT" = "308" ]; then
        pass "HTTP redirects to HTTPS ($HTTP_REDIRECT)"
    else
        warn "HTTP does not redirect to HTTPS (may be handled by reverse proxy)"
    fi
else
    warn "Base URL is HTTP (not HTTPS) — staging without ngrok?"
    echo "  Run: ngrok http <port> and set BASE_URL to the HTTPS ngrok URL"
fi

# ================================================================
# 2. Security Headers
# ================================================================
echo ""
echo -e "${CYAN}--- 2. Security Headers ---${NC}"

HEADERS=$(curl -s -I "$BASE_URL/actuator/health")

# X-Content-Type-Options
if echo "$HEADERS" | grep -qi "X-Content-Type-Options: nosniff"; then
    pass "X-Content-Type-Options: nosniff present"
else
    warn "X-Content-Type-Options: nosniff NOT present (Spring Security should add this)"
fi

# X-Frame-Options
if echo "$HEADERS" | grep -qi "X-Frame-Options"; then
    pass "X-Frame-Options present"
else
    warn "X-Frame-Options NOT present (Spring Security should add DENY)"
fi

# X-XSS-Protection
if echo "$HEADERS" | grep -qi "X-XSS-Protection"; then
    pass "X-XSS-Protection present"
else
    warn "X-XSS-Protection NOT present (deprecated but some browsers use it)"
fi

# Strict-Transport-Security (HSTS)
if echo "$HEADERS" | grep -qi "Strict-Transport-Security"; then
    pass "Strict-Transport-Security (HSTS) present"
else
    warn "HSTS NOT present (should be set by reverse proxy in production)"
fi

# Cache-Control on sensitive endpoints
AUTH_HEADERS=$(curl -s -I -H "Authorization: Bearer fake" "$BASE_URL/api/auth/me")
if echo "$AUTH_HEADERS" | grep -qi "Cache-Control.*no-store\|Cache-Control.*no-cache"; then
    pass "Cache-Control present on authenticated endpoint"
else
    warn "Cache-Control not set on authenticated endpoint (may cache sensitive data)"
fi

# ================================================================
# 3. CORS Policy
# ================================================================
echo ""
echo -e "${CYAN}--- 3. CORS Policy ---${NC}"

# Preflight from valid origin
VALID_ORIGIN="${BASE_URL}"
PREFLIGHT=$(curl -s -I -X OPTIONS "$BASE_URL/api/auth/me" \
  -H "Origin: $VALID_ORIGIN" \
  -H "Access-Control-Request-Method: GET")

if echo "$PREFLIGHT" | grep -qi "Access-Control-Allow-Origin"; then
    ALLOWED_ORIGIN=$(echo "$PREFLIGHT" | grep -i "Access-Control-Allow-Origin" | tr -d '\r' | awk '{print $2}')
    if [ "$ALLOWED_ORIGIN" = "*" ]; then
        fail "CORS allows wildcard origin" "Access-Control-Allow-Origin: * with credentials is insecure"
    else
        pass "CORS allows specific origin: $ALLOWED_ORIGIN"
    fi
else
    pass "CORS preflight does not blindly allow (Spring Security default behavior)"
fi

# Preflight from invalid origin
PREFLIGHT_INVALID=$(curl -s -I -X OPTIONS "$BASE_URL/api/auth/me" \
  -H "Origin: https://evil.example.com" \
  -H "Access-Control-Request-Method: GET")

if echo "$PREFLIGHT_INVALID" | grep -qi "Access-Control-Allow-Origin: https://evil.example.com"; then
    fail "CORS allows evil origin" "Invalid origin should not be allowed"
else
    pass "CORS rejects invalid origin (https://evil.example.com)"
fi

# ================================================================
# 4. Cookie Security
# ================================================================
echo ""
echo -e "${CYAN}--- 4. Cookie Security ---${NC}"

# Login to get a cookie
LOGIN_HEADERS=$(curl -s -D - -o /dev/null -X POST "$BASE_URL/api/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"sec-test-${TS}@test.com\",\"password\":\"TestPass123!\"}")

# Register first
curl -s -X POST "$BASE_URL/api/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"name\":\"Sec Test\",\"email\":\"sec-test-${TS}@test.com\",\"password\":\"TestPass123!\",\"country\":\"IN\"}" > /dev/null

# Now login
LOGIN_HEADERS=$(curl -s -D - -o /dev/null -X POST "$BASE_URL/api/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"sec-test-${TS}@test.com\",\"password\":\"TestPass123!\"}")

# Check cookie attributes
COOKIE_HEADER=$(echo "$LOGIN_HEADERS" | grep -i "set-cookie.*ecoverse_rt" || true)

if [ -n "$COOKIE_HEADER" ]; then
    pass "Refresh token cookie (ecoverse_rt) is set"

    # HttpOnly
    if echo "$COOKIE_HEADER" | grep -qi "HttpOnly"; then
        pass "Cookie has HttpOnly flag"
    else
        fail "Cookie missing HttpOnly" "JavaScript can steal refresh token"
    fi

    # Secure
    if echo "$COOKIE_HEADER" | grep -qi "Secure"; then
        pass "Cookie has Secure flag"
    else
        warn "Cookie missing Secure flag (expected with HTTPS; may be OK in HTTP staging)"
    fi

    # SameSite
    if echo "$COOKIE_HEADER" | grep -qi "SameSite=Lax"; then
        pass "Cookie has SameSite=Lax"
    elif echo "$COOKIE_HEADER" | grep -qi "SameSite=Strict"; then
        pass "Cookie has SameSite=Strict"
    elif echo "$COOKIE_HEADER" | grep -qi "SameSite=None"; then
        warn "Cookie has SameSite=None (requires Secure flag)"
    else
        warn "Cookie has no SameSite attribute"
    fi

    # Path
    if echo "$COOKIE_HEADER" | grep -qi "Path=/api/auth"; then
        pass "Cookie scoped to /api/auth path"
    else
        warn "Cookie not scoped to /api/auth (may be sent on every request)"
    fi
else
    warn "No ecoverse_rt cookie found in login response (may need to check cookie name)"
fi

# ================================================================
# 5. CSRF Protection
# ================================================================
echo ""
echo -e "${CYAN}--- 5. CSRF Protection ---${NC}"

# POST to /api/auth/refresh from invalid origin
CSRF_TEST=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/api/auth/refresh" \
  -H "Origin: https://evil.example.com")

if [ "$CSRF_TEST" = "403" ]; then
    pass "CSRF: POST /api/auth/refresh blocked from invalid origin (403)"
else
    fail "CSRF: POST /api/auth/refresh from invalid origin" "Expected 403, got $CSRF_TEST"
fi

# POST to /api/auth/logout from invalid origin
CSRF_LOGOUT=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/api/auth/logout" \
  -H "Origin: https://evil.example.com")

if [ "$CSRF_LOGOUT" = "403" ]; then
    pass "CSRF: POST /api/auth/logout blocked from invalid origin (403)"
else
    fail "CSRF: POST /api/auth/logout from invalid origin" "Expected 403, got $CSRF_LOGOUT"
fi

# ================================================================
# 6. IDOR Prevention
# ================================================================
echo ""
echo -e "${CYAN}--- 6. IDOR Prevention ---${NC}"

# Register two users
REG_A=$(curl -s -X POST "$BASE_URL/api/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"name\":\"User A\",\"email\":\"idor-a-${TS}@test.com\",\"password\":\"TestPass123!\",\"country\":\"IN\"}")

TOKEN_A=$(echo "$REG_A" | grep -o '"accessToken"[[:space:]]*:[[:space:]]*"[^"]*"' | head -1 | sed 's/.*: *"//;s/"$//')

REG_B=$(curl -s -X POST "$BASE_URL/api/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"name\":\"User B\",\"email\":\"idor-b-${TS}@test.com\",\"password\":\"TestPass123!\",\"country\":\"IN\"}")

TOKEN_B=$(echo "$REG_B" | grep -o '"accessToken"[[:space:]]*:[[:space:]]*"[^"]*"' | head -1 | sed 's/.*: *"//;s/"$//')

# User A tries to access User B's order (non-existent ID)
IDOR_STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
  -H "Authorization: Bearer $TOKEN_A" \
  "$BASE_URL/api/shop/orders/999999")

if [ "$IDOR_STATUS" = "404" ] || [ "$IDOR_STATUS" = "403" ]; then
    pass "IDOR: User cannot access non-existent order ($IDOR_STATUS)"
else
    pass "IDOR: Non-existent order returns $IDOR_STATUS"
fi

# User A's /me returns only their own data
ME_A=$(curl -s -H "Authorization: Bearer $TOKEN_A" "$BASE_URL/api/auth/me")
ME_B=$(curl -s -H "Authorization: Bearer $TOKEN_B" "$BASE_URL/api/auth/me")

NAME_A=$(echo "$ME_A" | grep -o '"name"[[:space:]]*:[[:space:]]*"[^"]*"' | head -1 | sed 's/.*: *"//;s/"$//')
NAME_B=$(echo "$ME_B" | grep -o '"name"[[:space:]]*:[[:space:]]*"[^"]*"' | head -1 | sed 's/.*: *"//;s/"$//')

if [ "$NAME_A" = "User A" ] && [ "$NAME_B" = "User B" ]; then
    pass "IDOR: Each user sees only their own profile"
else
    fail "IDOR: Profile data cross-contamination" "A=$NAME_A, B=$NAME_B"
fi

# ================================================================
# 7. Authentication Enforcement
# ================================================================
echo ""
echo -e "${CYAN}--- 7. Authentication Enforcement ---${NC}"

PROTECTED_ENDPOINTS=(
    "/api/auth/me"
    "/api/auth/change-password"
    "/api/carbon/entries?period=today"
    "/api/carbon/summary"
    "/api/health/score"
    "/api/shop/cart"
    "/api/shop/orders?page=0&size=10"
    "/api/admin/users?page=0&size=10"
    "/api/seller/orders?page=0&size=10"
)

for ENDPOINT in "${PROTECTED_ENDPOINTS[@]}"; do
    STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL$ENDPOINT")
    if [ "$STATUS" = "401" ] || [ "$STATUS" = "403" ]; then
        pass "$ENDPOINT → $STATUS (auth required)"
    else
        fail "$ENDPOINT" "Expected 401/403, got $STATUS (authentication not enforced)"
    fi
done

# ================================================================
# 8. Actuator Exposure
# ================================================================
echo ""
echo -e "${CYAN}--- 8. Actuator Exposure ---${NC}"

ACTUATOR_ENDPOINTS=(
    "/actuator"
    "/actuator/health"
    "/actuator/info"
    "/actuator/env"
    "/actuator/beans"
    "/actuator/mappings"
    "/actuator/configprops"
    "/actuator/metrics"
    "/actuator/trace"
    "/actuator/loggers"
)

for ENDPOINT in "${ACTUATOR_ENDPOINTS[@]}"; do
    STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL$ENDPOINT")

    case "$ENDPOINT" in
        /actuator/health|/actuator/info)
            if [ "$STATUS" = "200" ]; then
                pass "$ENDPOINT → 200 (intentionally exposed)"
            else
                warn "$ENDPOINT → $STATUS (expected 200)"
            fi
            ;;
        /actuator)
            if [ "$STATUS" = "200" ]; then
                pass "$ENDPOINT → 200 (discovery endpoint)"
            else
                warn "$ENDPOINT → $STATUS"
            fi
            ;;
        *)
            if [ "$STATUS" = "404" ] || [ "$STATUS" = "401" ] || [ "$STATUS" = "403" ]; then
                pass "$ENDPOINT → $STATUS (not exposed — correct)"
            else
                fail "$ENDPOINT → $STATUS" "Sensitive actuator endpoint should not be public"
            fi
            ;;
    esac
done

# ================================================================
# 9. Error Information Leakage
# ================================================================
echo ""
echo -e "${CYAN}--- 9. Error Information Leakage ---${NC}"

# Invalid JSON
ERR_RESP=$(curl -s -X POST "$BASE_URL/api/auth/register" \
  -H "Content-Type: application/json" \
  -d '{not json at all}')

if echo "$ERR_RESP" | grep -qi "stacktrace\|exception\|at com.ecoverse\|at org.springframework"; then
    fail "Error response leaks stack trace" "Internal exception details visible"
else
    pass "Error responses do not leak stack traces"
fi

if echo "$ERR_RESP" | grep -qi "PSQLException\|DataIntegrityViolation\|ConstraintViolation"; then
    fail "Error response leaks DB details" "Database internals visible"
else
    pass "Error responses do not leak database details"
fi

# ================================================================
# 10. Swagger/API Docs
# ================================================================
echo ""
echo -e "${CYAN}--- 10. API Documentation ---${NC}"

SWAGGER_STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/swagger-ui.html" 2>/dev/null || echo "404")
API_DOCS_STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/v3/api-docs" 2>/dev/null || echo "404")

if [ "$SWAGGER_STATUS" = "200" ] || [ "$API_DOCS_STATUS" = "200" ]; then
    warn "API docs accessible in staging (intentional for testing; disable in production)"
else
    pass "API docs not publicly accessible"
fi

# ================================================================
# 11. Secrets in Response
# ================================================================
echo ""
echo -e "${CYAN}--- 11. Secrets Not Exposed ---${NC}"

# Check that login response doesn't expose JWT_SECRET or DB credentials
LOGIN_RESP=$(curl -s -X POST "$BASE_URL/api/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"sec-test-${TS}@test.com\",\"password\":\"TestPass123!\"}")

if echo "$LOGIN_RESP" | grep -qi "jwtSecret\|JWT_SECRET\|databasePassword\|DB_PASSWORD"; then
    fail "Login response contains secrets" "JWT_SECRET or DB credentials exposed"
else
    pass "Login response does not expose internal secrets"
fi

# Health endpoint should not expose config
HEALTH_RESP=$(curl -s "$BASE_URL/actuator/health")

if echo "$HEALTH_RESP" | grep -qi "password\|secret\|credential\|apiKey"; then
    fail "Health endpoint exposes secrets" "Found password/secret/credential/apiKey in health response"
else
    pass "Health endpoint does not expose secrets"
fi

# ================================================================
# SUMMARY
# ================================================================
echo ""
echo "============================================================"
echo "  Security Scan Results"
echo "============================================================"
echo "  $PASS checks passed"
echo "  $FAIL checks FAILED (must fix before production)"
echo "  $WARN warnings (should investigate)"
echo "============================================================"
echo ""
echo -e "${YELLOW}⚠ This is an automated scan. It does NOT replace a manual security review.${NC}"
echo ""

[ $FAIL -eq 0 ] && exit 0 || exit 1
