# Multi-stage build for the Harf Ktor backend. Base images are multi-arch, so this works on an
# Oracle Ampere (aarch64) instance as well as an AMD (x86_64) one.

# --- build: JDK + Gradle wrapper, backend-only so no Android SDK is needed ---
FROM eclipse-temurin:17-jdk AS build
WORKDIR /src
COPY . .
# The `sed` covers a Windows checkout made before .gitattributes pinned gradlew to LF —
# a CRLF shebang fails here as the very unhelpful `./gradlew: not found`.
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew && \
    ./gradlew --no-daemon -PbackendOnly :backend:installDist

# --- runtime: slim JRE running the self-contained distribution ---
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /src/backend/build/install/backend/ ./
EXPOSE 8080
# PORT / DB_* / JWT_* / GOOGLE_CLIENT_IDS / APPLE_AUDIENCES / HARF_ENV come from the environment.
ENTRYPOINT ["./bin/backend"]
