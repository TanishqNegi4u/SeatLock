# ── Multi-Stage Build: Builder Stage ─────────────────────────────────
# Base Image Choice: maven:3.9.9-eclipse-temurin-21-alpine
# Why: Alpine-based Eclipse Temurin provides the official Java 21 LTS JDK compiler
# with minimal disk footprint (~300MB vs 800MB Debian standard), saving bandwidth
# and build cache space on resource-constrained development machines (i5/16GB).
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder
WORKDIR /app

# Cache dependencies layer
COPY pom.xml .
RUN mvn dependency:go-offline || true

# Build application jar
COPY src ./src
RUN mvn clean package -DskipTests

# ── Multi-Stage Build: Runtime Stage ─────────────────────────────────
# Base Image Choice: eclipse-temurin:21-jre-alpine
# Why: Stripped JRE (no javac, no build tools) on Alpine Linux produces an ultra-lean
# ~140MB final runtime image. Non-root user `seatlock` enforces security principle of
# least privilege in containerized environments.
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Run as non-root user
RUN addgroup -S seatlock && adduser -S seatlock -G seatlock
USER seatlock

COPY --from=builder /app/target/*.jar app.jar
EXPOSE 8080

# Container liveness check using Actuator endpoint
HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
  CMD wget -q -O - http://localhost:8080/actuator/health || exit 1

# Container-aware JVM memory tuning: G1GC for low-latency pause times, MaxRAMPercentage=75.0
# ensures Java heap gracefully respects Kubernetes pod memory limits (512Mi).
ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
