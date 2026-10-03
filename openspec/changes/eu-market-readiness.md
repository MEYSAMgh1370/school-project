# EU Market Readiness

Status: proposed on 2026-09-29 from a repository audit. This is a
documentation-only change. It authorizes no code, schema, test, or
current-system specification change by itself.

Open question 1 was answered on 2026-10-03 and is now recorded in
[`decisions/2026-10-03-multi-market-not-migration.md`](../decisions/2026-10-03-multi-market-not-migration.md):
Iranian schools remain a segment, so a second market is additive. Every work
item below is read under that decision — each coupling becomes a configurable
dimension resolved per tenant, not something removed.

## Why This Document Exists

The product owner is pursuing a European startup-visa route, whose evaluators
(an incubator, a national agency, or a ministry panel, depending on the
country) assess whether the company can operate in, and create value for, the
destination market. That assessment is a product question, not a legal one,
and it lands on this repository: the current build is locked to the Iranian
market at the code level, and the audit below records exactly where.

This document is the inventory of that lock-in. It states what was verified,
what each work item would have to change, and what remains an unresolved
product decision. It deliberately does not decide the target market, the
pricing model, or the monetary unit — those are product decisions, and the
open questions are listed rather than answered.

## Verified Baseline

Audited on 2026-09-29 against `src/`, `openspec/`, and the Flyway migrations.

### Localization

- No message bundle exists. No `messages*.properties` file is present under
  [`src/main/resources/`](../../src/main/resources/), and no class configures a
  `MessageSource` or a `LocaleResolver`.
- All 27 JTE templates under [`src/main/jte/`](../../src/main/jte/) contain
  Persian literal text — roughly 820 distinct text runs, concentrated in
  `study-plans.jte`, `expenses.jte`, and `exams.jte`.
- 43 Java files carry Persian inside string literals (roughly 400 lines), most
  densely in the per-module problem resolvers and exception types, for example
  [`IamProblemResolver.java`](../../src/main/java/com/example/roshdeandishe/iam/shared/exception/IamProblemResolver.java)
  and [`FinanceProblemResolver.java`](../../src/main/java/com/example/roshdeandishe/finance/shared/FinanceProblemResolver.java).
  These are user-facing API error messages.
- A further ~2,400 lines carry Persian in comments and Javadoc across 200
  files. Those are internal documentation, not user-facing output, and are
  out of scope for localization.
- 23 files under [`static/js/`](../../src/main/resources/static/js/) contain
  Persian literals (roughly 800 text runs).

### Calendar

- Jalali conversion is client-side only and contained:
  [`jalali.js`](../../src/main/resources/static/js/jalali.js) is 160 lines and
  exposes `window.Jalali`.
- It is consumed by four scripts (`expenses.js`, `my-grades.js`,
  `my-study-plan.js`, and itself) and referenced by four templates
  (`expenses.jte`, `grades.jte`, `my-grades.jte`, `my-study-plan.jte`).
- No Jalali or Persian-calendar logic was found in `src/main/java` or in the
  migrations; persisted dates are calendar-neutral.

### Document Direction And Typography

- [`layout/main.jte`](../../src/main/jte/layout/main.jte) line 16 hardcodes
  `<html lang="fa" dir="rtl">` for every page.
- [`app.css`](../../src/main/resources/static/css/app.css) binds Vazirmatn as
  `--bulma-family-primary`, served from a vendored
  [`static/fonts/`](../../src/main/resources/static/fonts/) asset.

### Monetary Values

- Seven money columns in
  [`V1__baseline_schema.sql`](../../src/main/resources/db/migration/V1__baseline_schema.sql)
  are `numeric(12,2)`: `finance.tuition_fees.amount`,
  `finance.invoices.amount`, `finance.invoices.amount_paid`,
  `finance.payments.amount`, `finance.expenses.amount`,
  `finance.finance_audit.amount`, and `academic.grades.numeric_score`
  (the last is a score, not money).
- No currency concept exists anywhere: no `currency` column, no `Currency`
  type, and no ISO currency code in Java or in the migrations.
- The canonical monetary unit is already recorded as unresolved in
  [`finance-monetary-unit.md`](finance-monetary-unit.md), which remains
  unimplemented. Any currency work must resolve or supersede that proposal
  rather than bypass it.

### Messaging Integration

- Audited on 2026-09-29, when a standalone `bale` module held the integration
  and was already well isolated: every reference to it from outside was a
  comment, and coupling ran inward only through a finance event listener.
- **Superseded on master before this branch merged.** PR #87 renamed the module
  `notification` and PR #88 put a package-private `NotificationChannel` behind
  it, with Bale as the only channel — so the port this audit proposed under W5
  already exists, built independently. See
  [`rename-bale-module-to-notification.md`](rename-bale-module-to-notification.md)
  and [`notification-channel-abstraction.md`](notification-channel-abstraction.md).
  The former `bale` package is gone; its classes now live under
  [`notification/`](../../src/main/java/com/example/roshdeandishe/notification/),
  and the listener is
  [`InvoiceIssuedEventListener.java`](../../src/main/java/com/example/roshdeandishe/notification/internal/InvoiceIssuedEventListener.java).
  Finance still does not know any channel exists.
- `BaleWebhookController` is one of the documented tenant-filter exemptions in
  [`SecurityConfig.java`](../../src/main/java/com/example/roshdeandishe/iam/config/SecurityConfig.java).

### Data Protection

- No audit-log, consent, data-export, erasure, anonymization, or retention
  facility exists. Searches for each term return no Java class and no table,
  with two partial exceptions:
  - `finance.finance_audit` records finance actions only (`action`,
    `actor_user_id`, `occurred_at`, `amount`, `school_id`, `student_id`);
    it covers no other module.
  - The string `retention` appears in three files, none of which implement a
    retention policy.
- Two `@DeleteMapping` endpoints exist in total
  ([`StudyIntervalController.java`](../../src/main/java/com/example/roshdeandishe/studyplan/controller/StudyIntervalController.java)
  and
  [`ExpenseController.java`](../../src/main/java/com/example/roshdeandishe/finance/controller/ExpenseController.java));
  neither deletes a person.
- The system stores personal data about minors and their guardians:
  `iam.users` holds `full_name`, `email`, and `phone_number`;
  `academic.students` links a user to a class; `academic.student_guardians`
  links a student to a guardian user. Grades, invoices, payments, exam
  attempts, and study plans are all attached to those identities.

### Commercial Surface

- No subscription, plan, price, or platform-invoice table exists. The full
  table inventory contains `iam.school_entitlements` and
  `iam.user_entitlements` and nothing billing-related.
- No REST mapping grants, revokes, or changes an entitlement. This matches the
  limitation already recorded in
  [`specs/current-system.md`](../specs/current-system.md): paid entitlement
  changes are facade-only.

## Proposed Work Items

Each item states current state, target, and acceptance criteria. Sizes are a
rough order of magnitude for planning only, not a commitment.

### W0 — Serve the web font at all (size: XS, a bug fix)

Current: the Vazirmatn font has never loaded. `/fonts/**` is absent from
`EXCLUDED_PATHS` in
[`TenantResolverFilter.java`](../../src/main/java/com/example/roshdeandishe/platform/config/TenantResolverFilter.java),
which requires an `X-School-ID` header on every non-excluded path and returns
400 without one. A browser fetching a CSS subresource sends no such header, so
`GET /fonts/Vazirmatn-Variable.woff2` returns 400 on every page. `/fonts/**` is
also absent from the `permitAll()` list in
[`SecurityConfig.java`](../../src/main/java/com/example/roshdeandishe/iam/config/SecurityConfig.java),
so it would additionally require authentication on the anonymous pages. No test
covers a `/fonts/` request. The app has been rendering on the system font
fallback since the typeface was vendored.

This was found while scoping W3 and is a prerequisite for it: making a font
stack locale-conditional is meaningless while the font 404s on every request.

Target: the font is served to an unauthenticated browser with no tenant header.

Acceptance criteria:

- `GET /fonts/Vazirmatn-Variable.woff2` returns 200 with a non-empty body, with
  no `X-School-ID` header and no authenticated session.
- A test asserts that, and fails against the pre-fix code.
- Nothing else in the security configuration is widened.

### W1 — Externalize user-facing strings (size: L)

Current: Persian literals in 27 templates, 43 Java files, and 23 scripts; no
`MessageSource`.

Target: every user-facing string resolves through a message key, with a
Persian bundle carrying the current wording verbatim and at least one second
locale. Comments and Javadoc stay as they are.

Acceptance criteria:

- A `MessageSource` and a `LocaleResolver` are configured.
- No JTE template, controller, problem resolver, or exception type emits a
  hardcoded user-facing literal in any language.
- Client-side strings resolve from a served bundle rather than script literals.
- The Persian rendering of every existing page is byte-identical to today's
  output when the locale is Persian.

### W2 — Abstract the calendar (size: S)

Current: `jalali.js` is the only calendar layer; four scripts and four
templates call it directly.

Target: a calendar-formatting seam so a locale selects Jalali or Gregorian
presentation. Persistence stays calendar-neutral and is not touched.

Acceptance criteria:

- Callers format dates through one seam, not through `window.Jalali` directly.
- Jalali output is unchanged for the Persian locale.
- A Gregorian locale renders the same data without Jalali conversion.
- Existing date-handling tests still pass unchanged.

### W3 — Make direction and typography locale-driven (size: S)

Current: `layout/main.jte` hardcodes `lang="fa" dir="rtl"`; Vazirmatn is the
unconditional primary family.

Target: `lang` and `dir` derive from the resolved locale, and the font stack
degrades to the existing system fallback for locales Vazirmatn does not serve.

Acceptance criteria:

- `lang` and `dir` reflect the request locale on every page.
- RTL layout and Vazirmatn are unchanged for Persian.
- An LTR locale renders without RTL-specific overrides leaking through.
- `DESIGN.md` records the locale-conditional rule.

### W4 — Attach a currency to monetary values (size: M)

Current: seven `numeric(12,2)` money columns, no currency anywhere, and an
unresolved canonical unit.

Target: money is persisted and exposed with an explicit currency, and the
`numeric(12,2)` range is re-checked against whichever unit is chosen.

This item is blocked on [`finance-monetary-unit.md`](finance-monetary-unit.md),
which proposes a single canonical unit and therefore cannot stand unchanged:
two segments mean at least two currencies, so that proposal must be revised or
superseded first. See
[`decisions/2026-10-03-multi-market-not-migration.md`](../decisions/2026-10-03-multi-market-not-migration.md).
It must not proceed as a blanket migration either — that proposal's legacy-data
policy already forbids one.

Acceptance criteria (once unblocked):

- Every persisted money value carries a currency.
- The REST contract states the currency of each amount field.
- Existing rows are classified by provenance before any conversion, and
  unclassified values are preserved rather than guessed at.
- Display and entry honor the per-value currency.

### W5 — Introduce a notification port (size: S) — **already done on master**

This item was built independently on master while this branch was open, and is
no longer outstanding. PR #87 renamed the `bale` module to `notification`; PR
#88 introduced a package-private `NotificationChannel` port with Bale as its
only implementation, and a `NotificationFacadeImpl` that tries channels in
registration order and stops at the first that delivers. The port's contract is
the pre-existing best-effort `boolean send(Long userId, String text)`.

What that leaves genuinely open is only the **second channel** — and master
deliberately did not build it, recording that "the YAGNI concern still stands
for what is *not* built: no second channel, no multi-channel policy". The
dispatcher's own Javadoc defers the multi-channel policy (deliver to all
channels, or stop at the first success) to whenever a second channel arrives.

An email channel was drafted on this branch and then dropped when master's
version proved both earlier and more thorough — master moved the whole Bale
integration inside the module rather than wrapping it from outside. Two findings
from that attempt are worth keeping, because they will apply to whoever adds the
second channel:

- **Unconditional fan-out would be a production regression.** With Bale
  configured, delivering to every channel means every existing Iranian school
  receives an extra message per invoice. Master's first-success-wins dispatcher
  already avoids that, but a future change to "deliver to all" would reintroduce
  it.
- **The notification text is hardcoded Persian.** `InvoiceIssuedEventListener`
  builds its body as a Persian string literal and the port carries neither a
  locale nor a message key. Any channel serving a non-Iranian tenant would send
  Persian text. Fixing it depends on W1: until a message bundle exists there is
  nothing to resolve a key against.

Remaining acceptance criteria for the second channel, when it is requested:

- Publishers keep depending on the facade, not on any channel.
- Bale behavior is unchanged when Bale is configured.
- A deployment with no Bale configuration starts and delivers through the other
  channel.
- The multi-channel policy is decided explicitly rather than inherited.
- `ApplicationModules.verify()` still passes.

### W6 — Build the data-protection surface (size: L)

Current: no audit log outside finance, no consent record, no export, no
erasure, no retention policy — over data about minors and their guardians.

Target: a cross-module audit log, a per-subject data export, a defined erasure
or anonymization path, and a consent record.

This is the largest item and the one with genuine product and legal questions
attached. The legal profile belongs to a qualified adviser for the chosen
jurisdiction; this repository can only record what the software does.

Acceptance criteria:

- Access to and modification of personal data is recorded across modules, not
  only in finance.
- A subject's data can be exported in a machine-readable form.
- An erasure or anonymization path exists and states what it preserves and why
  (for example, financial records subject to a statutory retention period).
- Consent, where the product requires it, is recorded with its scope and time.
- Retention periods are configuration, not code constants.

### W7 — Build the commercial surface (size: M)

Current: entitlements exist as data; there is no plan, price, subscription, or
platform invoice, and no REST path to change a paid entitlement.

Target: a subscription model over the existing entitlement catalogue, so the
free-tier decision in
[`2026-07-20-free-tier-entitlements.md`](../decisions/2026-07-20-free-tier-entitlements.md)
becomes one plan among several.

Acceptance criteria:

- Plans and subscriptions are modelled and persisted.
- A school's entitlements derive from its subscription.
- The existing free Academic and Finance grant is expressible as a plan, and
  its current behavior is unchanged for existing schools.
- Granting a paid entitlement has an authorized path that is not facade-only.

## Suggested Order

W0 is a bug fix and comes first; W3 depends on it to be meaningful. W2 then
depends on W3, since the calendar seam reads the locale that W3 establishes.
W5 is already done on master, so it is not in the order at all. W2 and W3 are
small and unblock a credible non-Persian demo. W1 is the long pole and should start early because it touches every
template. W6 and W7 are product-heavy and should not start until the open
questions below are answered. W4 stays blocked.

## Unresolved Questions

These are product decisions. This document does not answer them.

1. ~~Does the product keep Iranian schools as a segment, or migrate away from
   them?~~ **Answered 2026-10-03: Iranian schools remain a segment.** See
   [`decisions/2026-10-03-multi-market-not-migration.md`](../decisions/2026-10-03-multi-market-not-migration.md).
   Which *second* market is targeted is still open, but no work item below
   depends on which one it is.
2. Which locales must ship beyond Persian? Persian stays a first-class locale
   and the Iranian-tenant default per the decision above, so this question is
   now only about what is added. Work on the locale-driven items proceeds under
   a stated working assumption of one additional locale, `en`, which commits to
   no particular country and does not preclude adding others.
3. Does the canonical monetary unit decision in
   [`finance-monetary-unit.md`](finance-monetary-unit.md) still stand if the
   product serves a second currency? W4 cannot start until this is settled.
4. Bale remains a supported channel — the `notification` module keeps it as
   its only one. What still has no answer is which channel serves a tenant
   where Bale is unavailable, and whether delivery then goes to one channel or
   to all of them.
5. Which jurisdiction's data-protection profile applies, where is data hosted,
   and who is the controller when a school is the tenant? W6's acceptance
   criteria are shaped by this and it needs qualified legal advice, not a
   repository decision.
6. What is the pricing model — per school, per seat, per module, or a
   combination? W7 cannot be specified without it.

## Out Of Scope

- Any claim about visa eligibility, immigration procedure, or legal
  compliance. This document records software state only.
- Translating comments and Javadoc.
- Changing persistence of dates, which is already calendar-neutral.
- Any blanket monetary migration, which
  [`finance-monetary-unit.md`](finance-monetary-unit.md) forbids.
