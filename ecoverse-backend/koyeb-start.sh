#!/bin/sh
# ================================================================
# EcoVerse — Koyeb Startup Script
# 1. Parse Supabase pooled DATABASE_URL into JDBC format
# 2. Disable prepared statements for PgBouncer transaction pooling
# 3. Start the Spring Boot application with tuned JVM memory
# ================================================================

echo "=== EcoVerse Koyeb Startup ==="

if [ -n "$DATABASE_URL" ]; then
    case "$DATABASE_URL" in
        jdbc:postgresql://*)
            echo "DATABASE_URL already in JDBC format, using as-is"
            export SPRING_DATASOURCE_URL="$DATABASE_URL"
            ;;
        postgresql://*)
            echo "Converting Supabase DATABASE_URL to JDBC format..."

            AFTER_SCHEME=$(echo "$DATABASE_URL" | sed 's|postgresql://||')
            USER_PASS=$(echo "$AFTER_SCHEME" | cut -d'@' -f1)
            DB_USER=$(echo "$USER_PASS" | cut -d':' -f1)
            DB_PASS=$(echo "$USER_PASS" | cut -d':' -f2-)
            AFTER_AT=$(echo "$AFTER_SCHEME" | cut -d'@' -f2-)
            HOST_PORT=$(echo "$AFTER_AT" | cut -d'/' -f1)
            DB_NAME=$(echo "$AFTER_AT" | cut -d'/' -f2- | cut -d'?' -f1)

            if echo "$HOST_PORT" | grep -q ':'; then
                DB_HOST=$(echo "$HOST_PORT" | cut -d':' -f1)
                DB_PORT=$(echo "$HOST_PORT" | cut -d':' -f2)
            else
                DB_HOST="$HOST_PORT"
                DB_PORT="5432"
            fi

            JDBC_URL="jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}"

            # PgBouncer transaction mode (port 6543) does not support
            # server-side prepared statements — disable them in pgjdbc.
            if [ "$DB_PORT" = "6543" ]; then
                JDBC_URL="${JDBC_URL}?prepareThreshold=0"
                echo "Pooled endpoint detected (6543) — prepared statements disabled"
            fi

            export SPRING_DATASOURCE_URL="$JDBC_URL"
            export DB_USERNAME="$DB_USER"
            export DB_PASSWORD="$DB_PASS"

            echo "DB host: ${DB_HOST}  port: ${DB_PORT}  name: ${DB_NAME}  user: ${DB_USER}"
            ;;
        *)
            echo "WARNING: DATABASE_URL format not recognized"
            ;;
    esac
else
    echo "WARNING: DATABASE_URL is not set!"
fi

# Koyeb routes traffic to the port exposed by the service.
# Dockerfile EXPOSEs 8081; respect the PORT env var if Koyeb sets one.
if [ -n "$PORT" ]; then
    export SERVER_PORT="$PORT"
    echo "Koyeb PORT detected: $PORT"
fi

# JAVA_OPTS is set in the Dockerfile for 256MB RAM (Koyeb Nano):
#   -Xmx180m -Xms128m -XX:+UseSerialGC -XX:MaxMetaspaceSize=64m
echo "=== Starting EcoVerse Backend ==="
exec java \
    $JAVA_OPTS \
    -Djava.security.egd=file:/dev/./urandom \
    -jar app.jar
