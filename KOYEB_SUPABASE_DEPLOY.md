# EcoVerse — Koyeb + Supabase Deployment Guide

## Architecture

- **App:** Koyeb Free Tier (Nano instance, 256MB RAM, always on)
- **Database:** Supabase Free Tier (PostgreSQL, pooled connections via port 6543)
- **Heartbeat:** GitHub Actions runs `SELECT 1` every 2 days to prevent DB pause

## Prerequisites

- GitHub account with this repo
- [Koyeb account](https://app.koyeb.com/) (free)
- [Supabase account](https://supabase.com/) (free)

---

## Step 1: Create Supabase Project

1. Go to [supabase.com/dashboard](https://supabase.com/dashboard)
2. Click **New Project**
3. Set name: `ecoverse`
4. Set database password (save it securely)
5. **Region: Singapore (ap-southeast-1)** — lowest latency from India
6. Click **Create new project** (takes ~2 minutes)

### Get Connection String

1. Go to **Settings → Database → Connection string**
2. Select **Transaction** mode (pooled, port 6543)
3. Copy the connection string — format:
   ```
   postgresql://postgres.[project-ref]:[password]@aws-0-ap-southeast-1.pooler.supabase.com:6543/postgres
   ```
4. Save this as `DATABASE_URL` for Koyeb

### Run Flyway Migrations

Since Supabase is a fresh database, Flyway will auto-run all migrations (V1–V25) on first app start. No manual SQL needed.

If you want to migrate data from Render's existing database:

```bash
# Export from Render PostgreSQL
pg_dump "postgresql://user:pass@render-host:port/dbname" > ecoverse_backup.sql

# Import into Supabase (use direct connection port 5432 for imports)
psql "postgresql://postgres.[ref]:[password]@db.[ref].supabase.co:5432/postgres" < ecoverse_backup.sql
```

---

## Step 2: Deploy to Koyeb

### Option A: GitHub Auto-Deploy (Recommended)

1. Go to [app.koyeb.com](https://app.koyeb.com/)
2. Click **Create Service → Docker**
3. Connect your GitHub repository
4. Configure:
   - **Name:** `ecoverse`
   - **Dockerfile path:** `ecoverse-backend/Dockerfile`
   - **Builder:** Docker
   - **Instance:** Nano (Free)
   - **Region:** Singapore (sgp)
   - **Entrypoint override:** `/app/koyeb-start.sh`
   - **Port:** `8081`

5. Set Environment Variables:

| Variable | Value |
|----------|-------|
| `DATABASE_URL` | Supabase pooled connection string (from Step 1) |
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `JWT_SECRET` | Generate: `openssl rand -base64 48` |
| `GEMINI_API_KEY` | Your Gemini API key |
| `ADMIN_EMAIL` | `admin@ecoverse.app` |
| `ADMIN_PASSWORD` | Your admin password |
| `CORS_ORIGINS` | `https://your-app-name.koyeb.app` |
| `COOKIE_SECURE` | `true` |
| `SERVER_FORWARD_HEADERS_STRATEGY` | `FRAMEWORK` |
| `APP_URL` | `https://your-app-name.koyeb.app` |

6. Click **Deploy**

### Option B: Koyeb CLI

```bash
# Install Koyeb CLI
npm install -g @koyeb/cli

# Login
koyeb login

# Create service
koyeb service create ecoverse \
  --docker ecoverse-backend/Dockerfile \
  --docker-entrypoint "/app/koyeb-start.sh" \
  --region sgp \
  --instance-type nano \
  --port 8081 \
  --env DATABASE_URL="postgresql://..." \
  --env SPRING_PROFILES_ACTIVE=prod \
  --env JWT_SECRET="$(openssl rand -base64 48)" \
  --env GEMINI_API_KEY="your-key" \
  --env ADMIN_EMAIL="admin@ecoverse.app" \
  --env ADMIN_PASSWORD="your-password" \
  --env CORS_ORIGINS="https://your-app.koyeb.app" \
  --env COOKIE_SECURE=true \
  --env SERVER_FORWARD_HEADERS_STRATEGY=FRAMEWORK \
  --env APP_URL="https://your-app.koyeb.app"
```

---

## Step 3: Set Up Heartbeat

The heartbeat workflow is already in `.github/workflows/supabase-heartbeat.yml`.

1. Go to your GitHub repo → **Settings → Secrets and variables → Actions**
2. Add secret:
   - Name: `SUPABASE_DATABASE_URL`
   - Value: Same pooled connection string from Step 1
3. Test manually: **Actions → Supabase Heartbeat → Run workflow**

This runs `SELECT 1` every 2 days, preventing Supabase from pausing the free tier database.

---

## Step 4: Verify

1. Open `https://your-app-name.koyeb.app/`
2. Login with test credentials or register
3. Check AI chat works (if GEMINI_API_KEY is set)
4. Check admin panel at `/` → Admin Panel tab

---

## JVM Memory Configuration

The Dockerfile sets these defaults for 256MB RAM:

```
-Xmx180m    # Max heap: 180MB
-Xms128m    # Initial heap: 128MB
-XX:+UseSerialGC  # Low-memory GC
-XX:MaxMetaspaceSize=64m  # Cap metaspace
```

Override via `JAVA_OPTS` env var if needed.

---

## Troubleshooting

### App crashes with OOM
- Increase `JAVA_OPTS`: `-Xmx200m -Xms128m`
- Or reduce HikariCP pool: set `HIKARI_MAX_POOL=5`

### Database connection timeout
- Ensure using **pooled endpoint** (port 6543), not direct (5432)
- Check Supabase dashboard → Database is not paused
- Verify `DATABASE_URL` format matches koyeb-start.sh parsing

### Flyway migration fails
- Check Koyeb logs for specific migration error
- Supabase may have different constraints than Render PostgreSQL
- Set `FLYWAY_REPAIR_ENABLED=true` if checksum mismatch

### Cold start / slow response
- Koyeb Nano should NOT have cold starts (always on)
- If experiencing delays, check Koyeb dashboard for restart loops
- May indicate OOM — check memory usage in Koyeb metrics

---

## Comparison: Render vs Koyeb + Supabase

| Feature | Render (old) | Koyeb + Supabase (new) |
|---------|-------------|----------------------|
| Cold Start | ❌ 15min idle sleep | ✅ Always on |
| Keep-Alive Needed | ❌ Yes | ✅ No |
| DB Free Duration | ❌ 90 days | ✅ Forever (with heartbeat) |
| RAM | 512MB | 256MB |
| Latency (India) | ~200ms | ~50ms (SG region) |
| Monthly Cost | $0 | $0 |