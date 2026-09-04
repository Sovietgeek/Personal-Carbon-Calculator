# EcoVerse — Staging Deployment Guide

## Table of Contents
1. [Architecture](#architecture)
2. [Prerequisites](#prerequisites)
3. [Step-by-Step Deployment](#step-by-step-deployment)
4. [Environment Variables](#environment-variables)
5. [HTTPS via ngrok](#https-via-ngrok)
6. [Database Migrations](#database-migrations)
7. [Health Verification](#health-verification)
8. [Rollback Procedure](#rollback-procedure)
9. [Known Limitations](#known-limitations)

---

## Architecture

```
Internet → ngrok HTTPS tunnel → localhost:8082 (Spring Boot) → localhost:5433 (PostgreSQL)
```

- **No Kubernetes** — Docker Compose only
- **No Kafka/RabbitMQ** — not needed at this scale
- **No Redis** — not demonstrated need yet
- **ngrok** provides real HTTPS with valid TLS certificate

---

## Prerequisites

| Tool | Version | Purpose |
|------|---------|---------|
| Docker | 20+ | Container runtime |
| Docker Compose | 2.0+ | Multi-container orchestration |
| ngrok | 3+ | HTTPS tunnel (free tier) |
| curl | any | Health verification |
| Java | 17 | Building JAR (optional if using Docker build) |

---

## Step-by-Step Deployment

### 1. Clone and Navigate

```bash
git clone <repo-url>
cd EcoVerse-Complete-Latest
git checkout staging
```

### 2. Create Environment File

```bash
cp .env.staging.example .env.staging
```

Edit `.env.staging` and fill in the required values. **See [Environment Variables](#environment-variables) below.**

**CRITICAL:**
- Generate a unique `JWT_SECRET`: `openssl rand -base64 64 | tr -d '\n'`
- Set `CORS_ORIGINS` to the ngrok URL (after step 4)
- Set `APP_URL` to the ngrok URL (after step 4)
- **NEVER use production secrets in staging**
- **NEVER use Razorpay LIVE keys**

### 3. Build the Docker Image

```bash
cd ecoverse-backend
docker build -t ecoverse-backend:staging .
cd ..
```

Or build the JAR first:

```bash
cd ecoverse-backend
./mvnw clean package -DskipTests
cd ..
```

### 4. Start Staging Infrastructure

```bash
# Start PostgreSQL + Backend
docker compose -f docker-compose.staging.yml --env-file .env.staging up -d

# Wait for backend to be healthy (up to 90 seconds)
echo "Waiting for backend..."
for i in $(seq 1 45); do
  if curl -sf http://localhost:8082/actuator/health > /dev/null 2>&1; then
    echo "✅ Backend is healthy!"
    break
  fi
  echo "Waiting... ($i/45)"
  sleep 2
done
```

### 5. Start ngrok HTTPS Tunnel

```bash
ngrok http 8082
```

Copy the ngrok HTTPS URL (e.g., `https://xxxx.ngrok-free.app`).

### 6. Update CORS and APP_URL

Edit `.env.staging`:
```
CORS_ORIGINS=https://xxxx.ngrok-free.app
APP_URL=https://xxxx.ngrok-free.app
```

Restart the backend:
```bash
docker compose -f docker-compose.staging.yml --env-file .env.staging restart backend
```

Wait for healthy:
```bash
for i in $(seq 1 30); do
  if curl -sf http://localhost:8082/actuator/health > /dev/null 2>&1; then
    echo "✅ Backend restarted with new CORS!"
    break
  fi
  sleep 2
done
```

### 7. Verify Deployment

```bash
# Health check
curl -s https://xxxx.ngrok-free.app/actuator/health | jq .

# Or using the startup script:
./scripts/start-staging.sh
```

### 8. Run Verification Scripts

```bash
# Set the ngrok URL
export STAGING_URL=https://xxxx.ngrok-free.app

# CORS verification
./scripts/verify-cors.sh $STAGING_URL $STAGING_URL

# Authentication flow
./scripts/verify-auth.sh $STAGING_URL

# CSRF protection
./scripts/verify-csrf.sh $STAGING_URL $STAGING_URL

# Webhook endpoint
./scripts/verify-webhook.sh $STAGING_URL

# User journeys
./scripts/verify-user-journeys.sh $STAGING_URL

# Multi-user security
./scripts/verify-multi-user-security.sh $STAGING_URL

# Error handling
./scripts/verify-error-handling.sh $STAGING_URL

# Performance smoke test
./scripts/verify-performance.sh $STAGING_URL

# Full security scan
./scripts/verify-security.sh $STAGING_URL

# Razorpay TEST (requires test credentials)
./scripts/verify-razorpay-test.sh $STAGING_URL
```

---

## Environment Variables

### Required (Mandatory — app will not start without these)

| Variable | Example | Description |
|----------|---------|-------------|
| `POSTGRES_PASSWORD` | (generated) | PostgreSQL password |
| `JWT_SECRET` | (base64, 64+ bytes) | HMAC-SHA512 key for JWT |
| `CORS_ORIGINS` | `https://xxxx.ngrok-free.app` | Allowed frontend origins (comma-separated) |
| `APP_URL` | `https://xxxx.ngrok-free.app` | Public-facing app URL |

### Database

| Variable | Default | Description |
|----------|---------|-------------|
| `POSTGRES_DB` | `ecoverse_staging` | Database name |
| `POSTGRES_USER` | `ecoverse` | Database user |
| `POSTGRES_PASSWORD` | (required) | Database password |
| `POSTGRES_PORT` | `5433` | External port (avoids dev conflict) |

### Backend

| Variable | Default | Description |
|----------|---------|-------------|
| `BACKEND_PORT` | `8082` | External port (avoids dev conflict) |
| `SPRING_PROFILES_ACTIVE` | `staging` | Spring profile (hardcoded in compose) |
| `JAVA_OPTS` | `-Xmx512m -Xms256m -XX:+UseG1GC` | JVM options |

### JWT

| Variable | Default | Description |
|----------|---------|-------------|
| `JWT_SECRET` | (required) | Secret key for JWT signing |
| `JWT_ACCESS_EXPIRATION` | `900` | Access token lifetime (seconds) |
| `JWT_REFRESH_EXPIRATION` | `604800` | Refresh token lifetime (7 days) |

### Cookie

| Variable | Default | Description |
|----------|---------|-------------|
| `COOKIE_SECURE` | `true` | Set Secure flag on cookies |
| `COOKIE_SAMESITE` | `Lax` | SameSite attribute |

### Google OAuth2

| Variable | Default | Description |
|----------|---------|-------------|
| `GOOGLE_CLIENT_ID` | `dummy-id` | Google OAuth2 client ID |
| `GOOGLE_CLIENT_SECRET` | `dummy-secret` | Google OAuth2 client secret |

### Razorpay (TEST MODE ONLY)

| Variable | Default | Description |
|----------|---------|-------------|
| `RAZORPAY_KEY_ID` | (empty) | Razorpay TEST key ID (rzp_test_*) |
| `RAZORPAY_KEY_SECRET` | (empty) | Razorpay TEST key secret |
| `RAZORPAY_WEBHOOK_SECRET` | (empty) | Webhook signature secret |

**🚫 NEVER use Razorpay LIVE keys in staging!**

### Email SMTP

| Variable | Default | Description |
|----------|---------|-------------|
| `SPRING_MAIL_HOST` | (empty) | SMTP host |
| `SPRING_MAIL_PORT` | `587` | SMTP port |
| `SPRING_MAIL_USERNAME` | (empty) | SMTP username |
| `SPRING_MAIL_PASSWORD` | (empty) | SMTP password |

### External APIs

| Variable | Default | Description |
|----------|---------|-------------|
| `OPEN_METEO_BASE_URL` | `https://api.open-meteo.com/v1` | Weather API |
| `RSS2JSON_BASE_URL` | `https://api.rss2json.com/v1/api.json` | News API |
| `GEMINI_API_KEY` | (empty) | Google Gemini AI API key |
| `GEMINI_BASE_URL` | `https://generativelanguage.googleapis.com/v1beta` | Gemini API URL |
| `GEMINI_MODEL` | `gemini-2.0-flash` | Gemini model |
| `NEWSAPI_KEY` | (empty) | NewsAPI key |

---

## HTTPS via ngrok

ngrok provides:
- Real HTTPS with valid TLS certificate
- Internet-accessible URL for webhooks
- Secure cookie testing

### Setting up ngrok

1. Sign up at [ngrok.com](https://ngrok.com) (free tier)
2. Install: `choco install ngrok` or download from ngrok.com
3. Authenticate: `ngrok config add-authtoken YOUR_TOKEN`
4. Start tunnel: `ngrok http 8082`
5. Copy the HTTPS URL shown

### Important Notes

- ngrok free tier URL changes every restart — update `CORS_ORIGINS` and `APP_URL`
- ngrok adds `X-Forwarded-Proto`, `X-Forwarded-For`, `Host` headers automatically
- Spring Boot `server.forward-headers-strategy: FRAMEWORK` processes these headers
- The `ProductionStartupValidator` verifies `X-Forwarded-Proto: https` in staging mode

---

## Database Migrations

Flyway runs automatically on startup:

1. **First deploy**: All migrations V1–V16 run sequentially
2. **Subsequent deploys**: Only new migrations run
3. **Flyway is forward-only**: No auto-rollback

### Verify Migrations

```bash
# Check Flyway schema history
docker compose -f docker-compose.staging.yml exec db \
  psql -U ecoverse -d ecoverse_staging -c "SELECT version, description, installed_on FROM flyway_schema_history ORDER BY installed_rank;"
```

### Test on Clean Database

```bash
# Destroy and recreate
docker compose -f docker-compose.staging.yml down -v
docker compose -f docker-compose.staging.yml --env-file .env.staging up -d
```

---

## Health Verification

```bash
# Basic health
curl -s https://YOUR_NGROK_URL/actuator/health

# Application info
curl -s https://YOUR_NGROK_URL/actuator/info

# Auth endpoint
curl -s -X POST https://YOUR_NGROK_URL/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"TestPass123!"}'
```

---

## Rollback Procedure

### Application Rollback

1. **Before each deployment**, tag the current Docker image:
   ```bash
   docker tag ecoverse-backend:staging-latest ecoverse-backend:staging-$(date +%Y%m%d-%H%M%S)
   ```

2. **On failure**, revert to the previous image:
   ```bash
   # List available tags
   docker images | grep ecoverse-backend

   # Revert to specific tag
   docker tag ecoverse-backend:staging-YYYYMMDD-HHMMSS ecoverse-backend:staging-latest
   docker compose -f docker-compose.staging.yml restart backend
   ```

### Database Rollback

**⚠️ Flyway migrations are forward-only. There is NO automatic rollback.**

- If a migration fails, the database is locked — manual intervention required
- Always test migrations on a copy of the database first
- Consider `flyway repair` if a migration partially applied

---

## Known Limitations

| Limitation | Impact | Mitigation |
|-----------|--------|------------|
| ngrok URL changes on restart | CORS/webhook URLs must be updated | Use ngrok paid tier for stable subdomain |
| No auto-deploy | Manual deployment steps | CI/CD workflow builds/tests but does not deploy |
| No Redis | Session state in JWT (stateless) | Add Redis if sticky sessions or caching needed |
| No Kafka | Async operations are synchronous | Add message queue if event-driven architecture needed |
| Email not tested | Verification/password reset emails not verified | Configure SMTP or Mailtrap for staging |
| Razorpay TEST | Only works with test credentials | Mark as NOT VERIFIED if no credentials provided |
