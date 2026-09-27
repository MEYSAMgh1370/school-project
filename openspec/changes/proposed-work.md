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
- [`../../CLAUDE_LEGACY.md`](../../CLAUDE_LEGACY.md)

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
- SMS provider integration is not written: `SmsDriver` reports failure for any
  nonblank `roshdeandishe.sms.api-key`, so configuration cannot enable delivery;
- codes are logged instead of sent only when `roshdeandishe.sms.dev-log-codes`
  is `true` (the default outside the `prod` profile);
- `requestOtp` fails closed on delivery failure — it rejects the request with
  `OtpDeliveryUnavailable` and stores no code;
- OTP rate limiting is explicitly not implemented.

Proposed work:

- integrate a real SMS provider in `SmsDriver` (a prerequisite for OTP login in
  production, not just hardening);
- add rate limiting for repeated OTP requests;
- define lockout, retry, audit, and user-facing error behavior;
- add integration tests for abuse cases.

Source documents:

- [`../../MOSHAVERE_PORT_SPEC.md`](../../MOSHAVERE_PORT_SPEC.md)

## Design Follow-Ups

Resolved parts as of 2026-08-16:

- shared CSS tokens exist in `app.css`;
- ~~the existing ink-blue primary color is adopted~~ — **superseded
  2026-09-27:** Persian turquoise `hsl(177, 58%, 29%)` with a warm neutral
  scheme (`DESIGN.md` §4);
- ~~the web UI retains the system font stack with no new font dependency~~ —
  **superseded 2026-09-27:** Vazirmatn is vendored as a static variable font
  (`DESIGN.md` §3);
- finance amount inputs normalize Persian and Arabic-Indic digits on edit and
  before serialization;
- finance amounts temporarily display as neutral Persian digits with `٬`
  grouping and no unit or conversion;
- a skeleton component remains need-driven rather than standardized
  speculatively;
- Android Material design decisions remain separate from Bulma/JTE web
  decisions.

Remaining proposed work:

- [Approve the focused finance monetary-unit contract](finance-monetary-unit.md)
  before changing any financial-value persistence, REST semantics, conversion,
  labels, demo data, or migration. It remains proposed work until a separate
  implementation change is completed.

A custom font, skeleton component, or Android design change still requires a
new concrete need.

Source documents:

- [`../../DESIGN.md`](../../DESIGN.md)
- [`../../FRONTEND.md`](../../FRONTEND.md)
- [`web-design-followups.md`](web-design-followups.md)
