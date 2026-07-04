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
- **API documentation:** springdoc-openapi (Swagger UI at `/swagger-ui.html`,
  `/v3/api-docs`). Both are `permitAll` in `SecurityConfig` and excluded from the
  `X-School-ID` requirement in `TenantResolverFilter` — documentation endpoints, not
  data, so left open even though everything else requires authentication.

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
│   ├── MembershipController.java   (REST: create/list memberships, toggle active —
│   │                                 @RequiresPermission("MEMBERSHIP_MANAGE"))
│   ├── internal/
│   │   ├── UserEntity / UserRepository        (now includes nullable passwordHash)
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
│   │                                    with a working password; replace w/ Flyway
│   │                                    pre-prod)
│   └── shared/
│       ├── RequiresModule.java + ModuleAccessAspect.java     (@Order(10))
│       ├── RequiresPermission.java + PermissionAccessAspect.java (@Order(11))
│       ├── SecurityConfig.java        (session auth config; lives here, not
│       │                                platform, because it wires IamUserDetailsService)
│       ├── AuthController.java        (POST /api/auth/login, /api/auth/logout —
│       │                                JSON, not Spring's default form login)
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
│   ├── ClassController.java        (REST: create/list classes —
│   │                                 @RequiresPermission("CLASS_MANAGE"),
│   │                                 SCHOOL_ADMIN only)
│   ├── StudentController.java      (REST: enroll/get student —
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
└── finance/                    ← tuition, invoices, payments, audit
    ├── FinanceFacade.java           (issueTuitionInvoice — still a stub, no real
    │                                  InvoiceEntity/persistence yet)
    ├── InvoiceIssuedEvent.java      (schoolId, studentId, amount, actorUserId
    │                                  nullable, occurredAt)
    └── internal/
        ├── StudentEnrolledEventListener   (reacts to academic's enrollment event,
        │                                    calls issueTuitionInvoice — amount
        │                                    hardcoded to 0, real pricing not built)
        ├── FinanceAuditEntity / FinanceAuditRepository   (append-only audit trail)
        └── FinanceAuditEventListener      (@ApplicationModuleListener, writes one
                                             audit row per InvoiceIssuedEvent)
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
`schoolExceptionHandler`.

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
│                  enrollment flow via findOrCreateUser and can't log in yet;
│                  no self-service "set your password" flow exists yet, see §7)
└── status (ACTIVE | SUSPENDED)
```

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
annotation (`MembershipController`, `ClassController`, `StudentController`) explicitly
verifies the header matches the path before doing anything — a header/path mismatch
is a real security bug class (permission checked against one school, mutation applied
to another), not a hypothetical. This check is currently duplicated three times, not
yet extracted into a shared helper — extract it if a fourth controller needs it, not
before.

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
- Dev-only seed data: roles, modules, permissions, role-permission grants, one demo
  school, one demo admin user
- `ModularityTests` enforcing module boundaries

### Open / explicitly deferred

1. **`PlatformContext` permission checking.** `PermissionDecisionService` denies all
   non-school-scoped checks unconditionally. Blocks: REST exposure of
   `setSchoolEntitlement` and `setSchoolStatus`. Do not build until a real need
   (billing integration, ops tooling) exists — see §5.
2. **Self-service password / account activation.** Users created via
   `findOrCreateUser` (school creation's auto-admin, enrollment's auto-student) have
   `passwordHash = null` and **cannot log in**. No invite-token or "set your
   password" flow exists. This is the actual practical blocker to a real person
   using the system end-to-end today.
3. **Real invoice persistence.** `FinanceFacade.issueTuitionInvoice` is still a stub
   — no `InvoiceEntity`, no real pricing (hardcoded `0`), no persistence. The audit
   trail records that an invoice-issuing *attempt* happened, not a real invoice.
4. **Multi-DataSource.** Single shared datasource. Per-module DataSource routing is
   a distinct, not-yet-started phase. Each module's tables live in their own Postgres
   *schema* already (`iam`, `school`, `academic`, `finance`) but all through one
   connection — schemas must currently be created manually
   (`CREATE SCHEMA IF NOT EXISTS <name>;`) since Hibernate's `ddl-auto` creates
   tables, not schemas.
5. **Flyway/Liquibase.** All seed runners + `ddl-auto: update` are dev-only
   stand-ins.
6. **Angular / Thymeleaf frontends.** Not started.
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

**Phase D — Finance basics: not started.** `StudentEnrolledEventListener` calls
`issueTuitionInvoice` but that method is still a stub (§7 item 3). Next concrete
step here is real `InvoiceEntity` + persistence + actual pricing, not more
audit/event scaffolding — the audit pattern is already established and should be
reused as-is once real invoices exist.

**Immediate practical blocker across all phases:** §7 item 2 (no password-set flow
for admin/enrollment-created accounts). Worth prioritizing before more feature work,
since it currently means no admin-invited teacher or enrolled student can actually
log in and use anything built so far.

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
2. How does a user holding multiple memberships switch between schools/roles in the
   UI? Still open — no frontend work has started, but affects what the backend needs
   to expose (e.g. a "list my memberships" endpoint for a switcher).
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
   fully closed if a concrete need surfaces.
