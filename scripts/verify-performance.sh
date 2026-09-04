#!/bin/bash
# ================================================================
# EcoVerse — Performance Smoke Test (Phase 8, Part S)
#
# NOT destructive load testing — just verifies reasonable response
# times for key endpoints and checks for obvious performance issues.
# ================================================================
set -euo pipefail

BASE_URL="${1:-http://localhost:8082}"
MAX_TIME_MS=3000  # 3 seconds threshold for individual requests

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

PASS=0
FAIL=0
SLOW=0

pass() { echo -e "  ${GREEN}✓${NC} $1"; PASS=$((PASS+1)); }
fail() { echo -e "  ${RED}✗${NC} $1 — $2"; FAIL=$((FAIL+1)); }
slow() { echo -e "  ${YELLOW}⚠${NC} $1 (SLOW: ${2}ms)"; SLOW=$((SLOW+1)); }

TS=$(date +%s)

echo "============================================================"
echo "  Performance Smoke Test — $BASE_URL"
echo "  Threshold: ${MAX_TIME_MS}ms per request"
echo "============================================================"
echo ""

# Helper: Measure response time in milliseconds
measure() {
    local start end
    start=$(date +%s%N)
    RESPONSE=$(curl -s -w "\n%{http_code}" "$@")
    end=$(date +%s%N)
    ELAPSED_MS=$(( (end - start) / 1000000 ))
    HTTP_CODE=$(echo "$RESPONSE" | tail -1)
    BODY=$(echo "$RESPONSE" | sed '$d')
}

# ================================================================
# 1. Actuator health
# ================================================================
echo -e "${CYAN}--- 1. Actuator health ---${NC}"

measure "$BASE_URL/actuator/health"

if [ "$HTTP_CODE" = "200" ]; then
    if [ $ELAPSED_MS -lt $MAX_TIME_MS ]; then
        pass "Health check: ${ELAPSED_MS}ms"
    else
        slow "Health check" $ELAPSED_MS
    fi
else
    fail "Health check" "HTTP $HTTP_CODE"
fi

# ================================================================
# 2. Register + Login
# ================================================================
echo ""
echo -e "${CYAN}--- 2. Registration ---${NC}"

measure -X POST "$BASE_URL/api/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"name\":\"Perf Test\",\"email\":\"perf-${TS}@test.com\",\"password\":\"TestPass123!\",\"country\":\"IN\"}"

if [ $ELAPSED_MS -lt $MAX_TIME_MS ]; then
    pass "Registration: ${ELAPSED_MS}ms"
else
    slow "Registration" $ELAPSED_MS
fi

echo ""
echo -e "${CYAN}--- 3. Login ---${NC}"

measure -X POST "$BASE_URL/api/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"perf-${TS}@test.com\",\"password\":\"TestPass123!\"}"

TOKEN=$(echo "$BODY" | grep -o '"accessToken"[[:space:]]*:[[:space:]]*"[^"]*"' | head -1 | sed 's/.*: *"//;s/"$//')

if [ $ELAPSED_MS -lt $MAX_TIME_MS ]; then
    pass "Login: ${ELAPSED_MS}ms"
else
    slow "Login" $ELAPSED_MS
fi

AUTH="Authorization: Bearer $TOKEN"

# ================================================================
# 4. Get profile
# ================================================================
echo ""
echo -e "${CYAN}--- 4. Get profile (/api/auth/me) ---${NC}"

measure -H "$AUTH" "$BASE_URL/api/auth/me"

if [ $ELAPSED_MS -lt $MAX_TIME_MS ]; then
    pass "Profile: ${ELAPSED_MS}ms"
else
    slow "Profile" $ELAPSED_MS
fi

# ================================================================
# 5. Product listing
# ================================================================
echo ""
echo -e "${CYAN}--- 5. Product listing ---${NC}"

measure "$BASE_URL/api/shop/products?page=0&size=20"

if [ $ELAPSED_MS -lt $MAX_TIME_MS ]; then
    pass "Product listing: ${ELAPSED_MS}ms"
else
    slow "Product listing" $ELAPSED_MS
fi

# ================================================================
# 6. Product search
# ================================================================
echo ""
echo -e "${CYAN}--- 6. Product search ---${NC}"

measure "$BASE_URL/api/shop/products?keyword=eco&page=0&size=20"

if [ $ELAPSED_MS -lt $MAX_TIME_MS ]; then
    pass "Product search: ${ELAPSED_MS}ms"
else
    slow "Product search" $ELAPSED_MS
fi

# ================================================================
# 7. Carbon entry creation
# ================================================================
echo ""
echo -e "${CYAN}--- 7. Carbon entry creation ---${NC}"

measure -X POST "$BASE_URL/api/carbon/entries" \
  -H "$AUTH" \
  -H "Content-Type: application/json" \
  -d '{"category":"transport","type":"car-petrol","distance":10}'

if [ $ELAPSED_MS -lt $MAX_TIME_MS ]; then
    pass "Carbon entry: ${ELAPSED_MS}ms"
else
    slow "Carbon entry" $ELAPSED_MS
fi

# ================================================================
# 8. Carbon summary
# ================================================================
echo ""
echo -e "${CYAN}--- 8. Carbon summary ---${NC}"

measure -H "$AUTH" "$BASE_URL/api/carbon/summary"

if [ $ELAPSED_MS -lt $MAX_TIME_MS ]; then
    pass "Carbon summary: ${ELAPSED_MS}ms"
else
    slow "Carbon summary" $ELAPSED_MS
fi

# ================================================================
# 9. Cart operations
# ================================================================
echo ""
echo -e "${CYAN}--- 9. Cart view ---${NC}"

measure -H "$AUTH" "$BASE_URL/api/shop/cart"

if [ $ELAPSED_MS -lt $MAX_TIME_MS ]; then
    pass "Cart view: ${ELAPSED_MS}ms"
else
    slow "Cart view" $ELAPSED_MS
fi

# ================================================================
# 10. Order history
# ================================================================
echo ""
echo -e "${CYAN}--- 10. Order history ---${NC}"

measure -H "$AUTH" "$BASE_URL/api/shop/orders?page=0&size=20"

if [ $ELAPSED_MS -lt $MAX_TIME_MS ]; then
    pass "Order history: ${ELAPSED_MS}ms"
else
    slow "Order history" $ELAPSED_MS
fi

# ================================================================
# 11. Weather endpoint
# ================================================================
echo ""
echo -e "${CYAN}--- 11. Weather ---${NC}"

measure -H "$AUTH" "$BASE_URL/api/weather?lat=28.6139&lon=77.2090"

if [ $ELAPSED_MS -lt 5000 ]; then
    pass "Weather: ${ELAPSED_MS}ms"
else
    slow "Weather (external API)" $ELAPSED_MS
fi

# ================================================================
# 12. News endpoint
# ================================================================
echo ""
echo -e "${CYAN}--- 12. News ---${NC}"

measure -H "$AUTH" "$BASE_URL/api/news?page=0&size=10"

if [ $ELAPSED_MS -lt 5000 ]; then
    pass "News: ${ELAPSED_MS}ms"
else
    slow "News (external API)" $ELAPSED_MS
fi

# ================================================================
# 13. Actuator info
# ================================================================
echo ""
echo -e "${CYAN}--- 13. Actuator info ---${NC}"

measure "$BASE_URL/actuator/info"

if [ $ELAPSED_MS -lt $MAX_TIME_MS ]; then
    pass "Actuator info: ${ELAPSED_MS}ms"
else
    slow "Actuator info" $ELAPSED_MS
fi

# ================================================================
# 14. Concurrent requests (5 simultaneous)
# ================================================================
echo ""
echo -e "${CYAN}--- 14. Concurrent requests (5 simultaneous) ---${NC}"

START_CONCURRENT=$(date +%s%N)

for i in 1 2 3 4 5; do
    curl -s -o /dev/null -w "%{http_code}\n" "$BASE_URL/api/shop/products?page=0&size=5" &
done

# Wait for all background jobs
wait

END_CONCURRENT=$(date +%s%N)
CONCURRENT_MS=$(( (END_CONCURRENT - START_CONCURRENT) / 1000000 ))

if [ $CONCURRENT_MS -lt 5000 ]; then
    pass "5 concurrent requests: ${CONCURRENT_MS}ms total"
else
    slow "5 concurrent requests" $CONCURRENT_MS
fi

# ================================================================
# SUMMARY
# ================================================================
echo ""
echo "============================================================"
echo "  Performance Smoke Test Results"
echo "============================================================"
echo "  $PASS endpoints within ${MAX_TIME_MS}ms threshold"
echo "  $SLOW endpoints slower than ${MAX_TIME_MS}ms (investigate)"
echo "  $FAIL endpoints failed"
echo ""
echo "  NOTE: This is a smoke test, not load testing."
echo "  Response times depend on hardware, network, and data size."
echo "============================================================"

[ $FAIL -eq 0 ] && exit 0 || exit 1
