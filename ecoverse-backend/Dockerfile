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

# Copy the built JAR from stage 1
COPY --from=build /app/target/*.jar app.jar

# Copy startup scripts and make them executable (must be before USER switch)
COPY render-start.sh /app/render-start.sh
COPY koyeb-start.sh /app/koyeb-start.sh
RUN chmod +x /app/render-start.sh /app/koyeb-start.sh

# Switch to non-root user after file setup
USER ecoverse

# JVM memory tuning for 256MB RAM environments (Koyeb nano)
# Max heap 180MB leaves room for metaspace, threads, and OS overhead
ENV JAVA_OPTS="-Xmx180m -Xms128m -XX:+UseSerialGC -XX:MaxMetaspaceSize=64m"

# Configurable defaults (overridden by hosting platform env vars)
ENV SPRING_PROFILES_ACTIVE=prod
ENV SERVER_FORWARD_HEADERS_STRATEGY=FRAMEWORK

# Expose port
EXPOSE 8081

# Health check — uses Actuator health endpoint
HEALTHCHECK --interval=30s --timeout=10s --start-period=90s --retries=3 \
  CMD wget -q --spider http://localhost:8081/actuator/health || exit 1

# Default: Render startup script. Override with koyeb-start.sh on Koyeb.
ENTRYPOINT ["/app/render-start.sh"]
