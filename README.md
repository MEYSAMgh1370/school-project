# Roshdeandishe — School Management System

A multi-tenant SaaS school management platform for Iranian schools, built as a
**Spring Boot Modular Monolith** (Spring Modulith). Start with
[`openspec/project.md`](openspec/project.md) for the curated documentation map.
The source code and tests remain the evidence for current behavior; older
Markdown files are useful context but may contain stale AI-assisted notes.

- [`openspec/project.md`](openspec/project.md) — current documentation map,
  evidence order, and OpenSpec workflow.
- [`openspec/architecture.md`](openspec/architecture.md) — verified
  architecture, module, persistence, frontend, Android, and deployment shape.
- [`openspec/specs/current-system.md`](openspec/specs/current-system.md) —
  current product behavior and known limitations.
- [`CLAUDE_LEGACY.md`](CLAUDE_LEGACY.md) — legacy detailed architecture and product notebook.
  Use it as source material, not as unchecked authority.
- [`openspec/conventions.md`](openspec/conventions.md) — working discipline,
  Java/Spring conventions, frontend constraints, schema rules, and test commands.
- [`UX_ROADMAP.md`](UX_ROADMAP.md) — frontend page inventory, build order, and
  shared UI conventions for the management UI.
- [`FRONTEND.md`](FRONTEND.md) — onboarding guide for a frontend developer: the
  stack, how a page is built end to end, the rules every page follows, and the
  full REST surface the UI calls.

## Stack

Java 21, Spring Boot 4.1, Spring Modulith, PostgreSQL, JTE + Alpine.js + Bulma
(server-rendered, no npm/webpack build), session-based auth via Spring
Security. One Maven artifact (`com.example:roshdeandishe`) — module boundaries
are enforced by package structure and `ApplicationModules.verify()`, not by
separate build artifacts.

## Running locally

1. **Postgres** — just the database; the per-module schemas are created for you:
   ```sql
   CREATE DATABASE roshdeandishe_iam;
   ```
   Flyway creates the eight per-module schemas (`iam`, `school`, `academic`,
   `finance`, `bale`, `curriculum`, `studyplan`, `exam`) from
   `spring.flyway.schemas` on first start, then applies
   `src/main/resources/db/migration/` — so an empty database is enough, and
   `CREATE SCHEMA` by hand is no longer a step. Connection defaults live in
   `src/main/resources/application.yml` (`localhost:5432/roshdeandishe_iam`,
   `postgres`/`postgres`) and each is overridable via
   `DATABASE_URL`/`DATABASE_USERNAME`/`DATABASE_PASSWORD`.

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
   admin login: `admin@roshdeandishe.test` / `password`. That demo data is
   skipped under the `prod` profile — see [`DEPLOYMENT.md`](DEPLOYMENT.md).

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
| `DATABASE_URL` | `jdbc:postgresql://<liara-db-host>:<port>/<db>` |
| `DATABASE_USERNAME` / `DATABASE_PASSWORD` | DB credentials |
| `JWT_SECRET` | mobile bearer JWT signing key — the value in `application.yml` is dev-only |
| `EXAM_TOKEN_SECRET` | exam-take token signing key — same |
| `APP_BASE_URL` | public URL, so invite emails link somewhere real |
| `MAIL_HOST` / `MAIL_PORT` / `MAIL_USERNAME` / `MAIL_PASSWORD` | real SMTP; without it invite email just fails soft (`emailSent=false`) |
| `BALE_BOT_TOKEN`, `BALE_WEBHOOK_SECRET` | only if the Bale integration is actually in use |
| `SMS_API_KEY` | nothing yet — no SMS provider is integrated in code, so setting it does not enable OTP delivery (see [`DEPLOYMENT.md`](DEPLOYMENT.md)) |

The first six are **required** under the `prod` profile — the app refuses to
start without them rather than quietly running on the dev signing keys
committed in `application.yml`. [`DEPLOYMENT.md`](DEPLOYMENT.md) is the full
runbook, including every optional variable and its default.

The per-module schemas (`school`, `academic`, …) do not need to be created by
hand: Flyway creates all eight from `spring.flyway.schemas` on first start, then
applies `src/main/resources/db/migration/`. The app's DB user needs `CREATE`
on the database; the manual `CREATE SCHEMA` list above is the fallback if a
managed Postgres refuses that.

Pointing this release at a database built by an earlier version (which used
`ddl-auto: update`) is safe: Flyway baselines it at v1 and skips the baseline
migration, leaving existing rows alone.

The demo seed data — including the `admin@roshdeandishe.test` / `password`
login — is **not** created under the `prod` profile, which the Docker image
sets itself. Only the Module/Role/Permission catalog is seeded there, since
nothing works without it. Schema management is likewise no longer a gap (see
[`openspec/decisions/2026-07-30-flyway-schema-ownership.md`](openspec/decisions/2026-07-30-flyway-schema-ownership.md)).

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
