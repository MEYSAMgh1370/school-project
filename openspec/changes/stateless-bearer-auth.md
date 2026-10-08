# Stateless Bearer Authentication

Status: implemented 2026-10-08 on branch `claude/stateless-bearer-auth`. Current
behavior is recorded in [`../architecture.md`](../architecture.md) once merged.

## Decision

Requests authenticated with `Authorization: Bearer` must not create an
`HttpSession`. The mobile token path is documented as stateless and has been
written as if it were, but it was not: every Bearer request created a session.

## Verified Baseline

Checked against `1fb1415` on 2026-10-08.

- [`JwtAuthenticationFilter.java`](../../src/main/java/com/example/roshdeandishe/iam/config/JwtAuthenticationFilter.java)
  states that a Bearer request places the `Authentication` on the holder
  "without storing it in `HttpSession`", and that JWT auth "stays completely
  stateless".
- [`MobileAuthFlowIntegrationTest.java`](../../src/test/java/com/example/roshdeandishe/MobileAuthFlowIntegrationTest.java)
  is named `...WithNoSession`, but its body did not assert that. The defect
  passed the suite because nothing checked it.
- Measured on unmodified `1fb1415`: `GET /api/me` with a valid Bearer token
  returns a `JSESSIONID` cookie.

The surrounding `sessionManagement` block in
[`SecurityConfig.java`](../../src/main/java/com/example/roshdeandishe/iam/config/SecurityConfig.java)
sets `SessionCreationPolicy.IF_REQUIRED`. A stack trace from the failing run
points at `SessionManagementFilter`, which treats an authenticated request that
has no stored security context as a new login and registers a session for it.

## What Was Tried First

The first attempt replaced the security-context repository with one that
skipped the session write for Bearer requests. **It did not work**: the session
was still created. This rules out the save step as the source. The session is
created by the session-management path, which is what the second attempt
changes. Recording this because the obvious first fix was wrong.

## Proposed Behavior

Two security filter chains, sharing one definition:

- **A Bearer chain**, `@Order(1)`, matched on `Authorization: Bearer ...`, with
  `SessionCreationPolicy.STATELESS`. Spring Security then uses no session
  repository and no session-registration strategy for these requests.
- **The default chain**, unchanged except that it is built by the same helper,
  with `IF_REQUIRED`. Cookie-based web login is unaffected.

The authorization rules, CSRF exemptions and entry points live in one helper,
`webSecurity(...)`, so the two chains cannot drift apart. The "is this a Bearer
request" test is also a single method, used for the matcher and for the existing
CSRF exemption.

## Acceptance Criteria

1. A `GET /api/me` with a valid Bearer token leaves no `HttpSession` on the
   request. This is asserted in `MobileAuthFlowIntegrationTest`, and it fails
   on the unmodified code.
2. A missing or malformed Bearer token still yields 403 on `/api/**`, as today.
3. Cookie-based web login, including the dashboard flows, is unchanged. The full
   suite passes in CI.
4. The authorization matrix is unchanged. The helper is the only definition of
   the rules.
5. `ModularityTests` still passes.

## Behavior That Changes, Stated Plainly

A request that carries **both** a `Authorization: Bearer` header and a session
cookie now goes through the Bearer chain. That chain is stateless, so the cookie
session is ignored for that request. Before this change, an invalid Bearer
token combined with a valid session cookie authenticated through the cookie.

This combination does not occur in practice. Browsers never attach an
`Authorization` header automatically, and the web client does not send one. It
is recorded here so that the change is a decision, not an accident.

## Migration and Compatibility Concerns

- **Mobile clients are unaffected.** Token issuance and validation are
  unchanged, and the response to a valid token is the same.
- **Existing sessions are unaffected.** No schema change, and cookie sessions
  are untouched.
- **Session rows stop appearing for Bearer requests.** This matters for
  [`jdbc-backed-web-sessions.md`](jdbc-backed-web-sessions.md), which records Bearer
  requests as a source of session rows. Once this lands, that document's
  "Discovered" section describes a defect that no longer exists and should be
  updated.

## Verification

Run locally against Postgres, with Testcontainers replaced by a local-only
configuration that is not committed:

- `MobileAuthFlowIntegrationTest`: failed before the fix on the new no-session
  assertion; passes after, 6 of 6.
- The full suite on a fresh database: 815 tests ran, 0 failures. One test,
  `PhoneNumberCanonicalizationMigrationTest`, errored because it starts its own
  Testcontainers container and no Docker daemon is available here. It is not
  affected by this change, and CI runs it.

Docker is not available in this environment, so the Testcontainers-based
configuration itself is verified only by CI.

## Unresolved Questions

1. Should the Bearer chain also accept a session cookie as a fallback, to keep
   the one combination above working as it did? Adding that would reintroduce
   the session lookup this change removes, so the default answer here is no.
2. Should the same stateless policy be applied to the `/api/auth/token`
   issuance endpoint? It is currently in the default chain and does not create a
   session, so this is not a defect, only a question of consistency.
