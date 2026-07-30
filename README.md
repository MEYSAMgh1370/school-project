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
   CREATE SCHEMA IF NOT EXISTS exam;
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

## Deploying (Liara / any Docker host)

The repo root carries a multi-stage [`Dockerfile`](Dockerfile) (Maven +
Temurin 21 to build, `eclipse-temurin:21-jre` to run) and a minimal
[`liara.json`](liara.json) (`platform: docker`, `port: 8080`). Tests are
skipped in the image build on purpose — they need Testcontainers, i.e. a
Docker daemon, which a PaaS build environment doesn't provide.

Everything environment-specific is an env var, set in the Liara panel, not a
file in the image:

| Env var | Why |
|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://<liara-db-host>:<port>/<db>` |
| `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` | DB credentials |
| `ROSHDEANDISHE_JWT_SECRET` | mobile bearer JWT signing key — the value in `application.yml` is dev-only |
| `ROSHDEANDISHE_EXAM_TOKENSECRET` | exam-take token signing key — same |
| `ROSHDEANDISHE_MAIL_ACTIVATIONBASEURL` | public URL, so invite emails link somewhere real |
| `SPRING_MAIL_HOST` / `SPRING_MAIL_PORT` / `SPRING_MAIL_USERNAME` / `SPRING_MAIL_PASSWORD` | real SMTP; without it invite email just fails soft (`emailSent=false`) |
| `ROSHDEANDISHE_BALE_BOTTOKEN`, `ROSHDEANDISHE_BALE_WEBHOOKSECRET`, `ROSHDEANDISHE_SMS_APIKEY` | only if those integrations are actually in use |

The per-module schemas (`school`, `academic`, …) no longer need to be created
by hand on a server: `hibernate.hbm2ddl.create_namespaces` is enabled in
`application.yml`, so Hibernate creates them alongside the tables. The manual
`CREATE SCHEMA` list above is still valid, and still the fallback if a managed
Postgres refuses schema creation to the app's user.

Still unresolved for a real production deployment (see `CLAUDE.md` §7 item 5):
schema management is `ddl-auto: update` + dev seed runners, not Flyway, and
`IamSeedDataRunner`/`SchoolSeedDataRunner` will seed demo data — including the
`admin@roshdeandishe.test` / `password` login — on any database they start
against.

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
