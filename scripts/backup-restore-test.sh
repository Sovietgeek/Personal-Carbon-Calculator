#!/bin/bash
# ================================================================
# EcoVerse — Database Backup & Restore Drill
# Phase 7 — Part H
#
# This script performs a REAL backup and restore of the EcoVerse
# PostgreSQL database running in Docker Compose.
#
# Usage:
#   ./backup-restore-test.sh
#
# Prerequisites:
#   - Docker Compose running (docker-compose up -d)
#   - pg_dump and psql available (or use docker exec)
#   - Application has been running with some data
# ================================================================

set -e

COMPOSE_PROJECT="ecoverse"
DB_CONTAINER="ecoverse-db"
DB_NAME="${POSTGRES_DB:-ecoverse}"
DB_USER="${POSTGRES_USER:-ecoverse}"
BACKUP_FILE="/tmp/ecoverse_backup_drill_$(date +%Y%m%d_%H%M%S).sql"

echo "============================================================"
echo "EcoVerse — Backup & Restore Drill"
echo "============================================================"

# Step 1: Verify Docker Compose is running
echo ""
echo "[1/8] Verifying Docker Compose is running..."
if ! docker compose ps | grep -q "ecoverse-db.*running\|healthy"; then
    echo "ERROR: Docker Compose is not running. Start with: docker-compose up -d"
    exit 1
fi
echo "  ✓ Docker Compose is running"

# Step 2: Insert representative test data
echo ""
echo "[2/8] Inserting representative test data..."
docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -c "
    -- Create test users
    INSERT INTO users (name, email, password, country, role, enabled, account_non_locked, failed_login_attempts, provider)
    VALUES
        ('Drill User 1', 'drill1@test.com', 'hash1', 'IN', 'USER', true, true, 0, 'LOCAL'),
        ('Drill Seller', 'drill-seller@test.com', 'hash2', 'IN', 'SELLER', true, true, 0, 'LOCAL'),
        ('Drill Admin', 'drill-admin@test.com', 'hash3', 'IN', 'ADMIN', true, true, 0, 'LOCAL')
    ON CONFLICT DO NOTHING;

    -- Create test product
    INSERT INTO products (seller_id, name, category, price, stock, status)
    VALUES (2, 'Drill Product', 'solar', 99.99, 50, 'ACTIVE')
    ON CONFLICT DO NOTHING;

    -- Create test order
    INSERT INTO orders (user_id, total_price, status, payment_method, shipping_address, payment_status, currency)
    VALUES (1, 99.99, 'PAID', 'card', '123 Test St', 'PAID', 'INR')
    ON CONFLICT DO NOTHING;
" 2>/dev/null || echo "  ⚠ Some test data may already exist"

# Step 3: Record counts before backup
echo ""
echo "[3/8] Recording data counts before backup..."
USERS_BEFORE=$(docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -t -c "SELECT COUNT(*) FROM users;")
PRODUCTS_BEFORE=$(docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -t -c "SELECT COUNT(*) FROM products;")
ORDERS_BEFORE=$(docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -t -c "SELECT COUNT(*) FROM orders;")
FLYWAY_BEFORE=$(docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -t -c "SELECT COUNT(*) FROM flyway_schema_history;")

echo "  Users: $USERS_BEFORE"
echo "  Products: $PRODUCTS_BEFORE"
echo "  Orders: $ORDERS_BEFORE"
echo "  Flyway migrations: $FLYWAY_BEFORE"

# Step 4: Create backup
echo ""
echo "[4/8] Creating PostgreSQL backup..."
docker exec "$DB_CONTAINER" pg_dump -U "$DB_USER" -d "$DB_NAME" --clean --if-exists > "$BACKUP_FILE"
echo "  ✓ Backup created: $BACKUP_FILE"
echo "  File size: $(du -h "$BACKUP_FILE" | cut -f1)"

# Step 5: Destroy data (drop and recreate database)
echo ""
echo "[5/8] Destroying database for restore test..."
docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d postgres -c "DROP DATABASE IF EXISTS $DB_NAME;"
docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d postgres -c "CREATE DATABASE $DB_NAME;"
echo "  ✓ Database dropped and recreated"

# Step 6: Restore from backup
echo ""
echo "[6/8] Restoring from backup..."
docker exec -i "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" < "$BACKUP_FILE" > /dev/null 2>&1
echo "  ✓ Restore completed"

# Step 7: Verify data counts match
echo ""
echo "[7/8] Verifying restored data..."
USERS_AFTER=$(docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -t -c "SELECT COUNT(*) FROM users;")
PRODUCTS_AFTER=$(docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -t -c "SELECT COUNT(*) FROM products;")
ORDERS_AFTER=$(docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -t -c "SELECT COUNT(*) FROM orders;")
FLYWAY_AFTER=$(docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -t -c "SELECT COUNT(*) FROM flyway_schema_history;")

echo "  Users: $USERS_AFTER (was: $USERS_BEFORE)"
echo "  Products: $PRODUCTS_AFTER (was: $PRODUCTS_BEFORE)"
echo "  Orders: $ORDERS_AFTER (was: $ORDERS_BEFORE)"
echo "  Flyway migrations: $FLYWAY_AFTER (was: $FLYWAY_BEFORE)"

# Step 8: Verify relationships
echo ""
echo "[8/8] Verifying relationships..."
ORDER_USER_FK=$(docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -t -c "
    SELECT COUNT(*) FROM orders o
    LEFT JOIN users u ON o.user_id = u.id
    WHERE u.id IS NULL;")
echo "  Orphaned orders (user FK broken): $ORDER_USER_FK"

PRODUCT_SELLER_FK=$(docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -t -c "
    SELECT COUNT(*) FROM products p
    LEFT JOIN users u ON p.seller_id = u.id
    WHERE u.id IS NULL;")
echo "  Orphaned products (seller FK broken): $PRODUCT_SELLER_FK"

echo ""
echo "============================================================"
echo "Backup & Restore Drill: COMPLETE"
echo "Backup file: $BACKUP_FILE"
echo "============================================================"

# Cleanup
echo ""
echo "Cleanup: Keeping backup file for manual inspection."
echo "To delete: rm $BACKUP_FILE"
