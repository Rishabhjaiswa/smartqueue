# ─────────────────────────────────────────────────────────────────────────────
# SmartQueue Backend — multi-stage Dockerfile
#
# Stage 1: build the JAR with Maven
# Stage 2: run with a minimal JRE 21 image (non-root, layered JAR)
# ─────────────────────────────────────────────────────────────────────────────

# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-21-alpine AS build

WORKDIR /workspace

# Cache dependency resolution layer separately from source changes
COPY pom.xml .
RUN mvn dependency:go-offline -q

COPY src ./src
RUN mvn package -DskipTests -q

# Extract layered JAR for better Docker cache utilisation
RUN rm -f target/*-plain.jar && \
    java -Djarmode=layertools -jar target/*.jar extract --destination target/extracted

# ── Stage 2: Runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

# Non-root user for security
RUN addgroup -S smartqueue && adduser -S smartqueue -G smartqueue
USER smartqueue

WORKDIR /app

# Copy layered JAR contents (dependencies first for cache efficiency)
COPY --from=build /workspace/target/extracted/dependencies/          ./
COPY --from=build /workspace/target/extracted/spring-boot-loader/   ./
COPY --from=build /workspace/target/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/target/extracted/application/          ./

EXPOSE 8080

# JVM flags: G1GC, container-aware heap, startup optimisations
ENTRYPOINT ["java", \
  "-XX:+UseG1GC", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "org.springframework.boot.loader.launch.JarLauncher"]
