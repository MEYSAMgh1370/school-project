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
- **Authentication:** session-based (`HttpSession`), resolved — see §7 and the former
  Open Question in §11 history. Spring Security is used for authentication only;
  authorization stays entirely with the two-layer `@RequiresModule` /
  `@RequiresPermission` mechanism in §5. Spring Security's `GrantedAuthority`/
  `hasRole()` machinery is deliberately unused for authorization, since roles are
  per-school, not global per principal.
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
  — seven page templates (`landing.jte`, `activate.jte`, `login.jte`,
  `register.jte`, `dashboard.jte`, `people.jte`, `school-new.jte`), all wrapped
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
  (the shared school/role switcher, see below), and `people.js` (see `web`
  module below); `src/main/resources/static/css/app.css` is the one global
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
│   ├── MeController.java           (REST: GET /api/me, /api/me/memberships —
│   │                                 authenticated-only, deliberately NO
│   │                                 permission/module annotations and NO
│   │                                 X-School-ID: it's about the user, not a
│   │                                 school. The "list my memberships" endpoint
│   │                                 Open Question 2 anticipated.)
│   ├── internal/
│   │   ├── UserEntity / UserRepository        (now includes nullable passwordHash)
│   │   ├── InviteTokenEntity / InviteTokenRepository  (per-user, single-use,
│   │   │                                        7-day TTL — backs account activation)
│   │   ├── InviteEmailSender          (best-effort SMTP delivery of the
│   │   │                                activation link; deliberately in
│   │   │                                iam/internal, not platform — only iam
│   │   │                                sends email, no generic abstraction)
│   │   ├── RoleEntity / RoleRepository
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
│       ├── SecurityConfig.java        (session auth config; lives here, not
│       │                                platform, because it wires IamUserDetailsService)
│       ├── AuthController.java        (POST /api/auth/login, /api/auth/logout,
│       │                                /api/auth/activate, /api/auth/register —
│       │                                JSON, not Spring's default form login;
│       │                                activate redeems an invite token to set a
│       │                                first password; register is public
│       │                                self-service sign-up → IamFacade.registerUser)
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
│   ├── ClassController.java        (REST: create/list classes — class-level
│   │                                 @RequiresModule("ACADEMIC") +
│   │                                 @RequiresPermission("CLASS_MANAGE"),
│   │                                 SCHOOL_ADMIN only)
│   ├── StudentController.java      (REST: enroll/get student — class-level
│   │                                 @RequiresModule("ACADEMIC") +
│   │                                 @RequiresPermission("STUDENT_ENROLL"),
│   │                                 SCHOOL_ADMIN + TEACHER)
│   ├── internal/
│   │   ├── ClassEntity (schoolId, name, teacherId nullable, section) / ClassRepository
│   │   ├── StudentEntity (userId, schoolId, classId, enrolledAt) / StudentRepository
│   │   │   — StudentEntity is NOT an identity; the person is the iam UserEntity.
│   │   │     enrollStudent() internally calls IamFacade.findOrCreateUser +
│   │   │     createMembership(..., "STUDENT") before creating the StudentEntity.
│   │   └── AcademicFacadeImpl
│   └── shared/
│       ├── AcademicError.java (sealed: StudentNotFound, ClassNotFound)
│       ├── AcademicException.java
│       └── AcademicExceptionHandler.java   (bean name "academicGlobalExceptionHandler")
│
├── finance/                    ← tuition, invoices, payments, audit
    ├── FinanceFacade.java           (issueTuitionInvoice — real persistence now;
    │                                  amount comes from the school's TuitionFee,
    │                                  no longer a parameter. Plus setTuitionFee /
    │                                  getTuitionFee / listInvoices)
    ├── FinanceController.java       (REST: PUT+GET tuition-fee, GET invoices —
    │                                  first controller carrying BOTH layers:
    │                                  @RequiresModule("FINANCE") class-level +
    │                                  @RequiresPermission per method)
    ├── InvoiceIssuedEvent.java      (schoolId, studentId, amount, actorUserId
    │                                  nullable, occurredAt)
    ├── internal/
    │   ├── TuitionFeeEntity / TuitionFeeRepository   (one row per school —
    │   │                                  unique school_id; the pricing source)
    │   ├── InvoiceEntity / InvoiceRepository   (persisted invoices; deliberately
    │   │                                  no status/payment fields until payments
    │   │                                  are actually built)
    │   ├── StudentEnrolledEventListener   (reacts to academic's enrollment event,
    │   │                                    calls issueTuitionInvoice — amount now
    │   │                                    resolved from TuitionFee internally)
    │   ├── FinanceAuditEntity / FinanceAuditRepository   (append-only audit trail)
    │   └── FinanceAuditEventListener      (@ApplicationModuleListener, writes one
    │                                        audit row per InvoiceIssuedEvent)
│   └── shared/
│       ├── FinanceError.java (sealed: TuitionFeeNotSet, InvalidTuitionFee)
│       ├── FinanceException.java
│       └── FinanceExceptionHandler.java   (bean name "financeExceptionHandler")
│
└── web/                        ← UI-composition layer (see §2 Frontend): page
    ├── LandingPageController.java     controllers that need more than one
    ├── DashboardPageController.java   module's Facade. GET / (public landing
    ├── SchoolRegistrationPageController.java  page — see below) composes iam
    ├── PeoplePageController.java      memberships + school names; GET /people
    ├── ClassesPageController.java     (first management-UI slice past the
    ├── NavbarModel.java               dashboard, see UX_ROADMAP.md)
    ├── NavbarModelAssembler.java      lists/adds/toggles a school's
    └── RoleLabels.java                memberships and issues invite tokens;
                                  GET /classes (second slice) creates/lists a
                                  school's classes — both entirely client-side
                                  against their respective REST controllers
                                  (school selection lives only in localStorage —
                                  see §11 Q2). ClassesPageController does NOT
                                  depend on AcademicFacade or any academic type —
                                  it's a thin shell like PeoplePageController;
                                  classes.js composes iam.MembershipController
                                  (for teacher names) and academic.ClassController
                                  in JavaScript, not the Java controller, so `web`
                                  gains no new module dependency for this slice.
                                  NavbarModel/NavbarModelAssembler build the
                                  shared school/role switcher navbar (rendered
                                  by layout/navbar.jte: page links in
                                  navbar-start — every page must be linked
                                  there, no orphan URLs — plus a Bulma burger
                                  for mobile) once, reused by every page instead
                                  of recomposed per controller. RoleLabels maps
                                  role codes to Persian labels for
                                  server-rendered templates (mirrored
                                  client-side in people.js — raw codes never
                                  reach the UI, UX_ROADMAP.md §2). No Facade, no
                                  entities, no REST, no exception handler of its
                                  own; nothing depends on it.
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

### Role

A named, assignable bundle of permissions — not a user type. Adding a new access
level is a data change (insert a `RoleEntity` + `RolePermissionEntity` rows), not a
code change.

```
RoleEntity
├── id
├── code (STUDENT, TEACHER, PARENT, SCHOOL_ADMIN, FINANCE_ADMIN, PLATFORM_ADMIN)
└── displayName
```

`PLATFORM_ADMIN` is seeded as a role but has **no working permission-check path** —
see §7. Don't assume granting someone `PLATFORM_ADMIN` today does anything
enforceable.

### Permission

The actual capability. Deliberately separate from Role so two roles can carry
different access levels without any code change.

```
PermissionEntity
├── id
├── code (GRADES_VIEW, GRADES_EDIT, FINANCE_VIEW, FINANCE_MANAGE,
│         MEMBERSHIP_MANAGE, STUDENT_ENROLL, CLASS_MANAGE)
└── displayName

RolePermissionEntity  (join table: which roles have which permissions)
├── roleId
└── permissionId
```

Current grants (seeded in `IamSeedDataRunner`):

| Role | Permissions |
|---|---|
| `STUDENT` | `GRADES_VIEW` |
| `TEACHER` | `GRADES_VIEW`, `GRADES_EDIT`, `STUDENT_ENROLL` |
| `PARENT` | `GRADES_VIEW`, `FINANCE_VIEW` |
| `SCHOOL_ADMIN` | `GRADES_VIEW`, `FINANCE_VIEW`, `FINANCE_MANAGE`, `MEMBERSHIP_MANAGE`, `CLASS_MANAGE`, `STUDENT_ENROLL` |
| `FINANCE_ADMIN` | `FINANCE_VIEW`, `FINANCE_MANAGE` |

Design intent behind the split: **class creation is administrative/scheduling
(`SCHOOL_ADMIN` only)**, while **student enrollment is a day-to-day task
(`SCHOOL_ADMIN` + `TEACHER`)** — a deliberate, explicit choice, not an oversight.

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
├── code (ACADEMIC, FINANCE, LIBRARY, ATTENDANCE, TRANSPORTATION,
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
└── issuedAt
```

Deliberately no `status`/payment fields — payments aren't built yet; the field
arrives with them, not speculatively. `FinanceFacade.issueTuitionInvoice(schoolId,
studentId)` reads the amount from `TuitionFeeEntity` itself (no longer a parameter).
**Explicit decision:** if the school has no tuition fee configured, no invoice is
issued (warn log only) and enrollment is never blocked; students enrolled before the
fee was set do not get backfilled invoices — build backfill when a real need shows
up. After persisting, it publishes `InvoiceIssuedEvent` (audit trail unchanged).

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
module code). `IamSeedDataRunner.seedDemoSchoolEntitlements()` seeds an
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
  cross-dependency in the Java layer. Navbar link added (`SCHOOL_ADMIN`-only
  visibility, same pattern as `/people`); `TenantResolverFilter` excludes
  `/classes` the page route for the same reason it excludes `/people`.
  Covered by `ClassManagementFlowIntegrationTest`.
- Dev-only seed data: roles, modules, permissions, role-permission grants, one demo
  school, one demo admin user with a SCHOOL_ADMIN membership at the demo school
  (without it the demo admin couldn't call any school-scoped API — the demo school
  is seeded via repository, bypassing the self-service flow that auto-creates the
  creator's membership), and ACADEMIC+FINANCE entitlements for the demo school
  (so the first `@RequiresModule`-guarded controller works out of the box in dev)
- `ModularityTests` enforcing module boundaries

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
   the public landing page (`GET /`), **People (`/people`)**, and **Classes
   (`/classes`)** — see §7 Done for all four. Remaining: enrollment, finance
   pages (in that order per the roadmap doc). New management pages go in the
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
dependency added to `web`). See `UX_ROADMAP.md` for the full page inventory
and build order for the remaining slices (enrollment, finance).

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

---

## 11. Open Questions (do not resolve without explicit discussion)

1. ~~Session vs. JWT for authentication~~ — **Resolved: session-based** (§2, §8).
   Revisit only if/when the KMP mobile client makes session cookies impractical.
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
3. Can a school define custom roles, or only choose from system-defined ones?
   `RoleEntity`-as-data supports it technically; no admin UI or facade method exists.
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
