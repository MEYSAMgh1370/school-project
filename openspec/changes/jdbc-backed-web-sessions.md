# JDBC-Backed Web Sessions

Status: proposed on 2026-10-08 and implemented the same day. Current behavior
is in [`../architecture.md`](../architecture.md) and
[`../../DEPLOYMENT.md`](../../DEPLOYMENT.md).

## Decision

Move web `HttpSession` state from the application's heap into PostgreSQL, using
`spring-session-jdbc` with the session tables owned by Flyway like every other
table in this repository.

Two things this delivers, and one widely assumed thing it does not.

Delivered:

- **A deploy or restart no longer logs everyone out.** Sessions outlive the
  process that created them.
- **Session state leaves the heap.** It becomes rows in Postgres rather than an
  unbounded, unmeasured contributor to resident memory.

**Not delivered: this does not make the application safe to run on more than
one instance.** Sessions were one blocker. At least one other remains, and it
is not addressed here — see "What this does not unlock".

## Verified Baseline

Checked against the repository at `1fb1415` on 2026-10-08.

- Web authentication stores the Spring Security context in `HttpSession` via
  `HttpSessionSecurityContextRepository`, constructed directly in
  [`AuthController.java`](../../src/main/java/com/example/roshdeandishe/iam/controller/AuthController.java).
- Nothing else is placed in the session. A search of `src/main/java` finds no
  `setAttribute` call; the session holds the security context and nothing more.
- The authenticated principal is Spring Security's own
  `org.springframework.security.core.userdetails.User`, built in
  [`IamUserDetailsService.java`](../../src/main/java/com/example/roshdeandishe/iam/internal/IamUserDetailsService.java)
  with an empty authority list. That type is `Serializable`, which is what makes
  a serializing session store viable without touching application code.
- Mobile authentication is unaffected: `JwtAuthenticationFilter` sets the
  security context per request and deliberately does not create a session.
- [`DEPLOYMENT.md`](../../DEPLOYMENT.md) already records the current limitation
  and names `spring-session-jdbc` as the intended remedy, so this proposal
  implements a direction the repository had already written down rather than
  introducing a new one.

### Why the session tables are schema-qualified

Spring Session JDBC is plain JDBC, not JPA. It does not see
`spring.jpa.properties.hibernate.default_schema`; an unqualified table name
resolves through the connection's `search_path`, which on this database is
`"$user", public` — not `iam`.

Rather than depend on that, the table name is configured schema-qualified as
`iam.spring_session`. `JdbcIndexedSessionRepository` substitutes a single
`%TABLE_NAME%` token into its statements and derives the attribute table as
`%TABLE_NAME%_ATTRIBUTES`, so one qualified value covers both tables.

## Proposed Behavior

- Add `org.springframework.session:spring-session-jdbc`. The version is managed
  by `spring-boot-dependencies` (4.1.0); none is pinned.
- Add `V20__spring_session.sql` creating `iam.spring_session` and
  `iam.spring_session_attributes`. The columns, indexes and constraints are
  those of `schema-postgresql.sql` shipped inside `spring-session-jdbc-4.1.0`,
  transcribed into this repository's lowercase, schema-qualified migration
  style. Postgres folds unquoted identifiers, so the lowercase form is the same
  table Spring Session addresses.
- Set `spring.session.jdbc.initialize-schema: never`. Flyway owns the schema
  here, per
  [`2026-07-30-flyway-schema-ownership.md`](../decisions/2026-07-30-flyway-schema-ownership.md),
  and Spring Session must not create or alter tables behind it.
- Set `spring.session.jdbc.table-name: iam.spring_session`.
- Configure this in `application.yml`, not only in the production profile, so
  development runs the same session store as production. A session mechanism
  that differs between profiles is a defect waiting for a deploy.
- **Except under test**, where Spring Session is switched off. This was not the
  original intent and is forced by the existing suite — see below.
- Session timeout is left at its existing default. This change moves where
  sessions live, not how long they last.

## What This Does Not Unlock

Running more than one instance is still unsafe, because two scheduled jobs have
no multi-instance guard — no leader election, no advisory lock, no
`skip locked`:

- `EventReminderJob` ([`EventReminderJob.java`](../../src/main/java/com/example/roshdeandishe/notification/internal/EventReminderJob.java)),
  hourly at minute 10, sends reminder messages.
- `AcademicYearProvisioner` ([`AcademicYearProvisioner.java`](../../src/main/java/com/example/roshdeandishe/school/internal/AcademicYearProvisioner.java)),
  daily at 00:15, provisions academic years.

On two instances both jobs run twice per tick. `EventReminderJob` inserts its
sent-log row before sending, with `on conflict do nothing`, so duplicate
*messages* are unlikely — that guard was built for retries and restarts and
happens to help here. `AcademicYearProvisioner` has no such guard and was
written on the documented assumption of a single instance, recorded in
[`event-reminders.md`](event-reminders.md).

Treat multi-instance as a separate, unapproved change. This proposal removes
one precondition for it and nothing more.

## Acceptance Criteria

1. Logging in writes a row to `iam.spring_session`, and a row per session
   attribute to `iam.spring_session_attributes`.
2. **A session survives a full process restart**: a cookie obtained before the
   restart still authenticates an authenticated-only page afterwards, with no
   second login.
3. Flyway, not Spring Session, creates the tables: a boot against an
   already-migrated database makes no schema change, and `ddl-auto: validate`
   still starts cleanly.
4. ~~Bearer-token (mobile) authentication creates no session row, since
   `JwtAuthenticationFilter` is stateless.~~

   **This criterion was false when written, and testing disproved it.** It was
   taken from the Javadoc on
   [`JwtAuthenticationFilter.java`](../../src/main/java/com/example/roshdeandishe/iam/config/JwtAuthenticationFilter.java),
   which states that a Bearer request places the `Authentication` on the holder
   "without storing it in `HttpSession`" and that JWT auth "stays completely
   stateless". It does not. A single `GET /api/me` with a valid Bearer token
   creates a session holding `SPRING_SECURITY_CONTEXT` for that principal and
   returns a session cookie.

   This is **pre-existing and not caused by this change**, verified by running
   unmodified `1fb1415`: the same request there returns a `JSESSIONID` cookie,
   so a session was always being created — in heap, invisibly, discarded on
   restart. This change relocates those sessions to Postgres, which is what
   made the defect observable.

   The corrected criterion: a Bearer request creates a session row, matching the
   behavior that already existed. See "Discovered: JWT auth is not stateless"
   below. Nothing about it is made worse here beyond visibility and durability,
   and it is not fixed here because the remedy is a change to the security
   filter chain, which needs its own proposal and its own authorization test
   matrix per [`../conventions.md`](../conventions.md).
5. `ModularityTests.verifyModularStructure()` still passes.
6. ~~Production memory does not regress.~~ **It does, measurably: +14 MB.**

   Three `prod`-profile runs each, same tree, same session, same load:

   | | RSS | spread |
   |---|---|---|
   | Without this change | 413 MB | 411–418 |
   | With this change | **427 MB** | 423–430 |

   The two spreads barely overlap, so unlike Tier 2 in
   [`production-memory-footprint.md`](production-memory-footprint.md) this is a
   real effect rather than noise. It comes from the added library: 22,998
   classes load against 22,597 before, about 401 more.

   The criterion was wrong to demand no regression, because this change trades
   memory deliberately. It swaps an **unbounded, unmeasured** heap cost that
   grows with every concurrent login for a **bounded, known** 14 MB of class
   metadata plus rows in Postgres. That is the trade worth making, and it is
   the reason this change was proposed as a memory measure in the first place.
   What it is not is a reduction, and it should not be recorded as one.

## Spring Session is switched off under test, and why

The first CI run of this change produced **193 failures and 38 errors out of
820 tests**, every one of them `IllegalArgumentException: 'session' must not be
null`.

The cause is mechanical, not a defect in either side. **44 test files** carry
login state between MockMvc requests with

```java
MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
```

With Spring Session active, `SessionRepositoryFilter` wraps the request and the
session lives in the repository, not on the `MockHttpServletRequest` that
`getRequest()` hands back. So `getSession(false)` returns `null`, and
`MockMvcRequestBuilders.session(null)` rejects it.

The fix excludes `SessionAutoConfiguration` and `JdbcSessionAutoConfiguration`
for the test run, through `systemPropertyVariables` on the surefire plugin. The
test context's session behavior then becomes exactly what it was before this
change — container sessions — which is the configuration those 820 tests were
written against and passed under.

What this costs, stated plainly: the suite does not exercise the production
session store. That sounds worse than it is, because **it never did**. Those
tests used `MockHttpSession`, which is not Spring Session and is not the
servlet container's session either. No coverage that exists today is lost.
[`WebSessionPersistenceFlowIntegrationTest`](../../src/test/java/com/example/roshdeandishe/WebSessionPersistenceFlowIntegrationTest.java)
clears the exclusion for itself and is the one place the real JDBC path is
asserted.

The alternative — rewriting 44 test files to carry the session cookie instead
of a `MockHttpSession` — would exercise the real store everywhere. It is a
large change to tests this proposal did not otherwise touch, and it belongs in
its own change if the parity is judged worth it.

## Discovered: JWT auth is not stateless

Reported rather than fixed, per the convention on adjacent bugs.

[`JwtAuthenticationFilter.java`](../../src/main/java/com/example/roshdeandishe/iam/config/JwtAuthenticationFilter.java)
documents Bearer authentication as leaving no `HttpSession`. Measured
behavior, on unmodified `1fb1415` and with this change alike, is that every
Bearer-authenticated request creates a session carrying
`SPRING_SECURITY_CONTEXT`.

The likely mechanism is the explicit `sessionManagement` block in
[`SecurityConfig.java`](../../src/main/java/com/example/roshdeandishe/iam/config/SecurityConfig.java),
which sets `SessionCreationPolicy.IF_REQUIRED` and brings
`SessionManagementFilter` into the chain — visible in a stack trace in the
application log. That filter persists a security context the JWT filter has
just changed. This is a hypothesis about the cause; the behavior itself is
measured.

Consequences, in order of how much they matter today:

- Before this change: an invisible per-request session in heap, discarded on
  restart. A quiet contributor to the very heap growth that motivated moving
  sessions out.
- After this change: a durable row per Bearer request, removed by Spring
  Session's cleanup job once it expires. Row churn proportional to mobile API
  traffic.
- Today that traffic is approximately zero, because phone login does not work
  in production at all: `SmsDriver` has no provider integration and
  `requestOtp` fails closed, as [`../../DEPLOYMENT.md`](../../DEPLOYMENT.md)
  records under "Known limitations". So this is a latent cost, not a live one.

The remedy is a security-filter-chain change — most plausibly a separate
`SecurityFilterChain` matched on Bearer requests with
`SessionCreationPolicy.STATELESS`, so that the two authentication paths stop
sharing one session policy. That touches authorization behavior and needs
protected and negative scenarios, so it belongs in its own change proposal
rather than being folded in here.

**Sequencing note:** if mobile login is ever made to work, fix this first.
Shipping a working phone login on top of a session-per-request path would put
real churn into the session table.

## Migration and Compatibility Concerns

- **Existing sessions are lost once, on the deploy that introduces this.**
  In-memory sessions cannot be migrated into the table, so everyone is logged
  out exactly one more time. Every deploy after that preserves sessions.
- **The migration is additive.** Two new tables, no change to existing ones, so
  no backup is required beyond normal practice.
- **Login now writes to the database.** Session reads and writes become SQL
  against the same Hikari pool that serves application queries;
  `DATABASE_POOL_SIZE` is unchanged, and session traffic is proportional to
  request volume, which is low for this deployment.
- **Rolling back** means reverting the dependency and configuration; the tables
  can be left in place harmlessly, and sessions revert to in-memory.

## Verification

```bash
./mvnw test -Dtest=ModularityTests
./mvnw clean verify
git diff --check
```

`clean verify` requires Docker for the Testcontainers suites.

## Unresolved Questions

1. Should the session timeout be made explicit rather than inherited from the
   framework default? It is a product decision about how long a school
   administrator stays signed in, and nobody has stated one.
2. Does the deployment want `SPRING_SESSION` cleanup tuned? Spring Session runs
   a cleanup job every minute by default; at this scale that is harmless, but it
   is a knob nobody has considered.
3. Multi-instance operation remains unapproved and needs the scheduled-job
   guard described above before it can be considered.
