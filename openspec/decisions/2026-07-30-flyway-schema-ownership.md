# ADR: Flyway Owns Schema Creation

Date: 2026-07-30

Status: accepted and implemented.

## Context

The application has multiple database schemas that correspond to module ownership. Earlier guidance mentioned manual schema creation and Hibernate update behavior, but the current repository uses Flyway and Hibernate validation.

## Decision

Flyway owns database schema creation and migrations.

- Keep the schema list in `spring.flyway.schemas`.
- Create or evolve tables through versioned migrations in `src/main/resources/db/migration/`.
- Keep Hibernate `ddl-auto` in validation mode.
- Do not edit applied migrations.
- Do not rely on manual `CREATE SCHEMA` steps except as an operational fallback when a managed database user cannot create schemas.

## Consequences

- A new empty database can be initialized by the application when the database user has the needed privileges.
- Schema drift is visible at startup because Hibernate validates the mapped tables.
- New modules that need storage must include migration work in the same change.
- Local and production setup instructions must describe Flyway as the primary path.

## Evidence

- [`application.yml`](../../src/main/resources/application.yml)
- [`application-prod.yml`](../../src/main/resources/application-prod.yml)
- [`V1__baseline_schema.sql`](../../src/main/resources/db/migration/V1__baseline_schema.sql)
- [`V2__tenant_finance_consistency_constraints.sql`](../../src/main/resources/db/migration/V2__tenant_finance_consistency_constraints.sql)
- [`README.md`](../../README.md)
- [`DEPLOYMENT.md`](../../DEPLOYMENT.md)
