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

**Before starting substantive work on anything listed in §7 "Open /
explicitly deferred", any §11 Open Question, or a priority item from
`UX_ROADMAP.md`** (§6.6's order of attack, or any item flagged as the next
thing to build), you MUST first run `git fetch origin master`, then check
BOTH of the following — they catch two different failure modes, not one:

1. **Already finished (no concurrency involved at all):** run
   `git log HEAD..origin/master --oneline` and, if your branch/worktree is
   behind, read **master's own copy** of the relevant CLAUDE.md/
   UX_ROADMAP.md section (`git show origin/master:CLAUDE.md`), not just the
   copy checked out in your worktree. A worktree/branch created from an
   older commit can sit stale for a long time; treating your local
   checkout's "Open" marker as ground truth, without ever diffing against
   current master, is how an already-merged, already-documented resolution
   gets silently redone from scratch. **This file being up to date in your
   worktree proves nothing about whether master has moved past it.**
2. **In flight right now (genuine concurrency):** run `gh pr list --state
   open` for work already in progress on the same item. A parallel
   session's doc update only lands here once it merges, so an in-progress
   branch is invisible to a plain file read by construction.

This project has repeatedly hit both: parallel sessions independently
resolving the same open item differently (twice for a moshavere-port
module, once for the §11 Q10 free-tier/entitlement decision — see the
"merge note" entries in §7 for the module case), and separately, work
resuming on a stale worktree that redid something master had already
finished. If you find either — overlapping in-flight work, or the item
already resolved on master — stop and tell the user rather than building a
second independent resolution or silently redoing finished work. Once you
do resolve an item, keep following this repo's existing convention of
updating the relevant CLAUDE.md/UX_ROADMAP.md section in the *same* commit
as the code, not a follow-up one — see any §7 Done entry for the expected
shape (what changed, why, what was explicitly deferred, test coverage).

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
(which doesn't exist yet — see §7) just to onboard a new tenant. A new school also
receives the **free tier** at creation — `ACADEMIC` + `FINANCE` entitlements
(§11 Q10, resolved 2026-07-20); `CURRICULUM`/`STUDY_PLANNER`/`EXAM` are paid
plans, grantable only via the facade until real billing exists. Genuinely
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
│   ├── internal/                   school metadata. Also owns the school's
│   │   ├── SchoolEntity / SchoolRepository     optional unique slug (branded
│   │   ├── SchoolFacadeImpl                    entry URL /s/{slug} — §4, §11
│   │   └── SchoolSeedDataRunner    (dev-only: seeds one demo school,          Q9; validated as a DNS label in
│   │                                 slug "nemooneh")                          SchoolFacadeImpl so it can become
│   └── shared/                                                                 the subdomain in Tier 2 unchanged)
│       ├── SchoolError.java (sealed: SchoolNotFound, InvalidSlug,
│       │                      SlugAlreadyTaken) + SchoolException.java
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
│   ├── GradeController.java        (REST: POST/GET grades — §11 Q8, resolved
│   │                                 2026-07-18. Class-level @RequiresModule("ACADEMIC");
│   │                                 both methods deliberately gated by GRADES_EDIT,
│   │                                 not GRADES_VIEW — GRADES_VIEW is held by
│   │                                 STUDENT/PARENT too, and this endpoint returns a
│   │                                 whole class's grades, so gating it on VIEW would
│   │                                 let a student see classmates' grades by calling
│   │                                 the admin endpoint directly. See §4's Grade
│   │                                 subsection.)
│   ├── MyGradesController.java     (REST: GET /api/me/schools/{id}/grades — the "my"
│   │                                 pattern, same trust model as MyInvoicesController:
│   │                                 own grades (findMyEnrollment) + linked wards'
│   │                                 grades (listWards), no @RequiresPermission/
│   │                                 @RequiresModule at all.)
│   ├── internal/
│   │   ├── ClassEntity (schoolId, name, teacherId nullable, section,
│   │   │                 gradingScheme — §11 Q8, resolved) / ClassRepository
│   │   ├── StudentEntity (userId, schoolId, classId, enrolledAt) / StudentRepository
│   │   │   — StudentEntity is NOT an identity; the person is the iam UserEntity.
│   │   │     enrollStudent() internally calls IamFacade.findOrCreateUser +
│   │   │     createMembership(..., "STUDENT") before creating the StudentEntity.
│   │   ├── StudentGuardianEntity (schoolId, studentId — real FK within this
│   │   │                           module, guardianUserId — opaque Long, same
│   │   │                           convention as StudentEntity.userId, linkedAt)
│   │   │                           / StudentGuardianRepository — see §4
│   │   ├── GradeEntity (schoolId, studentId, classId, academicYear, term,
│   │   │                 scheme — copied from the class at record time, same
│   │   │                 convention as InvoiceEntity.amount — descriptiveLevel
│   │   │                 nullable, numericScore nullable, comment nullable,
│   │   │                 recordedByUserId nullable, recordedAt; unique(studentId,
│   │   │                 classId, academicYear, term)) / GradeRepository — see
│   │   │                 §4's Grade subsection
│   │   └── AcademicFacadeImpl
│   └── shared/
│       ├── AcademicError.java (sealed: StudentNotFound, ClassNotFound,
│       │                        GuardianNotEligible, StudentNotInClass,
│       │                        GradingSchemeMismatch, InvalidGrade, GradeNotFound)
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
├── studyplan/                  ← per-school study plans + auto-scheduling
│   ├── StudyPlanFacade.java        (moshavere port, step 3 —
│   ├── PlanController.java         MOSHAVERE_PORT_SPEC.md §6). Depends on
│   ├── WeekCourseController.java   CurriculumFacade (topic validation) and
│   ├── IntervalScheduleController  AcademicFacade (student validation) — the
│   │                       .java   first module with two new-module Facade
│   ├── StudyIntervalController     dependencies at once.
│   │                       .java
│   ├── MyStudyIntervalController   «برنامه‌ی مطالعه‌ی من» (student self-view,
│   │                       .java   built 2026-07-17 for the Claude Design
│   │                               handoff — see §7 Done, "Student study-plan
│   │                               self-view"). GET/PATCH
│   │                               /api/me/schools/{schoolId}/study-intervals —
│   │                               same "my" trust model as MyEnrollmentController/
│   │                               MyExamsController (no @RequiresModule/
│   │                               @RequiresPermission, studentId resolved via
│   │                               AcademicFacade.findMyEnrollment, never
│   │                               client input). PATCH goes through the new
│   │                               StudyPlanFacade.updateOwnInterval, not the
│   │                               admin updateInterval — the admin one only
│   │                               checks schoolId, not studentId (fine for a
│   │                               STUDYPLAN_MANAGE caller, who is already
│   │                               authorized school-wide; unsafe for a
│   │                               student self-service endpoint, where any
│   │                               authenticated student could otherwise edit
│   │                               another student's interval by guessing its
│   │                               id). Topic names are resolved server-side
│   │                               via CurriculumFacade.listTopics and embedded
│   │                               in the response, since curriculum's own
│   │                               endpoints are CURRICULUM_VIEW/MANAGE-gated
│   │                               (SCHOOL_ADMIN/TEACHER only — the bank
│   │                               carries correct answers) and a student
│   │                               can't call them directly.
│   ├── internal/                   Course weighting is a real child table
│   │   ├── PlanEntity /             (plan_course_weights), NOT the JSON column
│   │   │    PlanRepository          moshavere used — same join-table-over-array
│   │   ├── PlanCourseWeightEntity   preference already applied to
│   │   │    / …Repository           WeekCourseTopicEntity/RolePermissionEntity.
│   │   ├── WeekCourseEntity /       default/currentSchedule stay jsonb (real
│   │   │    …Repository             nested day→interval shape, spec §6).
│   │   ├── WeekCourseTopicEntity /
│   │   │    …Repository             StudentPlanAssignmentEntity keeps the
│   │   ├── StudentPlanAssignment    student↔plan link *inside* this module
│   │   │    Entity / …Repository    (opaque studentId, no FK/column added to
│   │   ├── StudyIntervalEntity /    academic.StudentEntity) — same
│   │   │    …Repository             opaque-cross-module-reference convention
│   │   ├── StudyIntervalSubTopic    as StudentGuardianEntity.guardianUserId.
│   │   │    Entity / …Repository
│   │   ├── IntervalScheduleEntity   SliceScheduleInterval/FillSlices/Suggestor
│   │   │    / …Repository           are a faithful logic port of moshavere's
│   │   └── StudyPlanFacadeImpl       table_suggester.py, with two deliberate
│   └── shared/                      normalizations: the block-size counter is
│       ├── StudyPlanError.java      per-call state, not a module-level mutable
│       │    (sealed, 14 cases)      dict (moshavere's version leaked across
│       ├── StudyPlanException.java  runs — a bug, not behavior), and days are
│       ├── StudyPlanProblemResolver walked Saturday→Friday (moshavere's
│       │    .java                   Friday-first order was a Postgres jsonb
│       └── StudyPlanExceptionHandler key-sort artifact, not a decision).
│            .java  (bean name       ObjectMapper is a private `new ObjectMapper()`,
│            "studyPlanExceptionHandler") not injected — same reason as
│                                    bale.internal.BaleWebhookService (Jackson 3
│                                    from spring-modulith-events-jackson makes
│                                    the injected bean ambiguous).
│
├── exam/                       ← exam engine: auto-generated weekly exams +
│   ├── ExamType.java               instructor-authored collective exams,
│   ├── ExamController.java         timed token-scoped exam-taking sessions,
│   ├── ExamResultController.java   on-demand scoring (moshavere port, step 4
│   ├── MyExamsController.java      and last — MOSHAVERE_PORT_SPEC.md §5). The
│   ├── ExamTakeController.java     one module in this port depending on
│   ├── internal/                   another *new* module (studyplan), on top
│   │   ├── ExamEntity / …Repository of curriculum + academic + iam.
│   │   ├── SectionEntity /
│   │   │    …Repository            ExamType (WEEKLY|COLLECTIVE) replaces
│   │   ├── SectionQuestionEntity /  moshavere's Django model-inheritance chain
│   │   │    …Repository             with a flat entity, same as ModuleScope/
│   │   ├── ExamAssignmentEntity /   IamError elsewhere. studentId (WEEKLY
│   │   │    …Repository             owner) and teacherId (COLLECTIVE author)
│   │   ├── AttemptEntity /          are two separate nullable fields, not one
│   │   │    …Repository             overloaded Exam.author like the source.
│   │   ├── SubmitEntity /
│   │   │    …Repository            ExamTokenService: a third, independently-
│   │   ├── ExamTokenService         secreted JWT (roshdeandishe.exam.token-secret),
│   │   └── ExamFacadeImpl           issued per Attempt, distinct from the
│   └── shared/                      session cookie and the mobile bearer JWT -
│       ├── ExamError.java           its own permission mechanism, not
│       │    (sealed, 13 cases)      @RequiresPermission. ExamTakeController
│       ├── ExamException.java       is permitAll + CSRF-ignored in
│       ├── ExamProblemResolver      SecurityConfig and excluded from
│       │    .java                   TenantResolverFilter (same treatment as
│       └── ExamExceptionHandler     BaleWebhookController) - the token itself
│            .java  (bean name       carries examId/studentId, so no session,
│            "examExceptionHandler") no X-School-ID, needed at all.
│
├── web/                        ← UI-composition layer (see §2 Frontend): page
    ├── LandingPageController.java     controllers that need more than one
    ├── DashboardPageController.java   module's Facade. GET / (public landing
    ├── SchoolEntryPageController.java (GET /s/{slug} — public per-school
    │                                   branded entry page, §4's School note,
    │                                   §7 Done "School entry page"; permitAll
    │                                   + excluded in TenantResolverFilter,
    │                                   no navbar — same anonymous trust model
    │                                   as the landing page. SUSPENDED schools
    │                                   render the 404 not-found state — the
    │                                   "don't advertise suspended schools"
    │                                   presentation rule lives here, not in
    │                                   SchoolFacade.findSchoolBySlug.)
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
    ├── RoleLabels.java                described separately below; GET /finance
    ├── CurriculumPageController.java
    ├── StudyPlansPageController.java
    ├── ExamsPageController.java
    ├── MyExamsPageController.java
    ├── ExamTakePageController.java
    ├── MyStudyPlanPageController.java
    ├── GradesPageController.java
    └── MyGradesPageController.java
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
                                  for SCHOOL_ADMIN only. CurriculumPageController
                                  (GET /curriculum, built 2026-07-17 — first
                                  frontend slice of the moshavere port,
                                  see the `curriculum` module entry above and
                                  §7 Done) is another thin shell, same pattern —
                                  no new `web`→curriculum Java dependency,
                                  curriculum.js composes TopicController/
                                  QuestionController directly. Navbar link
                                  visible for SCHOOL_ADMIN/TEACHER (both hold
                                  CURRICULUM_MANAGE/CURRICULUM_VIEW identically).
                                  StudyPlansPageController (GET /study-plans,
                                  built 2026-07-17, second moshavere-port
                                  frontend slice, see the `studyplan` module
                                  entry above and §7 Done) is the same thin-shell
                                  pattern — study-plans.js composes
                                  PlanController/IntervalScheduleController/
                                  StudyIntervalController plus the existing
                                  curriculum/academic/iam endpoints for topic
                                  and student names, no new `web` Java
                                  dependency. Navbar link visible for
                                  SCHOOL_ADMIN/TEACHER. ExamsPageController
                                  (GET /exams, built 2026-07-17, last
                                  moshavere-port slice, see the `exam` module
                                  entry above and §7 Done) is the same
                                  thin-shell pattern for the admin/teacher
                                  surface. MyExamsPageController (GET
                                  /my-exams) and ExamTakePageController (GET
                                  /exam-take) are the two student-facing
                                  pages built in the same pass — see §7 Done
                                  for why exam-take is a second, separate,
                                  navbar-less, permitAll page rather than a
                                  section of /my-exams. MyStudyPlanPageController
                                  (GET /my-study-plan, built 2026-07-17 from a
                                  Claude Design handoff bundle — see §7 Done,
                                  "Student study-plan self-view") is a thin
                                  shell like /my-exams — no X-School-ID, data
                                  entirely client-side against the new
                                  studyplan.MyStudyIntervalController. Navbar
                                  link visible for STUDENT only. GradesPageController
                                  (GET /grades, built 2026-07-20 via the
                                  `design-page` skill — first UI slice for the
                                  Grade model, §11 Q8, backend-only until now)
                                  is a thin shell like /finance: class + academic
                                  year + term selector, then an editable
                                  gradebook table (one row per enrolled student,
                                  a select for DESCRIPTIVE classes or a 0–20
                                  number input for NUMERIC ones, resolved from
                                  each class's own `gradingScheme`), entirely
                                  client-side against the existing
                                  `academic.GradeController` — reuses the
                                  `classifyError` two-403 pattern verbatim
                                  (ACADEMIC is now free-tier, §11 Q10, but can
                                  still be revoked via the facade, so the
                                  module-not-entitled state is still real).
                                  Navbar link visible for SCHOOL_ADMIN/TEACHER.
                                  MyGradesPageController (GET /my-grades, same
                                  pass) is a thin shell like /my-invoices —
                                  no X-School-ID, data entirely client-side
                                  against the existing `academic.MyGradesController`.
                                  Navbar link visible for STUDENT/PARENT.
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
| `TEACHER` | `GRADES_VIEW`, `GRADES_EDIT`, `STUDENT_ENROLL`, `CLASS_VIEW`, `CURRICULUM_VIEW`, `CURRICULUM_MANAGE`, `STUDYPLAN_VIEW`, `STUDYPLAN_MANAGE`, `EXAM_VIEW`, `EXAM_MANAGE` |
| `PARENT` | `GRADES_VIEW`, `FINANCE_VIEW` |
| `SCHOOL_ADMIN` | `GRADES_VIEW`, `GRADES_EDIT`, `FINANCE_VIEW`, `FINANCE_MANAGE`, `MEMBERSHIP_MANAGE`, `CLASS_MANAGE`, `CLASS_VIEW`, `STUDENT_ENROLL`, `GUARDIAN_MANAGE`, `EXPENSE_VIEW`, `EXPENSE_MANAGE`, `EXPENSE_APPROVE`, `ROLE_MANAGE`, `CURRICULUM_VIEW`, `CURRICULUM_MANAGE`, `STUDYPLAN_VIEW`, `STUDYPLAN_MANAGE`, `EXAM_VIEW`, `EXAM_MANAGE` |
| `FINANCE_ADMIN` | `FINANCE_VIEW`, `FINANCE_MANAGE`, `EXPENSE_VIEW`, `EXPENSE_MANAGE` |

**`GRADES_EDIT` granted to `SCHOOL_ADMIN` (added 2026-07-18, alongside actually
building the grades feature — §11 Q8, resolved):** `GRADES_VIEW`/`GRADES_EDIT`
existed in seed data since the very start of the project, unused by any real
feature — `GRADES_EDIT` had only ever been granted to `TEACHER`. Now that the
feature is real, this asymmetry is fixed to match every other `*_MANAGE`-tier
permission (`FINANCE_MANAGE`, `CLASS_MANAGE`, `EXPENSE_MANAGE`,
`CURRICULUM_MANAGE`, `STUDYPLAN_MANAGE`, `EXAM_MANAGE`), all of which
`SCHOOL_ADMIN` already holds alongside `TEACHER`. **`GradeController`'s two
endpoints are deliberately gated by `GRADES_EDIT`, not `GRADES_VIEW`** — see
§3/§4's Grade subsection: `GRADES_VIEW` is held by `STUDENT`/`PARENT` too, and
this endpoint returns a *whole class's* grades, so gating a class-wide list on
`VIEW` would let a student see classmates' grades by calling the admin
endpoint directly. `GRADES_VIEW` stays exactly as seeded (unchanged) and is
inert for `STUDENT`/`PARENT` today — their own-grade access runs entirely
through `MyGradesController`'s "my" pattern (no permission check at all), same
precedent as `MyInvoicesController`/`MyExamsController`.

**`CURRICULUM_VIEW`/`CURRICULUM_MANAGE` (added 2026-07-16, for the `curriculum`
module — moshavere port step 2, resolving the spec's "exact permission split
TBD"):** both granted to `SCHOOL_ADMIN` *and* `TEACHER` — authoring the
question bank is day-to-day teacher work (the `STUDENT_ENROLL` reasoning, not
the `CLASS_MANAGE` one; spec §2: "each school's own teachers own their own
topics/questions/plans"). Deliberately **not** granted to `STUDENT`/`PARENT`,
not even `CURRICULUM_VIEW`: a question row carries `correctChoice` and the
`answer` explanation, so raw bank read access leaks answers — student access
is instead mediated by the `exam` module's scoped exam-take token (§4's
Exam subsection, moshavere port step 4), never by reading the bank directly.
The VIEW/MANAGE split exists despite identical
system grants today so a *custom role* (§4's Role subsection) can be composed
read-only — same reasoning as `CLASS_VIEW`.

**`ROLE_MANAGE` (added 2026-07-13, for custom role management — see §4's
Role subsection):** deliberately `SCHOOL_ADMIN`-only, not granted to
`FINANCE_ADMIN` — the ability to define what a role can do is as sensitive as
`MEMBERSHIP_MANAGE` (who holds a role), arguably more so, since it shapes what
*every* role, including ones not yet created, is allowed to touch.

**`STUDYPLAN_VIEW`/`STUDYPLAN_MANAGE` (added 2026-07-16, `studyplan`
module — moshavere port, step 3):** both granted to `SCHOOL_ADMIN` *and*
`TEACHER`, same reasoning as `CURRICULUM_VIEW`/`CURRICULUM_MANAGE` — the
plan author in moshavere is the `Instructor`, so authoring/scheduling a
study plan is day-to-day teacher work, not admin-only. Deliberately not
granted to `STUDENT`/`PARENT` — a student self-view of their own study
schedule is a plausible later `/api/me`-style slice, not built
speculatively here.

**`EXAM_VIEW`/`EXAM_MANAGE` (added 2026-07-17, `exam` module — moshavere
port, step 4 and last):** both granted to `SCHOOL_ADMIN` *and* `TEACHER`,
same reasoning as `CURRICULUM_*`/`STUDYPLAN_*` — authoring/assigning exams
is day-to-day teacher work. Deliberately **not** granted to `STUDENT`/
`PARENT`, same reasoning as `CURRICULUM_VIEW` — these two permissions gate
the *admin/instructor* REST surface (`ExamController`/`ExamResultController`)
only; a student's own access to their exams runs entirely through
`MyExamsController` (the "my" pattern, no `@RequiresPermission` at all) and
`ExamTakeController` (its own exam-take-token mechanism), never through
either of these two permissions.
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
├── code (ACADEMIC, FINANCE, CURRICULUM, STUDY_PLANNER, EXAM, LIBRARY,
│         ATTENDANCE, TRANSPORTATION, AI_TUTOR, EXAM_PREP, PORTFOLIO_BUILDER,
│         TEACHER_MARKETPLACE, PROFESSIONAL_DEVELOPMENT)
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

**Free tier (§11 Q10, resolved 2026-07-20):** `IamFacade.grantFreeTierEntitlements(schoolId)`
grants `ACADEMIC` + `FINANCE` (the `FREE_TIER_MODULES` list in `IamFacadeImpl` —
iam owns the pricing rule, one place to change when billing arrives), called by
`SchoolController.createSchool` right after the creator's `SCHOOL_ADMIN`
membership. `CURRICULUM`/`STUDY_PLANNER`/`EXAM` are paid plans — never granted
at creation, facade-only until real billing exists. Idempotent (rides on
`setSchoolEntitlement`).

### School (owned by the `school` module, not `iam`)

```
SchoolEntity
├── id
├── name
├── status (ACTIVE | SUSPENDED)
├── timezone
├── locale
├── schoolLevel (SchoolFacade.SchoolLevel: ELEMENTARY | LOWER_SECONDARY | UPPER_SECONDARY —
│                 added 2026-07-18, §7 Done "School/class level classification")
└── slug        (nullable, unique — added 2026-07-19, Tier 1 of §11 Q9. The school's
                  optional branded entry URL: /s/{slug}. Validated in SchoolFacadeImpl
                  as a DNS label on purpose — lowercase [a-z0-9] with inner hyphens,
                  3–63 chars, reserved names (www/api/admin/…) blocked, normalized to
                  lowercase — so the exact same value becomes the school's subdomain
                  in Tier 2 with zero migration. Set at creation only; there is no
                  "edit my school's slug" endpoint yet — that needs a school-settings
                  permission decision that doesn't exist, see §11 Q9.)
```

Created via `SchoolFacade.createSchool`, always self-service (§1).
`SchoolFacade.findSchoolBySlug(slug)` backs the public entry page
(`web.SchoolEntryPageController`, §7 Done "School entry page") — it does NOT
filter by status; the "don't advertise a SUSPENDED school" rule is
presentation, applied in the page controller. `setSchoolStatus`
(suspension) exists on the facade but, like entitlement toggling, has no REST
endpoint yet for the same platform-privilege reason.

**`SchoolLevel`** (added 2026-07-18, resolving half of §11 Q8's blocker) — Iran's
three-tier school system, required at creation, one level per school (a real
دبستان building only ever has grades 1–6, never a K-12 campus):
`ELEMENTARY` (دبستان, grades 1–6), `LOWER_SECONDARY` (متوسطه اول, grades 7–9),
`UPPER_SECONDARY` (متوسطه دوم, grades 10–12). Each variant carries its own
`minGrade()`/`maxGrade()`/`includesGrade(int)` — the single source of truth for
the grade range, consumed by `academic.ClassEntity.gradeLevel` validation below.
**This is classification only, not a grading scheme** — see the `Class` entry
below and §11 Q8 for what's deliberately *not* built yet on top of it.

### Class (owned by `academic`)

```
ClassEntity
├── id
├── schoolId
├── name           (e.g. "Grade 5 - A" — free-text display label; does not need to
│                    agree with gradeLevel below, though new classes should)
├── teacherId       (nullable — a class may exist before a teacher is assigned;
│                    references an iam userId, not enforced at the DB level since
│                    academic has no FK into iam's tables)
├── section
├── gradeLevel      (int, required — added 2026-07-18. Validated at creation against
│                     the owning school's SchoolLevel range (school.SchoolFacade.SchoolLevel,
│                     AcademicFacadeImpl.requireValidGradeLevel) — a grade outside that
│                     range is AcademicError.InvalidGradeLevel, 400. This is the first
│                     thing `academic` depends on `school`'s Facade for; previously
│                     schoolId was a fully opaque Long with zero cross-module validation.)
└── gradingScheme   (GradingScheme: DESCRIPTIVE | NUMERIC — §11 Q8, resolved
                      2026-07-18, same day as gradeLevel above (two parallel
                      sessions, reconciled — see the Grade entry below).
                      Per-class, explicit and overridable on the class itself,
                      not derived at read time — but its *default*, when a
                      caller doesn't set it explicitly, is computed from
                      gradeLevel: 1–6 → DESCRIPTIVE, 7–12 → NUMERIC (in
                      ClassController.CreateClassRequest's compact
                      constructor, not here). Column stays nullable at the DB
                      level so adding it to the existing table doesn't break
                      dev data.)
```

### Grade (owned by `academic` — resolves §11 Q8)

A student's grade in a class for one term. Building this required four explicit
decisions (all made 2026-07-18, each a real fork — see §11 Q8 for the options
considered and rejected):

1. **Scheme is per-class, explicit and overridable** (`ClassEntity.gradingScheme`
   above), not per-school and not *solely* derived from a grade-level rule —
   though its default *is* now derived from `ClassEntity.gradeLevel` (a
   parallel session's classification work, reconciled the same day — see the
   `Class` entry above): 1–6 → `DESCRIPTIVE`, 7–12 → `NUMERIC`, still
   overridable per class at creation.
2. **Manual entry only** — a teacher enters a grade directly; it is not derived
   from `exam` module results. Real grades blend homework, participation, and
   exams, not just exam scores; deriving from `exam` is a plausible future
   extension, not built here (YAGNI).
3. **Descriptive grades use the real Iranian Ministry-of-Education four-level
   scale** (`DescriptiveGradeLevel`: `EXCELLENT`/`GOOD`/`ACCEPTABLE`/
   `NEEDS_EFFORT` — خیلی خوب/خوب/قابل قبول/نیازمند تلاش), not free text, plus an
   optional comment — matches what a real report card looks like.
4. **Cadence is per term** (`GradingTerm`: `FIRST`/`SECOND` — نوبت اول/دوم),
   paired with a plain `academicYear` string (e.g. "1404-1405") — the real
   Iranian report-card structure, not a running unstructured log.

```
GradeEntity   (unique(studentId, classId, academicYear, term))
├── id
├── schoolId
├── studentId       (real FK — academic's own StudentEntity)
├── classId         (real FK — academic's own ClassEntity)
├── academicYear    (plain String, e.g. "1404-1405" — no calendar-year validation,
│                     same manual-validation-only bias as RoleEntity.code)
├── term            (GradingTerm)
├── scheme          (GradingScheme — copied from ClassEntity.gradingScheme at
│                     record time, not just referenced, so a later change to a
│                     class's scheme never retroactively alters past grades —
│                     same convention as InvoiceEntity.amount copied from
│                     TuitionFee at issue time)
├── descriptiveLevel (nullable DescriptiveGradeLevel — populated iff scheme is
│                      DESCRIPTIVE)
├── numericScore    (nullable BigDecimal(4,2), 0–20 — populated iff scheme is
│                     NUMERIC; validated in-range in AcademicFacadeImpl,
│                     not Bean Validation, same convention as everywhere else
│                     in this codebase)
├── comment         (nullable — optional note, either scheme)
├── recordedByUserId (nullable — the teacher/admin who entered it; nullable per
│                      the standard actorUserId convention, §3's audit pattern)
└── recordedAt
```

`AcademicFacade.recordGrade(schoolId, studentId, classId, academicYear, term,
descriptiveLevel, numericScore, comment)` — **idempotent upsert**: re-recording
the same `(studentId, classId, academicYear, term)` updates the existing row
(a teacher correcting a mistake) rather than duplicating — same pattern as
`setTuitionFee`/`assignPlanToStudent`. Validates: the student must currently be
enrolled in that exact class (`AcademicError.StudentNotInClass` otherwise — a
grade can't be recorded against a class the student has since moved out of);
exactly the field matching the class's own `gradingScheme` must be populated
(`AcademicError.GradingSchemeMismatch` otherwise — the scheme is not a request
parameter, it's read from `ClassEntity`); a numeric score must be in `[0, 20]`
(`AcademicError.InvalidGrade` otherwise).

`AcademicFacade.listGradesForClass(schoolId, classId, academicYear, term)` — a
teacher's gradebook view for one class/term. **Exposed at
`GET /api/admin/schools/{schoolId}/grades`, deliberately gated by
`GRADES_EDIT`, not `GRADES_VIEW`** (§4's Permission section, §7 Done) — see
`GradeController`'s note in §3.

`AcademicFacade.listGradesForStudents(schoolId, studentIds)` — backs
`MyGradesController` (own grades + linked wards' grades), same shape as
`FinanceFacade.listInvoicesForStudents`.

No UI page yet — same posture as `curriculum`/`studyplan` at their own
new-module time: backend + REST + tests first; a `/grades` gradebook page and
a `/my-grades` self-view are a separate slice, through the `design-page` skill
when built.

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
neither). **Management UI: `GET /curriculum` (`web.CurriculumPageController`,
built 2026-07-17 via the `design-page` skill)** — a thin shell like every
other management page, entirely client-side against the REST endpoints
above. Topic tree rendered as a flattened, depth-indented list (computed
client-side from `/topics/tree`, not a recursive JTE template) doubling as
both the navigation panel and the parent-picker/topic-picker dropdown
source. Two box-level creation forms (topic, question) rather than one
header-level primary action — same multi-entity-page precedent as
`/expenses` (categories/vendors/expenses). Selecting a topic reuses the
backend's subtree-inclusive filter as-is (picking a parent topic shows its
descendants' questions too, unchanged from the API semantics). Behind
`@RequiresModule("CURRICULUM")`, so it reuses the `classifyError`
two-kinds-of-403 pattern from `finance.js`/`expenses.js` verbatim. Navbar
link visible for `SCHOOL_ADMIN`/`TEACHER` (both hold `CURRICULUM_MANAGE`/
`CURRICULUM_VIEW` identically, unlike Classes' admin/day-to-day split).
`discriminationIndex` is deliberately not exposed on the create-question
form — nothing in this codebase computes it yet, so a raw manual-entry
field would have no clear meaning; it stays server-side-only until an
analytics feature needs it.

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

### Plan, WeekCourse, StudentPlanAssignment, StudyInterval, IntervalSchedule (owned by `studyplan` — moshavere port, step 3)

Per-school study plans with course weighting, a per-week topic schedule, one
active plan-per-student assignment, actual scheduled study blocks, and a
student's weekly free-time template — the third of four pieces
`MOSHAVERE_PORT_SPEC.md` §6 lays out for the moshavere port, ported from
`agency-panel-master/plan` + `exam-maker-master/{plan,study_table}`.

```
PlanEntity
├── id
├── schoolId
├── teacherId       (nullable, opaque Long → iam User — same no-FK convention
│                     as ClassEntity.teacherId; taken from the current
│                     authenticated user at creation, null from a userless
│                     caller such as a test/seed)
├── title
└── createdAt

PlanCourseWeightEntity   (a real child table, NOT moshavere's course_info
├── id                    JSON column — same join-table-over-array preference
├── planId                already applied to RolePermissionEntity/
├── topicId               WeekCourseTopicEntity; unique(planId, topicId))
├── weight          (>= 0, same COURSE_INFO_SCHEMA constraint as moshavere)
└── priority        (>= 1; the auto-scheduler walks weights ordered by
                      priority ascending then id — a deterministic
                      replacement for "JSON array order", which a join table
                      no longer has)

WeekCourseEntity
├── id
├── planId
├── weekStart       (LocalDate; must be a Saturday — InvalidWeekStart,
│                     ported from moshavere's DB CheckConstraint on
│                     date's weekday)
└── courseTopicId   (opaque Long → curriculum Topic; unique(planId,
                      weekStart, courseTopicId), same as moshavere's
                      unique_together)

WeekCourseTopicEntity    (sub-topics for a WeekCourse — moshavere's
├── id                    ArrayField(size=10) ported to a real join table,
├── weekCourseId          same reasoning as PlanCourseWeightEntity above;
└── topicId               facade enforces the size-10 cap)

StudentPlanAssignmentEntity   (the student↔plan link lives *inside*
├── id                         studyplan, not as a planId column on
├── schoolId                   academic.StudentEntity — same opaque
├── studentId       (unique — one active plan per student, upsert on           cross-module-reference convention as
│                     reassignment, same FK-tightness as moshavere's           StudentGuardianEntity.guardianUserId;
│                     single Student.plan FK)                                  keeps academic unaware studyplan exists)
├── planId
└── assignedAt

StudyIntervalEntity
├── id
├── schoolId
├── studentId
├── topicId
├── startTime / endTime   (LocalDateTime — wall-clock study block, not an
│                           Instant-stamped event)
├── done            (boolean, default false)
└── testCount       (int, default 0)

StudyIntervalSubTopicEntity   (sub-topics of one interval — join table,
├── id                         ported from moshavere's ManyToMany sub_topics)
├── intervalId
└── topicId

IntervalScheduleEntity   (one row per student — unique(studentId))
├── id
├── schoolId
├── studentId
├── defaultSchedule (jsonb: {SAT: [{start,end}, …], SUN: […], …} — real
│                     nested shape stays jsonb per spec §6, unlike course
│                     weighting above which became a real table)
├── currentSchedule (jsonb, nullable — a temporary override)
└── lastEditedAt    (Instant, nullable — replaces moshavere's "edited this
                      week" Persian-calendar weekday check with a plain
                      timestamp comparison: currentSchedule is only
                      effective if lastEditedAt is on or after the most
                      recent Saturday)
```

`StudyPlanFacade.createPlan(schoolId, title, weights)` validates every
weighted `topicId` against `CurriculumFacade.listTopics(schoolId)`
(`TopicNotFound` for a foreign/unknown topic — the same "foreign school =
not found" convention as `ClassNotFound`/`CurriculumError.TopicNotFound`),
plus the weight/priority/no-duplicate-topic rules above (`InvalidPlan`).
`assignPlanToStudent` validates the student via
`AcademicFacade.findStudentInfo` and upserts the single assignment row.
`suggestWeek(schoolId, studentId, weekStart)` is the auto-scheduler: `weekStart`
must be a Saturday, the target week must have zero existing intervals
(`WeekNotEmpty`), the student must have an assigned plan
(`PlanNotAssigned`), and the effective free-time schedule (current if
edited since the most recent Saturday, else default) must yield at least
180 minutes total (`ScheduleTooSmall`) — all four are direct ports of
moshavere's `StudyViewSet.suggest` checks. The algorithm itself
(`internal.SliceScheduleInterval` → `internal.FillSlices` →
`internal.Suggestor`) is a faithful logic port of
`study_table/table_suggester.py`'s three classes, with two deliberate
normalizations flagged during the port (neither changes the algorithm's
actual output, only its implementation hygiene): the block-size counter
is now per-call state instead of a dict `table_suggester.py` held at
module scope (the Python version leaked counts across unrelated calls —
a bug, not behavior worth preserving), and the seven weekdays are walked
Saturday→Friday in `StudyPlanFacade.Weekday` (moshavere's own
Friday-first iteration order was an artifact of Postgres jsonb key
sorting, not a decision). `copyWeek` validates every target student and
checks all overlaps *before* writing any interval, so a failure partway
through a multi-target copy never leaves one target half-populated (a
small correctness improvement over moshavere's row-by-row
`copy_week`, which could). `getRecentTopics(studentId)` — the method
`exam`'s weekly auto-generation will depend on (`MOSHAVERE_PORT_SPEC.md`
§5) — returns the distinct topics (with merged sub-topics) studied in the
current calendar week, a direct port of
`week_functions.get_week_intervals_topics`.

REST: `PlanController` (`POST`/`GET .../study-plans`, plus
`POST`/`GET .../study-plans/{planId}/assignments`), `WeekCourseController`
(`POST`/`GET .../study-plans/{planId}/week-courses`),
`IntervalScheduleController` (`GET`/`PUT .../students/{studentId}/schedule`,
`.../schedule/default`, `.../schedule/current`), `StudyIntervalController`
(`POST`/`GET`/`PATCH`/`DELETE .../students/{studentId}/study-intervals`,
plus `/suggest`, `/clear`, `/copy`) — all class-level
`@RequiresModule("STUDY_PLANNER")` + per-method `@RequiresPermission`
(`STUDYPLAN_MANAGE` write / `STUDYPLAN_VIEW` read — see §4's Permission
section for who holds them). No audit trail and no UI page yet — same as
`curriculum`, nothing here is money-like or reacted-to, and the spec ports
backend domain logic only (§7 there).

### Exam, Section, SectionQuestion, ExamAssignment, Attempt, Submit (owned by `exam` — moshavere port, step 4 and last)

The exam engine: auto-generated weekly exams (built from a student's recent
study-plan topics), instructor-authored collective exams, timed token-scoped
exam-taking sessions, and on-demand scoring — the fourth and final piece
`MOSHAVERE_PORT_SPEC.md` §5 laid out for the moshavere port, ported from
`exam-maker-master/azmoon`, built 2026-07-17. Depends on `curriculum`
(topics/questions), `academic` (students), `studyplan`
(`StudyPlanFacade.getRecentTopics`), and `iam` — the one place in this port
where a new module depends on another new module.

```
ExamEntity
├── id
├── schoolId
├── examType        (enum WEEKLY | COLLECTIVE — flat entity + explicit enum,
│                     replacing moshavere's Django model-inheritance chain
│                     Exam → PersonalExam → WeeklyExam/CollectiveExam, same
│                     preference as ModuleScope/IamError elsewhere)
├── title            (nullable — required for COLLECTIVE, always null for WEEKLY)
├── teacherId         (nullable, opaque Long → iam User — COLLECTIVE's author,
│                       captured from the current authenticated caller, same
│                       convention as PlanEntity.teacherId)
├── studentId         (nullable, opaque Long → academic Student — WEEKLY's
│                       owner. teacherId/studentId are two separate nullable
│                       fields, not one overloaded Exam.author column like
│                       the source — clearer given the two exam types have
│                       genuinely different "owner" semantics)
├── startAt / endAt   (LocalDateTime — the exam's scheduled window)
├── durationMinutes   (per-attempt allotted time, independent of the window)
└── weekStart         (LocalDate, WEEKLY only — must be Saturday)

SectionEntity            (a topic-scoped section of the exam; unique(examId, topicId))
├── id / examId / topicId (opaque → curriculum Topic) / name (nullable)

SectionQuestionEntity     (the specific questions chosen for a section — manual
├── id / sectionId /       pick or random-from-subtree; unique(sectionId, questionId))
│    questionId (opaque → curriculum Question)

ExamAssignmentEntity      (which students may attempt a COLLECTIVE exam — a
├── id / examId /          real join table, replacing moshavere's
│    studentId             CollectiveExam.students ManyToMany, same
                           join-table-over-ORM-relation preference as
                           RolePermissionEntity/WeekCourseTopicEntity;
                           unique(examId, studentId))

AttemptEntity              (unique(examId, studentId) — "we only support
├── id / examId / studentId single-attempt exams", same invariant as the
├── startedAt (Instant)     source's own comment. expireAt is persisted
└── expireAt (Instant)      here, not a @Transient property like the
                            source's Attempt.jwt_token, since the exam-take
                            JWT itself is regenerated fresh on every call
                            rather than stored anywhere)

SubmitEntity                (a student's answer to one SectionQuestion;
├── id / sectionQuestionId /  choice constrained 1-4; unique(sectionQuestionId,
│    studentId / choice       studentId) — same as source)
```

**Exam-taking session token** (`exam.internal.ExamTokenService`): a third,
independently-secreted JWT (`roshdeandishe.exam.token-secret`, its own HMAC
key) issued per `Attempt`, distinct from both the session cookie and the
mobile bearer JWT (`roshdeandishe.jwt.secret`) — a faithful port of
`azmoon/token.py`'s scoped short-lived token, except expiry rides the JWT's
own native `exp` claim rather than a hand-rolled comparison. Every
`ExamFacade` exam-take method (`getExamForToken`/`getSectionQuestions`/
`submitAnswer`) takes the raw token string and decodes/validates it
internally — `ExamTakeController` never touches the codec directly, and
never needs `TenantContext`/`X-School-ID`: `schoolId` is resolved from the
token's `examId` via the loaded `ExamEntity`.

`ExamFacade.createCollectiveExam` validates `title` non-blank, `end > start`,
and the scheduled window is at least `durationMinutes` long
(`ExamError.InvalidExam` otherwise) — deliberately **not** the source's
"start must be ≥ now" check, a UX-layer constraint from its own admin panel,
not a domain invariant (skipping it keeps creation simple for tests/seed
callers, same as `studyplan`/`curriculum`'s own no-past-of-mind checks).
`assignStudents`/`createSection` (manual questionIds) /`createSectionAuto`
(random pick from the topic's subtree via `CurriculumFacade.listQuestions`,
`lazy=false` throws `ExamError.InsufficientQuestions` on shortfall,
`lazy=true` never does) round out COLLECTIVE authoring.

`ExamFacade.generateWeeklyExam(schoolId, studentId, weekStart)` — `weekStart`
must be Saturday; calls `StudyPlanFacade.getRecentTopics(studentId)` directly
(a plain synchronous facade call, this codebase's stated default — events
are for one-way reactions, not this) and builds one `Section` per recent
topic with up to 5 randomly-picked questions (`lazy=true`, same as source).
**Regeneration is a full delete-and-rebuild** (porting the source's
`auto-update` action) **but is refused once an `Attempt` already exists**
for that exam (`ExamError.InvalidExam`) — the source unconditionally wiped
`Attempt`/`Submit` rows via cascade on regenerate; silently destroying a
student's already-taken exam is a real data-integrity concern, the same
class of small correctness improvement already applied to
`studyplan.copyWeek`.

`ExamFacade.startAttempt` — validates the exam has started and not ended,
and the student is eligible (WEEKLY: owns it; COLLECTIVE: has an
`ExamAssignmentEntity` row) — `ExamError.CannotAttempt` otherwise.
**Deliberately more forgiving than the source**: a second call while the
existing `Attempt` hasn't expired yet returns the same still-valid token
again instead of erroring (the source raised a validation error on any
repeat `POST`) — a small UX improvement, since a client retry/refresh
shouldn't be a hard failure. The one-shot invariant itself is unchanged:
once the `Attempt` has expired, `startAttempt` throws
`ExamError.AttemptExpired` — no second chance. Note the exact precedence:
"has the exam itself ended" is checked *before* "has this attempt expired",
so once `now` is past `exam.endAt` the caller sees `CannotAttempt`, not
`AttemptExpired` — both are real, reachable errors, just gated by which
condition is true first.

**Scoring is computed on demand** in `ExamFacadeImpl.computeResult`, not via
moshavere's Postgres materialized view + Celery refresh job (no such infra
exists in this codebase, and none was stood up speculatively — spec §5's own
recommendation). `getMyResult`/`getStudentResult` require the exam to have
ended (`ExamError.ExamNotEnded`) and an `Attempt` to exist
(`ExamError.NoAttempt`), then return per-section
`correctCount`/`incorrectCount`/`unansweredCount`/`score`.
**`rank`/`balance` (cross-student standard deviation) were not ported** —
they're artifacts of the source's materialized view, not core exam data, and
would need a real cross-student aggregate query; deferred until a UI
actually asks for them, same as everything else in this module (backend-only
so far, no page — see `curriculum`/`studyplan` precedent).

REST: `ExamController` (`/api/admin/schools/{schoolId}/exams` — create/
list/get collective exams, `/assign`, `/sections` + `/sections/auto`,
`/weekly` — class-level `@RequiresModule("EXAM")` + per-method
`EXAM_MANAGE`/`EXAM_VIEW`), `ExamResultController` (instructor-side results,
`EXAM_VIEW`), `MyExamsController`
(`/api/me/schools/{schoolId}/exams` — the "my" pattern: list own exams,
start an attempt, view own result; deliberately **no**
`@RequiresPermission`/`@RequiresModule`, `studentId` resolved via
`AcademicFacade.findMyEnrollment`, never client input — same trust model as
`MyEnrollmentController`/`MyInvoicesController`), `ExamTakeController`
(`/api/exam-take/**`, token-authenticated — `permitAll` + CSRF-ignored in
`SecurityConfig`, excluded from `TenantResolverFilter`, same treatment as
`BaleWebhookController`: its own permission mechanism keyed off the exam
token, not the normal two-layer check). No UI page yet — backend domain
logic only, same as `curriculum`/`studyplan`.

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
resolved `userId`), not from these two aspects. `IamSeedDataRunner.seedDemoSchoolEntitlements()` seeds all
five B2B module entitlements for the demo school (id 1) so every module-gated
endpoint stays usable in dev. A school created self-service at runtime gets
the **free tier** (`ACADEMIC` + `FINANCE`) automatically at creation
(`IamFacade.grantFreeTierEntitlements` — §11 Q10, resolved 2026-07-20) and
will correctly get 403 `ModuleNotEntitled` on the paid modules
(`CURRICULUM`/`STUDY_PLANNER`/`EXAM`) until granted via
`setSchoolEntitlement` (facade-only, see §7 item 1).

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
    no link). `mvn test -Dtest=ModularityTests` green. **The
    `*FlowIntegrationTest` suite was NOT run in the authoring session** —
    no Docker and no local Postgres available, the same gap documented for
    the `MyStudyPlanFlowIntegrationTest` and Bale Phase 1 slices; the code
    and tests compile (`mvn test-compile`, which also precompiles the JTE
    template) and CI runs the suite for real. Treat this slice as fully
    verified only once CI is green.

### Open / explicitly deferred

1. **`PlatformContext` permission checking.** `PermissionDecisionService` denies all
   non-school-scoped checks unconditionally. Blocks: REST exposure of
   `setSchoolEntitlement` and `setSchoolStatus`. Do not build until a real need
   (billing integration, ops tooling) exists — see §5. **Consequence flagged
   2026-07-19 (§11 Q10, UX_ROADMAP.md §6.1): resolved 2026-07-20** — the
   free tier (`ACADEMIC`+`FINANCE` granted at self-service creation, §11
   Q10) unblocks real-school onboarding; paid modules (`CURRICULUM`/
   `STUDY_PLANNER`/`EXAM`) still wait on this item for any REST/billing
   exposure.
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
- ~~Do not build a grades data model or UI~~ — **resolved 2026-07-18, §11
  Q8.** The grades data model and REST layer are built (§4's Grade
  subsection, §7 Done) — the four decisions that unblocked it (per-class
  scheme defaulting from `gradeLevel` but explicit and overridable, manual
  entry only, fixed descriptive scale, per-term cadence) are documented
  there, including how this reconciles with the same-day School/class level
  classification work (§7 Done). The `/grades`/`/my-grades` **UI** is also
  now built (2026-07-20, §7 Done "Grades UI") — went through the
  `design-page` skill as required.
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
8. ~~A grades feature/UI was considered next... elementary grading in Iranian
   schools is typically descriptive... while higher grade levels use a
   numeric scale... no chosen exam plan yet either~~ — **resolved
   2026-07-18.** Both original blockers are gone: the `exam` module now
   exists (moshavere port step 4, §7 Done), and the grading-scheme question
   is answered — via **two parallel sessions that independently tackled this
   same question the same day and had to be reconciled** (§7 Done, "School/
   class level classification" and "Grades" — a real instance of the
   parallel-session-conflict pattern this project has hit before, not a
   hypothetical). One session added `school.SchoolFacade.SchoolLevel` +
   `academic.ClassEntity.gradeLevel` (a validated 1–12 classification, with a
   *planned* fixed derivation rule not yet wired to anything); the other
   built the actual `Grade` entity/REST with an *explicit, per-class*
   `gradingScheme` field, chosen deliberately over pure derivation. Reconciled
   as: **`gradeLevel`'s rule supplies `gradingScheme`'s default** (1–6 →
   `DESCRIPTIVE`, 7–12 → `NUMERIC`, in
   `ClassController.CreateClassRequest`'s compact constructor) **but the
   field stays explicit and overridable per class** — neither session's
   decision was silently discarded. The four decisions behind the `Grade`
   model itself:
   - **Scheme is per-class, explicit and overridable** (`ClassEntity.gradingScheme`),
     not per-school — with its default now derived from `gradeLevel` per the
     reconciliation above.
   - **Manual entry only**, not derived from `exam` results — real grades
     blend homework, participation, and exams; auto-deriving from exam scores
     is a plausible future extension, not built now.
   - **Descriptive grades use the real Ministry-of-Education four-level
     scale** (`DescriptiveGradeLevel`: `EXCELLENT`/`GOOD`/`ACCEPTABLE`/
     `NEEDS_EFFORT`) plus an optional comment — not free text.
   - **Cadence is per term** (`GradingTerm`: `FIRST`/`SECOND` + a plain
     `academicYear` string) — matching the real report-card structure, not a
     running unstructured log.

   See §4's Class and Grade subsections and §7 Done ("School/class level
   classification", "Grades") for the full model, `GradeController`'s
   deliberate `GRADES_EDIT`-only gating (to prevent a `STUDENT`/`PARENT`
   holder of `GRADES_VIEW` from seeing a whole class's grades), and test
   coverage. **UI built 2026-07-20** (§7 Done, "Grades UI") — `/grades`
   (admin/teacher gradebook) and `/my-grades` (student/parent self-view).

9. **New (raised 2026-07-19; Tier 1 built the same day):** Per-school domains.
   Decided as three escalating tiers, only the first of which is built:
   - **Tier 1 — branded entry URL `/s/{slug}` — BUILT** (§7 Done, "School
     entry page"): an optional unique slug on `SchoolEntity`, deliberately
     validated as a DNS label so it forward-ports to Tier 2 unchanged.
   - **Tier 2 — subdomains (`{slug}.<platform-domain>`) — deferred until a
     real deployment exists.** The code half is small (resolve the tenant
     from the `Host` header in `TenantResolverFilter`, falling back to
     `X-School-ID`); the substance is wildcard DNS + wildcard TLS +
     reverse-proxy config, none of which can be exercised before deployment
     (and per the standing no-deploy priority, features win until then). A
     session cookie scoped to the parent domain keeps the multi-school
     switcher (§11 Q2) working across subdomains.
   - **Tier 3 — true custom domains (school-owned DNS, CNAME + per-domain
     TLS provisioning + domain-verification flow) — deferred**, gated on
     `PLATFORM_ADMIN` infrastructure (§7 item 1) and a paying customer who
     actually asks; also breaks cross-school shared sessions (cookies don't
     cross domains), which is a real model decision, not an implementation
     detail.
   Also deliberately unbuilt in Tier 1: editing/adding a slug *after*
   creation — there is no school-settings surface or permission code for
   "manage my school's own metadata," and inventing one as a drive-by would
   violate the discipline in §10; decide it explicitly when a school-settings
   page is actually requested. **Still unbuilt as of 2026-07-24**, when the
   read-only half (displaying the entry URL on `/dashboard`) shipped
   without touching it — a school whose slug is empty gets a caption
   saying the address is chosen at registration, deliberately *not* an
   edit affordance (§7 Done, "Dashboard entry-URL display").

10. ~~How does a *real* (non-demo) school ever obtain a module entitlement
    before billing exists?~~ (raised 2026-07-19 by the critical user-journey
    review, UX_ROADMAP.md §6) — **Resolved 2026-07-20, explicit user
    decision: a free tier.** `ACADEMIC` and `FINANCE` are free — granted
    automatically at self-service school creation
    (`IamFacade.grantFreeTierEntitlements`, called by
    `SchoolController.createSchool` right after the creator's `SCHOOL_ADMIN`
    membership). `CURRICULUM`, `STUDY_PLANNER`, and `EXAM` are **paid
    plans**: not granted at creation, still obtainable only via the
    facade-only `setSchoolEntitlement` (no REST — §10's prohibition stands)
    until real billing + `PlatformContext` permission checking exist (Q5,
    §7 item 1). The free-module list lives in `IamFacadeImpl`
    (`FREE_TIER_MODULES`) — iam owns the entitlement concept, so when
    billing arrives only that one spot changes. The demo school's dev seed
    (all five modules, `IamSeedDataRunner`) is unchanged and now simply a
    superset of the free tier. Covered by the reworked fresh-school tests in
    `FinanceManagementFlowIntegrationTest`/`ExpenseManagementFlowIntegrationTest`/
    `GradeManagementFlowIntegrationTest` (free module works immediately,
    facade revocation restores `module-not-entitled`), while the
    `CurriculumFlowIntegrationTest`/`StudyPlanManagementFlowIntegrationTest`/
    `ExamFlowIntegrationTest` fresh-school cases now double as proof that
    the free tier does *not* include the paid modules.
