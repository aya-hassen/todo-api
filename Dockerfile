# ---------- Build stage ----------
# Pin an exact Gradle+JDK image so the build is reproducible across machines.
FROM gradle:8.12.1-jdk21 AS build
WORKDIR /workspace

# Copy build descriptors first for better layer caching.
COPY settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY gradlew ./gradlew

# Pre-download dependencies (best-effort; do not fail the build if offline).
RUN ./gradlew --no-daemon dependencies --quiet || true

COPY src ./src
RUN ./gradlew --no-daemon shadowJar -x test

# ---------- Runtime stage ----------
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Render injects $PORT at runtime; we default to 8080 for local runs.
ENV PORT=8080
EXPOSE 8080

# Keep memory low so the free-tier 512 MB plan doesn't OOM.
ENV JAVA_OPTS="-Xms128m -Xmx400m -XX:+ExitOnOutOfMemoryError"

COPY --from=build /workspace/build/libs/*-all.jar /app/app.jar

# Non-root user for hygiene.
RUN useradd -r -u 1001 todo && chown -R todo /app
USER todo

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
