# Deploying Roshdeandishe to Liara

Everything needed to run this application on [Liara](https://liara.ir) — an
Iranian PaaS. The repo ships a `Dockerfile` and a `liara.json`, so a deploy is
one CLI command once the app and its database exist.

This is the first real deployment of this project. Read
[Known limitations](#known-limitations) before going live with real schools —
several are deliberate, documented choices rather than oversights, and one
(sessions) constrains how you scale.

---

## 1. Prerequisites

- A Liara account with the CLI installed and authenticated:
  ```bash
  npm install -g @liara/cli
  liara login
  ```
- A **PostgreSQL** database on Liara (or any Postgres reachable from the app).
- A domain — either the free `<app>.liara.run` subdomain or your own.

---

## 2. Create the app and the database

1. **App**: create a Docker-platform app in the Liara console. Whatever you name
   it must match `app` in `liara.json` (currently `roshdeandishe`) — or pass
   `--app <name>` on every deploy.

2. **Database**: create a PostgreSQL instance. Note the host, port, database
   name, username, and password — the app needs them as environment variables.

   **An empty database is all you need.** Flyway owns the schema (see
   [`openspec/decisions/2026-07-30-flyway-schema-ownership.md`](openspec/decisions/2026-07-30-flyway-schema-ownership.md)):
   on first start it creates all eight per-module schemas from
   `spring.flyway.schemas`, then applies `src/main/resources/db/migration/`.
   No manual SQL — provided the database user can `CREATE`, which is the
   default for a Liara-provisioned one. If yours can't, create the schemas by
   hand once:

   ```sql
   CREATE SCHEMA IF NOT EXISTS iam;
   CREATE SCHEMA IF NOT EXISTS school;
   CREATE SCHEMA IF NOT EXISTS academic;
   CREATE SCHEMA IF NOT EXISTS finance;
   CREATE SCHEMA IF NOT EXISTS bale;
   CREATE SCHEMA IF NOT EXISTS curriculum;
   CREATE SCHEMA IF NOT EXISTS studyplan;
   CREATE SCHEMA IF NOT EXISTS exam;
   ```

   Pointing this release at a database built by an older `ddl-auto: update`
   build is safe: `baseline-on-migrate` baselines it at v1 and skips the
   baseline migration, leaving existing rows alone.

---

## 3. Generate the secrets

Two independent HMAC-SHA256 signing keys are required, and they must **not** be
the values committed in `application.yml` (those are dev-only and public):

```bash
echo "JWT_SECRET=$(openssl rand -base64 32)"
echo "EXAM_TOKEN_SECRET=$(openssl rand -base64 32)"
```

They are deliberately separate keys: `JWT_SECRET` signs the mobile app's bearer
tokens, `EXAM_TOKEN_SECRET` signs per-attempt exam-taking session tokens. Do not
reuse one for both.

> Rotating either key invalidates every token signed with the old one — mobile
> clients get logged out, in-progress exam attempts break. Rotate deliberately.

---

## 4. Set the environment variables

Set these in the Liara console (**App → Settings → Environment Variables**) or
with `liara env set`.

Do **not** put them in `liara.json`'s `envs` field: that field *replaces* the
app's entire environment on each deploy, so console-set values silently vanish.
Secrets also do not belong in a committed file.

### Required — the app refuses to start without them

This is intentional. An application that boots with a public signing key or a
`localhost` activation link fails silently, which is worse than not booting.

| Variable | Meaning |
|---|---|
| `DATABASE_URL` | Full JDBC URL, e.g. `jdbc:postgresql://<host>:<port>/<db>` |
| `DATABASE_USERNAME` | Database user |
| `DATABASE_PASSWORD` | Database password |
| `APP_BASE_URL` | Public base URL, e.g. `https://roshdeandishe.liara.run` — no trailing slash. Used to build account-activation links |
| `JWT_SECRET` | Mobile bearer-token signing key (§3) |
| `EXAM_TOKEN_SECRET` | Exam-session token signing key (§3) |

### Optional

| Variable | Default | Meaning |
|---|---|---|
| `DATABASE_POOL_SIZE` | `10` | Hikari max pool size. Lower it if your Postgres plan has a small connection cap |
| `MAIL_HOST` | `localhost` | SMTP host. Without it, invite emails simply fail (see below) |
| `MAIL_PORT` | `587` | SMTP port |
| `MAIL_USERNAME` | — | SMTP user |
| `MAIL_PASSWORD` | — | SMTP password |
| `MAIL_FROM` | `no-reply@roshdeandishe.ir` | From-address on system email |
| `MAIL_SMTP_AUTH` | `true` | SMTP auth on/off |
| `MAIL_SMTP_STARTTLS` | `true` | STARTTLS on/off |
| `BALE_BOT_TOKEN` | *(empty)* | Bale bot token. Empty = the bot logs what it would send and makes no HTTP call |
| `BALE_WEBHOOK_SECRET` | *(empty)* | Secret path segment for the Bale webhook. Empty = the webhook always 404s (fails closed) |
| `SMS_API_KEY` | *(empty)* | Placeholder for a future SMS provider key. **Setting it does not enable OTP delivery** — no provider integration is written yet, so a nonblank key makes `SmsDriver` log an error and report failure. OTP login does not work in production at any value (see below) |
| `SWAGGER_ENABLED` | `false` | API docs are **off** in production. `/swagger-ui.html` and `/v3/api-docs` are `permitAll` in `SecurityConfig`, so enabling this publishes your whole API surface publicly |
| `SESSION_COOKIE_SECURE` | `true` | Session cookie `Secure` flag. Leave on; only turn off if you deliberately serve plain HTTP |
| `HEALTH_CHECK_MAIL` | `false` | Whether SMTP reachability counts toward `/actuator/health` (see §7) |
| `PLATFORM_ADMIN_EMAILS` | *(empty)* | Comma-separated emails of platform administrators (who manage platform-wide holidays). Empty = **no** platform admin. See "Platform administrators" below before setting it |
| `REMINDER_SEND_HOUR` | `8` | School-local hour (0-23) from which yearly-plan event reminders are sent. Reminders are Bale messages, so they only reach users who linked Bale and only when `BALE_BOT_TOKEN` is set; the dashboard box «رویدادهای نزدیک» works regardless. An invalid value stops the app at startup |
| `REMINDER_ENABLED` | `true` | Kill switch for the yearly-plan reminder job. Set `false` to stop all reminder messages without a redeploy of code (the dashboard box «رویدادهای نزدیک» is unaffected) |

**Email is optional by design.** Invite/activation email is best-effort
throughout this codebase: if SMTP is unset or fails, issuing an invite still
succeeds and returns `emailSent: false`, and the admin hands the activation link
over manually. Missing SMTP degrades one feature; it never blocks startup.

### Platform administrators

A platform administrator can add platform-wide holidays (`/platform/holidays`);
nothing else. There is no sign-up, invite or seed path for them: the only way is
`PLATFORM_ADMIN_EMAILS`, applied at every startup.

1. **Register your own account first** through the normal `/register` page.
2. Then set `PLATFORM_ADMIN_EMAILS` to that account's email and restart.

Why the order matters: registration is open and does not verify email ownership.
If you list an email nobody has registered yet, **whoever registers it first
becomes a platform administrator.** Never list an address you do not control.

How it behaves: the variable is the single source of truth. At each start the
platform-admin table is set to exactly the listed emails that belong to an
existing, active, activated (has a password) account. Removing an email, or
suspending the account, removes the access at the next restart; an empty or unset
variable means no platform administrators. Matching ignores letter case, and an
email that matches more than one account (for example `a@x` and `A@x`) is
rejected for everyone. The app never creates the account and never logs the
addresses.

---

## 5. Deploy

```bash
liara deploy
```

`liara.json` pins the important choices:

- `platform: "docker"` — builds from the repo's `Dockerfile`.
- `port: 8080` — the container's listening port.
- `build.location: "germany"` — the build pulls from Maven Central, which is
  more reliable from Liara's German build servers than the Iranian ones. Switch
  to `iran` if you'd rather, but expect dependency-resolution trouble.
- `healthCheck` — polls `/actuator/health` with a 90-second `startPeriod`
  (a cold start plus first-boot schema creation takes roughly 20–40 seconds).

The image bakes in `SPRING_PROFILES_ACTIVE=prod`. That is what disables the demo
seed data and makes the secrets above mandatory, so do not override it.

**Tests do not run during the image build** (`-DskipTests`): the integration
suite needs a Docker daemon for Testcontainers, which a PaaS build server does
not provide. Tests are CI's job — see `.github/workflows`.

---

## 6. First run

There is no admin account. Production seeds only the catalog data the app cannot
run without — modules, roles, permissions, role-permission grants — and no users
or schools at all.

Onboard yourself through the normal self-service flow:

1. Go to `https://<your-domain>/register` and create an account.
2. Log in, then go to `/schools/new` and create your school. You automatically
   become its `SCHOOL_ADMIN`.
3. The school is granted the **free tier** at creation — `ACADEMIC` and
   `FINANCE`. The paid modules (`CURRICULUM`, `STUDY_PLANNER`, `EXAM`) return
   403 `module-not-entitled` until granted.

Granting a paid module has **no REST endpoint on purpose** (see
[`openspec/decisions/2026-07-20-free-tier-entitlements.md`](openspec/decisions/2026-07-20-free-tier-entitlements.md))
— it needs platform-admin permission infrastructure that does not exist yet.
Until billing is built, grant one directly in the database:

```sql
INSERT INTO iam.school_entitlements (school_id, module_id, active, starts_at)
SELECT <schoolId>, id, true, now() FROM iam.modules WHERE code = 'CURRICULUM';
```

---

### Demo data for a presentation

`scripts/seed-demo-data.py` fills a deployment with believable content — a
school, four classes, ~24 students with Persian names, tuition and a realistic
mix of paid/partial/unpaid invoices, a term of descriptive grades, plus a topic
tree, question bank, and a collective exam:

```bash
python3 scripts/seed-demo-data.py \
    --url https://your-app.liara.run \
    --email demo@example.com \
    --password 'a-strong-password'
```

Standard library only, no `pip install`. It works entirely through the public
REST API as a real admin would, so nothing it creates can be in a state the app
would refuse to produce, and re-running is safe — an existing account, school,
classes, students, and exam are reused rather than duplicated.

It deliberately does **not** re-enable seed data. The account is an ordinary one
whose password you supply at run time, so it can be changed or deleted after the
presentation — unlike a seeded admin baked into the image, which would be a
public `SCHOOL_ADMIN` with a known password.

The paid modules can't be granted over HTTP (§10), so the script prints the SQL
for them and carries on. Run that SQL, then re-run the script to add the
curriculum and exam content.

## 7. Health and troubleshooting

`/actuator/health` is the deploy's health signal. In production only `health` is
exposed and details are hidden — the public response is just
`{"status":"UP"}`. `/actuator/env`, `/actuator/modulith`, and the rest stay 404.

Health reflects **database reachability**, which is what genuinely makes this app
unusable. SMTP is excluded (`HEALTH_CHECK_MAIL=false`): Boot's mail indicator
connects to SMTP and marks the whole endpoint `DOWN` if it can't, which would
have Liara flagging every deploy unhealthy over an optional feature.

| Symptom | Cause |
|---|---|
| Startup fails, `Could not resolve placeholder 'X'` | A required variable from §4 is unset. The name in the message is the missing one |
| Startup fails, `schema "iam" does not exist` | The database user can't `CREATE SCHEMA`. Run the SQL in §2 manually |
| Health check never passes | Check `DATABASE_URL` first — a wrong host looks exactly like a slow start until the `startPeriod` runs out |
| Login works, then requests 403 | Usually a CSRF/cookie issue. Confirm the app is served over HTTPS, since `SESSION_COOKIE_SECURE` defaults to on |
| Everyone logged out after a deploy | Expected — see below |

Logs: `liara logs --follow`.

### Memory

The image sets its own JVM limits in `JAVA_OPTS` (`Dockerfile`), so the plan's
memory is not handed to the heap wholesale: the heap is capped at 192 MB
against a measured live set of about 54 MB, and the collector is SerialGC
because this is a single instance with a small heap. Measured at 408 MB RSS,
down from 537 MB before those limits were set.

Two cautions before you size a plan on that number. It was measured on an
**empty database** with no accumulated logins, and sessions live in this
container's memory (see below), so real heap grows with concurrent users.
Expense attachments also pass through the heap as `byte[]`, up to 5 MB each.
Watch memory on the current plan before moving to a smaller one.

`spring.autoconfigure.exclude` in `application-prod.yml` switches off the Spring
Modulith runtime bootstrap, which otherwise scans the whole classpath through
ArchUnit at every startup for a module model that production never exposes.
Module boundaries are still verified by `ModularityTests` in CI, and
`/actuator/modulith` still works in development. Full rationale and the
measurement method are in
[`openspec/changes/production-memory-footprint.md`](openspec/changes/production-memory-footprint.md).

---

## Known limitations

These are real constraints on this deployment, not a wishlist.

**You can still run exactly one instance — but no longer because of sessions.**
Sessions now live in Postgres (`spring-session-jdbc`, tables created by
`V20__spring_session.sql`), so a deploy or restart **no longer logs everyone
out**. The remaining blocker is scheduled work: `EventReminderJob` (hourly) and
`AcademicYearProvisioner` (daily) have no leader election or lock, so on two
instances both run on every instance. `EventReminderJob` writes its sent-log
row before sending with `on conflict do nothing`, so duplicate *messages* are
unlikely; `AcademicYearProvisioner` has no such guard. Fix that before scaling.
See [`openspec/changes/jdbc-backed-web-sessions.md`](openspec/changes/jdbc-backed-web-sessions.md).

One-time cost: the deploy that introduces this logs everyone out once more,
because in-memory sessions cannot be migrated into the table. Every deploy
after it keeps people signed in.

**Bearer-token requests also create session rows.** `JwtAuthenticationFilter`
documents itself as stateless, but every Bearer-authenticated request creates a
session carrying the security context. This predates the move to Postgres —
unmodified builds hand back a `JSESSIONID` for the same request — and it was
simply invisible while sessions lived in heap. Expired rows are cleaned up
automatically, and phone login does not work in production anyway (see below),
so there is no live cost today. Fix it before mobile login goes live; the same
proposal explains why and how.

**Every entity change needs a migration file.** Flyway owns the schema and
`ddl-auto` is `validate`, so Hibernate no longer creates or alters anything: if
an entity and the real schema drift apart, the application **fails to start**
rather than silently adding a column. That is the intended behaviour — but it
means a release whose entities changed without a matching
`src/main/resources/db/migration/V<n>__....sql` will not boot. See
[`openspec/decisions/2026-07-30-flyway-schema-ownership.md`](openspec/decisions/2026-07-30-flyway-schema-ownership.md)
for the working rules. Take a backup before deploying a migration that drops or
rewrites anything.

**There is no platform-admin path.** Granting paid modules and suspending a
school are facade-only, with no REST endpoint, because no real permission check
exists for platform-privileged actions. Both are database operations for now;
see [`openspec/changes/proposed-work.md`](openspec/changes/proposed-work.md).

**A school's slug cannot be changed after creation.** There is no
school-settings surface yet. Branded entry lives at
`/s/{slug}`; per-school *subdomains* (Tier 2) need wildcard DNS plus wildcard
TLS, and are unbuilt — now that a real deployment exists, that's a decision you
can actually make.

**OTP login does not work in production, and no configuration changes that.**
`SmsDriver` has no SMS provider integration — the HTTP call was never written.
With `SMS_API_KEY` empty it logs "not configured"; with a nonblank key it logs
"provider integration not implemented". Either way it reports failure, and
`IamFacadeImpl.requestOtp` fails closed: the request is rejected with
`OtpDeliveryUnavailable` and no verification code is stored. Codes are printed
to the log only in dev, where `roshdeandishe.sms.dev-log-codes` defaults to
`true`; `application-prod.yml` sets it to `false`. Do not plan a launch around
phone login until a provider is integrated in code. Email/password login is
unaffected.
