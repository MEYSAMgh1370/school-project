# Post-Authentication School-Registration Handoff

Status: implemented on 2026-09-27. Every bullet of the contract below is
current behavior; the current-state statement lives in
[`specs/current-system.md`](../specs/current-system.md) under Web UI. This
record is retained for the rationale and the security argument, and is not
evidence of production deployment.

## Verified Baseline

Before this change, the public landing linked directly to `/schools/new`,
which is authenticated. `SecurityConfig` redirected an anonymous request there
to plain `/login`. Both `login.js` and `register.js` then sent a successfully
authenticated user to `/dashboard`, so neither the landing CTA nor a bookmarked
school-registration URL reliably reached the intended form.

Evidence:

- [`landing.jte`](../../src/main/jte/landing.jte)
- [`SecurityConfig.java`](../../src/main/java/com/example/roshdeandishe/iam/config/SecurityConfig.java)
- [`login.js`](../../src/main/resources/static/js/login.js)
- [`register.js`](../../src/main/resources/static/js/register.js)
- [`LoginDashboardFlowIntegrationTest.java`](../../src/test/java/com/example/roshdeandishe/LoginDashboardFlowIntegrationTest.java)

## Proposed Contract

- The public school-registration CTAs start at
  `/login?next=/schools/new`; the account-creation path starts at
  `/register?next=/schools/new`.
- An anonymous direct request to `/schools/new` redirects to that same login
  URL. Other protected HTML routes continue to redirect to `/login` without a
  destination.
- The only accepted `next` value is `/schools/new`. Missing or unapproved
  values resolve to `/dashboard`; external, protocol-relative, and arbitrary
  local paths are not redirect targets.
- Password login, OTP login, and the successful automatic login after
  registration navigate to the approved destination.
- If automatic login after registration fails, the user is sent to the
  corresponding allow-listed login URL, preserving the school-registration
  intent for a retry.

## Compatibility And Security

This changes no REST payload, permission, tenant, database, or session model.
`/schools/new` remains authenticated and the school-creation endpoint keeps
its existing authorization behavior. Restricting the value at the rendering
boundary prevents this public query parameter from becoming an open redirect.

## Acceptance Criteria

- Both landing CTAs, direct anonymous `/schools/new` access, and the
  registration-to-login retry path retain `/schools/new` through
  authentication.
- Password and OTP login, plus registration's automatic login, navigate to
  the rendered approved destination.
- Invalid `next` values render `/dashboard`; no unapproved destination reaches
  client-side navigation.
- Existing protected pages other than `/schools/new` keep their plain login
  redirect.
- Integration coverage proves the landing and direct-entry journeys, the
  allow-list fallback, and access to the school form with the resulting
  session.

## Implementation

The landing CTAs, the rendered destination, and the allow-list shipped first;
the direct-entry redirect and the registration retry path followed on
2026-09-27.

- [`PostAuthenticationDestination.java`](../../src/main/java/com/example/roshdeandishe/iam/config/PostAuthenticationDestination.java) -
  `from()` resolves the rendered `next`; `loginUrlFor()` resolves the login URL
  for an anonymous request. Both read the same allow-list.
- [`SecurityConfig.java`](../../src/main/java/com/example/roshdeandishe/iam/config/SecurityConfig.java) -
  `PostAuthenticationAwareLoginEntryPoint` replaces the stock
  `LoginUrlAuthenticationEntryPoint`. It builds the redirect from the
  allow-list constant rather than echoing the request, and strips the context
  path before matching.
- [`login.js`](../../src/main/resources/static/js/login.js) and
  [`register.js`](../../src/main/resources/static/js/register.js) - password
  login, OTP login, and the successful automatic login navigate to the
  rendered destination; `loginPagePath` covers the failed-auto-login retry.

## Verification

Run `./mvnw test -Dtest=LoginDashboardFlowIntegrationTest,RegistrationFlowIntegrationTest`
and `git diff --check`. No migration or module-boundary verification is needed:
this is a page-security entry-point and client-navigation change within
`iam.shared`.

Last run on 2026-09-27: the full suite passed at 186 tests, 0 failures.
Browser verification was not performed; the environment had no PostgreSQL
instance, so the evidence is the integration tests driving the real Spring
Security filter chain.
