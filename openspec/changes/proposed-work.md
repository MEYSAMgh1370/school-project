# Proposed Work

Status: roadmap and change-proposal seed list. Items here are not current behavior until implemented and moved into a current spec.

Before implementing any substantial item, create a focused change proposal or split this file into a dedicated change document with acceptance criteria, migration notes, and tests.

## Platform And Billing

Current behavior:

- self-service school creation grants ACADEMIC and FINANCE;
- paid modules are not granted through a public REST workflow;
- platform-only school status and entitlement changes are facade-only.

Proposed work:

- define a platform actor/permission model;
- add billing or operator workflows for paid module grants;
- decide whether school status changes need a REST/API surface.

Source documents:

- [`../specs/current-system.md`](../specs/current-system.md)
- [`../decisions/2026-07-20-free-tier-entitlements.md`](../decisions/2026-07-20-free-tier-entitlements.md)
- [`../../CLAUDE.md`](../../CLAUDE.md)

## School Public Identity

Current behavior:

- schools have slugs;
- `/s/{slug}` exists as a branded public entry route;
- slug validation and reservation are implemented;
- custom domains and subdomain routing are not implemented.

Proposed work:

- decide whether slugs can be edited;
- decide whether subdomains or custom domains are needed;
- define ownership, verification, and collision behavior before implementation.

## Android And Mobile

Current behavior:

- native Android v1 exists as a read-only Kotlin/Compose shell;
- backend JWT auth and current-user endpoints exist;
- no push provider or offline write sync exists.

Proposed work:

- externalize Android base URL through build configuration;
- choose a push notification provider suitable for Iran;
- add richer native student workflows only after backend behavior is specified;
- decide whether native grade, homework, exam, messaging, and video workflows are needed in Android v1 or later phases.

Source documents:

- [`../../MOBILE_ROADMAP.md`](../../MOBILE_ROADMAP.md)
- [`../../android/README.md`](../../android/README.md)

## Homework, Feed, Messaging, And Video

Current behavior:

- homework and class-feed domains are not implemented;
- finance expense attachment upload exists, but homework-specific upload does not;
- real-time messaging is not implemented;
- BigBlueButton integration is not implemented.

Proposed work:

- define whether homework belongs in `academic` or a new module;
- specify attachment ownership, retention, size limits, and access checks for homework before reusing upload patterns;
- define messaging scope separately from Bale notifications;
- specify BigBlueButton hosting and operational ownership before building.

Source documents:

- [`../../MOBILE_ROADMAP.md`](../../MOBILE_ROADMAP.md)
- [`../../MOSHAVERE_PORT_SPEC.md`](../../MOSHAVERE_PORT_SPEC.md)

## OTP Hardening

Current behavior:

- OTP login is implemented for users with an existing phone number;
- SMS delivery is best-effort/log-only unless configured;
- OTP rate limiting is explicitly not implemented.

Proposed work:

- add rate limiting for repeated OTP requests;
- define lockout, retry, audit, and user-facing error behavior;
- add integration tests for abuse cases.

Source documents:

- [`../../MOSHAVERE_PORT_SPEC.md`](../../MOSHAVERE_PORT_SPEC.md)

## Design Follow-Ups

Current behavior:

- shared CSS tokens exist in `app.css`;
- the JTE web design system is documented in `DESIGN.md`;
- several design items remain pending sign-off.

Proposed work:

- confirm primary color and currency display rules;
- decide Persian font loading strategy;
- decide whether the skeleton/loading component should be standardized;
- keep Android Material design decisions separate from Bulma/JTE web decisions.

Source documents:

- [`../../DESIGN.md`](../../DESIGN.md)
- [`../../FRONTEND.md`](../../FRONTEND.md)
