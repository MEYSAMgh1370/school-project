# Roshdeandishe — CLAUDE.md

This file is the source of truth for all architectural, domain, and product decisions.
When in doubt, follow what is written here. Do not invent alternatives or reopen resolved
decisions.

This file replaces an earlier draft that sketched a different identity model
(`Person`/`User`/`RoleGrant`, `kernel/school/optional` packages, `ir.roshdeandishe`,
UUID ids). That draft was never implemented. What follows documents the model that
was actually built and is now the resolved architecture. **`RoleGrant` and a
`Person`/`User` split remain explicitly rejected** — see §10.

See [`BEST_PRACTICES.md`](BEST_PRACTICES.md) for working discipline and Java/Spring
conventions — it applies to every coding task in this repo, same as this file.

**Before creating any new module** (new top-level package under
`com.example.roshdeandishe` with its own Facade), you MUST invoke the
`new-module` skill first, even if the request doesn't use the word "module" —
this covers phrasing like "add Library support" or "build out Attendance."

**Before creating or substantially restructuring any web page** (a new `.jte`
page template, a new page controller in `web`, or reshaping an existing
page's layout/flow), you MUST invoke the `design-page` skill first — even if
the request doesn't use the word "page" ("add a screen for X", "let admins
manage Y"). Visual rules live in [`DESIGN.md`](DESIGN.md) (subordinate to
this file and `UX_ROADMAP.md` §2); token *values* live in the `:root` block
of `src/main/resources/static/css/app.css`.

---

## 1. What This Product Is

**Roshdeandishe is a multi-tenant SaaS school management system for Iranian schools.**

It serves four user-facing roles — admin, teacher, student, parent — built on top of
one underlying identity. The platform supports two subscription models at once:

- **B2B**: a school subscribes to a module (Academic, Finance, Library, Attendance,
  Transportation) and the feature becomes available to everyone at that school,
  subject to their role's permissions.
- **B2C**: an individual subscribes to a module independently of any school
  (AI Tutor, Exam Preparation, Portfolio Builder, Teacher Marketplace, Professional
  Development Courses).

Scope is **schools only** — not generic multi-tenant educational organizations, and
not solo/independent teachers as a parallel org type.

School creation is **self-service, not admin-gated**: any authenticated user can
create a school and automatically becomes its `SCHOOL_ADMIN` in the same call. This
was a deliberate choice to avoid needing `PLATFORM_ADMIN` permission infrastructure
(which doesn't exist yet — see §7) just to onboard a new tenant. Genuinely
platform-privileged actions (granting a school paid modules, suspending a school)
stay facade-only, with no REST endpoint, until real platform-permission
infrastructure and a real actor (ops tooling or a billing webhook) exist. Do not
expose those two operations over HTTP without that check in place first.

The project has no production data yet — this allowed the IAM/entitlement model to be
rebuilt big-bang (in place, no migration scaffolding) when the original design proved
too narrow.

---

## 2. Architecture

- **Style:** Spring Boot Modular Monolith, enforced by Spring Modulith
- **Base package:** `com.example.roshdeandishe`
- **Communication between modules:** primarily through public Facade interfaces
  (synchronous, in-process); Spring Modulith events are used where a module needs to
  react to something happening in another module without a direct call (e.g. finance
  reacting to a student being enrolled in academic, or finance's own audit trail
  reacting to an invoice being issued)
- **Multi-tenancy:** `TenantContext` (ThreadLocal) + `TenantResolverFilter`, both in
  `platform` — populated from the `X-School-ID` request header
- **Authentication:** session-based (`HttpSession`) for the web app, resolved — see
  §7 and the former Open Question in §11 history. **Additive JWT bearer-token
  auth also exists** (`POST /api/auth/token`, `iam.shared.JwtTokenProvider` +
  `JwtAuthenticationFilter`), added for the native Android student app (§7,
  §11 Q1 update) — session auth is unchanged and unaffected; the two mechanisms
  coexist in one `SecurityFilterChain`, distinguished purely by whether a request
  carries an `Authorization: Bearer` header. Spring Security is used for
  authentication only; authorization stays entirely with the two-layer
  `@RequiresModule` / `@RequiresPermission` mechanism in §5, identically for both
  auth mechanisms, since both leave `GrantedAuthority`/`hasRole()` deliberately
  empty — roles are per-school, not global per principal.
- **ID strategy:** `Long` / `GenerationType.IDENTITY` everywhere (not UUID — a
  deliberate choice to match the project's existing convention; revisit only if a
  concrete need for non-sequential, externally-safe IDs arises)
- **Deployment:** single deployable application; module boundaries are enforced by
  package + `ApplicationModules.verify()`, not by separate build artifacts. The whole
  project is **one Maven artifact**, not a multi-module reactor — Spring Modulith
  detects module boundaries by package, and a multi-module Maven split would break
  illegal-dependency detection and the Event Publication Registry.
- **Architecture documentation:** generated, never hand-drawn —
  `ModularityTests.writeDocumentationSnippets()` (Spring Modulith `Documenter`)
  writes C4/PlantUML diagrams + per-module AsciiDoc canvases to
  `target/spring-modulith-docs/` on every test run, derived from the same
  verified model as `verifyModularStructure()`. Not committed; regenerate with
  `mvn test -Dtest=ModularityTests`.
- **API documentation:** springdoc-openapi (Swagger UI at `/swagger-ui.html`,
  `/v3/api-docs`). Both are `permitAll` in `SecurityConfig` and excluded from the
  `X-School-ID` requirement in `TenantResolverFilter` — documentation endpoints, not
  data, so left open even though everything else requires authentication.
- **Frontend:** JTE (precompiled, type-checked Java templates — chosen over
  Thymeleaf specifically for compile-time template safety, matching this project's
  existing bias toward compile-time checks like sealed error types) + Alpine.js for
  light client-side interactivity. Both delivered as WebJars (`org.webjars.npm`),
  not a CDN `<script>` tag and not an npm/webpack/vite build — stays a zero-build
  addition consistent with "one Maven artifact." Templates live in `src/main/jte/`
  — ten page templates (`landing.jte`, `activate.jte`, `login.jte`,
  `register.jte`, `dashboard.jte`, `people.jte`, `school-new.jte`,
  `classes.jte`, `students.jte`, `finance.jte`), all wrapped
  in the shared **`layout/main.jte`** (`title`, `gg.jte.Content content`, and
  an optional `scripts` param — `List<String>` of `/js/...` paths). The layout
  is the *only* place that owns the `<head>` (WebJar stylesheet/script tags +
  the global `/css/app.css`); a page needing a page-specific asset adds an
  entry to `scripts`, never its own duplicated `<head>`. Each `scripts` entry
  is emitted *before* the Alpine `<script defer>` tag on purpose: deferred
  scripts run in document order, and `Alpine.data` registrations must exist
  before `alpine:init` fires. Bulma is used for CSS, also as a WebJar, no
  custom CSS framework/pipeline.
  **Frontend code-placement rule (explicit user decision, 2026-07-05):** Bulma
  classes and small Alpine expressions stay inline in the template; anything with
  `fetch`, a regex, or more than ~10 lines of JS moves to a static file (under
  `src/main/resources/static/js/`) as an `Alpine.data` component; custom CSS is
  always an external file, never inline `<style>`. `src/main/resources/static/js/`
  holds `school-new.js` (`createSchoolForm` — self-service school creation, §1,
  moved off the dashboard onto its own page 2026-07-06 — see §7 Done), `navbar.js`
  (the shared school/role switcher, see below), `people.js`, `classes.js`,
  `students.js`, `finance.js`, and `expenses.js` (one `Alpine.data` component per
  management page, see `web` module below), plus `jalali.js` (a standalone,
  dependency-free Jalali/Gregorian date conversion helper used only by
  `expenses.js`'s report-period picker — not an `Alpine.data` component itself);
  `src/main/resources/static/css/app.css` is the one global
  stylesheet so far (holds the `[x-cloak]` rule, used to keep closed
  `x-show`-gated forms from flashing open before Alpine initializes). `/js/**`
  and `/css/**` are `permitAll` in `SecurityConfig` and excluded in
  `TenantResolverFilter`. `login`/`register`/`activate` predate this rule and
  keep their inline components until next touched — apply the rule when writing
  new pages or meaningfully editing existing ones. View-rendering `@Controller`s
  (as opposed to the
  `@RestController`s everywhere else) live in two places, split by concern:
  pure auth/identity pages stay in `iam.shared`
  (`AccountActivationPageController`, `LoginPageController`,
  `RegisterPageController`), while any page
  that composes data across modules lives in the dedicated **`web` module**
  (`DashboardPageController`, `PeoplePageController` — memberships from iam +
  school names from school). This resolved the former UI-composition open point: `web` is a
  pure view-composition layer — it consumes other modules' Facades, owns no
  entities/Facade/REST of its own, and nothing depends on it. Do not put
  cross-module page controllers in `iam` (iam→school would be a dependency
  cycle), and do not put REST endpoints in `web`. The school/role switcher
  navbar is a **shared JTE template** (`layout/navbar.jte`, fed by
  `web.NavbarModelAssembler`) reused by every management page — see `web`
  module below and [`UX_ROADMAP.md`](UX_ROADMAP.md) for the full page-by-page
  plan and the shared UX conventions every new management page must follow.

### Module boundary rules (enforced by `ModularityTests.verifyModularStructure()`)

- Every module's `internal/` package is invisible outside that module — Spring
  Modulith's package-naming convention hides it automatically.
- A module's `shared/` package is invisible by default too, unless explicitly exposed
  with `@NamedInterface("...")`. **This applies per-package, not recursively** — a
  sub-package of an already-exposed `shared` package (e.g. `iam.shared.exception`)
  needs its own `@NamedInterface` if other modules need to reach into it.
- The only thing other modules may depend on is a module's public Facade interface
  (e.g. `IamFacade`) and any explicitly-named-interface package.
- `platform` is a special module: every other module may depend on it, but it must
  never depend back on `iam`, `academic`, `finance`, or `school`. This is what keeps
  it from becoming a second hidden coupling point. `ApplicationModules.verify()`
  enforces this at test time, not just by convention.
- **REST controllers live at a module's root package**, next to its Facade (e.g.
  `iam.MembershipController`, `school.SchoolController`, `academic.ClassController`)
  — not in `internal/` or `shared/`. This is the established convention as of the
  admin REST layer; follow it for new controllers.

---

## 3. Module Structure

```
com.example.roshdeandishe
├── platform/                  ← shared kernel, no domain knowledge, depends on nothing
│   ├── ProblemDetailFactory.java
│   ├── PlatformExceptionHandler.java   (generic 500 fallback, @Order(MAX_VALUE))
│   ├── TenantContext.java
│   ├── TenantResolverFilter.java       (also excludes /api/auth, swagger paths,
│   │                                     and precisely /api/admin/schools[/{id}]
│   │                                     via regex — see school module below)
│   ├── Loggable.java
│   ├── LoggingAspect.java             (@Order(1), outermost aspect)
│   └── OpenApiConfig.java             (Swagger/OpenAPI metadata only)
│
├── iam/                        ← identity, access control, authentication. Owns
│   ├── IamFacade.java              User, Role, Permission, SchoolMembership,
│   ├── AccessContext.java          Module, Entitlement
│   ├── AccessDecision.java         (sealed: SchoolContext | PlatformContext /
│   │                                 Granted | Denied)
│   ├── MembershipController.java   (REST: create/list memberships, toggle active,
│   │                                 issue invite-token —
│   │                                 @RequiresPermission("MEMBERSHIP_MANAGE"))
│   ├── RoleController.java         (REST: GET permissions (full fixed catalog),
│   │                                 GET/POST roles (system + this school's
│   │                                 custom roles; POST creates a new custom
│   │                                 role) — @RequiresPermission("ROLE_MANAGE"),
│   │                                 SCHOOL_ADMIN only, same sensitivity tier
│   │                                 as MEMBERSHIP_MANAGE. Built 2026-07-13,
│   │                                 resolving §11 Q3 — see §4's Role
│   │                                 subsection for the three explicit
│   │                                 decisions behind it)
│   ├── MeController.java           (REST: GET /api/me, /api/me/memberships,
│   │                                 PUT /api/me/phone — authenticated-only,
│   │                                 deliberately NO permission/module
│   │                                 annotations and NO X-School-ID: it's about
│   │                                 the user, not a school. The "list my
│   │                                 memberships" endpoint Open Question 2
│   │                                 anticipated. PUT /api/me/phone
│   │                                 (IamFacade.setPhoneNumber) is the ONLY way
│   │                                 phoneNumber gets set — deliberately
│   │                                 requires an authenticated session, not a
│   │                                 new unauthenticated identity-claim path;
│   │                                 see the bale module below for why.)
│   ├── internal/
│   │   ├── UserEntity / UserRepository        (now includes nullable passwordHash
│   │   │                                        and nullable, unique phoneNumber —
│   │   │                                        an additional identity attribute,
│   │   │                                        not a login credential; only settable
│   │   │                                        via MeController.setPhoneNumber above)
│   │   ├── InviteTokenEntity / InviteTokenRepository  (per-user, single-use,
│   │   │                                        7-day TTL — backs account activation)
│   │   ├── InviteEmailSender          (best-effort SMTP delivery of the
│   │   │                                activation link; deliberately in
│   │   │                                iam/internal, not platform — only iam
│   │   │                                sends email, no generic abstraction)
│   │   ├── RoleEntity / RoleRepository (schoolId nullable — null = system
│   │   │                                role, non-null = a specific school's
│   │   │                                custom role; unique(code, school_id),
│   │   │                                see §4's Role subsection)
│   │   ├── PermissionEntity / PermissionRepository
│   │   ├── RolePermissionEntity / RolePermissionRepository
│   │   ├── SchoolMembershipEntity / SchoolMembershipRepository
│   │   ├── ModuleEntity (+ ModuleScope enum) / ModuleRepository
│   │   ├── SchoolEntitlementEntity / SchoolEntitlementRepository
│   │   ├── UserEntitlementEntity / UserEntitlementRepository
│   │   ├── AccessDecisionService      (module-level: is this entitled at all?)
│   │   ├── PermissionDecisionService  (role-level: does this role allow this
│   │   │                                action? — only SchoolContext supported;
│   │   │                                PlatformContext always denies, see §7)
│   │   ├── IamUserDetailsService       (Spring Security UserDetailsService;
│   │   │                                empty authorities — see Architecture above)
│   │   ├── IamFacadeImpl
│   │   └── IamSeedDataRunner          (dev-only seed, incl. one demo admin user
│   │                                    with a working password + their
│   │                                    SCHOOL_ADMIN membership and the demo
│   │                                    school's ACADEMIC+FINANCE entitlements;
│   │                                    replace w/ Flyway pre-prod)
│   └── shared/
│       ├── RequiresModule.java + ModuleAccessAspect.java     (@Order(10))
│       ├── RequiresPermission.java + PermissionAccessAspect.java (@Order(11))
│       ├── SecurityConfig.java        (session auth config for web, PLUS the
│       │                                additive JwtAuthenticationFilter registration
│       │                                for mobile — one SecurityFilterChain, not two;
│       │                                lives here, not platform, because it wires
│       │                                IamUserDetailsService)
│       ├── JwtTokenProvider.java       (package-private: generate/validate mobile
│       │                                JWTs. Carries only the email claim — same
│       │                                principal shape session auth already uses
│       │                                (Authentication.getName()); IamFacade.currentUserId()
│       │                                resolves userId from that email either way, so
│       │                                no UserRepository dependency is needed here,
│       │                                which is why this lives in iam.shared, not
│       │                                iam.internal — package-private classes in
│       │                                internal aren't visible to shared at the Java
│       │                                level, only the public IamFacade is)
│       ├── JwtAuthenticationFilter.java (OncePerRequestFilter, additive to session
│       │                                auth: no-ops if there's no Authorization: Bearer
│       │                                header, so web login is untouched. Deliberately
│       │                                NOT @Component — constructed manually inside
│       │                                SecurityConfig and added via addFilterBefore,
│       │                                so Boot doesn't also auto-register it as a
│       │                                second, duplicate servlet-container filter)
│       ├── AuthController.java        (POST /api/auth/login, /api/auth/logout,
│       │                                /api/auth/activate, /api/auth/register, PLUS
│       │                                /api/auth/token (mobile JWT issuance, shares
│       │                                the same authenticate() credential check as
│       │                                login — both now map a failed
│       │                                AuthenticationException to IamError.InvalidCredentials
│       │                                → 401, fixed together since /api/auth/token would
│       │                                otherwise have inherited login's pre-existing
│       │                                500-on-bad-credentials gap) — JSON, not Spring's
│       │                                default form login; activate redeems an invite
│       │                                token to set a first password; register is public
│       │                                self-service sign-up → IamFacade.registerUser.
│       │                                /api/auth/token is also explicitly CSRF-exempt
│       │                                in SecurityConfig, unlike /api/auth/login: its
│       │                                response is a JWT in a JSON body, not a cookie,
│       │                                so CSRF's cross-origin-cookie-riding threat model
│       │                                doesn't apply the way it does to login)
│       ├── AccountActivationPageController.java   (GET /activate — renders
│       │                                src/main/jte/activate.jte, a JTE+Alpine.js
│       │                                +Bulma page whose JS calls AuthController's
│       │                                /api/auth/activate)
│       ├── LoginPageController.java   (GET /login — renders login.jte; its JS
│       │                                posts to /api/auth/login then goes to
│       │                                /dashboard; links to /register and /activate)
│       ├── RegisterPageController.java (GET /register — renders register.jte; its
│       │                                JS posts to /api/auth/register then
│       │                                auto-logs-in via /api/auth/login and goes
│       │                                to /dashboard)
│       └── exception/                 (@NamedInterface("shared.exception") — own
│           ├── IamError.java (sealed)   NamedInterface, does NOT inherit iam.shared's)
│           ├── IamException.java
│           ├── IamProblemResolver.java
│           └── IamExceptionHandler.java   (bean name "iamGlobalExceptionHandler")
│
├── school/                     ← owns School itself (name, status, timezone,
│   ├── SchoolFacade.java           locale). Deliberately separate from iam — iam
│   ├── SchoolController.java       only needs schoolId as an opaque key, not
│   ├── internal/                   school metadata.
│   │   ├── SchoolEntity / SchoolRepository
│   │   ├── SchoolFacadeImpl
│   │   └── SchoolSeedDataRunner    (dev-only: seeds one demo school)
│   └── shared/
│       ├── SchoolError.java (sealed) + SchoolException.java
│       └── SchoolExceptionHandler.java   (bean name "schoolExceptionHandler")
│
├── academic/                   ← students, classes, enrollment
│   ├── AcademicFacade.java
│   ├── StudentEnrolledEvent.java   (fields: schoolId, studentId, classId — NOT
│   │                                 gradeLevel; superseded by real ClassEntity)
│   ├── ClassController.java        (REST: create (CLASS_MANAGE, SCHOOL_ADMIN
│   │                                 only) / list (CLASS_VIEW, SCHOOL_ADMIN +
│   │                                 TEACHER) classes — class-level
│   │                                 @RequiresModule("ACADEMIC"), per-method
│   │                                 @RequiresPermission)
│   ├── StudentController.java      (REST: enroll/get/list students —
│   │                                 class-level @RequiresModule("ACADEMIC") +
│   │                                 @RequiresPermission("STUDENT_ENROLL"),
│   │                                 SCHOOL_ADMIN + TEACHER. Also POST+GET
│   │                                 .../students/{studentId}/guardians —
│   │                                 @RequiresPermission("GUARDIAN_MANAGE"),
│   │                                 SCHOOL_ADMIN only, see §4's Guardian note)
│   ├── MyEnrollmentController.java (REST: GET /api/me/enrollment?schoolId= —
│   │                                 "what class am I in at this school", for the
│   │                                 native Android student app's read-only shell.
│   │                                 Deliberately its own controller, not a method
│   │                                 on StudentController: same "about the caller,
│   │                                 not a school-wide capability" trust model as
│   │                                 iam.MeController — no @RequiresPermission/
│   │                                 @RequiresModule, scoped only to
│   │                                 iamFacade.currentUserId(), never a caller-supplied
│   │                                 userId. Lives in academic (not iam, next to
│   │                                 MeController) because iam cannot depend on
│   │                                 academic — same bale-module precedent (§4's
│   │                                 BaleLink note) of composing IamFacade +
│   │                                 AcademicFacade to answer "what can I tell this
│   │                                 identified user about their own data" without a
│   │                                 reverse module dependency. 204, not an exception,
│   │                                 when the caller isn't a student at that school.)
│   ├── internal/
│   │   ├── ClassEntity (schoolId, name, teacherId nullable, section) / ClassRepository
│   │   ├── StudentEntity (userId, schoolId, classId, enrolledAt) / StudentRepository
│   │   │   — StudentEntity is NOT an identity; the person is the iam UserEntity.
│   │   │     enrollStudent() internally calls IamFacade.findOrCreateUser +
│   │   │     createMembership(..., "STUDENT") before creating the StudentEntity.
│   │   ├── StudentGuardianEntity (schoolId, studentId — real FK within this
│   │   │                           module, guardianUserId — opaque Long, same
│   │   │                           convention as StudentEntity.userId, linkedAt)
│   │   │                           / StudentGuardianRepository — see §4
│   │   └── AcademicFacadeImpl
│   └── shared/
│       ├── AcademicError.java (sealed: StudentNotFound, ClassNotFound,
│       │                        GuardianNotEligible)
│       ├── AcademicException.java
│       └── AcademicExceptionHandler.java   (bean name "academicGlobalExceptionHandler")
│
├── curriculum/                 ← hierarchical topic taxonomy + multiple-choice
│   ├── CurriculumFacade.java       question bank (moshavere port, step 2 —
│   │                                 MOSHAVERE_PORT_SPEC.md §4). Content is
│   │                                 per-school, never a shared platform library
│   │                                 (spec §2). No audit trail, no events — pure
│   │                                 CRUD + read composition so far.
│   ├── QuestionDifficulty.java     (enum EDUCATIONAL|EASY|MEDIUM|HARD|VERY_HARD —
│   │                                 port of moshavere's 0/10/20/30/40 int scale;
│   │                                 nullable on a question, same as source)
│   ├── TopicController.java        (REST: POST topics (CURRICULUM_MANAGE) /
│   │                                 GET topics + GET topics/tree
│   │                                 (CURRICULUM_VIEW) — class-level
│   │                                 @RequiresModule("CURRICULUM"), per-method
│   │                                 @RequiresPermission, TenantContext.requireMatches
│   │                                 everywhere)
│   ├── QuestionController.java     (REST: POST questions (CURRICULUM_MANAGE) /
│   │                                 GET questions?topicId=&difficulty=…
│   │                                 (CURRICULUM_VIEW) — topicId filter includes
│   │                                 the topic's WHOLE SUBTREE (descendants),
│   │                                 the one behavior deliberately preserved
│   │                                 from moshavere's QuestionFilter)
│   ├── internal/
│   │   ├── TopicEntity (schoolId, parentId nullable self-ref as plain Long,
│   │   │                 name, sortOrder) / TopicRepository — plain adjacency
│   │   │                 list, deliberately NOT moshavere's dual ltree+pickled
│   │   │                 tree (spec §4/§7); subtree/tree reads are in-memory
│   │   │                 traversals over one school's topics. No topic-move
│   │   │                 operation exists, so cycles are structurally impossible
│   │   │                 (a new topic's parent must already exist).
│   │   ├── QuestionEntity (schoolId, topicId required — unlike source's nullable,
│   │   │                    a wart not worth porting — text + choice1..4 +
│   │   │                    answer as Postgres text columns, correctChoice 1–4,
│   │   │                    difficulty, discriminationIndex) / QuestionRepository
│   │   │                    — `answer` (explanation of the correct choice) was
│   │   │                    in the moshavere source but missing from the spec's
│   │   │                    field list; ported deliberately.
│   │   └── CurriculumFacadeImpl
│   └── shared/
│       ├── CurriculumError.java (sealed: TopicNotFound, InvalidTopic,
│       │                          InvalidQuestion — a topic belonging to another
│       │                          school is TopicNotFound, same convention as
│       │                          academic's requireClassExists)
│       ├── CurriculumException.java
│       └── CurriculumExceptionHandler.java   (bean name "curriculumExceptionHandler")
│
├── finance/                    ← tuition, invoices, payments, expenses, audit.
│                                  Two public Facades at the module root
│                                  (FinanceFacade for tuition/invoices/payments,
│                                  ExpenseFacade for the school's own outgoing
│                                  spending) — an extension of this module, not
│                                  a new one; both stay behind the same FINANCE
│                                  entitlement and finance schema.
    ├── FinanceFacade.java           (issueTuitionInvoice — real persistence;
    │                                  amount comes from the school's TuitionFee.
    │                                  Plus setTuitionFee / getTuitionFee /
    │                                  listInvoices / listInvoicesForStudents
    │                                  (guardian/student self-view support) /
    │                                  recordPayment / listPayments /
    │                                  backfillInvoices)
    ├── FinanceController.java       (REST: PUT+GET tuition-fee, GET invoices,
    │                                  POST/GET invoices/{id}/payments, POST
    │                                  invoices/backfill — first controller
    │                                  carrying BOTH §5 layers:
    │                                  @RequiresModule("FINANCE") class-level +
    │                                  @RequiresPermission per method. GET
    │                                  invoices now also depends on IamFacade +
    │                                  AcademicFacade (2026-07-13, explicit
    │                                  decision — see §4's StudentGuardian note):
    │                                  a caller whose only active role at the
    │                                  school is PARENT sees only their linked
    │                                  wards' invoices; SCHOOL_ADMIN/
    │                                  FINANCE_ADMIN keep full school-wide
    │                                  visibility)
    ├── MyInvoicesController.java    (GET /api/me/schools/{schoolId}/invoices —
    │                                  same trust model as iam.MeController/
    │                                  academic.MyEnrollmentController: no
    │                                  @RequiresPermission/@RequiresModule,
    │                                  scoped only to the caller's own
    │                                  enrollment + StudentGuardianEntity-linked
    │                                  wards, composed via AcademicFacade. The
    │                                  path/header split explained there is
    │                                  unchanged; only the admin endpoint's own
    │                                  scoping changed, see FinanceController
    │                                  above)
    ├── ExpenseController.java       (REST: expense-categories, vendors,
    │                                  expenses (CRUD + approve/pay/cancel
    │                                  transitions), attachments (multipart
    │                                  upload/list/download/delete), summary +
    │                                  finance-summary reports — same
    │                                  @RequiresModule("FINANCE") class-level +
    │                                  per-method @RequiresPermission pattern,
    │                                  every method calls
    │                                  TenantContext.requireMatches)
    ├── InvoiceIssuedEvent.java      (schoolId, studentId, amount, actorUserId
    │                                  nullable, occurredAt)
    ├── PaymentRecordedEvent.java    (schoolId, invoiceId, studentId, amount,
    │                                  actorUserId nullable, occurredAt)
    ├── ExpenseStatusChangedEvent.java  (schoolId, expenseId, previousStatus
    │                                  nullable (null = created), newStatus,
    │                                  amount, actorUserId nullable, occurredAt —
    │                                  one event for every lifecycle transition,
    │                                  not four, since the only consumer so far
    │                                  is the audit listener switching on
    │                                  newStatus)
    ├── InvoiceStatus.java           (enum PAID | PARTIAL | UNPAID | OVERDUE —
    │                                  derived at read time from amount/
    │                                  amountPaid/dueDate, never persisted: a
    │                                  stored column could go stale as time
    │                                  passes without any mutation (OVERDUE))
    ├── ExpenseStatus.java           (enum DRAFT | APPROVED | PAID | CANCELLED —
    │                                  unlike InvoiceStatus this genuinely is
    │                                  persisted, since transitions are explicit
    │                                  human actions, not derivable from other
    │                                  columns)
    ├── PaymentMethod.java           (enum CASH | CARD | BANK_TRANSFER | CHEQUE
    │                                  | OTHER — shared by both PaymentEntity
    │                                  (tuition) and ExpenseEntity; one enum,
    │                                  not two identical copies)
    ├── internal/
    │   ├── TuitionFeeEntity / TuitionFeeRepository   (one row per school —
    │   │                                  unique school_id; the pricing source)
    │   ├── InvoiceEntity / InvoiceRepository   (persisted invoices; gained
    │   │                                  dueDate (issuedAt + configurable
    │   │                                  roshdeandishe.finance.default-due-days,
    │   │                                  default 14) and amountPaid (running
    │   │                                  total kept in sync inside
    │   │                                  recordPayment's transaction) — status
    │   │                                  itself stays derived, see
    │   │                                  InvoiceStatus above)
    │   ├── PaymentEntity / PaymentRepository   (a manually-recorded payment
    │   │                                  against an invoice — partial/
    │   │                                  installment payments allowed;
    │   │                                  overpayment past the invoice amount
    │   │                                  is rejected outright, no credit
    │   │                                  balances. No online payment gateway
    │   │                                  exists — recording is always manual,
    │   │                                  admin/finance-admin only)
    │   ├── ExpenseCategoryEntity / ExpenseCategoryRepository   (per-school,
    │   │                                  unique (school_id, name); a school
    │   │                                  can define its own alongside the 14
    │   │                                  standard Persian categories seeded
    │   │                                  on demand via
    │   │                                  ExpenseFacade.seedDefaultCategories —
    │   │                                  idempotent by name, not a global table)
    │   ├── VendorEntity / VendorRepository   (per-school; only name required,
    │   │                                  phone/nationalId/economicCode/address
    │   │                                  optional)
    │   ├── ExpenseEntity / ExpenseRepository   (a school expense: date,
    │   │                                  amount, required categoryId,
    │   │                                  optional vendorId — both opaque
    │   │                                  Longs with facade-level tenant
    │   │                                  validation, same convention as
    │   │                                  teacherId/studentId elsewhere —
    │   │                                  paymentMethod, status, description,
    │   │                                  receipt/invoice/document numbers,
    │   │                                  createdByUserId nullable. No hard
    │   │                                  delete — CANCELLED is the only way
    │   │                                  to void one. ExpenseRepository also
    │   │                                  carries the three report
    │   │                                  aggregation queries (by category/
    │   │                                  vendor/paymentMethod) plus the
    │   │                                  income-comparison total — all fixed
    │   │                                  to count only APPROVED+PAID, never
    │   │                                  DRAFT or CANCELLED)
    │   ├── ExpenseAttachmentEntity / ExpenseAttachmentRepository   (metadata
    │   │                                  only — fileName, contentType,
    │   │                                  sizeBytes, uploadedByUserId nullable)
    │   ├── ExpenseAttachmentContentEntity / ExpenseAttachmentContentRepository
    │   │                                  (the actual bytes, in their own
    │   │                                  table so listing attachments never
    │   │                                  loads a blob — this project has no
    │   │                                  bytecode enhancement configured for
    │   │                                  lazy @Basic loading. Plain byte[],
    │   │                                  deliberately NOT @Lob: Hibernate 6+
    │   │                                  maps plain byte[] to Postgres
    │   │                                  bytea correctly; @Lob would produce
    │   │                                  oid large objects instead, which
    │   │                                  break dump/restore. 5MB domain cap
    │   │                                  enforced in ExpenseFacadeImpl, below
    │   │                                  the servlet's multipart limits in
    │   │                                  application.yml so an oversize file
    │   │                                  gets a proper 400 ProblemDetail
    │   │                                  instead of a container-level 500)
    │   ├── StudentEnrolledEventListener   (reacts to academic's enrollment event,
    │   │                                    calls issueTuitionInvoice — amount now
    │   │                                    resolved from TuitionFee internally)
    │   ├── FinanceAuditEntity / FinanceAuditRepository   (append-only audit trail;
    │   │                                  gained a nullable expense_id column
    │   │                                  alongside the existing nullable
    │   │                                  student_id — one shared timeline for
    │   │                                  every financial action at a school,
    │   │                                  not two half-tables)
    │   └── FinanceAuditEventListener      (@ApplicationModuleListener; one
    │                                        listener method per event type —
    │                                        InvoiceIssuedEvent → INVOICE_ISSUED,
    │                                        PaymentRecordedEvent →
    │                                        PAYMENT_RECORDED,
    │                                        ExpenseStatusChangedEvent →
    │                                        EXPENSE_RECORDED/APPROVED/PAID/
    │                                        CANCELLED depending on newStatus)
│   └── shared/
│       ├── FinanceError.java (sealed: TuitionFeeNotSet, InvalidTuitionFee,
│       │                       InvoiceNotFound, InvalidPaymentAmount,
│       │                       PaymentExceedsBalance, ExpenseNotFound,
│       │                       ExpenseCategoryNotFound, VendorNotFound,
│       │                       InvalidExpense, ExpenseNotEditable,
│       │                       InvalidExpenseStatusTransition,
│       │                       AttachmentNotFound, InvalidAttachment)
│       ├── FinanceException.java
│       ├── FinanceProblemResolver.java   (extracted once the sealed switch
│       │                       passed the "trivial number of cases" threshold
│       │                       in §6 — same pattern as
│       │                       iam.shared.exception.IamProblemResolver)
│       └── FinanceExceptionHandler.java   (bean name "financeExceptionHandler";
│                                            now just routes to
│                                            FinanceProblemResolver)
│
├── web/                        ← UI-composition layer (see §2 Frontend): page
    ├── LandingPageController.java     controllers that need more than one
    ├── DashboardPageController.java   module's Facade. GET / (public landing
    ├── SchoolRegistrationPageController.java  page — see below) composes iam
    ├── PeoplePageController.java      memberships + school names; GET /people
    ├── ClassesPageController.java     (first management-UI slice past the
    ├── StudentsPageController.java    dashboard, see UX_ROADMAP.md)
    ├── FinancePageController.java     lists/adds/toggles a school's
    ├── ExpensesPageController.java    memberships and issues invite tokens;
    ├── MyInvoicesPageController.java  GET /classes (second slice) creates/lists
    ├── RolesPageController.java       a school's classes; GET /students (third
    ├── NavbarModel.java               slice) enrolls/lists students into a class;
    ├── NavbarModelAssembler.java      GET /my-invoices and GET /roles are both
    └── RoleLabels.java                described separately below; GET /finance
                                  (fourth slice) sets/views the
                                  tuition fee, lists invoices, records payments,
                                  and runs invoice backfill; GET /expenses
                                  (finance-module extension, not a separate UI
                                  slice number) manages expense categories/
                                  vendors/expenses/attachments and shows the
                                  Jalali-period expense report — all entirely
                                  client-side against their respective REST
                                  controllers (school selection lives only in
                                  localStorage — see §11 Q2). None of
                                  ClassesPageController, StudentsPageController,
                                  FinancePageController, or
                                  ExpensesPageController depend on
                                  AcademicFacade/FinanceFacade/ExpenseFacade or
                                  any academic/finance type — all four are thin
                                  shells like PeoplePageController;
                                  classes.js/students.js/finance.js/expenses.js
                                  compose iam.MembershipController (for
                                  teacher/student names) with
                                  academic.ClassController/StudentController
                                  and finance.FinanceController/
                                  ExpenseController in JavaScript, not the Java
                                  controller, so `web` gains no new module
                                  dependency for any of the four slices.
                                  finance.js was the first client script that
                                  had to tell ModuleNotEntitled apart from
                                  PermissionDenied (both 403s) by inspecting the
                                  ProblemDetail `type` URI suffix (§5);
                                  expenses.js reuses the same classifyError
                                  pattern verbatim. expenses.js also loads
                                  static/js/jalali.js (a small dependency-free
                                  Jalali↔Gregorian conversion utility — no such
                                  library existed anywhere in this codebase
                                  before) purely for its report-period picker;
                                  the server only ever sees Gregorian
                                  LocalDate query params. NavbarModel/
                                  NavbarModelAssembler build the shared
                                  school/role switcher navbar (rendered by
                                  layout/navbar.jte: page links in navbar-start
                                  — every page must be linked there, no orphan
                                  URLs — plus a Bulma burger for mobile) once,
                                  reused by every page instead of recomposed
                                  per controller. RoleLabels maps role codes to
                                  Persian labels for server-rendered templates
                                  (mirrored client-side in people.js — raw
                                  codes never reach the UI, UX_ROADMAP.md §2).
                                  No Facade, no entities, no REST, no exception
                                  handler of its own; nothing depends on it.
                                  MyInvoicesPageController (GET /my-invoices,
                                  built 2026-07-13) is a thin shell like the
                                  rest — STUDENT's own invoices + PARENT's
                                  linked wards' invoices, entirely client-side
                                  against the pre-existing
                                  finance.MyInvoicesController (no new REST,
                                  no new `web`→finance/academic Java
                                  dependency). See CLAUDE.md §7 Done, "Finance
                                  deepening, phase 3". RolesPageController
                                  (GET /roles, also built 2026-07-13) is
                                  another thin shell — lists every role
                                  visible to the school plus a custom-role
                                  creation form, entirely client-side against
                                  the new iam.RoleController (§4's Role
                                  subsection, §11 Q3). Navbar link visible
                                  for SCHOOL_ADMIN only.
│
└── bale/                       ← Bale (بله) messenger bot integration — outbound
    ├── BaleFacade.java             notifications + inbound self-service queries.
    ├── BaleWebhookController.java  Not a subscribable feature: no Module/
    ├── internal/                  Entitlement row, no @RequiresModule on its
    │   ├── BaleLinkEntity          controller — same reasoning as
    │   │    / BaleLinkRepository   MembershipController/SchoolController (§5).
    │   ├── BaleClient              Deliberately has no shared/BaleError sealed
    │   ├── BaleUpdateHandler       stack (see BaleFacade's own doc comment) —
    │   ├── BaleWebhookService      its one exception-worthy case doesn't exist:
    │   ├── BaleFacadeImpl          "chat not linked" is normal, not an error,
    │   └── InvoiceIssuedEventListener  modeled as a boolean return instead.
    └── shared/                    (empty — @NamedInterface("shared") exists for
                                     symmetry with other modules but nothing is
                                     exposed through it yet)
```

**Adding a new module**: copy the `academic/shared` error-handling pattern
(`<Module>Error` sealed interface, `<Module>Exception`, a local
`<Module>ExceptionHandler` with an explicit unique bean name that only knows its own
exceptions). Never add a new module's error cases to `iam`'s or `platform`'s
exception handling — each module owns its own.

**`@RestControllerAdvice` beans must have explicit, unique names**
(`@RestControllerAdvice("moduleNameHandler")`). Spring derives a default bean name
from the simple class name, and two modules both naming their handler
`GlobalExceptionHandler` (or any identical name) causes a
`ConflictingBeanDefinitionException` at startup — this already happened once.
Current names: `iamGlobalExceptionHandler`, `academicGlobalExceptionHandler`,
`schoolExceptionHandler`, `financeExceptionHandler`.

**Audit pattern** (established in `finance`, to be repeated in any module that needs
an audit trail — see `finance/internal/FinanceAuditEventListener.java`):
1. Public event in the module's outer package (not `internal`), carrying `schoolId`
   + whatever identifies the record + `actorUserId` (nullable — see below) +
   `occurredAt`.
2. `internal/<Module>AuditEntity` — append-only, own schema, no update method ever.
3. `internal/<Module>AuditEventListener` — `@ApplicationModuleListener`, restores
   `TenantContext` from the event (thread-boundary — see Multi-tenancy note below),
   saves one row.
4. The facade method that performs the mutation publishes the event via
   `ApplicationEventPublisher` *after* its own work succeeds.
5. `actorUserId` is nullable by design: some mutations are system-triggered, not
   human-triggered, and `IamFacade.currentUserId()` throws when there's no
   authenticated principal (e.g. when called from an internal event listener, not an
   HTTP request). Only add a richer `actorType` (human vs. system) concept when a
   real system-triggered mutation actually exists — it doesn't yet anywhere in this
   codebase; don't build it speculatively.

**Thread-boundary rule for all `@ApplicationModuleListener`s**: they run
asynchronously in a new thread after the triggering transaction commits.
`TenantContext` is `ThreadLocal` and does not cross threads — every listener must
`TenantContext.set(event.schoolId())` at the top and `TenantContext.clear()` in a
`finally` block (not just `set`, or a pooled thread reused for an unrelated request
would silently inherit the wrong tenant).

---

## 4. Core Domain Model

### User

The single identity root. There is no separate `Teacher`/`Student`/`Parent`/
`SchoolAdmin` entity type — those are roles, not identities. One `User` row per human.

```
UserEntity
├── id (Long, IDENTITY)
├── email (unique)
├── fullName
├── passwordHash (nullable — null means the account was created by an admin/
│                  enrollment flow via findOrCreateUser and can't log in yet.
│                  Activated via a redeemed InviteToken — see below — which sets
│                  this field via GET /activate (AccountActivationPageController);
│                  the activation link is emailed on issue — see InviteToken below)
├── phoneNumber (nullable, unique — an *additional* identity attribute, not a
│                 login credential. Only settable via `PUT /api/me/phone`
│                 (`MeController.setPhoneNumber` → `IamFacade.setPhoneNumber`),
│                 which requires an authenticated session — deliberately no
│                 unauthenticated "claim a phone number" path, since that would
│                 let anyone take over an account by guessing/knowing its phone
│                 number. Exists to back `bale.BaleLinkEntity` (§3) — the primary
│                 lookup key when a user shares their contact with the Bale bot.)
└── status (ACTIVE | SUSPENDED)
```

### InviteToken

Backs self-service account activation for users created with a null `passwordHash`
(§7 item 2). One-time-use, scoped to a `User` (not a `SchoolMembership` — a password
belongs to the person, and one person can hold multiple memberships).

```
InviteTokenEntity
├── id
├── userId
├── token           (unique, SecureRandom-generated, Base64 URL-safe)
├── createdAt
├── expiresAt       (createdAt + 7 days)
└── redeemedAt       (nullable — null means still unredeemed)
```

`IamFacade.issueInviteToken(membershipId)` — resolves the membership to its user,
returns `Optional.empty()` (idempotent no-op, not an error) if that user already has
a password, otherwise reuses any still-valid unredeemed token or mints a new one,
then emails the activation link (`{base-url}/activate?token=...`) to the user via
`iam/internal/InviteEmailSender` — so re-issuing doubles as "resend the invite
email." Returns `InviteIssued(token, emailSent)`. Email delivery is **best-effort**:
an SMTP failure never fails token issuance; `emailSent=false` in the response tells
the admin to hand over the link manually (the pre-email flow). SMTP config is
`spring.mail.*` (dev default: localhost:1025, i.e. Mailpit/MailHog); sender address
and link base come from `roshdeandishe.mail.from` /
`roshdeandishe.mail.activation-base-url` in `application.yml`.
Exposed at `POST /api/admin/schools/{schoolId}/memberships/{membershipId}/invite-token`
(`MEMBERSHIP_MANAGE`, same as the rest of `MembershipController`).

`IamFacade.redeemInviteToken(token, newPassword)` — validates the token (exists, not
redeemed, not expired) and the new password (min. 8 chars — first and so-far-only
manual-validation convention in `iam`, no Bean Validation/`@Valid` used anywhere in
this codebase yet), then BCrypt-encodes it onto the `UserEntity` and marks the token
redeemed. Exposed at `POST /api/auth/activate`, intentionally unauthenticated (the
token *is* the credential), same trust model as `/api/auth/login`.
(`spring-boot-starter-validation` is already in `pom.xml` but currently unused —
don't treat its presence as license to sprinkle `@Valid` in ad hoc; if/when
declarative validation is actually adopted, do it as one deliberate pass across
all modules, not incrementally per endpoint.)

### VerificationCode

Backs phone/OTP login (a **second login factor**, not a new registration path —
`MOSHAVERE_PORT_SPEC.md` §3, the first piece ported from the older moshavere
codebase). Same one-time-use/TTL/nullable-consumed-marker shape as `InviteToken`
above, deliberately *not* moshavere's `OneToOne` version: a user must be able to
request a fresh code before an old one expires, which a `OneToOne` row can't
represent cleanly.

```
VerificationCodeEntity
├── id
├── userId
├── code          (6 digits)
├── createdAt
├── expiresAt     (createdAt + 5 minutes)
└── consumedAt    (nullable — null means still unredeemed)
```

Unlike `issueInviteToken` (which reuses a still-valid unredeemed token),
`IamFacade.requestOtp(phoneNumber)` **always inserts a new row** — and
`verifyOtp` only ever looks at the single most-recently-created unconsumed row
for that user, so requesting a fresh code silently supersedes any earlier one
even if it hasn't expired yet. `requestOtp` 404s with `IamError.PhoneNotRegistered`
if no `UserEntity` carries that phone number — **this never creates a user or a
new login path**; a phone number only gets onto an account via the existing
authenticated `PUT /api/me/phone` (`MeController`/`setPhoneNumber`, §4 User),
exactly the same security boundary that field already had before OTP existed.
`verifyOtp(phoneNumber, code)` validates the code (`IamError.OtpExpired` if no
active code exists or it's past `expiresAt`; `IamError.OtpIncorrect` if it exists
but doesn't match — deliberately *not* marked consumed on a wrong guess, so the
user can retry until expiry), marks it consumed, and returns the matched
`UserProfile`. The Facade stops there — it knows nothing about
`SecurityContext`/`HttpSession`; establishing the session is `AuthController`'s
job, the same separation `login`/`authenticate` already has.

Exposed at `POST /api/auth/otp/request` / `POST /api/auth/otp/verify`
(`iam.shared.AuthController`), both intentionally unauthenticated — same trust
model as `/api/auth/login`/`/api/auth/activate` (the code itself is the
credential), and consequently **not** in `SecurityConfig`'s CSRF-exempt list
(only the Bale webhook, mobile token issuance, and Bearer-authenticated calls are
exempt — see §7's Mobile Auth entry). `verifyOtp` builds a pre-authenticated
`UsernamePasswordAuthenticationToken` directly (no password to check —
`AuthenticationManager` plays no role in this path) and saves it to
`SecurityContext`/`HttpSession` exactly like `login` does; it does **not** issue
a JWT — the spec ties OTP to the *session* path specifically, a mobile/JWT
variant would be a separate, not-yet-asked-for decision.

Delivery is `iam.internal.SmsDriver` — a **concrete class**, not the interface
the port spec sketched: this codebase's actual convention for a best-effort
external side-channel is one concrete class that no-ops-and-logs when
unconfigured (`BaleClient`, `InviteEmailSender`), never a speculative
interface for a single implementation (§10). Tests reach the generated code via
`JdbcTemplate` against `iam.verification_codes` directly (same pattern
`ExpenseManagementFlowIntegrationTest`/`FinancePaymentFlowIntegrationTest` use
to reach data a Facade deliberately doesn't return), not via a swapped test
double — the code is never returned in any HTTP response, unauthenticated or
not. `roshdeandishe.sms.api-key` defaults empty, same convention as
`roshdeandishe.bale.bot-token`: a real SMS provider is deployment config, not
missing code. **Not built, per the spec's own explicit flag**: rate-limiting
repeated OTP requests — a conscious skip (moshavere had none either), not an
oversight.

### Role

A named, assignable bundle of permissions — not a user type. Adding a new access
level is a data change (insert a `RoleEntity` + `RolePermissionEntity` rows), not a
code change. This is also, since 2026-07-13, literally true for schools themselves:
a `SCHOOL_ADMIN` can insert their own `RoleEntity` + `RolePermissionEntity` rows
through `iam.RoleController` (§3) without any code change — see **Custom roles**
below.

```
RoleEntity
├── id
├── code (STUDENT, TEACHER, PARENT, SCHOOL_ADMIN, FINANCE_ADMIN, PLATFORM_ADMIN —
│         these six are the *system* roles; a school's own custom role codes
│         also live in this same column/table, see below)
├── displayName
└── schoolId (nullable — null means a system role, shared by every school;
              non-null means a custom role belonging to exactly that school)
```

`PLATFORM_ADMIN` is seeded as a role but has **no working permission-check path** —
see §7. Don't assume granting someone `PLATFORM_ADMIN` today does anything
enforceable. It's also filtered out of every role listing exposed to a school
(`IamFacade.listRoles`, same convention as `web.RoleLabels` never rendering it).

#### Custom roles

Resolves **Open Question 3** (§11): *"Can a school define custom roles, or only
choose from system-defined ones?"* Answered 2026-07-13 with three explicit,
narrow decisions (each was a real fork, not a default — see §11 Q3 for the
options considered and rejected):

1. **Per-school, not global.** A custom role's `schoolId` scopes it to exactly
   the school that created it — `IamFacade.listRoles(schoolId)` only returns
   system roles (`schoolId = null`) plus that one school's own custom roles.
   Two different schools can each create a role coded `LIBRARIAN` without any
   collision — the DB's `unique(code, school_id)` constraint (§3's `RoleEntity`
   note) permits that, since neither `code` is null in either row. What the
   constraint can't express is "a custom code must never collide with a
   *system* role's code" (system rows all share `schoolId = null`, and SQL
   unique constraints never treat two `NULL`s as equal to each other) — that
   check is application-level, in `IamFacadeImpl.createCustomRole`.
2. **Existing permissions only, never invented ones.** `createCustomRole`'s
   `permissionCodes` must each resolve to a real row in `iam.permissions` —
   `IamError.InvalidPermissionCode` otherwise. A school cannot type in a
   permission code that doesn't correspond to any `@RequiresPermission(...)`
   check anywhere in the code; doing so would look like it restricts/grants
   something in the UI while actually being a no-op everywhere.
3. **System roles are immutable through this path.** There is no "edit an
   existing role's permissions" operation — only `createCustomRole` (insert a
   new row). `SCHOOL_ADMIN`'s own grants can't be weakened by mistake through
   this UI, and no hardcoded role-code check elsewhere in the codebase (the
   guardian-eligibility check requires literally `PARENT`, the invoice-scoping
   in `finance.FinanceController` checks literally `SCHOOL_ADMIN`/`FINANCE_ADMIN`
   — §4's StudentGuardian note, §3's `FinanceController` entry) is affected by
   custom roles at all: a custom role only ever unlocks whatever its selected
   permission codes gate through the generic `@RequiresPermission` mechanism
   (§5) — `PermissionDecisionService` (§3) was already fully data-driven, with
   zero role-code awareness, so this "just worked" the moment `RolePermissionEntity`
   rows existed for a custom role; no change was needed there.

`IamFacade.createCustomRole(schoolId, code, displayName, permissionCodes)` —
`code` must match `^[A-Z][A-Z0-9_]*$` (§9's `UPPER_SNAKE_CASE` convention,
enforced here too) and not collide with any system role code or this school's
own existing custom role codes (`IamError.RoleCodeAlreadyExists`, 409);
`displayName` and `permissionCodes` must be non-blank/non-empty
(`IamError.InvalidRoleDefinition`, 400). Exposed at
`POST /api/admin/schools/{schoolId}/roles`; `GET` on the same path lists roles,
`GET .../permissions` lists the full fixed permission catalog (for building a
checklist UI) — all three gated by the new `ROLE_MANAGE` permission (below),
`SCHOOL_ADMIN`-only, same sensitivity tier as `MEMBERSHIP_MANAGE`.
`IamFacade.createMembership`'s role lookup (used by `MembershipController`,
`AcademicFacade.enrollStudent`, etc.) now resolves a `roleCode` against *this
school's own* custom roles first, falling back to system roles — never another
school's custom role, so a role created for School A can never be assigned at
School B (`IamError.RoleNotFound` if attempted).
**Management UI:** `GET /roles` (`web.RolesPageController`, §7 Done) — lists
every role visible to the school with its permission set, and a form to create
a new custom role from a permission checklist. `/people`'s own role-assignment
dropdown (`people.js`) fetches the same `GET .../roles` endpoint and merges
custom roles in alongside the five hardcoded system-role options, so a
freshly-created custom role is immediately assignable, not just visible.

### Permission

The actual capability. Deliberately separate from Role so two roles can carry
different access levels without any code change.

```
PermissionEntity
├── id
├── code (GRADES_VIEW, GRADES_EDIT, FINANCE_VIEW, FINANCE_MANAGE,
│         MEMBERSHIP_MANAGE, STUDENT_ENROLL, CLASS_MANAGE, CLASS_VIEW,
│         GUARDIAN_MANAGE, EXPENSE_VIEW, EXPENSE_MANAGE, EXPENSE_APPROVE,
│         ROLE_MANAGE, CURRICULUM_VIEW, CURRICULUM_MANAGE)
└── displayName

RolePermissionEntity  (join table: which roles have which permissions — for a
├── roleId                          custom role, these are the exact rows
└── permissionId                    IamFacade.createCustomRole inserts)
```

Current grants (seeded in `IamSeedDataRunner`):

| Role | Permissions |
|---|---|
| `STUDENT` | `GRADES_VIEW` |
| `TEACHER` | `GRADES_VIEW`, `GRADES_EDIT`, `STUDENT_ENROLL`, `CLASS_VIEW`, `CURRICULUM_VIEW`, `CURRICULUM_MANAGE` |
| `PARENT` | `GRADES_VIEW`, `FINANCE_VIEW` |
| `SCHOOL_ADMIN` | `GRADES_VIEW`, `FINANCE_VIEW`, `FINANCE_MANAGE`, `MEMBERSHIP_MANAGE`, `CLASS_MANAGE`, `CLASS_VIEW`, `STUDENT_ENROLL`, `GUARDIAN_MANAGE`, `EXPENSE_VIEW`, `EXPENSE_MANAGE`, `EXPENSE_APPROVE`, `ROLE_MANAGE`, `CURRICULUM_VIEW`, `CURRICULUM_MANAGE` |
| `FINANCE_ADMIN` | `FINANCE_VIEW`, `FINANCE_MANAGE`, `EXPENSE_VIEW`, `EXPENSE_MANAGE` |

**`CURRICULUM_VIEW`/`CURRICULUM_MANAGE` (added 2026-07-16, for the `curriculum`
module — moshavere port step 2, resolving the spec's "exact permission split
TBD"):** both granted to `SCHOOL_ADMIN` *and* `TEACHER` — authoring the
question bank is day-to-day teacher work (the `STUDENT_ENROLL` reasoning, not
the `CLASS_MANAGE` one; spec §2: "each school's own teachers own their own
topics/questions/plans"). Deliberately **not** granted to `STUDENT`/`PARENT`,
not even `CURRICULUM_VIEW`: a question row carries `correctChoice` and the
`answer` explanation, so raw bank read access leaks answers — student access
will be mediated by the future `exam` module (scoped exam-take token), never
by reading the bank directly. The VIEW/MANAGE split exists despite identical
system grants today so a *custom role* (§4's Role subsection) can be composed
read-only — same reasoning as `CLASS_VIEW`.

**`ROLE_MANAGE` (added 2026-07-13, for custom role management — see §4's
Role subsection):** deliberately `SCHOOL_ADMIN`-only, not granted to
`FINANCE_ADMIN` — the ability to define what a role can do is as sensitive as
`MEMBERSHIP_MANAGE` (who holds a role), arguably more so, since it shapes what
*every* role, including ones not yet created, is allowed to touch.

**`EXPENSE_VIEW`/`EXPENSE_MANAGE`/`EXPENSE_APPROVE` (added for expense
management, §4's Expense note):** `EXPENSE_APPROVE` is deliberately withheld
from `FINANCE_ADMIN` — segregation of duties: the person who records and pays
an expense should not be the same person who approved it. Only `SCHOOL_ADMIN`
holds all three. Adding these three codes also surfaced and fixed a latent bug
in `IamSeedDataRunner`: `seedPermissions()`/`seedRolePermissions()` used to
early-return on `count() > 0`, so on any already-seeded dev database new
permission codes (and their role grants) would silently never arrive — tests
always passed (Testcontainers is always fresh) while every real dev database
would 403 on the new feature. Both methods are now per-code/per-grant
idempotent instead of guarded by a single row-count check; `CLASS_VIEW` and
`GUARDIAN_MANAGE` before this had only dodged the same trap by luck (a database
reset happened to occur before or alongside their addition).

**`GUARDIAN_MANAGE` (added for the Bale bot integration, §4's Guardian note
below):** deliberately its own permission, not a reuse of `MEMBERSHIP_MANAGE` —
linking a guardian to a student grants that guardian visibility into the
student's data (finance, eventually grades), which is a distinct capability
from managing memberships, even though both happen to be `SCHOOL_ADMIN`-only
today. `TEACHER` does **not** get it, unlike `STUDENT_ENROLL` — this is a
deliberate asymmetry: enrolling is day-to-day, but granting a stranger access
to a specific child's data is treated as admin-only, same sensitivity tier as
`MEMBERSHIP_MANAGE`.

Design intent behind the split: **class creation is administrative/scheduling
(`SCHOOL_ADMIN` only)**, while **student enrollment is a day-to-day task
(`SCHOOL_ADMIN` + `TEACHER`)** — a deliberate, explicit choice, not an oversight.

**`CLASS_VIEW` vs `CLASS_MANAGE` (added 2026-07-07, bug found after the
Enrollment UI slice shipped):** listing classes (`GET .../classes`) was
originally gated behind `CLASS_MANAGE`, same as creating one. Since `TEACHER`
was never granted `CLASS_MANAGE` (only `SCHOOL_ADMIN` creates classes), a
teacher opening `/students` couldn't populate the class picker at all —
`STUDENT_ENROLL` was nominally granted to them, but the class list they need
to enroll *into* was invisible. Fixed by splitting read from write: a new
`CLASS_VIEW` permission, granted to both `SCHOOL_ADMIN` and `TEACHER`, now
guards the `GET` endpoint; `CLASS_MANAGE` (create) stays `SCHOOL_ADMIN`-only,
preserving the admin/day-to-day split above unchanged.

### SchoolMembership

The fact that a `User` holds a `Role` at a `School`. A user may hold multiple
memberships (different roles at the same school, or roles at different schools,
simultaneously — e.g. teacher at School A and student at Academy B, at the same
time, same account).

```
SchoolMembershipEntity
├── id
├── userId
├── schoolId      (opaque Long from iam's perspective — see school module for the
│                   actual SchoolEntity)
├── roleId
└── active (boolean) ← deactivating ONE membership does not touch the user's
                        account, other memberships, or any other school
```

`IamFacade.setMembershipActive(membershipId, active)` — deactivate one specific
person's access without touching anything else about them.

`IamFacade.createMembership(userId, schoolId, roleCode)` — idempotent: if the same
`(user, school, role)` triple already exists (even inactive), it's reactivated rather
than duplicated. This is the operation used by every "X automatically becomes a
member of the school they just interacted with" flow (school creation →
`SCHOOL_ADMIN`, enrollment → `STUDENT`).

`IamFacade.findOrCreateUser(email, fullName)` — idempotent by email; returns the
existing `UserId` if one already exists rather than erroring or duplicating. The
first step of every "add this person to the system" flow.

`IamFacade.registerUser(email, fullName, password)` — public self-service sign-up
(§11 Q7): creates a `User` that has a `passwordHash` from the start and zero
memberships. **Deliberately NOT idempotent, unlike `findOrCreateUser`:** any
existing email is rejected with `IamError.EmailAlreadyRegistered` (409) — *even a
not-yet-activated account with `passwordHash = null`*. Claiming such an account is
only allowed through the emailed invite token (which proves email ownership);
otherwise anyone knowing an enrolled student's email could take over their account.
Validation: same 8-char password minimum as `redeemInviteToken`, plus non-blank
email/fullName. Exposed at `POST /api/auth/register` (unauthenticated, same trust
model as login) with the `GET /register` page in front of it.

### Module + ModuleScope

A feature area that can be subscribed to. `scope` decides which entitlement table is
checked.

```
ModuleEntity
├── id
├── code (ACADEMIC, FINANCE, CURRICULUM, LIBRARY, ATTENDANCE, TRANSPORTATION,
│         AI_TUTOR, EXAM_PREP, PORTFOLIO_BUILDER, TEACHER_MARKETPLACE,
│         PROFESSIONAL_DEVELOPMENT)
├── displayName
└── scope: PLATFORM | SCHOOL | USER
```

- `SCHOOL` modules are checked against `SchoolEntitlementEntity` (B2B — the school
  subscribed).
- `USER` modules are checked against `UserEntitlementEntity` (B2C — the individual
  subscribed, independent of any school).
- `PLATFORM` modules are always granted, no entitlement row needed.

### Entitlement

```
SchoolEntitlementEntity(schoolId, moduleId, active, startsAt, endsAt)
UserEntitlementEntity(userId, moduleId, active, startsAt, endsAt)
```

Kept as two separate tables (not one polymorphic table) — different lifecycle,
different billing source, different query patterns.

`IamFacade.setSchoolEntitlement(schoolId, module, active)` exists, is idempotent
(upserts), and validates the module is actually `SCHOOL`-scoped (throws
`IamError.ModuleScopeMismatch` otherwise) — **but has no REST endpoint**. This is a
genuinely platform-privileged action (granting a school paid features for free if
misused) with no real actor or permission check behind it yet. See §7 before
exposing it over HTTP.

### School (owned by the `school` module, not `iam`)

```
SchoolEntity
├── id
├── name
├── status (ACTIVE | SUSPENDED)
├── timezone
└── locale
```

Created via `SchoolFacade.createSchool`, always self-service (§1). `setSchoolStatus`
(suspension) exists on the facade but, like entitlement toggling, has no REST
endpoint yet for the same platform-privilege reason.

### Class (owned by `academic`)

```
ClassEntity
├── id
├── schoolId
├── name           (e.g. "Grade 5 - A" — carries the grade-level meaning; there is
│                    no separate gradeLevel field anywhere)
├── teacherId       (nullable — a class may exist before a teacher is assigned;
│                    references an iam userId, not enforced at the DB level since
│                    academic has no FK into iam's tables)
└── section
```

### Student (owned by `academic`, not an identity — see §4 User)

```
StudentEntity
├── id
├── userId          (references the iam UserEntity — the actual identity)
├── schoolId
├── classId
└── enrolledAt
```

`AcademicFacade.enrollStudent(email, fullName, schoolId, classId)` is idempotent: a
second call for the same user at the same school updates their `classId` (i.e. moves
them to a different class) rather than erroring or duplicating.
`AcademicFacade.listStudents(schoolId)` (added for the `/students` UI slice, §7)
returns every student row for a school, same flat-list shape as `listClasses`.

### StudentGuardian (owned by `academic` — added for the Bale bot integration)

The relationship the original identity model never had: **there was no way to
express "this `PARENT` is the guardian of that specific `StudentEntity`."**
`SchoolMembershipEntity` only ever linked `User`↔`School`↔`Role` — a `PARENT`
role member had permission grants but no modeled link to *which* child was
theirs. Building the Bale bot's "my children" self-service query forced this
gap into the open and this is where it was closed — deliberately scoped
narrowly (this relationship only, not a broader `Family`/`Staff` concept,
which §10 explicitly rejects).

```
StudentGuardianEntity
├── id
├── schoolId          (denormalized, same convention as StudentEntity/ClassEntity)
├── studentId          (real FK — academic's own StudentEntity, unlike guardianUserId below)
├── guardianUserId     (opaque Long → iam User, no FK — same convention as
│                        StudentEntity.userId/ClassEntity.teacherId)
└── linkedAt
```

`AcademicFacade.linkGuardian(schoolId, studentId, guardianUserId)` — idempotent
(same `(student, guardian)` pair returns the existing link, not a duplicate).
**Validates that `guardianUserId` holds an active `PARENT` membership at the
same school** (via `IamFacade.findMembershipsByUser` — deliberately *not*
`findMembership`, which only returns the first membership and would miss a
user holding multiple roles at the same school) — otherwise
`AcademicError.GuardianNotEligible` (409). This is a real security boundary,
not just data hygiene: without it, any user (e.g. a `TEACHER`) could be linked
to a student they have no family relationship to, and would then receive that
student's Bale notifications (see the `bale` module below).
`AcademicFacade.listGuardians(studentId)` / `listWards(guardianUserId, schoolId)`
are the read side — `listWards` is what the Bale bot's "my children" command
and the outbound invoice-notification listener both actually use.
Exposed at `POST`/`GET /api/admin/schools/{schoolId}/students/{studentId}/guardians`
(`GUARDIAN_MANAGE`, `SCHOOL_ADMIN`-only — see §4's Permission section).
**Was deliberately not wired into `/finance`'s existing `GET invoices` endpoint
at Bale-integration time** — that endpoint returned every invoice in the
school to anyone with `FINANCE_VIEW` (including `PARENT`, unscoped by
guardian), unchanged by the Bale work. Scoping it was flagged as "a separate,
real decision about existing shipped behavior, not a free extension of adding
the guardian relationship" — see §10. **That decision was made explicitly on
2026-07-13** (§7 Done, "Finance deepening, phase 3"): `FinanceController.listInvoices`
now checks the caller's active roles at the school (`IamFacade.findMembershipsByUser`)
and, when `PARENT` is the only one present (no `SCHOOL_ADMIN`/`FINANCE_ADMIN`),
scopes the result to `AcademicFacade.listWards(userId, schoolId)` instead of
the full school list. The Bale bot itself still never calls this endpoint —
it composes `listWards` + `FinanceFacade.listInvoices` directly, unaffected by
this change.

### Topic + Question (owned by `curriculum` — moshavere port, step 2)

The hierarchical topic taxonomy + multiple-choice question bank ported from
moshavere's `exam_generator` (MOSHAVERE_PORT_SPEC.md §4, built 2026-07-16).
Per-school content, never a shared platform library (spec §2). The `exam` and
`studyplan` modules (port steps 3–4, not built yet) will consume these via
`CurriculumFacade`.

```
TopicEntity
├── id
├── schoolId
├── parentId       (nullable self-reference as a plain Long column — null = root.
│                    Plain adjacency list, deliberately NOT moshavere's dual
│                    ltree + pickled-in-memory-tree representation. No move/
│                    reparent operation exists, so cycles are structurally
│                    impossible: a topic's parent must already exist at create
│                    time. A parent belonging to another school is rejected as
│                    TopicNotFound — same convention as academic's
│                    requireClassExists.)
├── name
└── sortOrder      (column sort_order — "order" is an SQL keyword)

QuestionEntity
├── id
├── schoolId
├── topicId        (required — moshavere allowed null, but a topic-less question
│                    was unreachable through its own required topic filter; a
│                    wart, not ported)
├── text / choice1..choice4   (Postgres text columns, all required non-blank)
├── correctChoice  (int, validated 1–4)
├── answer         (nullable text — the explanation of the correct choice; in
│                    the moshavere source but missing from the port spec's field
│                    list, ported deliberately)
├── difficulty     (nullable QuestionDifficulty enum: EDUCATIONAL | EASY |
│                    MEDIUM | HARD | VERY_HARD — port of moshavere's
│                    0/10/20/30/40 int scale)
└── discriminationIndex   (nullable Double)
```

`CurriculumFacade.createTopic / listTopics / getTopicTree / createQuestion /
listQuestions(schoolId, filter)`. The one behavior deliberately preserved from
moshavere's `QuestionFilter`: **the topic filter includes the whole subtree**
(the topic + all descendants), computed as an in-memory traversal over one
school's topics — if read performance ever actually demands it, a materialized
closure table is the next step (spec §4), not a second in-memory cache.
The difficulty filter is a set (`?difficulty=EASY&difficulty=HARD`); a question
with null difficulty is excluded when the filter is active (IN semantics, same
as source). Exposed at `POST`/`GET /api/admin/schools/{schoolId}/topics`
(+ `GET .../topics/tree`) and `POST`/`GET .../questions` — class-level
`@RequiresModule("CURRICULUM")`, per-method `@RequiresPermission`
(`CURRICULUM_MANAGE` write / `CURRICULUM_VIEW` read, both SCHOOL_ADMIN +
TEACHER — see the Permission section above for why students/parents get
neither). No web page yet — the port is backend domain logic only (spec §7);
a management UI is a separate slice that must go through the `design-page`
skill when asked for.

### TuitionFee (owned by `finance`)

```
TuitionFeeEntity
├── id
├── schoolId        (unique — one flat fee per school, deliberately not per-class/
│                     per-grade; richer pricing is YAGNI until actually requested)
└── amount          (BigDecimal(12,2))
```

`FinanceFacade.setTuitionFee(schoolId, amount)` — idempotent upsert; rejects null or
non-positive amounts with `FinanceError.InvalidTuitionFee`. `getTuitionFee` returns
`Optional.empty()` when unset (the REST GET maps that to 404 `TuitionFeeNotSet`).

### Invoice (owned by `finance`)

```
InvoiceEntity
├── id
├── schoolId
├── studentId       (references academic's StudentEntity id, opaque to finance)
├── amount          (copied from the school's TuitionFee at issue time)
├── issuedAt
├── dueDate         (nullable — issuedAt + roshdeandishe.finance.default-due-days,
│                     a global config property, default 14; nullable so rows
│                     issued before this column existed never spuriously
│                     become OVERDUE)
└── amountPaid      (BigDecimal, default 0 — running total, only ever moved
                      forward inside FinanceFacade.recordPayment's transaction)
```

**No `status` column** — `InvoiceStatus` (`PAID` / `PARTIAL` / `UNPAID` / `OVERDUE`)
is derived at read time in `FinanceFacadeImpl` from `amount`/`amountPaid`/`dueDate`,
never persisted (a stored value could go stale purely from time passing — OVERDUE
has no mutation that would trigger updating it). `FinanceFacade.issueTuitionInvoice(schoolId,
studentId)` reads the amount from `TuitionFeeEntity` itself (no longer a parameter).
**Explicit decision:** if the school has no tuition fee configured, no invoice is
issued (warn log only) and enrollment is never blocked; students enrolled before the
fee was set do not get backfilled invoices automatically — `FinanceFacade.backfillInvoices(schoolId)`
now exists as an explicit admin action (idempotent, priced at the *current* fee, not
a historical one — fee history isn't tracked) for exactly this case, but it must be
triggered on purpose; it throws `TuitionFeeNotSet` rather than silently no-op'ing,
since a deliberate admin action deserves a real error, unlike the silent skip during
ordinary enrollment. After persisting, `issueTuitionInvoice` publishes
`InvoiceIssuedEvent` (audit trail unchanged).

`FinanceFacade.recordPayment(schoolId, invoiceId, amount, method, note)` — manual
payment recording only (no online gateway anywhere in this codebase); partial/
installment payments are allowed, but a payment that would push `amountPaid` past
`amount` is rejected with `FinanceError.PaymentExceedsBalance` — deliberately no
credit-balance concept. Each call saves a `PaymentEntity` (`finance/internal`:
`invoiceId, schoolId, amount, method, note nullable, recordedByUserId nullable,
paidAt`) and publishes `PaymentRecordedEvent` for the audit trail.
`FinanceFacade.listInvoicesForStudents(schoolId, studentIds)` backs the guardian/
student self-view (`MyInvoicesController`, §3) and, since 2026-07-13, is also
reused by `FinanceController.listInvoices` itself for PARENT-only callers (§3's
`FinanceController` entry, §10) — the admin endpoint no longer stays unscoped
by guardian; `SCHOOL_ADMIN`/`FINANCE_ADMIN` still get the unscoped, full-school
`FinanceFacade.listInvoices(schoolId)` call.

### Expense, ExpenseCategory, Vendor, ExpenseAttachment (owned by `finance`)

The school's *outgoing* money — salaries, utilities, equipment — as opposed to
`Invoice`'s incoming tuition. A genuine extension of `finance`, not a new module
(reuses the `FINANCE` entitlement and schema).

```
ExpenseCategoryEntity          (per school, unique (schoolId, name))
├── id
├── schoolId
├── name
└── active

VendorEntity                   (per school; only name required)
├── id
├── schoolId
├── name
├── phone / nationalId / economicCode / address   (all nullable)
└── active

ExpenseEntity
├── id
├── schoolId
├── expenseDate     (LocalDate — the calendar day the expense happened, not a
│                     timestamp)
├── amount
├── categoryId      (required — opaque Long, facade-validated against schoolId,
│                     same convention as teacherId/studentId elsewhere)
├── vendorId        (nullable — same opaque-Long convention)
├── paymentMethod   (PaymentMethod — shared enum with tuition PaymentEntity)
├── status          (ExpenseStatus — DRAFT | APPROVED | PAID | CANCELLED;
│                     genuinely persisted, unlike InvoiceStatus, since these
│                     transitions are explicit human actions)
├── description / receiptNumber / invoiceNumber / documentNumber   (all nullable)
├── createdByUserId (nullable)
├── createdAt
└── updatedAt

ExpenseAttachmentEntity         (metadata only)
├── id
├── schoolId
├── expenseId
├── fileName / contentType / sizeBytes
├── uploadedByUserId (nullable)
└── uploadedAt

ExpenseAttachmentContentEntity  (the actual bytes — separate table on purpose,
├── id                           see §3's note on why)
├── attachmentId (unique)
└── content (byte[], not @Lob)
```

**Lifecycle** (`ExpenseFacadeImpl.transition`, a single private state machine):
`DRAFT → APPROVED` (requires `EXPENSE_APPROVE`) → `PAID` (requires
`EXPENSE_MANAGE`, terminal); `DRAFT` or `APPROVED` → `CANCELLED` (requires
`EXPENSE_MANAGE`, terminal). Field edits (`ExpenseFacade.updateExpense`) are only
allowed while `DRAFT` — otherwise `FinanceError.ExpenseNotEditable`. No hard
delete anywhere — `CANCELLED` is the only way to void an expense, consistent
with this codebase's `active`-flag discipline elsewhere. Multi-step approval
chains, budgets/spend ceilings, and installment expense payments were explicitly
scoped out (they were in the originating design doc's own "future" section) —
do not build them speculatively.

**Categories**: `ExpenseFacade.seedDefaultCategories(schoolId)` inserts the 14
standard Persian categories (حقوق و دستمزد، قبوض، تعمیرات، تجهیزات آموزشی، مواد
مصرفی، خدمات نظافت، پذیرایی، سرویس مدرسه، تبلیغات، مالیات، بیمه، اجاره، فوق
برنامه، سایر) — idempotent by name, not a one-time migration; a school can also
define its own custom categories freely.

**Attachments**: `ExpenseFacade.addAttachment` enforces a 5MB domain cap
(`FinanceError.InvalidAttachment` otherwise) and is allowed in any status except
`CANCELLED` — receipts routinely arrive at or after payment, so this is
deliberately *not* gated by the DRAFT-only edit lock. Multipart upload via
`ExpenseController` (`consumes = MULTIPART_FORM_DATA_VALUE`); download returns
raw bytes with an RFC 5987 `filename*=UTF-8''...` `Content-Disposition` header
(Persian filenames aren't valid in the plain ASCII `filename=` form). This is
the first file-upload feature anywhere in this codebase — `spring.servlet.multipart.max-file-size`/`max-request-size`
in `application.yml` are set just above the 5MB domain cap so an oversize file
still gets the domain's own `ProblemDetail`, not a container-level 500.

**Reports**: `ExpenseFacade.summaryByCategory` / `summaryByVendor` /
`summaryByMethod` (grouped sums over a date range, names resolved server-side
since category/vendor and expense all live in the same module) and
`financeSummary` (income — tuition `PaymentEntity` sum — vs. expense, same
range) all share one fixed, undocumented-as-a-parameter rule: **only `APPROVED`
and `PAID` expenses count** — `DRAFT` (unverified data entry) and `CANCELLED`
(voided) are always excluded, with no status filter parameter until a real need
for one shows up. The `/expenses` page's report section converts a Jalali
month/quarter/year picker to a Gregorian date range client-side
(`static/js/jalali.js`) before calling these endpoints — the server only ever
sees `LocalDate` query params, no Jalali awareness on the backend.

### BaleLink (owned by the `bale` module)

```
BaleLinkEntity
├── id
├── userId       (opaque Long → iam User, unique — one row per User, not per chat)
├── chatId       (unique — Bale's chat identifier)
└── linkedAt
```

One row per `User`, not per chat: if the same user re-shares their contact from
a different Bale chat, the existing row's `chatId` is overwritten (upsert keyed
on `userId`), not duplicated. Created by `bale.internal.BaleUpdateHandler` when
an inbound webhook `Update` carries a shared `Contact` whose `phone_number`
matches an existing `UserEntity.phoneNumber` (§4 User) — if no match, the bot
replies asking the person to register that phone number on their account
first (`PUT /api/me/phone`); there is deliberately no way to link a chat
without already having a phone number claimed through an authenticated
session (see the `phoneNumber` note on `UserEntity` — this is the same
security reasoning applied one level up).

`BaleFacade.sendMessageToUser(userId, text)` — the one public entry point
other modules use: best-effort, returns `false` (not an exception) if the
user hasn't linked a chat, since that's an expected, common state, not a
domain error. `bale.internal.InvoiceIssuedEventListener` is the first real
consumer — a second `@ApplicationModuleListener` on `finance.InvoiceIssuedEvent`
alongside `finance.internal.FinanceAuditEventListener` (multiple modules
listening to the same public event is normal, per §3's audit pattern note on
why the event lives outside `internal/`) — reacting by notifying both the
student (if linked) and every linked guardian
(`AcademicFacade.listGuardians`, §4's StudentGuardian note).

**Bale Bot API** (`bale.internal.BaleClient`) is a thin wrapper over
`https://tapi.bale.ai/bot<token>/METHOD` — deliberately a near-clone of
Telegram's Bot API (same JSON shapes, same `sendMessage`/`setWebhook`
endpoints, same `KeyboardButton(request_contact=true)` → `Contact.phone_number`
flow for requesting a user's phone number). `roshdeandishe.bale.bot-token` is
empty by default (dev/test): with no token configured, `BaleClient` only logs
what it would have sent and makes no HTTP call — registering a real bot with
Bale and setting the token is deployment config, not missing code, same
pattern as `roshdeandishe.mail.*` (§7 item 2). The webhook endpoint itself
(`POST /api/bale/webhook/{secret}`, `BaleWebhookController`) is secured by a
random secret path segment (`roshdeandishe.bale.webhook-secret`, also empty by
default — an empty secret means the webhook always 404s, fails closed) rather
than a cryptographic signature, since Bale's exact webhook-signing story
wasn't verified against live documentation; compared with
`MessageDigest.isEqual` to resist timing attacks. Deliberately **not** gated
by `@RequiresModule`/`@RequiresPermission`: the caller is Bale's server, not a
browser with a session, so `SecurityContext`/`TenantContext` never get
populated for this request — `SecurityConfig` `permitAll`s and CSRF-exempts
`/api/bale/webhook/**`, and `TenantResolverFilter` excludes it the same way it
excludes `/api/me`. Authorization instead comes from the data model itself:
every query the bot answers is scoped to the linked `userId`'s own data (or,
for `listWards`, their *actually-linked* children) — the same trust model
`MeController` already uses for `/api/me` (§3).

The bot is a fixed set of Persian text commands, not a conversational agent —
**no AI/LLM is involved anywhere in this module**, consistent with §10's
"do not build AI features."

---

## 5. Access Control — Two Layers

Every protected action is checked in two steps, in this order. Both must pass.

1. **Module entitlement** (`@RequiresModule("FINANCE")`, enforced by
   `ModuleAccessAspect`, `@Order(10)`) — has this school (or this user, for
   USER-scoped modules) subscribed to this module at all? Failure →
   `IamError.ModuleNotEntitled` → HTTP 403.

2. **Role permission** (`@RequiresPermission("FINANCE_MANAGE")`, enforced by
   `PermissionAccessAspect`, `@Order(11)`, runs *after* the module check) — does this
   specific user's *active* role at this school include this specific permission?
   Failure → `IamError.PermissionDenied` → HTTP 403.

**Never conflate these two failure states.**

Which controllers carry which layers (resolved): `ClassController` and
`StudentController` carry class-level `@RequiresModule("ACADEMIC")` (the aspect
supports `@Target(TYPE)` via `@within`) plus per-method `@RequiresPermission`.
`MembershipController` and `SchoolController` deliberately carry **no**
`@RequiresModule` — membership management and school creation/reading are
platform capabilities, not subscribable modules (there is no `IAM` or `SCHOOL`
module code). `BaleWebhookController` (§4's BaleLink note) carries **neither**
layer, for a different reason than the two above: the caller is Bale's server,
not an authenticated browser session, so there's no `SecurityContext`/
`TenantContext` for either aspect to check against — its authorization comes
entirely from the data model (every query is scoped to the webhook's own
resolved `userId`), not from these two aspects. `IamSeedDataRunner.seedDemoSchoolEntitlements()` seeds an
`ACADEMIC` entitlement for the demo school (id 1) so the academic endpoints
stay usable in dev; a school created self-service at runtime has zero
entitlements and will correctly get 403 `ModuleNotEntitled` on them until
granted via `setSchoolEntitlement` (facade-only, see §7 item 1).

**Critical caveat, not yet resolved:** `PermissionDecisionService` currently
**unconditionally denies** any check made with `AccessContext.PlatformContext`
(non-school-scoped) — it only knows how to evaluate `SchoolContext`. There is no
`PLATFORM_ADMIN` permission-check path. This is *why* school creation is self-service
(§1) and why entitlement-toggling/school-suspension have no REST endpoint (§4, §7) —
building either "fake" protection or leaving them open was judged worse than not
exposing them. Building real `PlatformContext` permission support is explicitly
deferred until a concrete need exists (e.g. real billing integration) — do not build
it speculatively.

**`@RequiresPermission`/`@RequiresModule` check `TenantContext` (from the
`X-School-ID` header), independent of any `schoolId` path variable a controller might
also have.** Every controller with a `{schoolId}` path variable and a permission
annotation (`MembershipController`, `ClassController`, `StudentController`,
`FinanceController`) explicitly verifies the header matches the path before doing
anything — a header/path mismatch is a real security bug class (permission checked
against one school, mutation applied to another), not a hypothetical. The check
lives in `platform.TenantContext.requireMatches(schoolIdFromPath)` — it was
duplicated privately in three controllers until the fourth (`FinanceController`)
arrived, which per the rule here triggered extracting it. New controllers call
`TenantContext.requireMatches(...)`; do not re-inline it.

`LoggingAspect` (`@Order(1)`, in `platform`, opt-in via `@Loggable`) wraps both of the
above.

---

## 6. Error Handling

- RFC 7807 `ProblemDetail` everywhere, never hand-rolled `Map` bodies.
- `ProblemDetailFactory` (in `platform`) is the *only* place that knows how to build a
  `ProblemDetail` — zero domain knowledge. Every module's exception handler calls it.
- Each module owns a sealed `<Module>Error` interface, a `<Module>Exception` that
  carries one, and its own `@RestControllerAdvice` handler (explicit unique bean
  name — see §3) that only knows about its own exception type.
- For modules with more than a trivial number of error cases, split the
  switch-expression mapping into a dedicated `<Module>ProblemResolver` `@Component`
  (see `iam.shared.exception.IamProblemResolver`) so it's unit-testable without an
  MVC context.
- `platform.PlatformExceptionHandler` is the final fallback (`@Order(MAX_VALUE)`).
- Because every `<Module>Error` is `sealed`, every switch over it is exhaustive at
  compile time.

---

## 7. What's Done vs. Open

### Done

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

### Open / explicitly deferred

1. **`PlatformContext` permission checking.** `PermissionDecisionService` denies all
   non-school-scoped checks unconditionally. Blocks: REST exposure of
   `setSchoolEntitlement` and `setSchoolStatus`. Do not build until a real need
   (billing integration, ops tooling) exists — see §5.
2. ~~**Self-service account activation — email delivery.**~~ **Resolved.**
   `issueInviteToken` now emails the activation link to the invited person
   (see §4's `InviteToken` subsection). The token still also comes back in the
   admin JSON response together with `emailSent` — deliberately kept as the
   manual fallback for when SMTP delivery fails. Remaining follow-up is
   deployment-time only: point `spring.mail.*` at a real SMTP provider and set
   `roshdeandishe.mail.activation-base-url` to the public URL.
3. ~~**Real invoice persistence.**~~ **Resolved** — `InvoiceEntity` +
   `TuitionFeeEntity` persistence and real per-school pricing exist (§4). Still
   deliberately *not* built, in rough priority order: payments (and with them an
   invoice `status`), invoice backfill for students enrolled before a fee was set,
   and per-class/per-grade pricing — each waits for a concrete request.
4. **Multi-DataSource.** Single shared datasource. Per-module DataSource routing is
   a distinct, not-yet-started phase. Each module's tables live in their own Postgres
   *schema* already (`iam`, `school`, `academic`, `finance`) but all through one
   connection — schemas must currently be created manually
   (`CREATE SCHEMA IF NOT EXISTS <name>;`) since Hibernate's `ddl-auto` creates
   tables, not schemas.
5. **Flyway/Liquibase.** All seed runners + `ddl-auto: update` are dev-only
   stand-ins.
6. **Broader admin/teacher frontend — management UI.** The login page and a
   read-only dashboard now exist (see Done). What's still missing is every
   *mutating* UI: membership/class/student/finance management pages. Angular was
   considered and rejected in favor of JTE given the session-cookie-based backend
   and this project's preference for zero-build, server-rendered pages over an
   SPA + token-auth rework. **Gate (a) — CSRF — is resolved: CSRF is now
   enabled** (`SecurityConfig`): `CookieCsrfTokenRepository.withHttpOnlyFalse()`
   + the Spring-docs `SpaCsrfTokenRequestHandler` (nested in `SecurityConfig`);
   the server writes an `XSRF-TOKEN` cookie, every JTE page's `fetch` sends it
   back as `X-XSRF-TOKEN` (`login`/`register`/`activate` each have an inline
   `csrfToken()` helper — copy it if writing another inline-only page; pages
   with a static JS component, per the code-placement rule above, each keep
   their own small copy of the same helper in that file instead), Swagger UI
   does the same via `springdoc.swagger-ui.csrf.enabled`, and MockMvc tests use
   `.with(csrf())`. **Gate (b) — the school/role switcher — is also resolved**
   (see §11 Q2 for the chosen UX and its semantics; the switcher navbar is now
   a shared `layout/navbar.jte` template rendered on every management page, not
   reimplemented per page). **The management UI is unblocked and underway** —
   see [`UX_ROADMAP.md`](UX_ROADMAP.md) for the full page inventory, build
   order, and shared conventions. **Done so far:** the dedicated self-service
   school-registration page (`GET /schools/new`, `createSchoolForm` in
   `/js/school-new.js` → `POST /api/admin/schools`; no `X-School-ID` sent, the
   school doesn't exist yet — moved off the dashboard 2026-07-06, see §7 Done),
   the public landing page (`GET /`), **People (`/people`)**, **Classes
   (`/classes`)**, **Enrollment (`/students`)**, and **Finance (`/finance`)**
   — see §7 Done for all six. The SCHOOL_ADMIN build order is now complete.
   New management pages go in the
   `web` module, reuse
   `NavbarModelAssembler` + `layout/navbar.jte`, and their JS must send both
   `X-School-ID` (the page's school — omitted only for school-creation itself,
   which precedes any schoolId existing) and `X-XSRF-TOKEN` on mutating
   requests.
7. **Class reassignment as an explicit action.** `enrollStudent` happens to support
   moving a student to a different class (idempotent re-enroll updates `classId`),
   but there's no dedicated "transfer" endpoint/semantics, and it's unclear whether a
   transfer should re-publish `StudentEnrolledEvent` or a distinct event. Not built
   because not yet asked for.

---

## 8. Roadmap: IAM → School MVP

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
§3, §4's Topic/Question subsection, and §7 Done. Remaining port steps per
MOSHAVERE_PORT_SPEC.md §8: `studyplan` (depends on curriculum + academic),
then `exam` (deepest dependency chain, built last so
`StudyPlanFacade.getRecentTopics` already exists) — each a genuinely new
module needing its own `new-module` pass.

---

## 9. Naming Conventions

- **Classes:** PascalCase. Entities suffixed `Entity` (internal only). Repositories
  suffixed `Repository`.
- **Permissions:** `RESOURCE_ACTION` upper snake-case (e.g. `FINANCE_MANAGE`,
  `STUDENT_ENROLL`).
- **Roles / Modules:** `UPPER_SNAKE_CASE` codes.
- **Events:** past-tense PascalCase (`StudentEnrolledEvent`, `InvoiceIssuedEvent`).
- **Database tables:** snake_case, in a schema matching the module name (`iam`,
  `school`, `academic`, `finance`).
- **Sealed error types:** `<Module>Error` interface, `<Module>Exception` wrapper,
  optionally a `<Module>ProblemResolver`.
- **`@RestControllerAdvice` bean names:** explicit and unique, `"<module>...Handler"`
  — never rely on the default class-name-derived bean name (see §3).
- **REST controllers:** live at module root, alongside the Facade (see §2).

---

## 10. What NOT To Do

- **Do not give `SchoolMembershipEntity` a single fixed role field that can't
  represent multiple simultaneous roles.**
- **Do not let one module's exception handler know about another module's error
  types.**
- **Do not let `platform` depend on `iam`, `academic`, `finance`, or `school`.**
- **Do not bypass the two-step access check** by checking only one of them.
- **Do not hard-delete `SchoolMembershipEntity` rows to revoke access.** Use `active`.
- **Do not build AI features.**
- **Do not reintroduce a `Person`/`User` split or a separate `RoleGrant` entity**
  without an explicit decision to revisit this file. Discussed and re-confirmed
  rejected multiple times — the current model (time-bounding would just be nullable
  `startsAt`/`endsAt` columns added directly to `SchoolMembershipEntity` if ever
  needed, not a `RoleGrant` table) covers every requirement raised so far.
- **Do not introduce a `Staff`/`Family` space category on `Role`.** Not part of this
  project's resolved model; do not import this concept from unrelated prior
  discussions.
- **Do not expose `setSchoolEntitlement` or `setSchoolStatus` over REST** without
  first building real `PlatformContext` permission checking (§5, §7 item 1). Bolting
  a privileged endpoint onto an unrelated task because the facade method already
  exists is exactly the scope creep this file exists to prevent.
- **Do not build a richer actor concept (human vs. system) for audit trails**
  speculatively. Nullable `actorUserId` is sufficient until a real system-triggered
  mutation exists — see §3's audit pattern.
- **Do not assume a `@RequiresPermission`-annotated controller's `schoolId` path
  variable is trustworthy without checking it against `TenantContext`** — see §5.
- **Do not build a generic `Notification`/messaging abstraction.** The `bale`
  module is Bale-specific on purpose (§3, §4's BaleLink note) — the same YAGNI
  reasoning that keeps `InviteEmailSender` inside `iam/internal` instead of a
  shared `platform` abstraction. Build a second channel's own module when a
  second channel is actually requested, not before.
- **Do not build a grades data model or UI** until the descriptive-vs-numeric
  grading split and exam planning are explicitly resolved — see §11 Q8. The
  `GRADES_VIEW`/`GRADES_EDIT` permissions already existing in seed data is not
  license to build the feature they imply.
- **Do not scope an already-shipped endpoint to `StudentGuardianEntity` (or
  any other narrowing) as a silent side effect of unrelated work.** This is
  exactly what protected `/finance`'s `GET invoices` from being scoped as a
  drive-by of the Bale integration (§7 Done) — it was later scoped, but only
  as its own explicit, named decision (§7 Done, "Finance deepening, phase 3"),
  not bundled into something else. Keep applying this discipline to every
  other already-shipped endpoint.

---

## 11. Open Questions (do not resolve without explicit discussion)

1. ~~Session vs. JWT for authentication~~ — **Resolved: session-based for the web
   app, additive JWT for mobile** (§2, §7, §8). The "KMP mobile client" this note
   originally anticipated didn't materialize as such — the mobile client that
   actually got built is native Android (Kotlin + Jetpack Compose, not Kotlin
   Multiplatform), decided explicitly in favor of KMP/Compose Multiplatform and
   Capacitor given no Kotlin exists in this codebase yet and iOS isn't a v1
   target. That decision made session cookies impractical for it (a
   `capacitor://`-style webview origin issue never even arose, since there's no
   webview at all — a native app simply has no cookie jar to share with the
   backend), which is exactly the condition this note flagged: JWT bearer auth
   was added additively, session auth for web is unchanged.
2. ~~How does a user holding multiple memberships switch between schools/roles in
   the UI?~~ — **Resolved (2026-07-05, explicit user decision):** two navbar
   dropdowns. The **school dropdown sits on the school name** (distinct schools
   from the user's *active* memberships, names composed by the `web` module);
   the **role dropdown sits on the person's name** and lists their roles at the
   currently selected school (plus logout). Semantics, deliberately chosen:
   - Selection is **client-side only** (localStorage: `selectedSchoolId`,
     `selectedRole`) — no server-side "current school" session state. Management
     pages send the selected school as `X-School-ID` on their API calls.
   - **The role selection is display-only.** The backend has no per-request
     "acting as role" concept — `PermissionDecisionService` evaluates all of the
     user's active roles at the school. The role dropdown may drive which UI is
     shown, never what the backend permits. Do not build per-request role
     scoping off the back of this dropdown without an explicit new decision.
   - The switcher **must tolerate zero school memberships** (dropdown simply not
     rendered): individual B2C accounts (e.g. a teacher self-registering with no
     school) are an anticipated future flow — see Q7.
3. ~~Can a school define custom roles, or only choose from system-defined
   ones?~~ **Resolved 2026-07-13, on explicit request:** yes, scoped
   per-school. `IamFacade.createCustomRole` + `iam.RoleController` +
   `GET /roles` page — see §4's Role subsection ("Custom roles") for the
   full model and §7 Done ("Custom role management") for what shipped. Three
   deliberate constraints, each a real decision (not the only option
   considered): (1) a custom role is visible/assignable only to the school
   that created it, never shared platform-wide; (2) its permissions must be
   selected from the existing fixed permission catalog, never invented
   fresh — an invented code would correspond to no `@RequiresPermission`
   check anywhere and silently do nothing; (3) the six system roles
   (`STUDENT`/`TEACHER`/`PARENT`/`SCHOOL_ADMIN`/`FINANCE_ADMIN`/
   `PLATFORM_ADMIN`) are not editable through this path — only new roles can
   be created, so this can't be used to accidentally weaken `SCHOOL_ADMIN`'s
   own grants or bypass a hardcoded role-code check elsewhere in the codebase
   (e.g. the guardian-eligibility check, which requires literally `PARENT`).
4. Should `UserEntitlementEntity` (B2C) ever interact with `SchoolEntitlementEntity`
   (B2B)? Currently fully independent.
5. **New:** What does a real `PLATFORM_ADMIN` permission-check path look like, and
   when should it actually be built? Tied to §7 item 1 — likely triggered by real
   billing integration, not before.
6. **New:** Should self-created schools have a `PENDING`/`VERIFIED`-style status
   distinguishing "someone just signed up" from "a real, approved customer," or is
   "has zero entitlements until manually granted" sufficient signal on its own?
   Discussed once; decided not to build the extra status field for now, but not
   fully closed if a concrete need surfaces. **2026-07-06 update:** the
   presentation half was addressed without touching this question — school
   creation moved off the dashboard to its own `GET /schools/new` page (§7
   Done) so it's no longer offered to every logged-in user regardless of
   intent. A real approval/verification flow (this question's actual subject)
   still waits on `PLATFORM_ADMIN` infrastructure (§7 item 1) — do not build a
   status field speculatively ahead of that.
7. **(raised 2026-07-05; sign-up part resolved 2026-07-05):** Individual (B2C)
   self-registration — e.g. a teacher creating their own account with no school,
   using USER-scoped modules. **The public sign-up flow now exists** (user asked
   for a registration page): `GET /register` + `POST /api/auth/register` +
   `IamFacade.registerUser` — see §4 and §7 Done. What remains open and unscoped
   is the *B2C module* side: no flow exists for an individual to actually obtain
   a `UserEntitlementEntity` (subscribe to AI Tutor, Exam Prep, …), and no UI
   consumes USER-scoped modules. Still "later maybe" — do not build until asked.
8. **(raised 2026-07-07, explicit decision):** A grades feature/UI was
   considered next (`GRADES_VIEW`/`GRADES_EDIT` permissions have existed in
   the seed data since the start, unused by any real feature or data model —
   see `UX_ROADMAP.md`'s PARENT/STUDENT section) but **deliberately deferred**
   in favor of the Bale bot integration (§7 Done) instead: elementary grading
   in Iranian schools is typically **توصیفی** (descriptive/qualitative — a
   short written evaluation, not a number), while higher grade levels use a
   **numeric** scale — a single `Grade` shape can't represent both without a
   real design decision on how (or whether) one `ClassEntity`/school
   distinguishes the two. Compounding that, there is no chosen exam plan yet,
   which a numeric grading model would presumably need to reference. Do not
   build a grades data model or UI assuming either scheme until both of these
   are explicitly resolved — this is *not* the same kind of "not scheduled
   yet" as e.g. the TEACHER roster view was (§7 Done); it's blocked on a real
   product decision, not just sequencing.
