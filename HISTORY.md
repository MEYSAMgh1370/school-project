# Roshdeandishe — Project History

This file is the archival companion to [`CLAUDE.md`](CLAUDE.md) — the full "what
shipped and why" build log (formerly CLAUDE.md §7's `### Done` subsection) and the
phase-by-phase roadmap (formerly §8). It moved here on 2026-07-26 to keep CLAUDE.md
itself under control (it had grown to 261KB, most of it this changelog); nothing in
the content changed, only its location.

**New completed-work entries still go here**, in the same commit as the code that
completes them, following the exact shape of the existing entries (what changed,
why, what was explicitly deferred, test coverage) — the standing convention CLAUDE.md
itself describes is unchanged, only the file it points to moved.

CLAUDE.md's `### Done` (§7) and `## 8. Roadmap` headers stay in place there — they
now just point back to this file — so every existing `§7 Done`/`§8` cross-reference
elsewhere in CLAUDE.md remains accurate.

---

## §7 Done


- User / Role / Permission / RolePermission / SchoolMembership model
- Module / ModuleScope / SchoolEntitlement / UserEntitlement model
- Two-layer access control (`@RequiresModule` + `@RequiresPermission`), for
  `SchoolContext` only
- Sealed error model + `ProblemDetail` across `iam`, `academic`, and `school`
- `platform` shared kernel incl. Swagger/OpenAPI
- **`school` module**: `SchoolEntity`/`SchoolFacade`, self-service creation
- **Session-based authentication**: `SecurityConfig`, `IamUserDetailsService`,
  `AuthController` (`/api/auth/login`, `/api/auth/logout`), `passwordHash` on
  `UserEntity`, one seeded demo admin (`admin@roshdeandishe.test` / `password`)
- **Admin REST layer**: `SchoolController`, `MembershipController`,
  `ClassController`, `StudentController`
- **Academic basics**: real `ClassEntity`/`StudentEntity`, `enrollStudent` wired to
  `StudentEnrolledEvent`
- **Finance audit trail**: `InvoiceIssuedEvent` → `FinanceAuditEntity` (append-only)
- **Finance basics (Phase D)**: real `InvoiceEntity` persistence + per-school
  `TuitionFeeEntity` pricing (see §4), `FinanceController` (tuition-fee PUT/GET,
  invoices GET) — the first controller carrying both §5 layers
  (`@RequiresModule("FINANCE")` + `@RequiresPermission`). Enrollment →
  `StudentEnrolledEvent` → invoice at the school's real fee, end-to-end tested in
  `FinanceFlowIntegrationTest`.
- **Account activation (backend + page + email)**: `InviteTokenEntity`/`InviteTokenRepository`,
  `IamFacade.issueInviteToken`/`redeemInviteToken`, `POST .../memberships/{id}/invite-token`,
  `POST /api/auth/activate` — see §4's `InviteToken` subsection — plus a real
  `GET /activate` HTML page (`AccountActivationPageController`, JTE + Alpine.js +
  Bulma) where an invited person pastes their token and sets a password, plus
  best-effort email delivery of the activation link (`iam/internal/InviteEmailSender`,
  `spring-boot-starter-mail`; covered end-to-end by GreenMail in
  `InviteTokenFlowIntegrationTest`). Dev SMTP default is localhost:1025
  (Mailpit/MailHog); a real production SMTP account/provider is still to be
  configured, but that's deployment config, not missing code.
- **Frontend stack chosen and started**: JTE (WebJar-delivered Alpine.js + Bulma,
  no npm/webpack build) replaces the previously-unused Thymeleaf dependency — see
  §2. Four pages exist: `GET /activate`, `GET /login`, `GET /register`,
  `GET /dashboard`.
- **Login page + read-only dashboard (first slice of §7 item 6)**: `GET /login`
  (posts to the existing `/api/auth/login`), `GET /dashboard` (server-rendered
  "my memberships"), `MeController` (`GET /api/me`, `GET /api/me/memberships`),
  `IamFacade.findMembershipsByUser`. Unauthenticated page requests redirect to
  `/login`; unauthenticated `/api/**` stays 403 (two explicit entry points in
  `SecurityConfig` — the first registered one is also the fallback). Covered by
  `LoginDashboardFlowIntegrationTest`. **Deliberately read-only** — see item 6
  below for what gates the mutating management UI.
- **CSRF enabled + school/role switcher (both former gates of item 6)**: CSRF is
  on (cookie-based `XSRF-TOKEN` / `X-XSRF-TOKEN` — see item 6 for the exact
  pattern), and the dashboard navbar now carries the school/role switcher
  (Open Question 2 resolved — see §11 Q2 for semantics). This also created the
  **`web` module** (§2 Frontend, §3): `DashboardPageController` moved there from
  `iam.shared` and now shows school *names* (composed from `SchoolFacade`)
  instead of raw schoolIds.
- **Public self-registration (resolves the sign-up gap of §11 Q7, built
  2026-07-05 on explicit request)**: `IamFacade.registerUser`,
  `POST /api/auth/register`, `GET /register` (JTE page, linked from `/login`;
  registers, auto-logs-in, lands on `/dashboard` with zero memberships — the
  case Q2's switcher already tolerates). Existing emails are always rejected
  (409), including invited-but-not-activated accounts — see §4's `registerUser`
  note for why. Covered by `RegistrationFlowIntegrationTest`.
- **Management UI, first slice — People (`/people`, built 2026-07-05, see
  [`UX_ROADMAP.md`](UX_ROADMAP.md) for the full page-by-page plan)**: lists a
  school's memberships, adds a member (email/fullName/roleCode), toggles
  active, issues/resends invite tokens — all client-side against the existing
  `iam.MembershipController` REST endpoints (`web.PeoplePageController` is a
  thin shell, same pattern as `/dashboard`). Required extracting the
  school/role switcher navbar out of `dashboard.jte` into a shared
  `layout/navbar.jte` template + `web.NavbarModel`/`NavbarModelAssembler`, and
  adding the `layout/main.jte` `scripts` param so pages can load page-specific
  static JS. `IamFacade.MembershipInfo`/`MembershipController.MembershipResponse`
  gained `email`/`fullName` (previously only `userId`) — the smallest change
  needed for a people list to show names, not just opaque user ids. Covered by
  `MembershipManagementFlowIntegrationTest`; `LoginDashboardFlowIntegrationTest`
  re-verified green after the navbar extraction.
- **Management UI, slice 2 — navigation + funnel fixes (built 2026-07-06, from
  the UX review in UX_ROADMAP.md §3 item 2)**: page links in the shared navbar
  (`navbar-start`; the People link's *visibility* follows the display-only
  selected role — first UI consumer of §11 Q2's role dropdown, enforcement
  stays server-side), proper Bulma `navbar-menu` + burger so the navbar works
  below 1024px, the navbar persists its *resolved default* school/role to
  localStorage on init (fixes "select a school" appearing for single-school
  admins who never clicked the dropdown), Persian role labels everywhere via
  `web.RoleLabels` (server) mirrored in `people.js` (client), and success
  notices on every `/people` mutation. No backend changes.
- **Management UI, slice 3 — public landing page + dedicated school
  registration (built 2026-07-06, explicit product decision)**: real people
  don't casually "create a school" from their own dashboard — the create-school
  form used to sit there for *every* logged-in user, including invited
  students/parents with zero reason to see it. Moved to its own page,
  `GET /schools/new` (`web.SchoolRegistrationPageController`, thin shell +
  shared navbar, same pattern as `/people`), with the `createSchoolForm`
  Alpine component relocated verbatim from `dashboard.js` to
  `static/js/school-new.js` (`dashboard.js` deleted). Also added `GET /`
  (`web.LandingPageController`) as the public entry point for anonymous
  visitors — login/register CTAs plus a "ثبت مدرسه" link to `/schools/new`;
  an already-authenticated visitor is redirected straight to `/dashboard`.
  `/` is `permitAll` in `SecurityConfig` and excluded in
  `TenantResolverFilter` (checked by `path.equals("/")`, not `startsWith`,
  since every path starts with `/`); `/schools/new` is excluded in the filter
  the same way `/people` is (the school doesn't exist yet to carry a
  schoolId) but still requires login via `SecurityConfig`. The dashboard's
  empty-membership state now links to `/schools/new` instead of hosting the
  form inline. Self-service creation itself is unchanged (CLAUDE.md §1) —
  this is presentation only, not a new gate. **This is a deliberate first
  step toward §11 Q6** (should self-created schools be distinguishable from
  verified customers?) — a real approval flow is still blocked on
  `PLATFORM_ADMIN` permission infrastructure (§7 item 1) and stays
  unbuilt until that exists; do not add a `PENDING`/`VERIFIED` status
  speculatively ahead of it. Covered by new tests in
  `LoginDashboardFlowIntegrationTest` (landing page content, authenticated
  redirect, unauthenticated `/schools/new` redirect) and an updated
  `RegistrationFlowIntegrationTest` (founder flow now visits `/schools/new`
  instead of asserting the form on `/dashboard`).
- **Management UI, slice 4 — Classes (`/classes`, built 2026-07-06, see
  [`UX_ROADMAP.md`](UX_ROADMAP.md) §3 item 3)**: create/list a school's
  classes (name, optional section, optional teacher), entirely client-side
  against the existing `academic.ClassController` REST endpoints
  (`web.ClassesPageController` is a thin shell, same pattern as `/people`).
  Teacher names are resolved client-side in `classes.js` from
  `iam.MembershipController`'s membership list (filtered to active `TEACHER`
  rows) — `academic` has no visibility into `iam` names by design (`teacherId`
  is an opaque `Long`, no FK across the module boundary), so this composition
  happens in JavaScript, not by adding an `IamFacade`/`AcademicFacade`
  cross-dependency in the Java layer. Navbar link added, initially
  `SCHOOL_ADMIN`-only (same pattern as `/people`), then widened 2026-07-07
  (commit `f31a8c0`) to also show for `TEACHER` once the `CLASS_VIEW` split
  below let them load the page usefully — read-only in practice, since
  `CLASS_MANAGE` (create) stays `SCHOOL_ADMIN`-only and the create form falls
  back to the same generic permission-denied notification every other page
  shows, with no client-side role-gating of the form itself.
  `TenantResolverFilter` excludes
  `/classes` the page route for the same reason it excludes `/people`.
  Covered by `ClassManagementFlowIntegrationTest`.
- **Management UI, slice 5 — Enrollment (`/students`, built 2026-07-07, see
  [`UX_ROADMAP.md`](UX_ROADMAP.md) §3 item 4)**: enroll a student (email,
  fullName, class picker) into one of the school's classes and list who's
  enrolled where, entirely client-side against `academic.StudentController`
  (`web.StudentsPageController` is a thin shell, same pattern as `/classes`).
  `StudentController` was previously enroll-only + get-by-id with no roster
  listing — added `GET /api/admin/schools/{schoolId}/students`
  (`STUDENT_ENROLL`, same permission as enroll — SCHOOL_ADMIN + TEACHER) backed
  by a new `AcademicFacade.listStudents(schoolId)` /
  `StudentRepository.findBySchoolId` (§4). Student name/email are resolved
  client-side in `students.js` from `iam.MembershipController`'s membership
  list (filtered to active `STUDENT` rows) — the same composition pattern
  `classes.js` uses for teacher names, for the same module-boundary reason
  (`academic` holds `userId` as an opaque `Long`, no FK into `iam`). Class
  names are resolved the same way from the existing `academic.ClassController`
  list. Navbar link added, visible while the selected role is `SCHOOL_ADMIN`
  *or* `TEACHER` — the first navbar link shown to a non-admin role, since
  `STUDENT_ENROLL` (unlike `CLASS_MANAGE`) is granted to both.
  `TenantResolverFilter` excludes `/students` the page route for the same
  reason it excludes `/people` and `/classes`. Covered by
  `StudentManagementFlowIntegrationTest`.
- **Management UI, slice 6 — Finance (`/finance`, built 2026-07-07, see
  [`UX_ROADMAP.md`](UX_ROADMAP.md) §3 item 5 — last SCHOOL_ADMIN slice)**:
  set/view the school's tuition fee and list its invoices, entirely
  client-side against the existing `finance.FinanceController`
  (`web.FinancePageController` is a thin shell, same pattern as `/students`).
  No backend changes — `FinanceController` already had `PUT`/`GET
  tuition-fee` and `GET invoices`. This is the first page that must render
  the two §5 access-control layers' 403s distinctly:
  `finance.js.classifyError()` reads the `ProblemDetail.type` URI's suffix
  (`module-not-entitled` vs `permission-denied`, set by
  `IamProblemResolver`/`ProblemDetailFactory`) and shows two different
  Persian messages, blocking the page body in both cases instead of
  rendering a broken form. Invoice rows carry only an opaque `studentId`
  (finance has no visibility into student names); `finance.js` resolves
  names the same way `students.js` resolves teacher/student names —
  composing `academic.StudentController` (id → userId) with
  `iam.MembershipController` (userId → name) client-side, both best-effort
  (falls back to `#id` if the current role lacks `STUDENT_ENROLL`, e.g.
  `FINANCE_ADMIN`). Navbar link visible while the selected role is
  `SCHOOL_ADMIN` or `FINANCE_ADMIN`. `TenantResolverFilter` excludes
  `/finance` the page route for the same reason it excludes `/people`,
  `/classes`, and `/students`. Covered by
  `FinanceManagementFlowIntegrationTest`, including both 403 flavors (a
  TEACHER member of the entitled demo school for `PermissionDenied`; a
  freshly self-service-created school with zero entitlements for
  `ModuleNotEntitled`). **This closes out the SCHOOL_ADMIN build order in
  UX_ROADMAP.md §3** — remaining scheduled work is the FINANCE_ADMIN/TEACHER
  personas reusing pages already built, and items explicitly deferred until
  asked (grades UI, custom roles, B2C module UI).
- **Bug fix — `CLASS_VIEW` permission split (2026-07-07)**: found while
  scoping the TEACHER persona's next step — `ClassController.listClasses`
  required `CLASS_MANAGE`, which `TEACHER` never had, so the class picker on
  `/students` silently rendered empty for every teacher (they could never
  actually enroll anyone). Fixed by adding `CLASS_VIEW` (granted to
  `SCHOOL_ADMIN` + `TEACHER`) for the `GET` endpoint, leaving `CLASS_MANAGE`
  (create) `SCHOOL_ADMIN`-only — see §4's `CLASS_VIEW` note. Covered by a new
  `ClassManagementFlowIntegrationTest.teacherCanListClassesButCannotCreateOne`.
  Follow-up commit `f31a8c0` widened the `/classes` navbar link itself to
  `TEACHER` (previously `SCHOOL_ADMIN`-only, see the Classes slice above) —
  the permission fix was pointless if TEACHER still couldn't discover the page.
- **TEACHER persona — read-only class roster view (built 2026-07-07, on
  `/classes`)**: `UX_ROADMAP.md`'s TEACHER section had flagged this as
  "plausible but not scheduled"; built on request. Adds a "مشاهده
  دانش‌آموزان" toggle per class row (`classes.js`/`classes.jte`) — labeled in
  plain Persian, not a transliteration of "roster" ("روستر"), which isn't a
  word Persian speakers recognize — that opens a dismissible panel (close
  button + `Escape`) listing that class's enrolled students —
  purely additive to the existing Classes slice, **zero backend changes**:
  it reuses `academic.StudentController`'s existing `GET .../students`
  (`STUDENT_ENROLL`, already granted to `SCHOOL_ADMIN` + `TEACHER`, the same
  endpoint `/students` already calls), filtered client-side by `classId`.
  Deliberately read-only — no enroll/remove controls on this view; enrolling
  still only happens on `/students`. **Found and fixed a pre-existing bug
  while building this**: `students.js`'s student-name resolution
  (`iam.MembershipController`'s list, gated by `MEMBERSHIP_MANAGE`, which
  `TEACHER` does not have) silently shows "نامشخص" per row for TEACHER on
  `/students` — a naive port of that same lookup into the roster view instead
  *dropped* unresolvable rows entirely, making a class with real enrollments
  look empty to a TEACHER. Fixed in `classes.js`'s `rosterFor()` by falling
  back to a `#userId` placeholder instead of filtering the row out (same
  fallback style `finance.js` already uses for invoice student names) — the
  roster count is now always correct even when names can't be resolved. The
  underlying `MEMBERSHIP_MANAGE`-only gate on `/students`' own name
  resolution is unchanged and not addressed here (a real fix would mean
  either widening `MEMBERSHIP_MANAGE` or adding a narrower read endpoint —
  a permission-model decision, out of scope for this slice). Verified
  end-to-end in a real browser (Playwright) as SCHOOL_ADMIN and as TEACHER,
  covering populated/empty classes and the close/`Escape` dismiss — no new
  Java integration test added since no controller/facade changed and the
  existing `ClassManagementFlowIntegrationTest` already covers the page
  shell.
- Dev-only seed data: roles, modules, permissions, role-permission grants, one demo
  school, one demo admin user with a SCHOOL_ADMIN membership at the demo school
  (without it the demo admin couldn't call any school-scoped API — the demo school
  is seeded via repository, bypassing the self-service flow that auto-creates the
  creator's membership), and ACADEMIC+FINANCE entitlements for the demo school
  (so the first `@RequiresModule`-guarded controller works out of the box in dev)
- `ModularityTests` enforcing module boundaries
- **Test infrastructure:** all `*FlowIntegrationTest` classes run against a real
  Postgres via `TestcontainersConfiguration` (`testcontainers-postgresql` +
  `spring-boot-testcontainers`), not an in-memory database — matching production's
  DB engine matters more here given the per-module Postgres *schema* convention
  (§7 item 4). `awaitility` is used where a test needs to wait on an
  `@ApplicationModuleListener` (async, runs on its own thread post-commit — see
  the thread-boundary rule above) rather than asserting immediately.
  `Demo5ApplicationTests` is the unmodified Spring Boot smoke test left over from
  the original project scaffold (stale "Demo5" name predates the
  `roshdeandishe` rename) — harmless, not worth touching until something else
  in that file changes.
- **Bale bot integration, Phase 1 (built 2026-07-07, explicit product decision
  — grades UI deferred first, see §11 Q8)**: a new `bale` module (§3, §4's
  BaleLink note) — outbound notifications (`InvoiceIssuedEvent` → notify the
  student + every linked guardian) and inbound self-service queries via a
  fixed set of Persian text commands ("مدرسه‌های من", "فرزندان من") over a
  Bale (بله) messenger webhook. Required two supporting pieces built first:
  `iam.UserEntity.phoneNumber` (§4 User) as the identity-linking key, and
  `academic.StudentGuardianEntity`/`AcademicFacade.linkGuardian`/`listWards`
  (§4's StudentGuardian note) — the first real Parent↔Student relationship
  this codebase has ever had; previously `PARENT` was a role with permissions
  but no modeled link to *which* child was theirs, discovered while scoping
  this exact feature. New `GUARDIAN_MANAGE` permission (`SCHOOL_ADMIN`-only,
  §4's Permission section) gates linking a guardian — deliberately its own
  permission, not a reuse of `MEMBERSHIP_MANAGE`, and deliberately not granted
  to `TEACHER` (unlike `STUDENT_ENROLL`). Verified end-to-end manually against
  a real local Postgres (no Docker/Testcontainers available in this session):
  admin links a `PARENT` to a student (rejected 409 for a non-`PARENT` user,
  idempotent on retry), the parent claims their own phone number via
  `PUT /api/me/phone` while authenticated, then simulated Bale webhook
  requests (`/start` → contact-request prompt → contact share → link
  confirmation → "فرزندان من" → correct child list) and a real
  `InvoiceIssuedEvent` (via tuition-fee + re-enrollment) confirmed the linked
  guardian's chat received the expected outbound notification (logged, since
  no real `roshdeandishe.bale.bot-token` exists in this environment — see
  §4's BaleLink note on why that's deployment config, not missing code). No
  new Java integration test added (no Docker available to run
  `TestcontainersConfiguration`-based tests in this session); this is a real
  gap — a `BaleFlowIntegrationTest` following the existing
  `*FlowIntegrationTest` pattern should be added when the suite can next be
  run against Testcontainers. **Explicitly out of scope for Phase 1**:
  scoping `/finance`'s existing `GET invoices` endpoint to a guardian's own
  children (unchanged, pre-existing behavior — §4's StudentGuardian note),
  admin-management UI for guardian links (curl/API only for now), and any
  grades-related bot command (grades UI itself remains deferred, §11 Q8).
- **Mobile auth backend + read-only enrollment endpoint (built 2026-07-08,
  first backend step of the native Android student app — supersedes
  `MOBILE_ROADMAP.md`'s original Capacitor framing, see that doc's own
  reconciliation note)**: `POST /api/auth/token` (JWT bearer issuance,
  `iam.shared.JwtTokenProvider` + `JwtAuthenticationFilter`) additive to the
  existing session-cookie web login — one `SecurityFilterChain`, not two;
  the new filter no-ops without an `Authorization: Bearer` header, so web
  login is provably unaffected (covered by
  `MobileAuthFlowIntegrationTest.bearerTokenAuthenticatesProtectedMeEndpointWithNoSession`
  plus re-verifying `LoginDashboardFlowIntegrationTest` stays green). Also
  fixed, since `/api/auth/token` would otherwise have inherited it: a
  pre-existing gap where `/api/auth/login` 500'd on bad credentials instead
  of 401 (`IamError.InvalidCredentials`, new sealed case, applied to both
  endpoints for consistency). New self-service `GET /api/me/enrollment`
  (`academic.MyEnrollmentController`, `AcademicFacade.findMyEnrollment`) —
  "what class am I in at this school," the first real data a student's app
  can show, since `STUDENT` otherwise only holds the still-deferred
  `GRADES_VIEW` permission (§11 Q8) and no timetable/announcements backend
  exists yet. Covered by `MyEnrollmentFlowIntegrationTest`. Token lifetime is
  12h (`roshdeandishe.jwt.expiration-seconds`), no refresh-token flow —
  logout is client-side token deletion only; deliberately deferred until a
  concrete need for silent long-lived sessions shows up, not built
  speculatively. **The native Android app itself (Kotlin + Jetpack Compose,
  Android-only for v1) is not built yet** — this is backend enablement only;
  the client is the next phase. Also fixed in passing: `init-schemas.sql`
  (used by `TestcontainersConfiguration`) was missing `CREATE SCHEMA bale`,
  causing `bale.internal.InvoiceIssuedEventListener` to fail (harmlessly,
  async, no test ever failed because of it) on every test run since the
  `bale` module was added — a one-line pre-existing gap, unrelated to this
  work, fixed alongside it since the full suite was being run anyway.
- **Finance deepening, phase 1 — payments (built 2026-07-12)**: manual payment
  recording against invoices (`FinanceFacade.recordPayment`/`listPayments`),
  partial/installment payments with overpayment rejected outright (no credit
  balances), a derived (never persisted) `InvoiceStatus` (`PAID`/`PARTIAL`/
  `UNPAID`/`OVERDUE`) computed from `amount`/`amountPaid`/`dueDate`, invoice due
  dates (`roshdeandishe.finance.default-due-days`, global config, default 14),
  admin-triggered idempotent invoice backfill (`FinanceFacade.backfillInvoices`,
  priced at the current fee), and a guardian/student self-view
  (`GET /api/me/schools/{schoolId}/invoices`, `MyInvoicesController`) —
  see §4's `Invoice` subsection for the full model. `/finance` gained a status
  tag, an expandable payment-history/record-payment panel per invoice, and a
  backfill button — no new page. Covered by `FinancePaymentFlowIntegrationTest`
  (partial→PARTIAL, full→PAID, overpayment 409, overdue derivation, backfill
  idempotency, guardian/student/stranger scoping on the new `/api/me/...`
  endpoint, audit row via awaitility).
- **Finance deepening, phase 2 — expense management (built 2026-07-12, from a
  user-supplied Persian design document)**: schools can now record, categorize,
  and report their *outgoing* money (salaries, utilities, equipment, ...), not
  just incoming tuition — see §4's `Expense`/`ExpenseCategory`/`Vendor`/
  `ExpenseAttachment` subsection for the full model, lifecycle, and report
  semantics. A genuine extension of the `finance` module (new `ExpenseFacade`
  alongside the existing `FinanceFacade`, both public at the module root), not
  a new module — the `new-module` skill's trigger does not apply. New
  `ExpenseController` (`/api/admin/schools/{schoolId}/expense-categories`,
  `/vendors`, `/expenses`, including multipart attachment upload/download and
  two report endpoints), new `EXPENSE_VIEW`/`EXPENSE_MANAGE`/`EXPENSE_APPROVE`
  permissions (§4), 8 new `FinanceError` cases past the point where the sealed
  switch needed extracting into `FinanceProblemResolver` (§6 pattern, mirrors
  `IamProblemResolver`). **First file-upload feature in this codebase**
  (`ExpenseAttachmentEntity`/`ExpenseAttachmentContentEntity`, plain `byte[]`
  not `@Lob` — see §4 for the Postgres `bytea` vs. `oid` reasoning — 5MB domain
  cap, multipart servlet limits in `application.yml`). New `/expenses` page
  (`web.ExpensesPageController`, thin shell like `/finance`) plus the first
  Jalali-calendar handling anywhere in this project (`static/js/jalali.js`, a
  small dependency-free conversion utility — no Jalali library existed before;
  used only by the report-period picker, the server stays Gregorian-only). Also
  fixed in passing: the `IamSeedDataRunner` permission-seeding gap described in
  §4's `EXPENSE_*` note, found while adding these three codes. Explicitly out
  of scope per the design doc and CLAUDE.md §10: OCR/AI receipt scanning
  (rejected permanently, not just deferred), budgets/spend ceilings,
  multi-step approval chains, installment expense payments, accounting/
  inventory-system integration, BI dashboards. Covered by
  `ExpenseManagementFlowIntegrationTest` (full DRAFT→APPROVED→PAID lifecycle,
  invalid transitions rejected, `FINANCE_ADMIN` can manage but not approve —
  segregation of duties, `TEACHER`/fresh-school 403 flavors, seed-defaults
  idempotency, audit rows), `ExpenseAttachmentFlowIntegrationTest` (Persian-filename
  upload/download round-trip, oversize rejection, upload-to-`CANCELLED`
  rejection), and `ExpenseReportFlowIntegrationTest` (DRAFT/CANCELLED/
  out-of-range exclusion, per-category/vendor/method sums, null-vendor
  grouping, income-vs-expense). Manually verified end-to-end in a real browser
  (Playwright): full category/vendor/expense/approve/pay/attachment/report
  flow against the dev seed data, screenshots confirmed correct rendering and
  Jalali period conversion. **Found in passing, then fixed on request:** every
  missing static resource under `/css/**`, `/js/**`, `/webjars/**` (most
  visibly a WebJar `.map` sourcemap request, e.g.
  `/webjars/alpinejs/.../cdn.min.js.map` — browsers/DevTools request these
  automatically for any page that loads Alpine/Bulma, so this reproduced on
  every page, not just `/expenses`) was returning HTTP 500 instead of 404.
  Root cause: `platform.PlatformExceptionHandler`'s fallback
  `@ExceptionHandler(Exception.class)` caught Spring's
  `NoResourceFoundException` (thrown by `ResourceHttpRequestHandler` for any
  unmatched static path) before Spring's own correct 404 handling could run —
  the catch-all doesn't discriminate exceptions that already know their real
  HTTP status from genuinely-unexpected ones. Fixed by adding a specific
  `@ExceptionHandler(NoResourceFoundException.class)` returning a proper 404
  ProblemDetail (`type` suffix `resource-not-found`) ahead of the generic
  handler — Spring dispatches to the more specific method automatically, no
  ordering annotation needed within the same `@RestControllerAdvice`. Covered
  by `PlatformExceptionHandlerFlowIntegrationTest` (missing static file → 404,
  missing WebJar sourcemap → 404, existing static file still 200 — this last
  one guards against the fix accidentally widening to swallow real resources).
  Pre-existing bug, unrelated to the finance/expense work above other than
  being noticed while manually verifying `/expenses` in a browser.
- **`iam` phone/OTP login extension (built 2026-07-12, `MOSHAVERE_PORT_SPEC.md`
  §3, step 1 of the moshavere port, build order §8 of that doc)**: a second
  login factor for a user who already has a phone number on file — see §4's new
  `VerificationCode` subsection for the full model. `IamFacade.requestOtp`/
  `verifyOtp`, `POST /api/auth/otp/request`/`/verify`
  (`iam.shared.AuthController`), new `IamError` cases `PhoneNotRegistered`/
  `OtpIncorrect`/`OtpExpired`, `iam.internal.SmsDriver` (best-effort,
  logs-only until `roshdeandishe.sms.api-key` is configured — same convention
  as `BaleClient`/`InviteEmailSender`, added as a concrete class rather than
  the interface the port spec sketched, since no second implementation exists
  yet — §10). **Does not weaken the existing phone-number security model**:
  `PUT /api/me/phone` remains the only way a phone number gets onto an
  account (requires an authenticated session), so `requestOtp`/`verifyOtp`
  can never create a user or a new unauthenticated registration path — only
  authenticate an existing one, exactly the constraint
  `MOSHAVERE_PORT_SPEC.md` §2 called out as already-decided. Covered by
  `OtpLoginFlowIntegrationTest` (happy path establishes a session; both 404
  `PhoneNotRegistered` paths — request and verify; `OtpIncorrect` does not
  consume the code, so a correct retry still succeeds; `OtpExpired` for both
  "no code ever requested" and "code aged past its TTL"). Full suite
  (90 tests) and `ModularityTests` re-verified green after this change.
  **Not built, per the spec's own explicit flag**: rate-limiting repeated OTP
  requests (moshavere had none either — a conscious skip, not an oversight).
  Remaining build order from the spec: `curriculum` → `studyplan` → `exam`
  (each a genuinely new module, each requiring its own `new-module` pass).
- **Finance deepening, phase 3 — parent/student self-view page + admin
  invoices scoping (built 2026-07-13, both on explicit user request)**:
  - **`GET /my-invoices`** (`web.MyInvoicesPageController`, JTE
    `my-invoices.jte`, `static/js/my-invoices.js`) — the page UX_ROADMAP.md's
    PARENT/STUDENT section had already flagged as "a natural next slice, not
    built speculatively ahead of being asked." A thin shell like every other
    management page, reading the pre-existing (2026-07-12)
    `GET /api/me/schools/{schoolId}/invoices` (`finance.MyInvoicesController`)
    — no new REST, no new Java module dependency. Read-only: no payment
    recording (that stays admin/finance-admin-only on `/finance`). Navbar link
    visible while the selected role is `STUDENT` or `PARENT` — the first
    navbar link for either role. `TenantResolverFilter` excludes `/my-invoices`
    the page route for the same reason it excludes `/finance` — the REST call
    underneath is itself already excluded as part of the `/api/me` prefix.
    Covered by `MyInvoicesPageFlowIntegrationTest` (page shell only — the REST
    scoping itself was already covered end-to-end by
    `FinancePaymentFlowIntegrationTest.myInvoicesReturnsOwnAndWardsInvoicesOnly`).
  - **Admin `GET .../invoices` scoped to a guardian's own children** — the
    decision CLAUDE.md §10 had explicitly withheld pending a real ask
    (`FinanceController.listInvoices` previously returned every invoice in
    the school to any `FINANCE_VIEW` holder, including `PARENT`). Now checks
    the caller's active roles at the school
    (`IamFacade.findMembershipsByUser`): if `SCHOOL_ADMIN` or `FINANCE_ADMIN`
    is among them, behavior is unchanged (full school-wide
    `FinanceFacade.listInvoices`); otherwise (a `PARENT`-only caller — the
    only other role holding `FINANCE_VIEW`, §4's Permission table) the
    response is scoped to `FinanceFacade.listInvoicesForStudents(schoolId,
    AcademicFacade.listWards(userId, schoolId))` — the same facade method
    `MyInvoicesController` already used. `FinanceController` gained `IamFacade`
    + `AcademicFacade` as constructor dependencies (both already legal —
    `MyInvoicesController` in the same module already depended on both).
    `/finance` and `/expenses` needed no JS changes: `finance.js` already
    renders whatever the endpoint returns and already best-effort-resolves
    student names. Covered by
    `FinancePaymentFlowIntegrationTest.adminInvoicesEndpointScopesParentToOwnWardsButAdminKeepsFullVisibility`
    (admin sees a stranger-family invoice the parent doesn't; parent sees
    exactly their linked child's invoice and nothing else; a `STUDENT` caller
    still gets a plain 403 since `STUDENT` never held `FINANCE_VIEW`).
    Manually verified end-to-end in a real browser (Playwright): admin
    enrolls two unrelated students and links a parent to only one, parent's
    `/finance` and `/my-invoices` both show exactly one invoice, admin's
    `/finance` shows both.
- **Custom role management (built 2026-07-13, on explicit user request,
  resolving §11 Q3)**: a `SCHOOL_ADMIN` can now define their own role (e.g.
  "کتابدار"/Librarian) as a named subset of the existing fixed permission
  catalog — see §4's Role subsection ("Custom roles") for the full model and
  the three explicit decisions behind it (per-school scoping, existing
  permissions only, system roles stay immutable through this path).
  `RoleEntity` gained a nullable `schoolId` and its unique constraint moved
  from single-column `code` to composite `(code, school_id)` (§3). New
  `iam.RoleController` (`GET .../permissions`, `GET`/`POST .../roles`, all
  `ROLE_MANAGE`-gated, `SCHOOL_ADMIN`-only) and `IamFacade.listPermissions` /
  `listRoles` / `createCustomRole`. `IamFacade.createMembership`'s role
  lookup now resolves a `roleCode` against the calling school's own custom
  roles first, then system roles — a role created for one school can never
  be assigned at another (`RoleNotFound` otherwise). Three new sealed
  `IamError` cases (`InvalidRoleDefinition`, `InvalidPermissionCode`,
  `RoleCodeAlreadyExists`) plus their `IamProblemResolver`/`IamException`
  switch arms. New `GET /roles` management page (`web.RolesPageController`,
  §3) — lists every role visible to the school with its permission set, plus
  a create-role form built from a permission checklist; navbar link
  `SCHOOL_ADMIN`-only. `/people`'s role-assignment dropdown (`people.js`)
  now also fetches `GET .../roles` and merges custom roles in alongside the
  five hardcoded system-role options — without this, a freshly-created
  custom role would be visible but never actually assignable to anyone.
  `PermissionDecisionService` needed **zero changes**: it was already fully
  data-driven off `role_permissions` with no role-code awareness, so a
  custom role started being enforced correctly the moment its
  `RolePermissionEntity` rows existed — this is the same generic mechanism
  every system role already runs through (§5). Covered by
  `RoleManagementFlowIntegrationTest` (system roles/permissions listed
  correctly with `PLATFORM_ADMIN` filtered out, non-`ROLE_MANAGE` caller
  gets 403, code collision against a system role and against the same
  school's own custom role both 409, invalid code format/blank
  fields/empty permission list/unknown permission code all 400, a custom
  role assigned via `POST .../memberships` grants *exactly* its selected
  permission — proven by a real endpoint call succeeding — and *nothing*
  else — proven by an unrelated endpoint call 403'ing, and a second
  self-service school can neither see nor assign the first school's custom
  role). Manually verified end-to-end in a real browser: created a
  "کتابدار" role scoped to `CLASS_VIEW` only on `/roles`, confirmed it
  appeared with the right permission tag, then assigned it to a new member
  on `/people` and confirmed the member row rendered the Persian
  `displayName` ("کتابدار"), not the raw code.
- **Moshavere port, step 2 — `curriculum` module (built 2026-07-16, via its
  own `new-module` pass per the standing instruction)**: hierarchical topic
  taxonomy + multiple-choice question bank — see §3's module tree entry and
  §4's Topic/Question subsection for the full model. Resolved the spec's two
  open items explicitly: a new `SCHOOL`-scoped `CURRICULUM` module code
  (none of the seeded codes fit; `EXAM_PREP` stays the B2C placeholder it
  was, its scope question deferred to the `exam` module's own pass), and
  the permission split — `CURRICULUM_MANAGE`/`CURRICULUM_VIEW`, both
  SCHOOL_ADMIN + TEACHER, neither to STUDENT/PARENT (the bank carries
  correct answers; see §4's Permission section). Two deliberate deviations
  from the spec's field list, both toward the actual moshavere source: the
  `answer` explanation field (in the source, missing from the spec) and
  difficulty as a 5-level enum (port of the source's 0/10/20/30/40 int
  scale). The subtree semantics of the question topic-filter (a topic
  matches its whole descendant tree) were preserved exactly. **Fixed in
  passing**: `IamSeedDataRunner.seedModules()` and
  `seedDemoSchoolEntitlements()` still had the early-return-on-count trap
  documented (and fixed) for `seedPermissions()` back when `EXPENSE_*`
  landed — a new module code / demo entitlement would silently never reach
  an already-seeded dev database while tests stayed green; both are now
  per-code idempotent, and the demo school gets a `CURRICULUM` entitlement.
  Also added the missing `bale` + new `curriculum` schemas to README's
  local-setup SQL (`bale` was a pre-existing doc gap; `init-schemas.sql`
  already had it). Covered by `CurriculumFlowIntegrationTest` (topic tree
  build + nested read, subtree question filter includes descendants and
  excludes unrelated roots, difficulty filter, invalid
  correctChoice/blank-choice/blank-name 400s, nonexistent topic 404,
  TEACHER can author, PARENT gets `permission-denied`, fresh
  entitlement-less school gets `module-not-entitled`, missing X-School-ID
  400). Full suite + `ModularityTests` green. **No UI** — the
  port is backend domain logic only (spec §7); a `/curriculum` management
  page is a separate slice gated by the `design-page` skill. Remaining
  port steps: `studyplan` → `exam`, each needing its own `new-module` pass.
  **Merge note (same day):** two sessions built this module in parallel; a
  second implementation (PR #37 — `Integer` difficulty, no `answer` field,
  exact-match topic filter, `CurriculumManagementFlowIntegrationTest`)
  briefly lived on master and was superseded by this one at merge-conflict
  time as the more faithful port (subtree filter semantics, `answer`
  ported, enum difficulty). Its one extra test case (unknown parent on
  topic create → 404) was folded into `CurriculumFlowIntegrationTest`, and
  its `curriculum/package-info.java` (`@ApplicationModule(displayName)`)
  was kept.
- **Moshavere port, step 3 — `studyplan` module (built 2026-07-16,
  `MOSHAVERE_PORT_SPEC.md` §6, continuing the port immediately after
  `curriculum`)**: per-school study plans with course weighting, weekly
  topic schedules, student↔plan assignment, a student's weekly free-time
  template, actual scheduled study blocks, and a faithful logic port of
  moshavere's `table_suggester.py` auto-scheduling algorithm — see §4's
  Plan/WeekCourse/StudentPlanAssignment/StudyInterval/IntervalSchedule
  subsection for the full model and §3 for the module layout. Decisions
  made at new-module time: new `SCHOOL`-scoped `STUDY_PLANNER` module code
  (spec §6's own suggestion — no existing seed code fits);
  `STUDYPLAN_VIEW`/`STUDYPLAN_MANAGE` both granted to `SCHOOL_ADMIN` +
  `TEACHER` (moshavere's plan author is the `Instructor` — same reasoning
  as `CURRICULUM_VIEW`/`CURRICULUM_MANAGE`), deliberately not to
  `STUDENT`/`PARENT` (a student self-view is a plausible later
  `/api/me`-style slice, not built here); course weighting became a real
  child table (`plan_course_weights`), not the JSON column the draft spec
  sketched — same join-table-over-array preference already applied to
  `WeekCourseTopicEntity`/`RolePermissionEntity`; the two schedule columns
  (`default_schedule`/`current_schedule`) stayed `jsonb` per the spec,
  since that structure is a genuinely nested day→interval shape, not a
  flat list. `ObjectMapper` is a private `new ObjectMapper()`, not an
  injected Spring bean — same documented reason as
  `bale.internal.BaleWebhookService`: `spring-modulith-events-jackson`
  brings a second Jackson (`tools.jackson`) onto the classpath, so
  auto-configuration no longer produces one unambiguous
  `com.fasterxml.jackson.databind.ObjectMapper` bean (discovered when the
  full `@SpringBootTest` context failed to wire `StudyPlanFacadeImpl`
  during this build, not assumed in advance). The algorithm port
  (`internal.SliceScheduleInterval`/`internal.FillSlices`/
  `internal.Suggestor`) preserves moshavere's behavior exactly, including
  its round-half-to-even interval-count rounding, with two deliberate
  normalizations of accidental implementation details (not behavior):
  the block-size counter is per-call state instead of a module-level
  mutable dict (moshavere's version leaked counts across unrelated calls
  — a bug), and weekdays are walked Saturday→Friday (moshavere's own
  Friday-first order was a Postgres jsonb key-sort artifact, not a
  decision). `init-schemas.sql` gained `CREATE SCHEMA IF NOT EXISTS
  studyplan;` (§7 item 4) and `IamSeedDataRunner` grants/seeds the new
  module and permissions using the same per-code-idempotent helpers the
  `CURRICULUM_*`/`EXPENSE_*` fix established — no early-return-on-count
  regression reintroduced. **Management UI: `GET /study-plans`
  (`web.StudyPlansPageController`, built 2026-07-17 via the `design-page`
  skill)** — a thin shell like `/curriculum`, entirely client-side against
  `PlanController`/`IntervalScheduleController`/`StudyIntervalController`.
  Deliberately scoped at the time to the plan→assignment→schedule→interval
  path only: `WeekCourseController` (a plan's weekly topic template) and the
  multi-student `copyWeek` bulk action were **not** built in that first
  pass — neither is load-bearing for anything else in the UI or for `exam`'s
  `getRecentTopics` (which reads actual `StudyIntervalEntity` rows, not
  `WeekCourse`), so they were lower priority than the core scheduling loop.
  **Both were added 2026-07-17** (§7 Done, "Study-plans page deepening") —
  see that entry for the UI shape. The `suggest`/`clear` week actions need an
  exact Saturday `weekStart`, so the page lets the user pick *any* day and
  derives the week's Saturday client-side
  (`mostRecentSaturday`/`formatLocalDate` in `study-plans.js`) — deliberately
  built on local Y/M/D string formatting, not `Date.toISOString()`, which
  shifts to UTC and is off-by-one near local midnight in any non-UTC
  timezone (caught during manual verification: the Saturday-required
  validation rejected a date computed via `toISOString()`). Behind
  `@RequiresModule("STUDY_PLANNER")`, reuses the `classifyError` two-403
  pattern verbatim. Navbar link visible for `SCHOOL_ADMIN`/`TEACHER` (both
  hold `STUDYPLAN_MANAGE`/`STUDYPLAN_VIEW` identically).
  Covered by `StudyPlanManagementFlowIntegrationTest` (plan creation with
  course weights + unknown-topic 404, student assignment + listing,
  default-schedule set, full suggest→week-not-empty→clear→suggest-again
  cycle, unassigned-student `plan-not-assigned` 409, non-Saturday
  `invalid-week-start` 400, TEACHER can author plans, STUDENT gets
  `permission-denied`, fresh self-service school gets
  `module-not-entitled` — both 403 flavors — missing `X-School-ID` 400).
  Full suite (113 tests) + `ModularityTests` green.
- **Moshavere port, step 4 and last — `exam` module (built 2026-07-17)**:
  the exam engine — auto-generated weekly exams (from a student's recent
  study-plan topics), instructor-authored collective exams, timed
  token-scoped exam-taking sessions, and on-demand scoring — see §3, §4's
  Exam/Section/SectionQuestion/ExamAssignment/Attempt/Submit subsection, and
  `MOSHAVERE_PORT_SPEC.md` §5 for the full model and every deliberate
  deviation from the source. New `SCHOOL`-scoped `EXAM` module code (not a
  reuse of the pre-existing `EXAM_PREP`, which stays the untouched
  `USER`-scoped B2C placeholder — the actual moshavere feature is
  instructor-authored, per-school content, a B2B shape) +
  `EXAM_VIEW`/`EXAM_MANAGE` permissions (`SCHOOL_ADMIN` + `TEACHER`, same
  reasoning as `CURRICULUM_*`/`STUDYPLAN_*`). The deepest dependency chain
  in this port (`curriculum` + `academic` + `studyplan` + `iam` — the one
  place a new module depends on another new module,
  `StudyPlanFacade.getRecentTopics` for weekly auto-generation) and the
  first module in this codebase with a *third* independently-secreted JWT
  (`roshdeandishe.exam.token-secret`, `exam.internal.ExamTokenService`) —
  scoped per `Attempt`, distinct from both the session cookie and the
  mobile bearer JWT, powering `ExamTakeController`
  (`/api/exam-take/**`, `permitAll` + CSRF-ignored in `SecurityConfig`,
  excluded from `TenantResolverFilter` — same treatment as
  `BaleWebhookController`, its own permission mechanism keyed off the exam
  token rather than the normal two-layer check). `MyExamsController` is the
  student-facing "my" pattern (list own exams, start an attempt, view own
  result — no `@RequiresPermission`/`@RequiresModule`, `studentId` resolved
  via `AcademicFacade.findMyEnrollment`). Three deliberate, documented
  deviations from the source: `startAttempt` is idempotent while the
  existing `Attempt` hasn't expired (repeat calls return the same valid
  token instead of erroring, though the underlying one-shot invariant is
  unchanged — once expired, no second chance);
  weekly-exam regeneration refuses to run once an `Attempt` already exists
  (the source unconditionally cascaded-deleted it); and `rank`/`balance`
  (cross-student score statistics) were not ported at all — they're
  artifacts of the source's Postgres materialized view + Celery refresh job
  (no such infra exists here, and none was stood up speculatively, per the
  spec's own recommendation), not core exam data. **Management UI: `GET /exams`
  (`web.ExamsPageController`, built 2026-07-17 via the `design-page` skill,
  third and last moshavere-port frontend slice)** — a thin shell like
  `/curriculum`/`/study-plans`, entirely client-side against `ExamController`/
  `ExamResultController`. The expanded-exam management panel uses Bulma
  `tabs` (Sections / Assign / Results) instead of three always-visible
  stacked boxes, since a single exam's authoring surface is deep enough that
  the curriculum/study-plan pattern would make the page unreadably long.
  Manual section authoring loads a topic's questions (subtree-inclusive,
  same semantics `/curriculum` already exercises) into a checkbox list; auto
  section authoring exposes `lazy` as a plain checkbox. The weekly-exam box
  reuses `mostRecentSaturday`/`formatLocalDate` from `study-plans.js`
  (duplicated inline again, second use, still under the rule-of-three) and
  only shows the just-generated exam's own sections inline — there is no
  admin-facing "list a student's weekly exams" endpoint to browse historical
  ones (`ExamFacade.listExamsForStudent` is reached only through the
  self-service `MyExamsController`, not the admin surface). Navbar link
  visible for `SCHOOL_ADMIN`/`TEACHER` (both hold `EXAM_MANAGE`/`EXAM_VIEW`
  identically).
  <br>
  **Student-facing UI, built in the same pass**: `GET /my-exams`
  (`web.MyExamsPageController`) is a thin shell like `/my-invoices` — no
  `X-School-ID` header, `schoolId` comes from the `/api/me/...` path, reads
  `exam.MyExamsController` directly. Starting an attempt navigates to a
  **second, deliberately separate page**: `GET /exam-take?token=...&expireAt=...`
  (`web.ExamTakePageController`). This page is `permitAll` in `SecurityConfig`
  and excluded from `TenantResolverFilter` (same treatment as `/activate` and
  the pre-existing `/api/exam-take/**`) and renders **without the shared
  navbar** — `NavbarModelAssembler` requires an authenticated principal that
  this page's own trust model doesn't guarantee, since the exam-take token
  is deliberately the sole credential (`ExamFacade`'s own class doc). Unlike
  `/activate` (which predates the 2026-07-05 code-placement rule and keeps
  inline Alpine), `exam-take.js` is a proper static `Alpine.data` file — and
  since a static JS file can't receive JTE `${...}` interpolation, it reads
  `token`/`expireAt` directly from `window.location.search` rather than
  through a server-injected model, which is also why
  `ExamTakePageController` stays a bare one-line route with no model at all.
  The countdown timer is computed from the `expireAt` query param (the
  attempt's own expiry, passed through from `my-exams.js`'s `startAttempt`);
  each answer selection POSTs immediately to `ExamTakeController` (no
  separate "submit exam" step exists on the backend — there never was one to
  wire up). Deliberately not built: an "clear my answer" control (the
  backend supports `choice=null`, moshavere's own UI rarely used it, and
  omitting it keeps the page to one interaction per question) — a disclosed,
  narrow scope cut, not a silent one. Manually verified end-to-end (no
  Docker/Testcontainers in this session, same gap noted for `bale` Phase 1):
  full collective-exam create→auto-section→assign→student
  start-attempt→answer→(simulated exam-end via direct DB update, since
  waiting out a real exam window isn't practical in a manual session)→both
  the student's own result view and the instructor's per-student result view
  showed the correct 100% score and 1/1 correct breakdown. Covered by
  `ExamFlowIntegrationTest` (full collective-exam
  create→section(manual+auto-pick with `lazy` both ways)→assign→attempt→
  exam-take→submit→(wait for the exam window to close)→result cycle, both
  instructor and student result views, the one-shot attempt invariant via
  token invalidation after expiry, weekly-exam generation from a real
  `StudyIntervalEntity` capped at the source's 5-question-per-topic limit
  and its regenerate-replaces-cleanly behavior, `CannotAttempt` for an
  unassigned student, and both 403 flavors — `permission-denied` for a
  `STUDENT` hitting the admin surface, `module-not-entitled` for a fresh
  self-service school). **This closes out the moshavere port** — all four
  pieces (`iam` OTP, `curriculum`, `studyplan`, `exam`) are now built; see
  `MOSHAVERE_PORT_SPEC.md` §8.
- **Student study-plan self-view (built 2026-07-17, from a Claude Design
  handoff bundle — implementing that tool's "Student Panel" mockup against
  this codebase's real `studyplan` domain, not the mockup's own invented
  one)**: `GET /my-study-plan` (`web.MyStudyPlanPageController`, thin shell
  like `/my-exams`) — a read-mostly weekly calendar of the student's own
  auto-scheduled `StudyInterval` blocks (RTL, Saturday→Friday, Jalali dates/
  Persian digits, week + Jalali month-picker navigation), a legend, and a
  client-computed "آمار و گزارش" tab (KPIs, per-day and per-topic charts) —
  all derived from one weekly fetch, no separate stats endpoint. Backed by a
  new `studyplan.MyStudyIntervalController` (`GET`/`PATCH
  /api/me/schools/{schoolId}/study-intervals`), same "my" trust model as
  `MyEnrollmentController`/`MyExamsController` (no `@RequiresModule`/
  `@RequiresPermission`, `studentId` resolved via
  `AcademicFacade.findMyEnrollment`). **Explicit scope decision, made with
  the user before implementing**: the design mockup invented per-session
  fields this codebase's `StudyIntervalEntity` doesn't have (assigned/actual
  pages read, actual minutes studied) — rather than extending the schema,
  the page was built strictly against the fields that already exist
  (`topicId`/`start`/`end`/`done`/`testCount`); a student marks a session
  done and logs a test count, nothing more. Topic names are resolved
  server-side via `CurriculumFacade.listTopics` and embedded in the
  response, since Curriculum's own endpoints are
  `CURRICULUM_VIEW`/`CURRICULUM_MANAGE`-gated (the bank carries correct
  answers) and a student can't call them directly. **New facade method:**
  `StudyPlanFacade.updateOwnInterval(schoolId, studentId, intervalId, done,
  testCount)` — the pre-existing admin `updateInterval` only checks
  `schoolId`, which is fine behind `STUDYPLAN_MANAGE` (already
  school-wide-authorized) but would have let any authenticated student edit
  another student's interval by guessing its id; the new method also
  verifies the interval belongs to the calling `studentId`, throwing the
  existing `IntervalNotFound` otherwise (same "foreign record = not found"
  convention as everywhere else in this module, so the 404 doesn't leak
  whether the id exists at all). Also extended two shared pieces rather than
  duplicating: `layout/main.jte` gained an optional `styles` param
  (mirroring the pre-existing `scripts` param) since this is the first page
  with its own external stylesheet (`static/css/my-study-plan.css` — the
  calendar grid, logical properties only, no new colors, reuses the
  `--bulma-primary-*`/`--bulma-success-*` HSL tokens); `static/js/jalali.js`
  (previously Jalali→Gregorian only, for the `/expenses` report picker)
  gained the reverse direction (`toJalali`) plus `toPersianDigits()` and
  Persian month/weekday name arrays — completing it into the single shared
  Jalali+digit-rendering utility DESIGN.md §8 already assumed existed.
  Navbar link visible for `STUDENT` only; `/my-study-plan` added to
  `TenantResolverFilter.EXCLUDED_PATHS` (same reasoning as `/my-exams` — its
  fetches are under the already-excluded `/api/me` prefix). Covered by
  `MyStudyPlanFlowIntegrationTest` (topic names + own-interval PATCH
  round-trip, a second student rejected with `interval-not-found` when
  attempting to PATCH the first student's interval, a non-student caller
  gets an empty week with no error, unauthenticated page access redirects to
  `/login`) — **not run against a real Postgres in the authoring session**
  (no Docker registry egress in that sandbox to pull the Testcontainers
  image); it compiles and follows the established
  `*FlowIntegrationTest`/`TestcontainersConfiguration` pattern exactly, same
  as this repo's CI (`.github/workflows/*.yml`, real internet access) already
  runs for every other such test — run it there before treating this slice
  as fully verified. `mvn test -Dtest=ModularityTests` passed in the
  authoring session (module boundaries intact, no new named-interface
  exposure needed since `studyplan` already depended on `AcademicFacade`/
  `CurriculumFacade`/`IamFacade`).
- **Bale integration gap cleanup (built 2026-07-17)**: two known gaps flagged
  since the Bale Phase 1 build (§7 Done above, `MOBILE_ROADMAP.md` §4/§5)
  closed now that Docker/Testcontainers is available again in-session:
  - **`BaleFlowIntegrationTest`**: covers the inbound webhook (wrong-secret
    404, `/start` contact-request prompt, contact-share linking + upsert on
    re-share, unknown-phone rejection, unlinked-chat "not linked" reply,
    `"مدرسه‌های من"`/`"فرزندان من"` text commands) and the outbound
    `finance.InvoiceIssuedEvent` → notify-student-and-linked-guardians flow.
    Since `roshdeandishe.bale.bot-token` is empty by default (`BaleClient`
    only logs, makes no real HTTP call), the test stands up a local JDK
    `com.sun.net.httpserver.HttpServer` and points
    `roshdeandishe.bale.api-base-url`/`bot-token`/`webhook-secret` at it via
    `@DynamicPropertySource` — the same role GreenMail plays for
    `InviteTokenFlowIntegrationTest`, but with zero new `pom.xml` dependency
    (the JDK's built-in HTTP server is sufficient for capturing outbound
    `sendMessage` calls). `mvn test -Dtest=ModularityTests` re-verified green.
  - **Guardian-link admin UI**: previously curl/API-only (`AcademicFacade.linkGuardian`/
    `listGuardians`, `GUARDIAN_MANAGE`, SCHOOL_ADMIN-only). Added as a
    per-student-row "مدیریت والدین" expandable panel on the existing
    `GET /students` page (`students.jte`/`students.js`) — same expand-in-place
    pattern as `/classes`'s student-roster panel (§7 Done, "TEACHER persona").
    **Zero backend change**: the panel composes the existing
    `academic.StudentController` guardian endpoints with
    `iam.MembershipController` (filtered to active `PARENT` memberships) for
    name/email resolution and the parent-picker options, client-side only.
    Guardians for a given student are fetched lazily the first time that
    student's panel is opened (no bulk "list every guardian link in the
    school" endpoint exists), cached per-student in memory for the rest of
    the page session. The already-linked-parent set is subtracted from the
    picker's options so the form doesn't re-offer someone already linked
    (the backend call is idempotent either way — this is UX polish, not a
    safety net). Manually verified end-to-end in a real browser: opened an
    already-linked student's panel (existing guardian listed correctly),
    opened an unlinked student's panel (empty-state text + picker shown),
    linked a parent, saw the success notice and the panel update immediately.
    Went through the `design-page` skill first per the standing instruction,
    since this reshapes `/students`'s layout; no navbar change needed (no new
    page, no new link).
- **Study-plans page deepening (built 2026-07-17)**: the two pieces
  deliberately left out of `/study-plans`'s first pass (§7 Done, "Moshavere
  port, step 3") — `WeekCourseController` (a plan's weekly topic template)
  and the multi-student `copyWeek` bulk action. **Zero backend change**,
  both REST endpoints already existed. Went through the `design-page` skill
  first since this reshapes `/study-plans`'s layout.
  - **Weekly topics**: the per-plan row's single "تخصیص به دانش‌آموز" toggle
    became "جزئیات برنامه", opening a panel with Bulma `tabs` — one tab for
    the existing student-assignment list, a second, lazy-loaded
    "سرفصل‌های هفتگی" tab listing/creating `WeekCourse` rows (week-start date
    snapped to that week's Saturday client-side via the same
    `mostRecentSaturday` helper `suggest`/`clear` already use, a topic
    picker, and a sub-topic checklist capped at 10 — mirroring
    `WeekCourseTopicEntity`'s server-side `ArrayField(size=10)` port — that
    excludes whichever topic is currently selected as the week's main
    topic, so a topic can't be checked as its own sub-topic). This is the
    same expanded-row-with-Bulma-tabs pattern `/exams` already established
    for its per-exam management panel (§7 Done, "Moshavere port, step 4").
  - **Copy week**: a new secondary button next to `پیشنهاد خودکار`/
    `خالی‌کردن این هفته` in the student-schedule box opens an inline panel —
    a checkbox list of the school's *other* students (the selected student
    excluded) plus a "کپی کردن" submit — calling the existing
    `POST .../study-intervals/copy`. Per-target results (نام + موفق/ناموفق)
    render in a small results table, and the top-level notice summarizes
    "X از Y مورد با موفقیت".
  - Manually verified end-to-end in a real browser: created a `WeekCourse`
    with a sub-topic (confirmed the self-reference exclusion and the
    Saturday-snap), then copied a populated week to a second student and
    confirmed both the success notice and the per-target result row.
    `mvn test -Dtest=StudyPlanManagementFlowIntegrationTest,ModularityTests`
    re-verified green (no backend touched).
- **School/class level classification (built 2026-07-18, explicit user decision —
  resolves half of §11 Q8's blocker)**: schools can now differentiate themselves
  as دبستان/متوسطه اول/متوسطه دوم ahead of building the actual grades feature —
  see §4's `School` and `Class` entries for the full model
  (`school.SchoolFacade.SchoolLevel`, `academic.ClassEntity.gradeLevel`). Four
  explicit decisions, made with the user before implementing (not defaults):
  (1) Iran's real 3-tier taxonomy, not a generic 2-tier primary/high split;
  (2) the classification lives on **both** `School` (`schoolLevel`, required at
  creation) and `Class` (`gradeLevel`, required at creation) — not just one —
  because a real school building only ever has classes within its own level's
  grade range, and that's only enforceable per-class; (3) the eventual grading
  scheme (descriptive vs. numeric) will be auto-derived from `gradeLevel` by a
  fixed platform rule (grades 1–6 → descriptive, 7–12 → numeric) once the grades
  module is actually built — **not built in this pass**, since nothing consumes
  it yet (YAGNI, CLAUDE.md's own top-level discipline); (4) scope for this pass
  is the classification model only, not the grades (marks) module itself — same
  incremental-slice precedent as curriculum → studyplan → exam each being a
  separate pass. **Update, same day**: a parallel session built the grades
  (marks) module concurrently, independently choosing an *explicit,
  per-class-overridable* `gradingScheme` over a purely-derived one — the two
  were reconciled (see the "Grades" entry immediately below): `gradeLevel`'s
  rule now supplies `gradingScheme`'s *default*, not its only source of truth.
  <br>
  **New cross-module dependency**: `academic` now depends on `school.SchoolFacade`
  (`AcademicFacadeImpl.requireValidGradeLevel`, called from `createClass`) — the
  first time `academic` validates anything about `schoolId` beyond treating it as
  an opaque Long. Legal per `ModularityTests` (no cycle: `school` has never
  depended on `academic`). Two new `AcademicError` cases:
  `SchoolNotFound` (defensive — `TenantContext` already validates `schoolId`
  before a controller call reaches the facade, so this shouldn't be reachable
  through the REST path) and `InvalidGradeLevel` (a real, reachable 400 when the
  submitted grade falls outside the school's level range).
  <br>
  **UI**: `GET /schools/new` gained a required "مقطع تحصیلی" select (three
  Persian-labeled options, `school-new.js`). `GET /classes` gained a required
  "پایه" number input on the create-class form (`classes.jte`/`classes.js`) and
  a "پایه" column in the class table; the form also fetches
  `GET /api/admin/schools/{id}` once per page load (already existed, no backend
  change — any active member may call it, §3's `SchoolController` entry) to show
  the school's valid grade range as helper text and set the input's `min`/`max` —
  best-effort only, the real validation is always server-side. Neither page
  reshapes layout/flow enough to warrant the `design-page` skill — both are a
  single new field on an already-designed form, not a new page or a new
  panel/tab.
  <br>
  **Test fallout**: `academic.createClass`'s new validation broke every test that
  called it (directly via `AcademicFacade` or through `POST .../classes`) with a
  schoolId that had no real backing `SchoolEntity` — a pattern several facade-level
  tests relied on precisely because `schoolId` used to be a fully opaque Long
  (`FinanceFlowIntegrationTest`, `FinancePaymentFlowIntegrationTest`,
  `BaleFlowIntegrationTest` all used synthetic/sequential schoolId literals with
  no real school row). Fixed by having those specific tests call
  `schoolFacade.createSchool(...)` for a real schoolId instead of fabricating one
  — the correct fix, not a workaround, since a class genuinely cannot exist
  without a real, level-compatible school now. Every other affected test (all
  `POST /api/admin/schools` bodies gained `"level":"ELEMENTARY"`; all
  `POST .../classes` bodies and direct `createClass` calls gained a `gradeLevel`
  within the demo school's ELEMENTARY 1–6 range, regardless of what a class's
  free-text `name` happens to say — e.g. a fixture named "پایه هشتم" may carry
  `gradeLevel: 6`, a harmless cosmetic mismatch in test data, not a product bug)
  needed only a field added to an existing request body. Full suite (139 tests)
  + `ModularityTests` green after the fix.
- **Grades (built 2026-07-18, resolving §11 Q8, an extension of `academic` —
  not a new module, same precedent as `Expense` extending `finance`)**: a
  student's grade in a class for one term — see §3, §4's Grade subsection,
  and §11 Q8 for the full model and the four explicit decisions behind it
  (per-class scheme, manual entry only, fixed four-level descriptive scale,
  per-term cadence). Built the same day as, and reconciled with, the
  School/class level classification entry immediately above (a genuine
  parallel-session collision, not a sequencing choice — see §11 Q8 for how
  the two were merged). New `GradingScheme`/`GradingTerm`/
  `DescriptiveGradeLevel` enums, `ClassEntity.gradingScheme` (nullable
  column, explicit and overridable per class; when a caller doesn't set it,
  `ClassController.CreateClassRequest`'s compact constructor defaults it from
  `gradeLevel` — 1–6 → `DESCRIPTIVE`, 7–12 → `NUMERIC` — not a bare `NUMERIC`
  fallback), `GradeEntity`/`GradeRepository`,
  `AcademicFacade.recordGrade`/`getGrade`/`listGradesForClass`/
  `listGradesForStudents`, four new `AcademicError` cases
  (`StudentNotInClass`, `GradingSchemeMismatch`, `InvalidGrade`,
  `GradeNotFound` — on top of the classification entry's own `SchoolNotFound`/
  `InvalidGradeLevel`, six new cases total this day). New `GradeController`
  (admin gradebook, `@RequiresModule("ACADEMIC")` + `GRADES_EDIT` on *both*
  methods — a deliberate security choice, see §4's Permission section —
  `GRADES_VIEW` alone would let a `STUDENT`/`PARENT` caller see a whole
  class's grades) and `MyGradesController` (`GET /api/me/schools/{id}/grades`,
  the established "my" pattern, no permission check — own grades + linked
  wards' grades, same shape as `MyInvoicesController`). Fixed a pre-existing
  seed-data asymmetry in passing: `GRADES_EDIT` had only ever been granted to
  `TEACHER`, not `SCHOOL_ADMIN` (unlike every other `*_MANAGE`-tier
  permission) — placeholder seed data from before this feature existed,
  corrected now that it's real. Covered by `GradeManagementFlowIntegrationTest`
  (both schemes recorded and listed, wrong-field-for-scheme rejected as
  `grading-scheme-mismatch`, out-of-range numeric score rejected as
  `invalid-grade`, re-recording the same student/class/term updates in place
  instead of duplicating, grading a student against a class they're not
  enrolled in rejected as `student-not-in-class`, `TEACHER` can record/list,
  `STUDENT` gets `permission-denied` on the admin endpoint *despite holding*
  `GRADES_VIEW` — the security case above — a fresh self-service school gets
  `module-not-entitled`, and `MyGradesController` returns own + linked wards'
  grades only, empty for an unrelated caller). Full suite + `ModularityTests`
  run against a real Postgres via Testcontainers after reconciling with the
  classification entry above, and green. ~~No UI yet~~ **UI built
  2026-07-20** — see "Grades UI" entry below (§7 Done), through the
  `design-page` skill, resolving the critical user-journey review's
  highest-priority gap (UX_ROADMAP.md §6.2).

- **School entry page — per-school branded entry URL (built 2026-07-19,
  Tier 1 of the custom-domain decision, §11 Q9)**: a school can claim an
  optional unique `slug` at creation (`/schools/new` gained the field with a
  live URL preview + client-side format check) and gets a public branded
  entry page at `GET /s/{slug}` (`web.SchoolEntryPageController` +
  `school-entry.jte` — server-rendered from `SchoolFacade.findSchoolBySlug`,
  no navbar/no fetch, same anonymous trust model as the landing page; went
  through the `design-page` skill). The slug is deliberately validated as a
  **DNS label** (`SchoolFacadeImpl`: lowercase `[a-z0-9]` + inner hyphens,
  3–63 chars, reserved-name list, lowercased server-side; two new sealed
  `SchoolError` cases `InvalidSlug` 400 / `SlugAlreadyTaken` 409) so the
  same value becomes the school's subdomain in Tier 2 with zero migration.
  CTA clicks (not page load, to avoid a drive-by visit overwriting a
  multi-school user's selection) write `localStorage.selectedSchoolId`, so
  post-login the navbar lands on that school — `navbar.js`'s existing
  init-validation makes a non-member's value fall back safely. An
  authenticated visitor is NOT redirected (unlike `GET /`) — they see an
  "ادامه به داشبورد" CTA instead, because the localStorage write needs a
  client-side click. Unknown *and SUSPENDED* slugs render a teaching
  not-found state with HTTP 404 (suspended schools aren't advertised —
  presentation rule in the controller, not the facade).
  `SchoolFacade.createSchool` gained the `slug` param (all four test
  call sites updated), `SchoolInfo`/`SchoolResponse` gained `slug`, demo
  school seeds slug `nemooneh`, `/s/**` is `permitAll` + excluded in
  `TenantResolverFilter` (`"/s/"` — no clash with `/students`, which
  doesn't start with `"/s/"`). Covered by `SchoolEntryFlowIntegrationTest`
  (create-with-slug → public page shows name/login CTA/localStorage write,
  uppercase input lowercased, unknown slug 404, suspended school 404,
  duplicate slug 409, invalid/short/reserved slugs 400, slug optional,
  authenticated visitor sees the dashboard CTA). Deliberately NOT built
  (see §11 Q9): editing/adding a slug after creation (needs a
  school-settings permission decision), ~~showing the entry URL on the
  dashboard~~ (**built 2026-07-24** — see "Dashboard entry-URL display"
  below), Host-header tenant resolution (Tier 2 — deployment-gated),
  and full custom domains (Tier 3 — `PLATFORM_ADMIN`-gated).

- **Free-tier entitlements at school creation (built 2026-07-20, explicit
  user decision — resolves §11 Q10, raised by the 2026-07-19 critical
  user-journey review, UX_ROADMAP.md §6.1)**: a self-service school no longer
  dead-ends with zero entitlements — `SchoolController.createSchool` now calls
  the new `IamFacade.grantFreeTierEntitlements(schoolId)` (right after the
  creator's `SCHOOL_ADMIN` membership), granting `ACADEMIC` + `FINANCE`.
  `CURRICULUM`/`STUDY_PLANNER`/`EXAM` are paid plans: not granted, still
  facade-only (`setSchoolEntitlement`, no REST — §10 unchanged) until real
  billing exists. The free-module list is `FREE_TIER_MODULES` in
  `IamFacadeImpl` — one place to change when billing arrives. Demo-school dev
  seed unchanged (all five modules — a superset of the free tier). The three
  fresh-school `module-not-entitled` tests on now-free modules were reworked
  rather than deleted: each first proves the free module works immediately
  (tuition-fee 404-not-403 / expenses 200 / classes 200), then revokes the
  entitlement via the facade and re-asserts `module-not-entitled` — so both
  the grant and the revocation path are covered
  (`FinanceManagementFlowIntegrationTest`, `ExpenseManagementFlowIntegrationTest`,
  `GradeManagementFlowIntegrationTest`); the paid-module fresh-school tests
  (`CurriculumFlowIntegrationTest`, `StudyPlanManagementFlowIntegrationTest`,
  `ExamFlowIntegrationTest`) pass unchanged and now double as proof the free
  tier excludes paid modules.
- **Grades UI (built 2026-07-20, via the `design-page` skill — resolves the
  highest-priority gap from the 2026-07-19 critical user-journey review,
  UX_ROADMAP.md §6.2)**: `GET /grades` (`web.GradesPageController`,
  `grades.jte` + `grades.js`) and `GET /my-grades`
  (`web.MyGradesPageController`, `my-grades.jte` + `my-grades.js`) — the two
  pages the Grade backend (§7 Done, "Grades", above) had been missing since
  2026-07-18. Both are thin shells, zero backend changes — entirely
  client-side against the existing `academic.GradeController`/
  `academic.MyGradesController`.
  - **`/grades`** (SCHOOL_ADMIN + TEACHER, same visibility as `/classes`):
    a class + academic-year + term selector box (`بارگذاری` submits it, same
    boxed-form-with-its-own-button convention as `/finance`'s tuition-fee
    box and `/classes`'s create-class box — no page-header primary button,
    consistent with every existing management page), then an editable
    gradebook table — one row per student currently enrolled in the
    selected class (`academic.StudentController`'s roster, filtered by
    `classId`, same composition pattern `/students` already uses for names
    via `iam.MembershipController`). Each row's editable cell is chosen from
    the *selected class's own* `gradingScheme` (already present on
    `ClassController`'s `ClassResponse`, no extra call needed): a
    `DESCRIPTIVE` class gets a four-option select (خیلی خوب/خوب/قابل قبول/
    نیازمند تلاش), a `NUMERIC` class gets a 0–20 number input. Existing
    grades for the selected class/year/term pre-fill their row (matched by
    `studentId` from `GradeController`'s `GET`); submitting a row calls the
    same idempotent `POST` per CLAUDE.md's `recordGrade` semantics. Inline
    validation (a row can't submit blank/out-of-range) plus a summarized
    notice on failure, per `DESIGN.md` §5/§6. Reuses the `classifyError`
    two-403 pattern verbatim (`finance.js`/`expenses.js`/now `grades.js`) —
    `ACADEMIC` is free-tier as of the entry above, but remains revocable via
    the facade, so `module-not-entitled` is still a real, reachable state,
    not dead code. `academicYear` is free text (e.g. "۱۴۰۴-۱۴۰۵"); per
    `DESIGN.md` §8's digit-normalization rule, `grades.js` normalizes
    Persian/Arabic-Indic digits to ASCII before sending — the first place in
    this codebase that rule is actually implemented (kept inline per the
    rule-of-three, not extracted to a shared helper on its first use).
  - **`/my-grades`** (STUDENT + PARENT, same visibility as `/my-invoices`):
    a flat read-only table, own grades (STUDENT) + linked wards' grades
    (PARENT), exactly the `/my-invoices` shape (no `X-School-ID`, `schoolId`
    from the path, no `@RequiresPermission`/`@RequiresModule` to react to —
    the "my" pattern never needs the two-403 split). Numeric scores render
    with `Jalali.toPersianDigits` (`jalali.js`, already loaded by
    `/my-study-plan`/`/expenses`); descriptive levels render as their
    Persian label — never the raw enum code.
  - Both routes added to `TenantResolverFilter.EXCLUDED_PATHS` (own-route
    exclusion, same reasoning documented there for every other management/
    "my" page) and to `layout/navbar.jte` (`نمرات` for SCHOOL_ADMIN/TEACHER,
    `نمره‌های من` for STUDENT/PARENT — no orphan URLs). Covered by
    `GradesPageFlowIntegrationTest`/`MyGradesPageFlowIntegrationTest`
    (page-shell only, same shape as `MyInvoicesPageFlowIntegrationTest` —
    the REST behavior itself is already fully covered by
    `GradeManagementFlowIntegrationTest`). Full suite + `ModularityTests`
    green.
- **Phone-number UI + OTP login tab (built 2026-07-20, resolves
  UX_ROADMAP.md §6.2 item 2 / §6.6 item 3)**: `PUT /api/me/phone` and the
  OTP endpoints (`POST /api/auth/otp/request`/`verify`, both from the
  earlier moshavere-port step) existed with zero UI — no real user could
  ever link the Bale bot or log in without a password. Went through the
  `design-page` skill first since both `/dashboard` and `/login` were
  meaningfully restructured.
  - **`/dashboard`** gained a "شماره تلفن" box (own primary action, same
    boxed-form-with-its-own-submit convention as `/expenses`/`/finance`) —
    a `tel` input pre-filled from the caller's current phone number, saving
    via the existing `PUT /api/me/phone`. `IamFacade.UserProfile` gained a
    fourth field, `phoneNumber` (nullable) — the only way `findUser` could
    expose it to `DashboardPageController`'s server-side render; three
    construction call sites in `IamFacadeImpl` updated together with it (no
    other call sites existed). The initial value is passed through a
    `data-phone-number` HTML attribute, read via `x-init`, deliberately
    **not** interpolated into the `x-data(...)` JS expression itself —
    `setPhoneNumber` validates only length, not characters, so a phone
    number containing a stray `'` would have broken (or, worse, injected
    into) the JS source if inlined directly into an Alpine expression;
    `data-*` + `.dataset` keeps it a plain string no matter its content.
    First JS file for `/dashboard` (`static/js/dashboard.js`).
  - **`/login`** gained a second tab ("کد پیامکی") alongside the existing
    password form, via Bulma `tabs is-toggle` (same tabs pattern as
    `/exams`'s per-exam panel) — a two-step flow inside the tab (enter
    phone → "دریافت کد" → enter code → "ورود"), calling the pre-existing
    OTP endpoints exactly as `OtpLoginFlowIntegrationTest` already proved
    them: `otp-incorrect` (400) keeps the user on the code step for a retry
    (the facade deliberately doesn't consume the code on a wrong guess,
    §4's `VerificationCode` note), `otp-expired` (410) surfaces a message
    plus a way back to the phone step, `phone-not-registered` (404) tells
    an unregistered visitor to log in with a password first and set their
    number on `/dashboard` — there is still no unauthenticated way to claim
    a phone number, unchanged from §4 User's security reasoning. Both phone
    and code inputs normalize Persian/Arabic-Indic digits to ASCII before
    sending, per `DESIGN.md` §8 (same rule `grades.js` implemented first).
    `login.jte` predates the 2026-07-05 code-placement rule and had kept
    its Alpine inline until now — CLAUDE.md's own grandfather clause says
    to apply the rule "when meaningfully editing" such a page, and adding a
    whole second auth flow qualified, so the component moved to
    `static/js/login.js` in the same change, unchanged in behavior for the
    password tab.
  - No new REST endpoints, no new permission/module code — every call was
    already built and tested by earlier moshavere-port work; this slice is
    UI-only. Covered by two new cases in `LoginDashboardFlowIntegrationTest`
    (phone field renders empty then pre-filled after a real
    `PUT /api/me/phone` round-trip; `/login` renders both tab labels). Full
    suite (171 tests) + `ModularityTests` green.
- **Invite-status visibility on the enrollment funnel (built 2026-07-20,
  resolves UX_ROADMAP.md §6.3 / §6.6 item 4)**: closes the "highest-volume
  flow" friction the critical user-journey review flagged — enrolling a
  student created a passwordless account with no visible signal of whether
  it had ever been activated, and inviting one meant navigating away to
  `/people` to find the right row. Two pieces, both UI/composition-only:
  - **`IamFacade.MembershipInfo` gains a `hasPassword` boolean**
    (`user.getPasswordHash() != null`, read in the same `toMembershipInfo`
    helper that already loads the `UserEntity` — no new query).
    `MembershipController.MembershipResponse` carries it through
    unchanged otherwise. **`/people`** now renders a second tag per row
    ("فعال‌سازی شده" / "در انتظار فعال‌سازی") alongside the existing
    active/inactive tag — same "وضعیت" column, no new column, so the table
    doesn't grow on mobile.
  - **`/students`' enrollment success path** gains an inline "میان‌بر
    دعوت" box: right after a successful enroll, `students.js` finds the
    just-created student's own membership row in the membership list it
    already fetches for name resolution (`this.pupils`, matched by the
    enroll response's `userId` — no new fetch), then offers a "ارسال
    دعوت‌نامه" button calling the *existing*
    `POST .../memberships/{membershipId}/invite-token` directly — the same
    endpoint `/people`'s own `sendInvite` already uses, reused verbatim
    (idempotent, same three outcomes: email sent / manual link to copy /
    already activated). **No new REST endpoint** — `academic` gained no
    new dependency on `iam`, same "compose in JS, not Java" precedent
    `classes.js`/`students.js` already established for teacher/student name
    resolution. A `TEACHER` (who lacks `MEMBERSHIP_MANAGE`) still sees the
    button per this app's "never hide by permission" rule and gets the
    standard `permission-denied` notice on click — no special-casing
    needed. Went through the `design-page` skill first since both pages
    were reshaped. Covered by extending
    `MembershipManagementFlowIntegrationTest` (asserts `hasPassword: false`
    right after creation, `true` after a real `/api/auth/activate`
    redemption, and that both tag labels render in `/people`'s static
    markup) and a new case in `StudentManagementFlowIntegrationTest`
    (`enrolledStudentsMembershipIsFindableAndInvitableRightAfterEnrollment`
    — proves the exact enroll → list-memberships → invite-token chain
    `students.js` performs, end to end, over real REST calls). Full suite
    (172 tests) + `ModularityTests` green.
- **Dashboard entry-URL display (built 2026-07-24, resolves UX_ROADMAP.md
  §6.4 / §6.6 item 5 — the last item on that review's order of attack)**:
  the school entry slug (§11 Q9 Tier 1, built 2026-07-19) was **write-only**
  — settable at creation and never shown again, so an admin who claimed
  `/s/{slug}` had no way to find it afterward. `/dashboard` now renders a
  "نشانی ورود مدرسه" box listing every school where the caller holds an
  *active* `SCHOOL_ADMIN` membership, each with its absolute entry URL
  (clickable, `dir="ltr"`) and a secondary "کپی نشانی" button. Went
  through the `design-page` skill first (it reshapes `/dashboard`'s
  layout). **Zero backend change** — no new REST endpoint, no new
  permission code, no new module dependency (`web` → `school` already
  existed via `NavbarModelAssembler`).
  - `DashboardPageController` now injects `SchoolFacade` directly and
    resolves each distinct `schoolId` to a full `SchoolInfo` **once**,
    taking both the display name and the slug from that single lookup —
    replacing its previous per-school `NavbarModelAssembler.schoolName`
    call (same lookup count, two fields instead of one; the assembler's
    own navbar lookup is unchanged, so the pre-existing
    controller/assembler duplication is neither widened nor fixed here).
    The `"مدرسه #id"` fallback for an unresolvable school is preserved
    verbatim.
  - **Deliberately server-rendered, not a management-page fetch**: no
    `X-School-ID`, no CSRF, and **no `school-changed` listener** — the box
    lists *all* of the caller's admin schools at once, so the navbar's
    selected school doesn't scope it. No module-gated call is made, so the
    `classifyError` two-403 pattern is deliberately absent (it would be
    dead code here). The only JS is `schoolEntryLinks()` in the existing
    `static/js/dashboard.js` — building the absolute URL client-side from
    `window.location.origin`, so the server needs no "public base URL"
    config property (`roshdeandishe.mail.activation-base-url` stays
    email-only), and what's displayed is exactly what's copied.
  - The role check is the literal string `SCHOOL_ADMIN`, same convention
    as `finance.FinanceController`'s literal `SCHOOL_ADMIN`/`FINANCE_ADMIN`
    check and academic's literal `PARENT` — custom roles (§4's Role
    subsection) deliberately don't affect it, and it is **not** a security
    boundary: `/s/{slug}` is a public page by design, so this only decides
    who gets shown the link. A caller with no active admin membership
    doesn't render the box at all.
  - A school with **no slug** is still listed, with a caption saying the
    address is chosen at registration — honest about §11 Q9's unbuilt
    edit path rather than implying one exists. `slug` reaches Alpine via a
    `data-slug` attribute rather than interpolation into a JS expression,
    following the convention the phone-number box on this same page
    documents (the slug *is* DNS-label-validated server-side, so this is
    consistency, not a live escaping bug).
  - Clipboard failure (unavailable outside a secure context, or denied) is
    a real, handled state: an `is-danger` notice telling the user to select
    and copy manually, never a silent no-op. Success echoes the button's
    verb (کپی → کپی شد), per DESIGN.md §8.
  - Covered by two new cases in `LoginDashboardFlowIntegrationTest`
    (`dashboardShowsEntryUrlForSchoolsWhereUserIsAdmin` — the seeded demo
    school's `nemooneh` link renders; and
    `dashboardHidesEntryUrlBoxWithoutAdminMembershipAndExplainsMissingSlug`
    — a freshly registered user with zero memberships sees no box at all,
    then creates a slug-less school and sees the box with the caption and
    no link). The `*FlowIntegrationTest` suite could not be run in the
    authoring session (no Docker and no local Postgres — the same gap
    documented for the `MyStudyPlanFlowIntegrationTest` and Bale Phase 1
    slices), so it was **verified in CI instead**: the PR's `build` job
    (`mvnw clean verify`) ran the full suite against real Testcontainers
    Postgres green — 165 tests, 0 failures, with
    `LoginDashboardFlowIntegrationTest` itself at 13 (11 pre-existing +
    the 2 above). No outstanding verification gap for this slice.

- **Deployment to Liara — production configuration (2026-07-29).** The first
  real deployment setup for this project: `Dockerfile` (multi-stage Maven →
  JRE 21), `liara.json` (Docker platform, port 8080, `/actuator/health`
  health check, German build server so Maven Central resolves reliably),
  `.dockerignore`, a `prod` Spring profile, and [`DEPLOYMENT.md`](DEPLOYMENT.md)
  as the runbook. The user deploys themselves; this commit prepares the repo.

  **Four production blockers found and fixed**, three of them only visible by
  actually booting the app under the `prod` profile against an empty database —
  none would have shown up in the test suite, which always runs seeded and
  never runs the prod profile:

  1. **Dev seed data would have run in production.** `IamSeedDataRunner` and
     `SchoolSeedDataRunner` were plain unguarded `@Component`s, so a public
     deployment would have booted with `admin@roshdeandishe.test` / password
     `password` holding `SCHOOL_ADMIN` — the seed runner's own comment had
     asked for a profile gate since it was written. Gating *all* of it,
     however, produced a **non-functional** production instance: with
     `iam.roles`/`iam.modules` empty, `SchoolController.createSchool` fails
     with `RoleNotFound` the moment it tries to grant the founder
     `SCHOOL_ADMIN`, and `grantFreeTierEntitlements` has no module rows to
     grant. So the runner is split by *kind*, not switched off wholesale:
     the **catalog half** (Module/Role/Permission/RolePermission) always
     runs — until Flyway exists it is the only thing creating those rows
     anywhere — and only the **demo half** (demo admin, demo school
     membership, demo school entitlements) is skipped, via
     `Environment.matchesProfiles("!prod")`. `SchoolSeedDataRunner` (a pure
     demo school) keeps a plain `@Profile("!prod")`. The negative form is
     deliberate: local runs and the whole integration suite have no active
     profile and must keep seeding exactly as before.
  2. **`/actuator/health` never existed.** `spring-boot-starter-actuator` was
     not a dependency — only `spring-modulith-actuator`, which drags in the
     `spring-boot-actuator` jar but not `spring-boot-actuator-autoconfigure`,
     so no endpoint was ever mapped and the entire `management:` block in
     `application.yml` (`exposure: health, info, modulith`) had been inert
     since it was written. Harmless until now; fatal for a PaaS health check.
     Starter added; the pre-existing config became live as originally intended.
  3. **The health endpoint reported `DOWN` anyway.** Boot's mail health
     indicator connects to SMTP and marks the *whole* endpoint `DOWN` (503)
     when it can't — so an unconfigured mail server, an explicitly
     best-effort optional feature in this codebase, would have had Liara
     flagging every deploy unhealthy and restarting the container. Disabled
     in prod (`management.health.mail.enabled: false`, env-toggleable). The
     `db` indicator stays on: database reachability is what genuinely makes
     this app unusable.
  4. **`seedRoles()` would have crashed on restart once two schools created
     a same-coded custom role.** Its `count() > 0` path collects *all* of
     `iam.roles` with `Collectors.toMap(RoleEntity::getCode, …)`, but since
     custom per-school roles shipped (§11 Q3) that table holds them too, and
     two schools are explicitly *allowed* to each define e.g. `LIBRARIAN` —
     a duplicate key, so `toMap` throws `IllegalStateException` and the
     application never starts again. Fixed by filtering to system roles
     (`schoolId == null`), which is also all the returned map is used for.
     Pre-existing latent bug, unreachable before custom roles existed, and a
     total outage triggered by ordinary tenant behaviour once deployed.

  **Externalized configuration.** `application-prod.yml` makes `DATABASE_URL`/
  `DATABASE_USERNAME`/`DATABASE_PASSWORD`, `APP_BASE_URL`, `JWT_SECRET`, and
  `EXAM_TOKEN_SECRET` **required with no defaults** — the app refuses to boot
  without them, deliberately: the failure mode being prevented is a silent one
  (running on the dev signing keys committed in `application.yml`, or emailing
  activation links pointing at `localhost:8080`). Everything optional keeps a
  default. Also in prod: Swagger/`api-docs` off (both are `permitAll`, so
  leaving them on publishes the whole API surface), actuator exposure narrowed
  to `health` with details hidden, `forward-headers-strategy: framework` (so
  cookies and redirects are `https` behind Liara's proxy), and `Secure`/
  `HttpOnly`/`SameSite=Lax` session cookies. SMTP is deliberately *not*
  required — missing mail config degrades one best-effort feature, it does not
  block startup.

  **Schema creation is no longer manual** (CLAUDE.md §7 item 4):
  `src/main/resources/schema.sql` + `spring.sql.init.mode: always` create all
  eight per-module schemas at DataSource initialization, before the
  `EntityManagerFactory` exists. Previously eight `CREATE SCHEMA` statements
  had to be run by hand or first boot failed — an unacceptable step against a
  managed database. Tests already did this via
  `TestcontainersConfiguration.withInitScript("init-schemas.sql")`; this gives
  real runs the same guarantee.

  **Explicitly not built**: session persistence (`spring-session-jdbc`) or
  sticky sessions, so the deployment is **single-instance only** — scaling is
  a real decision, not a config tweak, and nobody has asked to scale yet;
  Flyway (§7 item 5) — still `ddl-auto: update`, now in production, with the
  consequences documented rather than papered over; any REST exposure of
  paid-module entitlement granting (§10's prohibition stands — it is a
  documented SQL statement in `DEPLOYMENT.md` instead); Tier 2 per-school
  subdomains (§11 Q9), which were blocked on a deployment existing and are now
  merely unbuilt.

  **Verification.** The full `*FlowIntegrationTest` suite could **not** be run
  in the authoring session (no Docker daemon, so Testcontainers is
  unavailable) — CI runs it on this PR. What *was* verified here, against a
  real local Postgres 16 and a genuinely empty database: `ModularityTests` 3/3
  green (module boundaries intact after the actuator dependency); prod-profile
  boot with secrets missing **fails fast** with `Could not resolve placeholder`
  rather than starting; prod-profile boot fully configured starts in ~17s and
  creates all 8 schemas + 42 tables; catalog data present (13 modules, 6 roles,
  19 permissions, 36 role-permission grants) with **zero** demo rows (0 users,
  0 schools, 0 memberships, 0 entitlements); `/actuator/health` returns 200
  `{"status":"UP"}` with details hidden while `/actuator/env`,
  `/actuator/modulith`, and `/swagger-ui.html` all 404; and the whole
  production onboarding journey end-to-end — register → login → create school →
  founder receives `SCHOOL_ADMIN` → free tier `ACADEMIC`+`FINANCE` granted
  automatically → a free-module endpoint returns 200 → a paid-module endpoint
  returns 403 `module-not-entitled` → the public `/s/{slug}` entry page renders.


---

## §8 Roadmap: IAM → School MVP


**Phase A — School entity + admin basics: done.** `SchoolEntity`, self-service
creation, membership create/list/activate-deactivate, all with REST endpoints.
Entitlement toggling deliberately stayed facade-only (see §7 item 1).

**Phase B — Academic basics: done.** Real `ClassEntity`/`StudentEntity`, enrollment
wired to `StudentEnrolledEvent`, REST endpoints for both.

**Phase C — Authentication: done, pulled forward out of original order.**
Session-based (explicit decision — see former Open Question, now resolved).
Spring Security handles authentication only; all authorization stays in the
`@RequiresModule`/`@RequiresPermission` aspects (§2, §5).

**Phase C.1 — Account activation backend + page + email: done.**
`IamFacade.issueInviteToken`/`redeemInviteToken`, invite-token entity/repository,
`MembershipController`/`AuthController` endpoints — see §4/§7 — plus the
`GET /activate` JTE+Alpine.js+Bulma page, plus email delivery of the activation
link (`InviteEmailSender`, best-effort with the admin JSON token as fallback).
§7 item 2 is resolved; only production SMTP credentials remain, as deployment config.

**Phase D — Finance basics: done.** Real `InvoiceEntity` persistence, per-school
`TuitionFeeEntity` pricing, and `FinanceController` (see §4, §7 item 3). The audit
pattern was reused as-is; payments/invoice-status/backfill remain explicitly
deferred (§7 item 3).

**Phase E — Frontend, slice 1 (login + read-only dashboard): done.** `GET /login`,
`GET /dashboard`, `MeController` (`/api/me`, `/api/me/memberships`), page-vs-API
authentication entry points, and the dev-seed fixes (demo admin membership) — see
§7 Done. **Both former gates on the next slice — the mutating management UI — are
resolved**: CSRF is enabled (cookie-based, §7 item 6) and the school/role switcher
UX is decided and built (now a shared navbar template, `web` module, §11 Q2).

**Phase E.1 — Management UI, slice 1 (People): done.** `GET /people`, built
against the existing `iam.MembershipController` REST endpoints, with the
navbar extracted into a reusable template and the first `static/js/`
`Alpine.data` components.

**Phase E.2 — Management UI, slice 4 (Classes): done.** `GET /classes`, built
against the existing `academic.ClassController` REST endpoints; teacher names
resolved client-side from `iam.MembershipController` (no cross-module Java
dependency added to `web`).

**Phase E.3 — Management UI, slice 5 (Enrollment): done.** `GET /students`,
built against `academic.StudentController`, which gained its first roster-list
endpoint (`GET .../students`) plus `AcademicFacade.listStudents` to back it;
student names resolved client-side from `iam.MembershipController`, same
pattern as Classes' teacher-name resolution.

**Phase E.4 — Management UI, slice 6 (Finance): done. SCHOOL_ADMIN build
order complete.** `GET /finance`, built against the existing
`finance.FinanceController` (no backend changes needed — `PUT`/`GET
tuition-fee` and `GET invoices` already existed). First page distinguishing
the two §5 access-control layers' 403s (`ModuleNotEntitled` vs
`PermissionDenied`) with different user-facing messages, via the
`ProblemDetail.type` URI suffix. Invoice student names resolved client-side
the same way Enrollment resolves them. See `UX_ROADMAP.md` for the
FINANCE_ADMIN/TEACHER personas (reuse pages already built) and everything
still explicitly deferred (grades UI, custom roles, B2C module UI).

**Phase F — Bale bot integration, Phase 1: done.** New `bale` module (§3, §4's
BaleLink note): outbound invoice notifications + inbound self-service queries
over a Bale (بله) messenger webhook, gated by a phone-number identity link
(`iam.UserEntity.phoneNumber`, §4 User) rather than a session. Required
building the first real Parent↔Student relationship
(`academic.StudentGuardianEntity`, §4's StudentGuardian note) this codebase
has had — a genuine domain-model gap, not just a UI slice. Chosen explicitly
over building a grades feature next (§11 Q8) because grading scheme
(descriptive vs. numeric) and exam planning are still unresolved product
decisions, while the Bale integration had a clear, buildable scope.

**Phase G — Finance deepening, phase 1 (payments): done.** Manual payment
recording, partial payments, derived (never persisted) invoice status, due
dates, admin-triggered idempotent backfill, and a guardian/student self-view —
see §7 Done and §4's `Invoice` subsection.

**Phase H — Finance deepening, phase 2 (expense management): done.** A new
`ExpenseFacade` alongside `FinanceFacade` in the same `finance` module —
categories, vendors, expenses with a DRAFT→APPROVED→PAID/CANCELLED lifecycle,
receipt attachments (this codebase's first file-upload feature), and
Jalali-period reports. Built from a user-supplied Persian design document; see
§7 Done and §4's `Expense` subsection for what shipped and what was explicitly
scoped out (OCR/AI, budgets, multi-step approval, accounting integration).

**Phase I — Moshavere port, step 1 (`iam` phone/OTP login): done.** The first
of four pieces `MOSHAVERE_PORT_SPEC.md` lays out for porting logic from the
older moshavere codebase — chosen first per that spec's §8 build order
because it's the smallest and unblocks nothing else. A second login factor
for a user with an already-registered phone number, not a new registration
path — see §4's `VerificationCode` subsection and §7 Done. Remaining steps
(`curriculum` → `studyplan` → `exam`) are each a genuinely new module and each
needs its own `new-module` pass before building.

**Phase J — Finance deepening, phase 3 (parent/student self-view + guardian
invoice scoping): done.** `GET /my-invoices` (the page UX_ROADMAP.md's
PARENT/STUDENT section had flagged as a natural next slice) plus the
explicit decision, deferred since the Bale integration (§7 Done, §10), to
scope the admin `GET .../invoices` endpoint to a `PARENT`-only caller's own
linked children — `SCHOOL_ADMIN`/`FINANCE_ADMIN` visibility is unchanged. See
§7 Done for the full detail and test coverage.

**Phase K — Custom role management: done.** Resolves §11 Q3. A `SCHOOL_ADMIN`
can define a school-scoped custom role as a named subset of the existing fixed
permission catalog, via a new `iam.RoleController` + `GET /roles` management
page. `PermissionDecisionService` needed no changes at all — it was already
fully data-driven, so custom roles are enforced by the same generic mechanism
every system role runs through. See §4's Role subsection and §7 Done for the
full detail, the three explicit scoping decisions, and test coverage.

**Phase L — Moshavere port, step 2 (`curriculum` module): done.** New
`curriculum` module (topic taxonomy + question bank) via its own `new-module`
pass — new `SCHOOL`-scoped `CURRICULUM` module code,
`CURRICULUM_MANAGE`/`CURRICULUM_VIEW` permissions (SCHOOL_ADMIN + TEACHER;
students/parents get neither — the bank carries correct answers), subtree
topic-filter semantics preserved from the source. Backend only, no UI — see
§3, §4's Topic/Question subsection, and §7 Done.

**Phase M — Moshavere port, step 3 (`studyplan` module): done.** Per-school
study plans with course weighting, weekly topic schedules, one active
plan-per-student assignment, a student's weekly free-time template, and a
faithful logic port of moshavere's `table_suggester.py` auto-scheduling
algorithm — the third of the four pieces in `MOSHAVERE_PORT_SPEC.md`'s
build order (§8 there), the first new module in this port with two
new-module Facade dependencies (`CurriculumFacade` + `AcademicFacade`) at
once. New `SCHOOL`-scoped `STUDY_PLANNER` module code +
`STUDYPLAN_VIEW`/`STUDYPLAN_MANAGE` permissions (`SCHOOL_ADMIN` +
`TEACHER`, same reasoning as `CURRICULUM_*`). See §4's Plan/WeekCourse/
StudentPlanAssignment/StudyInterval/IntervalSchedule subsection and §7
Done for the full model, the algorithm-port normalizations, and test
coverage (`StudyPlanManagementFlowIntegrationTest`).

**Phase N — Moshavere port, step 4 and last (`exam` module): done.** The
exam engine — auto-generated weekly exams, instructor-authored collective
exams, timed token-scoped exam-taking sessions, on-demand scoring — the
fourth and last piece of `MOSHAVERE_PORT_SPEC.md`'s build order (§8 there),
depending on `curriculum` + `academic` + `studyplan` + `iam`, the deepest
chain in this port (the one place a new module depends on another new
module). New `SCHOOL`-scoped `EXAM` module code + `EXAM_VIEW`/`EXAM_MANAGE`
permissions (`SCHOOL_ADMIN` + `TEACHER`, same reasoning as
`CURRICULUM_*`/`STUDYPLAN_*`); student access runs entirely through the
"my" pattern (`MyExamsController`) plus a third independently-secreted JWT
scoped per exam attempt (`ExamTakeController`), never through either
permission. See §4's Exam/Section/SectionQuestion/ExamAssignment/Attempt/
Submit subsection and §7 Done for the full model, the three deliberate
deviations from the source, and test coverage (`ExamFlowIntegrationTest`).
**This closes out the moshavere port** — see `MOSHAVERE_PORT_SPEC.md` §8.

**Phase O — Grades (resolves §11 Q8): done.** A student's grade in a class
for one term, an extension of `academic` (not a new module). Four explicit
decisions made before building: per-class scheme (`ClassEntity.gradingScheme`,
not per-school or grade-level), manual entry only (not derived from `exam`
results), the real Iranian four-level descriptive scale (not free text), and
per-term cadence (not a running unstructured log). New `GradeController`
(admin gradebook, deliberately gated by `GRADES_EDIT` on both read and write —
`GRADES_VIEW` alone would leak a whole class's grades to any `STUDENT`/
`PARENT` caller) and `MyGradesController` (the "my" pattern, own + wards'
grades). See §4's Grade subsection and §7 Done for the full model and test
coverage (`GradeManagementFlowIntegrationTest`). **No UI yet** — backend +
REST + tests only, same posture `curriculum`/`studyplan` shipped with.

