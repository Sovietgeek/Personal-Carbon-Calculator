#!/bin/sh
# ================================================================
# EcoVerse — Render.com Startup Script
# 1. Parse Render's DATABASE_URL into JDBC format
# 2. Start the Spring Boot application
# ================================================================

echo "=== EcoVerse Render Startup ==="

if [ -n "$DATABASE_URL" ]; then
    case "$DATABASE_URL" in
        jdbc:postgresql://*)
            echo "DATABASE_URL already in JDBC format, using as-is"
            export SPRING_DATASOURCE_URL="$DATABASE_URL"
            ;;
        postgresql://*)
            echo "Converting Render DATABASE_URL to JDBC format..."

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

# Render assigns a PORT env var (usually 10000)
if [ -n "$PORT" ]; then
    export SERVER_PORT="$PORT"
    echo "Render PORT detected: $PORT"
fi

# Start the application
echo "=== Starting EcoVerse Backend ==="
exec java \
    -XX:+UseG1GC \
    -XX:MaxRAMPercentage=75.0 \
    -Djava.security.egd=file:/dev/./urandom \
    -jar app.jar
