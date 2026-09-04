# Database Backup & Restore Procedure

## Overview

EcoVerse uses PostgreSQL running in Docker. This document describes the backup and restore procedure for production-like deployments.

## Automated Backup

### Daily Backup (recommended for production)

```bash
# Add to crontab (runs at 2 AM daily)
0 2 * * * docker exec ecoverse-db pg_dump -U ecoverse -d ecoverse --clean --if-exists | gzip > /backups/ecoverse_$(date +\%Y\%m\%d).sql.gz
```

### Manual Backup

```bash
# Using Docker Compose
docker compose exec db pg_dump -U ecoverse -d ecoverse --clean --if-exists > backup.sql

# Using docker exec directly
docker exec ecoverse-db pg_dump -U ecoverse -d ecoverse --clean --if-exists > backup.sql
```

## Restore Procedure

### From Backup File

```bash
# 1. Stop the application (to prevent writes during restore)
docker compose stop backend

# 2. Drop and recreate the database
docker compose exec db psql -U ecoverse -d postgres -c "DROP DATABASE IF EXISTS ecoverse;"
docker compose exec db psql -U ecoverse -d postgres -c "CREATE DATABASE ecoverse;"

# 3. Restore from backup
docker compose exec -T db psql -U ecoverse -d ecoverse < backup.sql

# 4. Restart the application
docker compose start backend

# 5. Verify
curl -s http://localhost:8081/actuator/health | jq .
```

### From Compressed Backup

```bash
gunzip -c backup.sql.gz | docker compose exec -T db psql -U ecoverse -d ecoverse
```

## Verification Drill

A test script is provided at `scripts/backup-restore-test.sh` that:
1. Creates representative test data
2. Takes a full backup
3. Drops the database
4. Restores from backup
5. Verifies record counts and relationships

```bash
# Run the drill
chmod +x scripts/backup-restore-test.sh
./scripts/backup-restore-test.sh
```

## Backup Retention

| Environment | Frequency | Retention | Storage |
|-------------|-----------|----------|---------|
| Development | On-demand | Manual delete | Local filesystem |
| Staging | Daily | 7 days | `/backups/` volume |
| Production | Every 6 hours | 30 days | Off-site (S3/GCS) |

## What Gets Backed Up

- All user data (users, profiles, refresh tokens)
- All business data (products, orders, order items, cart items)
- All payment data (payment attempts, payment events)
- All tracking data (carbon entries, health logs, notes)
- All audit data (audit logs)
- Flyway schema history (migration tracking)

## What Does NOT Get Backed Up

- Docker images (rebuild from source)
- Application logs (ephemeral, use centralized logging in production)
- Environment variables (stored in .env, must be backed up separately)

## Disaster Recovery

In case of complete data loss:

1. Provision a new PostgreSQL instance
2. Set `SPRING_PROFILES_ACTIVE=dev` to run Flyway migrations (creates schema)
3. Restore latest backup: `psql -U ecoverse -d ecoverse < backup.sql`
4. Verify Flyway schema: `SELECT COUNT(*) FROM flyway_schema_history;` (should be ≥ 16)
5. Start the application and verify health endpoint
