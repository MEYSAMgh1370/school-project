# syntax=docker/dockerfile:1

# ---------------------------------------------------------------------------
# Stage 1 — build the single Boot fat jar.
#
# Tests are deliberately skipped here: the integration suite runs against
# Testcontainers (a real Postgres per run), which needs a Docker daemon that a
# PaaS build server does not provide. Tests are CI's job (.github/workflows),
# not the deploy image's.
# ---------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

# Dependency layer first, so a source-only change does not re-download the
# world on every deploy. This step is a cache warm, not a correctness gate —
# `mvn package` below is authoritative and can still fetch anything missing,
# so an incomplete go-offline must not fail the build.
COPY pom.xml ./
RUN mvn -B dependency:go-offline || echo "go-offline incomplete — package will resolve the rest"

# JTE templates are precompiled at build time (jte-maven-plugin, bound to
# generate-sources) because gg.jte.use-precompiled-templates=true at runtime.
COPY src/ src/
RUN mvn -B -DskipTests package

# ---------------------------------------------------------------------------
# Stage 2 — runtime.
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jre AS runtime

# curl is here only so liara.json's healthCheck command has something to run.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=build /build/target/*.jar app.jar

RUN useradd --system --uid 1001 spring \
    && chown -R spring:spring /app
USER spring

# The prod profile is baked in rather than left to a dashboard env var on
# purpose: forgetting it is not a small mistake. Without it the dev seed data
# runs (a public SCHOOL_ADMIN with a known password) and the committed dev JWT
# signing keys stay in effect. Override with -e SPRING_PROFILES_ACTIVE=... only
# when you deliberately want a non-production run of this image.
ENV SPRING_PROFILES_ACTIVE=prod

# MaxRAMPercentage lets the JVM size its heap from the container limit, so the
# same image behaves correctly on any Liara plan without a hardcoded -Xmx.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
