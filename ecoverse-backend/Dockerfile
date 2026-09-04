# ================================================================
# EcoVerse Backend — Multi-stage Docker Build
# Stage 1: Build with Maven
# Stage 2: Run with lightweight JRE
# ================================================================

# Stage 1: Build
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Copy pom.xml first (for dependency caching)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build
COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Run
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Add a non-root user for security
RUN addgroup -S ecoverse && adduser -S ecoverse -G ecoverse
USER ecoverse

# Copy the built JAR from stage 1
COPY --from=build /app/target/*.jar app.jar

# Copy Render startup script (parses DATABASE_URL, then runs the app)
COPY render-start.sh /app/render-start.sh
RUN chmod +x /app/render-start.sh

# Configurable defaults (overridden by docker-compose / Render env vars)
ENV SPRING_PROFILES_ACTIVE=dev
ENV SERVER_FORWARD_HEADERS_STRATEGY=FRAMEWORK

# Expose port
EXPOSE 8081

# Health check — uses Actuator health endpoint
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget -q --spider http://localhost:8081/actuator/health || exit 1

# Run via startup script (handles DATABASE_URL conversion for Render)
ENTRYPOINT ["/app/render-start.sh"]
