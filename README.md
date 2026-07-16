# Roshdeandishe — School Management System

A multi-tenant SaaS school management platform for Iranian schools, built as a
**Spring Boot Modular Monolith** (Spring Modulith). See
[`CLAUDE.md`](CLAUDE.md) for the full, authoritative architecture and domain
model — this file is intentionally just a quick-start pointer, so it doesn't
drift out of sync with the real source of truth again.

- [`CLAUDE.md`](CLAUDE.md) — architecture, module structure, domain model,
  access control, roadmap. Read this first for anything beyond "how do I run
  it locally."
- [`BEST_PRACTICES.md`](BEST_PRACTICES.md) — working discipline and Java/Spring
  conventions for this repo.
- [`UX_ROADMAP.md`](UX_ROADMAP.md) — frontend page inventory, build order, and
  shared UI conventions for the management UI.

## Stack

Java 21, Spring Boot 4.1, Spring Modulith, PostgreSQL, JTE + Alpine.js + Bulma
(server-rendered, no npm/webpack build), session-based auth via Spring
Security. One Maven artifact (`com.example:roshdeandishe`) — module boundaries
are enforced by package structure and `ApplicationModules.verify()`, not by
separate build artifacts.

## Running locally

1. **Postgres**, with the per-module schemas created up front (Hibernate's
   `ddl-auto: update` creates tables, not schemas):
   ```sql
   CREATE DATABASE roshdeandishe_iam;
   \c roshdeandishe_iam
   CREATE SCHEMA IF NOT EXISTS school;
   CREATE SCHEMA IF NOT EXISTS academic;
   CREATE SCHEMA IF NOT EXISTS finance;
   CREATE SCHEMA IF NOT EXISTS bale;
   CREATE SCHEMA IF NOT EXISTS curriculum;
   CREATE SCHEMA IF NOT EXISTS studyplan;
   ```
   Connection defaults live in `src/main/resources/application.yml`
   (`localhost:5432/roshdeandishe_iam`, `postgres`/`postgres`).

2. **(Optional) local SMTP** for invite/activation emails — dev default is
   `localhost:1025` (e.g. [Mailpit](https://github.com/axllent/mailpit):
   `docker run -p 1025:1025 -p 8025:8025 axllent/mailpit`). Email delivery is
   best-effort; the app works without it (the invite token is also returned
   in the API response as a manual fallback).

3. **Run the app**:
   ```bash
   ./mvnw spring-boot:run
   ```
   The app starts on `http://localhost:8080`. `IamSeedDataRunner` and
   `SchoolSeedDataRunner` seed dev-only data on startup, including a demo
   admin login: `admin@roshdeandishe.test` / `password`.

4. **Swagger UI**: `http://localhost:8080/swagger-ui.html`.

## Testing

```bash
./mvnw test
```

Integration tests (`*FlowIntegrationTest`) run against a real Postgres via
Testcontainers — Docker must be available. To check module boundaries and
regenerate architecture docs (`target/spring-modulith-docs/`, not committed):

```bash
./mvnw test -Dtest=ModularityTests
```
