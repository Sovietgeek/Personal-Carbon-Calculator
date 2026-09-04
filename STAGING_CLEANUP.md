# EcoVerse — Staging Cleanup Guide

## Overview

This document describes how to clean up the staging environment:
- Reset test data
- Remove test accounts
- Rotate staging secrets
- Reset the staging database
- Shut down staging infrastructure

---

## 1. Stop Staging Infrastructure

```bash
# Stop all containers (preserves data volumes)
docker compose -f docker-compose.staging.yml down

# Stop and DELETE data volumes (full reset)
docker compose -f docker-compose.staging.yml down -v

# Stop and remove images
docker compose -f docker-compose.staging.yml down -v --rmi local
```

---

## 2. Stop ngrok

Press `Ctrl+C` in the ngrok terminal, or:

```bash
# Kill ngrok process
pkill ngrok
```

---

## 3. Reset Test Data (SQL)

Connect to the staging database:

```bash
docker compose -f docker-compose.staging.yml exec db \
  psql -U ecoverse -d ecoverse_staging
```

### 3a. Delete All Test Orders and Payments

```sql
-- Delete in dependency order
DELETE FROM payment_events;
DELETE FROM payment_attempts;
DELETE FROM order_items;
DELETE FROM orders;
DELETE FROM cart_items;
DELETE FROM products;
DELETE FROM carbon_entries;
DELETE FROM health_logs;
DELETE FROM notes;
```

### 3b. Delete All Test Users (Except Admin)

```sql
-- Delete non-admin users
DELETE FROM users WHERE role != 'ADMIN';

-- Or delete ALL users (including admin)
DELETE FROM users;
```

### 3c. Full Database Reset

```sql
-- Truncate all tables (reset auto-increment)
TRUNCATE TABLE users, products, orders, order_items,
  cart_items, carbon_entries, health_logs, notes,
  payment_events, payment_attempts
  RESTART IDENTITY CASCADE;
```

### 3d. Verify Cleanup

```sql
-- Check all tables are empty
SELECT 'users' AS tbl, COUNT(*) FROM users
UNION ALL SELECT 'products', COUNT(*) FROM products
UNION ALL SELECT 'orders', COUNT(*) FROM orders
UNION ALL SELECT 'cart_items', COUNT(*) FROM cart_items
UNION ALL SELECT 'carbon_entries', COUNT(*) FROM carbon_entries
UNION ALL SELECT 'payment_events', COUNT(*) FROM payment_events;
```

---

## 4. Rotate Staging Secrets

After each staging cycle, rotate these secrets:

### 4a. JWT Secret

```bash
# Generate new JWT secret
NEW_JWT_SECRET=$(openssl rand -base64 64 | tr -d '\n')
echo "New JWT_SECRET: $NEW_JWT_SECRET"
```

Update `.env.staging`:
```
JWT_SECRET=<new-secret>
```

### 4b. Database Password

```bash
# Generate new DB password
NEW_DB_PASSWORD=$(openssl rand -base64 32 | tr -d '\n')
echo "New POSTGRES_PASSWORD: $NEW_DB_PASSWORD"
```

Update `.env.staging`:
```
POSTGRES_PASSWORD=<new-password>
```

Then recreate the database container:
```bash
docker compose -f docker-compose.staging.yml down -v
# Update .env.staging
docker compose -f docker-compose.staging.yml --env-file .env.staging up -d
```

### 4c. Razorpay Webhook Secret

If you configured a Razorpay webhook secret, generate a new one from the Razorpay Dashboard.

### 4d. Google OAuth2

If you configured Google OAuth2 for staging:
1. Go to Google Cloud Console
2. Update the authorized redirect URI
3. Regenerate the client secret if needed

---

## 5. Clean Up Docker Resources

```bash
# Remove staging images
docker rmi ecoverse-backend:staging-latest
docker rmi $(docker images | grep "ecoverse-backend:staging" | awk '{print $3}')

# Remove dangling images
docker image prune -f

# Remove unused volumes
docker volume prune -f

# Remove staging network
docker network rm ecoverse-staging-network 2>/dev/null || true
```

---

## 6. Clean Up Local Files

```bash
# Remove cookie jar files
rm -f /tmp/ecoverse-*-cookies.txt

# Remove error response temp files
rm -f /tmp/err_resp.txt

# Remove any downloaded ngrok binaries (if installed manually)
# rm -f /usr/local/bin/ngrok
```

---

## 7. Verify Complete Cleanup

```bash
# No running containers
docker ps | grep ecoverse || echo "✅ No ecoverse containers running"

# No staging volumes
docker volume ls | grep staging || echo "✅ No staging volumes"

# No staging images
docker images | grep ecoverse-backend:staging || echo "✅ No staging images"

# No ngrok process
pgrep ngrok || echo "✅ No ngrok process"

# Staging env file should NOT contain real secrets
grep -E "(rzp_live_|sk_live_)" .env.staging && echo "❌ LIVE keys found!" || echo "✅ No LIVE keys in staging env"
```

---

## 8. Pre-Production Checklist

Before moving from staging to production:

- [ ] All staging secrets rotated
- [ ] No test data in database
- [ ] No Razorpay LIVE keys used
- [ ] ngrok tunnel stopped
- [ ] `.env.staging` file deleted or secured
- [ ] Docker images cleaned up
- [ ] All verification scripts passed
- [ ] Security scan passed with no FAIL results
- [ ] Production readiness gap analysis reviewed

---

## Emergency: Something Went Wrong

### Database is corrupted
```bash
docker compose -f docker-compose.staging.yml down -v
docker compose -f docker-compose.staging.yml --env-file .env.staging up -d
# Fresh database with Flyway migrations will be created
```

### Backend won't start
```bash
# Check logs
docker compose -f docker-compose.staging.yml logs backend

# Common issues:
# - JWT_SECRET not set → generate and set in .env.staging
# - CORS_ORIGINS contains * → must be explicit origin
# - Database not ready → wait for db health check
```

### Port conflicts
```bash
# Check what's using ports
netstat -tlnp | grep 8082
netstat -tlnp | grep 5433

# Change ports in .env.staging if needed
```
