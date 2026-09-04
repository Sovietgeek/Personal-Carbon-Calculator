#!/bin/sh
# ================================================================
# EcoVerse — Render.com Startup Script
# Parses Render's DATABASE_URL (postgresql://user:pass@host:port/db)
# and converts to Spring Boot JDBC format + sets DB_USERNAME/DB_PASSWORD
# ================================================================

echo "=== EcoVerse Render Startup ==="

# If DATABASE_URL is in Render's format (postgresql://user:pass@host:port/db),
# convert it to JDBC format and extract username/password
if [ -n "$DATABASE_URL" ]; then
    # Check if it's already in JDBC format
    case "$DATABASE_URL" in
        jdbc:postgresql://*)
            echo "DATABASE_URL already in JDBC format, using as-is"
            export SPRING_DATASOURCE_URL="$DATABASE_URL"
            ;;
        postgresql://*)
            echo "Converting Render DATABASE_URL to JDBC format..."
            # Extract user and password from postgresql://user:pass@host:port/db
            DB_USER=$(echo "$DATABASE_URL" | sed -E 's|postgresql://([^:]+):([^@]+)@.*|\1|')
            DB_PASS=$(echo "$DATABASE_URL" | sed -E 's|postgresql://([^:]+):([^@]+)@.*|\2|')
            # Build JDBC URL from the postgresql:// URL
            JDBC_URL=$(echo "$DATABASE_URL" | sed -E 's|^postgresql://|jdbc:postgresql://|')

            export SPRING_DATASOURCE_URL="$JDBC_URL"
            export DB_USERNAME="$DB_USER"
            export DB_PASSWORD="$DB_PASS"

            echo "DB user: $DB_USER"
            echo "JDBC URL set (host hidden for security)"
            ;;
        *)
            echo "WARNING: DATABASE_URL format not recognized: ${DATABASE_URL%:*}//..."
            ;;
    esac
else
    echo "WARNING: DATABASE_URL is not set!"
fi

# Render assigns a PORT env var (usually 10000).
# Spring Boot defaults to 8081 — override if PORT is set.
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
