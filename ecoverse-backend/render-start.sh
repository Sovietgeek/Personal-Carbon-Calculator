#!/bin/sh
# ================================================================
# EcoVerse — Render.com Startup Script
# Parses Render's DATABASE_URL (postgresql://user:pass@host:port/db)
# and converts to Spring Boot JDBC format + sets DB_USERNAME/DB_PASSWORD
# ================================================================

echo "=== EcoVerse Render Startup ==="

# If DATABASE_URL is in Render's format (postgresql://user:pass@host:port/db),
# parse it properly and construct a valid JDBC URL
if [ -n "$DATABASE_URL" ]; then
    # Check if it's already in JDBC format
    case "$DATABASE_URL" in
        jdbc:postgresql://*)
            echo "DATABASE_URL already in JDBC format, using as-is"
            export SPRING_DATASOURCE_URL="$DATABASE_URL"
            ;;
        postgresql://*)
            echo "Converting Render DATABASE_URL to JDBC format..."

            # Parse: postgresql://user:password@host:port/database
            # Extract user
            DB_USER=$(echo "$DATABASE_URL" | sed -E 's|postgresql://([^:]+):([^@]+)@.*|\1|')
            # Extract password
            DB_PASS=$(echo "$DATABASE_URL" | sed -E 's|postgresql://([^:]+):([^@]+)@.*|\2|')
            # Extract host:port/database (everything after the @)
            HOST_PART=$(echo "$DATABASE_URL" | sed -E 's|postgresql://[^@]+@(.*)|\1|')
            # Extract host (before the colon for port, or before the slash for db)
            DB_HOST=$(echo "$HOST_PART" | sed -E 's|([^:]+).*|\1|')
            # Extract port (if present)
            DB_PORT=$(echo "$HOST_PART" | sed -n -E 's|[^:]+:([0-9]+).*|\1|p')
            # Extract database name (after the last /)
            DB_NAME=$(echo "$HOST_PART" | sed -E 's|.*/([^?]+).*|\1|')

            # Default port if not present
            if [ -z "$DB_PORT" ]; then
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
            echo "JDBC URL: jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}"
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
