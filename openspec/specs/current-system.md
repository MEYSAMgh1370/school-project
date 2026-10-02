# Current System Specification

Status: current behavior checked against implementation, tests, migrations, and
configuration on 2026-08-06. The web design and finance-display invariants were
updated and verified on 2026-08-16.

## Product Scope

Roshdeandishe is a multi-tenant school management SaaS for Iranian schools. A school is the tenant. One user account can have memberships and roles at multiple schools.

Current user-facing roles include:

- `SCHOOL_ADMIN`
- `TEACHER`
- `FINANCE_ADMIN`
- `STUDENT`
- `PARENT`

`PLATFORM_ADMIN` exists in the domain discussion as future infrastructure, but there is no complete current platform-admin permission path.

## Identity And Login

Users can register and log in with email/password. The OTP login flow is
implemented for an existing registered phone number, but it cannot complete
outside development: `SmsDriver` has no SMS provider integration, and no
configuration supplies one. See Current Limitations below.

IAM canonicalizes phone numbers at the application boundary before storing or
looking them up:

- boundary whitespace matching Java `Character.isWhitespace` or `Character.isSpaceChar` is stripped;
- Persian and Arabic-Indic digits are translated to ASCII digits;
- punctuation, separators, and country-code formatting are otherwise preserved.

Flyway migrations canonicalize existing `iam.users.phone_number` rows and refuse
to guess ownership when multiple rows would collapse to the same canonical phone
number.

Evidence:

- [`PhoneNumberCanonicalizer.java`](../../src/main/java/com/example/roshdeandishe/iam/internal/PhoneNumberCanonicalizer.java)
- [`SmsDriver.java`](../../src/main/java/com/example/roshdeandishe/iam/internal/SmsDriver.java)
- [`V3__canonicalize_iam_phone_numbers.sql`](../../src/main/resources/db/migration/V3__canonicalize_iam_phone_numbers.sql)
- [`V4__canonicalize_iam_phone_boundary_whitespace.sql`](../../src/main/resources/db/migration/V4__canonicalize_iam_phone_boundary_whitespace.sql)
- [`OtpLoginFlowIntegrationTest.java`](../../src/test/java/com/example/roshdeandishe/OtpLoginFlowIntegrationTest.java)
- [`PhoneNumberCanonicalizationMigrationTest.java`](../../src/test/java/com/example/roshdeandishe/PhoneNumberCanonicalizationMigrationTest.java)

## School Creation And Entitlements

Any authenticated user can create a school through the self-service school creation flow. The creator becomes a `SCHOOL_ADMIN` for that school.

On creation, a school receives free entitlements for:

- `ACADEMIC`
- `FINANCE`

The following modules are catalogued but not granted automatically:

- `CURRICULUM`
- `STUDY_PLANNER`
- `EXAM`
- other future/catalog modules seeded by IAM.

Paid or platform-controlled entitlement changes remain facade-only. There is no public REST workflow for billing or platform operators to grant paid modules.

Acceptance criteria:

- School creation succeeds for an authenticated user.
- The returned school is associated with the creator through an active `SCHOOL_ADMIN` membership.
- ACADEMIC and FINANCE endpoints can be used for the new school when the user has the needed permissions.
- Paid modules are not available unless entitlement is granted through non-REST code paths.

Evidence:

- [`SchoolController.java`](../../src/main/java/com/example/roshdeandishe/school/controller/SchoolController.java)
- [`SchoolFacadeImpl.java`](../../src/main/java/com/example/roshdeandishe/school/internal/SchoolFacadeImpl.java)
- [`IamSeedDataRunner.java`](../../src/main/java/com/example/roshdeandishe/iam/internal/IamSeedDataRunner.java)
- [`2026-07-20-free-tier-entitlements.md`](../decisions/2026-07-20-free-tier-entitlements.md)

## Access Control

Protected school-scoped API endpoints usually require:

- authenticated user;
- `X-School-ID`;
- active school membership;
- module entitlement through `@RequiresModule`;
- permission through `@RequiresPermission`.

Known intentional exceptions:

- public pages and static assets;
- auth/account endpoints;
- `/api/me` and related current-user endpoints;
- Bale webhook endpoints (`/api/bale/webhook`, served by the `notification` module);
- tokenized exam-taking endpoints.

Failure cases:

- missing required tenant header returns an error before controller logic;
- tenant/path mismatch is rejected by controllers that accept both;
- missing module entitlement and missing permission are separate authorization failures.

Evidence:

- [`SecurityConfig.java`](../../src/main/java/com/example/roshdeandishe/iam/config/SecurityConfig.java)
- [`TenantResolverFilter.java`](../../src/main/java/com/example/roshdeandishe/platform/config/TenantResolverFilter.java)
- authorization annotations across module controllers.

## Web UI

The current web UI is server-rendered JTE with page-level Alpine.js components and Bulma styling.

Built page areas include:

- public landing (with login, account-registration, and school-registration entry paths that preserve the approved post-authentication destination), login, registration, account activation, school entry, and exam-take; direct anonymous `/schools/new` entry preserves the same approved destination;
- dashboard (role-gated «دسترسی سریع» shortcut cards, «فرزندان من» for parents, memberships, school-entry box for admins, phone box) and shared navbar with a grouped sidebar;
- people, roles, classes, students, and grades;
- curriculum, study plans, and exams;
- finance, expenses, invoices, and payments;
- student/parent self-service pages for grades, invoices, exams, and study plan.

Current invariants:

- `layout/main.jte` owns shared head assets.
- `layout/navbar.jte` is reused by authenticated management pages: a top bar
  (school/role switcher, user menu) plus a grouped, collapsible sidebar (a
  drawer below 1024px). Page links come from `web.model.NavCatalog`, which also
  drives the dashboard shortcut cards; see
  [`changes/navigation-sidebar-and-dashboard-shortcuts.md`](../changes/navigation-sidebar-and-dashboard-shortcuts.md).
- `layout/page-header.jte`, `layout/school-scope.jte`, and
  `layout/empty-state.jte` are shared partials extracted on 2026-09-27;
  `school-scope.jte` owns the "no school selected" warning plus the error and
  success notice areas for every school-scoped page.
- Static JS and CSS are served from Spring Boot static resources, and the
  Vazirmatn web font is a vendored static asset under `static/fonts/`
  (no WebJar exists for it).
- The web UI uses the Vazirmatn font and the Persian-turquoise primary color
  from `app.css`, and owns its own dark colour scheme. This replaced the
  system font stack and the ink-blue primary on 2026-09-27; `DESIGN.md` is
  authoritative for the visual layer.
- Read-only finance amounts use neutral Persian digits and thousands grouping,
  without a Rial/Toman suffix or numeric conversion because the canonical
  financial unit is unresolved.
- Finance amount inputs normalize Persian and Arabic-Indic digits to ASCII
  during editing and again before request serialization.
- Page links are navigation affordances only; API authorization is authoritative.

Evidence:

- [`src/main/jte/`](../../src/main/jte/)
- [`src/main/resources/static/js/`](../../src/main/resources/static/js/)
- [`src/main/resources/static/css/app.css`](../../src/main/resources/static/css/app.css)
- [`src/main/resources/static/js/money.js`](../../src/main/resources/static/js/money.js)
- [`FRONTEND.md`](../../FRONTEND.md)

## Android V1

The native Android app exists as a separate Gradle/Kotlin/Compose project. Current behavior is a thin read-only student shell:

- authenticate with backend JWT token endpoint;
- persist auth state locally;
- read current user profile;
- list memberships;
- load current student enrollment for the first active student membership.

Current limitations:

- no write workflows;
- no push notification provider;
- no refresh-token flow;
- base URL is currently hardcoded for emulator/local development;
- richer native grades, homework, exams, messaging, and video workflows remain future work.

Evidence:

- [`android/README.md`](../../android/README.md)
- [`android/app/build.gradle.kts`](../../android/app/build.gradle.kts)
- Android source files under `android/app/src/main/java/`.

## Finance And Academic

Academic currently owns classes, students, guardians, grades, and the global course catalog. Finance owns tuition fees, invoices, payments, school expenses, expense attachments, and finance audit behavior.

Grades can be recorded per course (`openspec/changes/grades-by-course.md`): `academic.courses` is a global, per-grade-level catalog (no `school_id`), a grade's `course_id` is optional, and a grade without a course is the overall grade («نمرهٔ کل»; all grades recorded before this feature). Per (student, class, year, term) there is at most one overall grade and one grade per course (two partial unique indexes, migration `V5`). A course must exist and match the class's `grade_level`. `GET /api/admin/schools/{schoolId}/grades` without `courseId` returns only overall grades; `GET .../grades/courses?classId=` lists the class's grade-level courses. `/grades` has a «درس» select (default «نمرهٔ کل»), `/my-grades` a «درس» column. The catalog is read-only in the application: no endpoint or screen creates or edits courses, and none are seeded; rows are managed by the platform operator through data migrations or SQL until a platform actor exists.

Cross-module references are intentionally opaque IDs at module boundaries. When a UI needs names for IDs from another module, the established web pattern is client-side composition through multiple REST calls, unless a backend facade already owns the composition.

Evidence:

- `academic` and `finance` package controllers and facades.
- `Finance*FlowIntegrationTest`, `Expense*FlowIntegrationTest`, `Grade*FlowIntegrationTest`, and related tests.

## Current Limitations

These are not bugs unless a change proposal says otherwise:

- the course catalog (see Finance And Academic) has no administration screen or API and ships empty, because the platform-actor permission model it depends on does not exist yet and the list of courses per grade level is a pending product input;
- no platform-admin actor model or platform permission path — a
  `PLATFORM_ADMIN` role row is seeded, but it is reserved at both boundaries:
  `listRoles` keeps the code out of role pickers, and `createMembership`
  rejects it with the same `RoleNotFound` an undefined code produces, so it
  cannot be assigned. The role also has no permission grants, and
  `PermissionDecisionService` only decides school contexts, so it would confer
  nothing even if a row existed. Whether such rows already exist in the
  deployed database is unverified — see
  [`changes/platform-admin-not-assignable.md`](../changes/platform-admin-not-assignable.md);
- no billing/upgrade UI or REST endpoint for paid entitlements;
- no horizontal web-session scaling support;
- no SMS provider integration exists in `SmsDriver`, so OTP delivery cannot be
  enabled by configuration — a nonblank `roshdeandishe.sms.api-key` still
  reports failure, and `requestOtp` then fails closed;
- OTP rate limiting is not implemented;
- the canonical persisted, entry, and display unit for finance values, plus
  any Rial/Toman conversion and rounding rules, is not defined;
- slug editing, custom domains, and subdomain routing are not implemented;
- school status changes are facade-only;
- native Android app is read-only and local-development oriented.
