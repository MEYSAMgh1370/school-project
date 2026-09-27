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
| `SMS_API_KEY` | *(empty)* | **Has no effect — do not rely on it.** `SmsDriver.sendOtp` contains no provider call, so OTP login does not work in production whether this is set or not. Setting it makes delivery fail with an error log rather than succeed. See "Known limitations" |
| `SWAGGER_ENABLED` | `false` | API docs are **off** in production. `/swagger-ui.html` and `/v3/api-docs` are `permitAll` in `SecurityConfig`, so enabling this publishes your whole API surface publicly |
| `SESSION_COOKIE_SECURE` | `true` | Session cookie `Secure` flag. Leave on; only turn off if you deliberately serve plain HTTP |
| `HEALTH_CHECK_MAIL` | `false` | Whether SMTP reachability counts toward `/actuator/health` (see §7) |

**Email is optional by design.** Invite/activation email is best-effort
throughout this codebase: if SMTP is unset or fails, issuing an invite still
succeeds and returns `emailSent: false`, and the admin hands the activation link
over manually. Missing SMTP degrades one feature; it never blocks startup.

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

---

## Known limitations

These are real constraints on this deployment, not a wishlist.

**Sessions are in-memory, so you can run exactly one instance.** Web auth uses
`HttpSession` held in the container's memory. Every deploy or restart logs
everyone out, and scaling past one instance breaks login outright — requests
would land on an instance that has never seen the session. Scaling needs either
sticky sessions or `spring-session-jdbc` to move sessions into Postgres. Neither
is built; decide it deliberately when you actually need to scale.

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

**OTP login does not work in production, and no configuration fixes that.**
The SMS provider integration is unwritten: `SmsDriver.sendOtp` contains no
provider call on any path. Setting `SMS_API_KEY` does *not* enable delivery —
the method logs an error and returns failure, so no valid code is stored either.
Leaving it empty fails the same way, and under the `prod` profile the code is
not written to the log at all, because `application-prod.yml` pins
`roshdeandishe.sms.dev-log-codes` to `false`; code logging is a dev/test aid
only. Shipping phone login therefore needs code, not a key. Email/password
login is unaffected.
