#!/bin/sh
# ================================================================
# EcoVerse — Render.com Startup Script
# Parses Render's DATABASE_URL (postgresql://user:pass@host:port/db)
# and converts to Spring Boot JDBC format + sets DB_USERNAME/DB_PASSWORD
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

            # Strip the postgresql:// prefix
            AFTER_SCHEME=$(echo "$DATABASE_URL" | sed 's|postgresql://||')

            # Extract user:password (before the @)
            USER_PASS=$(echo "$AFTER_SCHEME" | cut -d'@' -f1)
            DB_USER=$(echo "$USER_PASS" | cut -d':' -f1)
            DB_PASS=$(echo "$USER_PASS" | cut -d':' -f2-)

            # Extract host:port/dbname (after the @)
            AFTER_AT=$(echo "$AFTER_SCHEME" | cut -d'@' -f2-)

            # host:port is before the first /
            HOST_PORT=$(echo "$AFTER_AT" | cut -d'/' -f1)
            # database name is after the first /
            DB_NAME=$(echo "$AFTER_AT" | cut -d'/' -f2- | cut -d'?' -f1)

            # Split host and port (port may be absent)
            if echo "$HOST_PORT" | grep -q ':'; then
                DB_HOST=$(echo "$HOST_PORT" | cut -d':' -f1)
                DB_PORT=$(echo "$HOST_PORT" | cut -d':' -f2)
            else
                DB_HOST="$HOST_PORT"
                DB_PORT="5432"
            fi

            # Build proper JDBC URL: jdbc:postgresql://host:port/dbname
            JDBC_URL="jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}"

            export SPRING_DATASOURCE_URL="$JDBC_URL"
            export DB_USERNAME="$DB_USER"
            export DB_PASSWORD="$DB_PASS"

            echo "DB host: ${DB_HOST}"
            echo "DB port: ${DB_PORT}"
            echo "DB name: ${DB_NAME}"
            echo "DB user: ${DB_USER}"
            echo "JDBC URL: ${JDBC_URL}"
            ;;
        *)
            echo "WARNING: DATABASE_URL format not recognized"
            ;;
    esac
else
    echo "WARNING: DATABASE_URL is not set!"
fi

# Render assigns a PORT env var (usually 10000).
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
