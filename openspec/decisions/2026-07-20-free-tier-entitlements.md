# ADR: Free-Tier School Entitlements

Date: 2026-07-20

Status: accepted and implemented.

## Context

School creation is self-service. There is no current platform-admin permission infrastructure, billing workflow, or operator-facing API for paid module grants.

The app still needs a new school to be usable immediately after creation.

## Decision

When an authenticated user creates a school:

- create the school;
- make the creator an active `SCHOOL_ADMIN`;
- grant the school free entitlements for `ACADEMIC` and `FINANCE`;
- do not grant paid modules such as `CURRICULUM`, `STUDY_PLANNER`, or `EXAM` automatically.

Paid or platform-controlled grants remain facade-only until platform/billing infrastructure exists.

## Consequences

- New schools can manage academic basics and finance immediately.
- Platform-only operations are not exposed over REST prematurely.
- Tests for paid-module endpoints must set up entitlement explicitly.
- Any future billing/operator work must define actor identity, permission checks, audit behavior, and failure cases before exposing REST endpoints.

## Evidence

- [`SchoolController.java`](../../src/main/java/com/example/roshdeandishe/school/SchoolController.java)
- [`SchoolFacadeImpl.java`](../../src/main/java/com/example/roshdeandishe/school/internal/SchoolFacadeImpl.java)
- [`IamSeedDataRunner.java`](../../src/main/java/com/example/roshdeandishe/iam/internal/IamSeedDataRunner.java)
- [`current-system.md`](../specs/current-system.md)
