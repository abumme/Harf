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

# --- admin-web: static export of the Kobweb staff panel (adminWeb/) ---
# Kobweb's export snapshots every page in a headless Chromium through Playwright. Playwright's Java image carries a
# JDK, Chromium and its system libraries, and must match Kobweb 0.25.1's Playwright (1.61.0) so no browser is
# downloaded. Pinned by digest (multi-arch index of v1.61.0-noble, checked 2026-09-16).
FROM mcr.microsoft.com/playwright/java:v1.61.0-noble@sha256:a23837c4bb84165c2156f64411ae79d7e42ef91c9d8f960691d06c5d43a684f4 AS admin-web
WORKDIR /src
# -PadminWebOnly builds only :sharedData (the shared admin DTOs) and :adminWeb.
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle/ gradle/
COPY sharedData/ sharedData/
COPY adminWeb/ adminWeb/
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew && \
    ./gradlew --no-daemon -PadminWebOnly :adminWeb:kobwebExport \
        -PkobwebReuseServer=false -PkobwebEnv=DEV -PkobwebRunLayout=STATIC \
        -PkobwebBuildTarget=RELEASE -PkobwebExportLayout=STATIC

# --- runtime: slim JRE running the self-contained distribution ---
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /src/backend/build/install/backend/ ./
# The exported panel, served at /admin (https://<host>/harf/admin/ behind Caddy).
COPY --from=admin-web /src/adminWeb/.kobweb/site/ ./admin-web/
ENV ADMIN_WEB_DIR=/app/admin-web
EXPOSE 8080
# PORT / DB_* / JWT_* / GOOGLE_CLIENT_IDS / APPLE_AUDIENCES / HARF_ENV / ADMIN_* come from the environment.
ENTRYPOINT ["./bin/backend"]
