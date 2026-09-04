#!/bin/bash
# ================================================================
# EcoVerse — Staging Startup Script
# Starts Docker Compose (staging) + ngrok HTTPS tunnel
# ================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
ENV_FILE="$PROJECT_DIR/.env.staging"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo "============================================================"
echo "  EcoVerse — Staging Environment Startup"
echo "============================================================"
echo ""

# --- Check prerequisites ---
check_prereq() {
    if ! command -v "$1" &>/dev/null; then
        echo -e "${RED}ERROR: $1 is not installed. Please install it first.${NC}"
        exit 1
    fi
}

check_prereq docker
check_prereq ngrok

# --- Check .env.staging ---
if [ ! -f "$ENV_FILE" ]; then
    echo -e "${YELLOW}No .env.staging file found.${NC}"
    echo "Creating from template..."
    cp "$PROJECT_DIR/.env.staging.example" "$ENV_FILE"
    echo -e "${RED}ACTION REQUIRED: Edit .env.staging with your staging secrets.${NC}"
    echo "  Required: POSTGRES_PASSWORD, JWT_SECRET, CORS_ORIGINS, APP_URL"
    echo "  File: $ENV_FILE"
    exit 1
fi

# --- Check required variables ---
source "$ENV_FILE"

MISSING=0
for var in POSTGRES_PASSWORD JWT_SECRET CORS_ORIGINS APP_URL; do
    val="${!var:-}"
    if [ -z "$val" ]; then
        echo -e "${RED}MISSING: $var must be set in .env.staging${NC}"
        MISSING=1
    fi
done

if [ "$MISSING" -eq 1 ]; then
    echo -e "${RED}Fix the missing variables in .env.staging and re-run.${NC}"
    exit 1
fi

# --- Stop any existing staging environment ---
echo "Stopping existing staging environment (if any)..."
docker compose -f "$PROJECT_DIR/docker-compose.staging.yml" --env-file "$ENV_FILE" down 2>/dev/null || true

# --- Start Docker Compose ---
echo ""
echo "Starting Docker Compose (staging profile)..."
docker compose -f "$PROJECT_DIR/docker-compose.staging.yml" --env-file "$ENV_FILE" up -d

# --- Wait for backend to be healthy ---
echo ""
echo "Waiting for backend to become healthy (up to 90 seconds)..."
attempts=0
max_attempts=18
while [ $attempts -lt $max_attempts ]; do
    if curl -sf "http://localhost:${BACKEND_PORT:-8082}/actuator/health" >/dev/null 2>&1; then
        echo -e "${GREEN}Backend is healthy!${NC}"
        break
    fi
    attempts=$((attempts + 1))
    echo "  Attempt $attempts/$max_attempts — waiting 5 seconds..."
    sleep 5
done

if [ $attempts -eq $max_attempts ]; then
    echo -e "${YELLOW}Backend did not become healthy within timeout.${NC}"
    echo "Check logs: docker compose -f docker-compose.staging.yml logs backend"
    echo ""
    echo "Continuing anyway (backend may still be starting)..."
fi

# --- Start ngrok ---
echo ""
echo "Starting ngrok HTTPS tunnel on port ${BACKEND_PORT:-8082}..."
echo "This provides a real HTTPS URL for staging."
echo ""

# Start ngrok in background
ngrok http ${BACKEND_PORT:-8082} --log=stdout &
NGROK_PID=$!

echo "ngrok PID: $NGROK_PID"
echo ""
echo "============================================================"
echo "  Staging Environment is running!"
echo "============================================================"
echo ""
echo "  Backend:    http://localhost:${BACKEND_PORT:-8082}"
echo "  Health:     http://localhost:${BACKEND_PORT:-8082}/actuator/health"
echo "  API Docs:   http://localhost:${BACKEND_PORT:-8082}/swagger-ui.html"
echo "  PostgreSQL: localhost:${POSTGRES_PORT:-5433}"
echo ""
echo "  ngrok URL:  Check https://dashboard.ngrok.com or:"
echo "              curl -s http://localhost:4040/api/tunnels | jq -r '.tunnels[0].public_url'"
echo ""
echo -e "${YELLOW}IMPORTANT: After ngrok starts, update these in .env.staging:${NC}"
echo "  1. CORS_ORIGINS=<ngrok-https-url>"
echo "  2. APP_URL=<ngrok-https-url>"
echo "  3. Restart: docker compose -f docker-compose.staging.yml restart backend"
echo ""
echo "  To stop: kill $NGROK_PID && docker compose -f docker-compose.staging.yml down"
echo "============================================================"
