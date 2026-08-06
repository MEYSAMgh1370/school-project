---
name: new-module
description: Scaffold a new Spring Modulith module in Roshdeandishe (package layout, error handling, facade, audit pattern, entitlement registration). Use when adding a new module such as Library, Attendance, Transportation, AI Tutor, etc.
---

# Adding a new module

Follow this checklist when creating a new module under `com.example.roshdeandishe`.
It codifies conventions from OpenSpec, current code, and legacy `CLAUDE.md`
sections on module boundaries. Read `openspec/project.md` and neighboring
modules first if anything here is ambiguous; this skill does not override
current implementation evidence.

## 1. Package layout

```
<module>/
├── <Module>Facade.java          ← public interface, the only thing other
│                                   modules may depend on
├── <Module>Controller.java      ← REST controller, lives at module root next
│                                   to the Facade (NOT in internal/)
├── internal/
│   ├── <Module>Entity / <Module>Repository
│   └── <Module>FacadeImpl
└── shared/                      ← only if other modules need something beyond
                                    the Facade; each exposed package needs its
                                    own @NamedInterface, not inherited from a
                                    parent package
```

- `internal/` is invisible outside the module automatically (Spring Modulith
  package-naming convention).
- Nothing outside the Facade + explicitly `@NamedInterface`d packages should be
  reachable from other modules.
- The module must not depend back on `platform` in a way that creates a cycle,
  and must never let `platform` depend on it.

## 2. Error handling — copy the `academic/shared` pattern exactly

1. `shared/<Module>Error.java` — **sealed** interface, one case per failure mode.
2. `shared/<Module>Exception.java` — wraps a `<Module>Error`.
3. `shared/<Module>ExceptionHandler.java` — `@RestControllerAdvice("<module>...Handler")`
   with an **explicit, unique bean name**. Never rely on the default
   class-name-derived bean name — two modules both defaulting to
   `GlobalExceptionHandler` caused a `ConflictingBeanDefinitionException` before.
   Existing names already taken: `iamGlobalExceptionHandler`,
   `academicGlobalExceptionHandler`, `schoolExceptionHandler`.
4. Delegate the actual `ProblemDetail` construction to
   `platform.ProblemDetailFactory` — never hand-roll a `Map` body.
5. If the module has more than a trivial number of error cases, split the
   switch-expression mapping into a `shared/<Module>ProblemResolver.java`
   `@Component` (see `iam.shared.exception.IamProblemResolver`) so it's
   unit-testable without an MVC context.
6. Because `<Module>Error` is sealed, every switch over it must be exhaustive —
   let the compiler catch missing cases.
7. This module's exception handler must only know about its own error type.
   Never add this module's cases to `iam`'s or `platform`'s handling, and never
   have this module's handler reference another module's error types.

## 3. Access control

- Every mutating/reading endpoint that should be gated needs both:
  - `@RequiresModule("<MODULE_CODE>")` — module entitlement check
  - `@RequiresPermission("<RESOURCE_ACTION>")` — role permission check
- Both layers must be present; never check only one (§5, §10).
- Register any new permission codes in the seed data
  (`iam.internal.IamSeedDataRunner`) and grant them to the appropriate roles —
  this is a data change, not new code, per the Role/Permission split in §4.
- If the controller has a `{schoolId}` path variable, explicitly verify it
  matches `TenantContext` (populated from `X-School-ID`) before doing anything
  — do not assume the path variable is trustworthy. See the three existing
  examples (`MembershipController`, `ClassController`, `StudentController`).

## 4. Register the module for entitlements (if it's subscribable)

Add a row via `Module`/`ModuleScope` (§4 in `CLAUDE.md`):

- `SCHOOL` scope → checked against `SchoolEntitlementEntity` (B2B).
- `USER` scope → checked against `UserEntitlementEntity` (B2C).
- `PLATFORM` scope → always granted, no entitlement row needed.

Pick the scope based on whether this is a school-subscribed feature (Library,
Attendance, Transportation) or an individual-subscribed one (AI Tutor, Exam
Prep, Portfolio Builder, Teacher Marketplace, Professional Development).

## 5. Audit trail (only if the module needs one)

Follow `finance/internal/FinanceAuditEventListener.java` verbatim — do not
invent a new shape:

1. Public event in the module's outer package (not `internal/`), carrying
   `schoolId` + record identifiers + nullable `actorUserId` + `occurredAt`.
2. `internal/<Module>AuditEntity` — append-only, own schema, no update method.
3. `internal/<Module>AuditEventListener` — `@ApplicationModuleListener`.
   **Must** `TenantContext.set(event.schoolId())` at the top and
   `TenantContext.clear()` in a `finally` block — listeners run on a new
   thread after commit, and `TenantContext` is `ThreadLocal` and does not cross
   threads. Skipping the `finally` risks a pooled thread silently leaking the
   wrong tenant into an unrelated request.
4. The Facade method publishes the event via `ApplicationEventPublisher`
   *after* its own work succeeds, not before.
5. Don't build a human-vs-system `actorType` concept speculatively — nullable
   `actorUserId` is sufficient (§3, §10).

## 6. Naming (§9)

- Entities suffixed `Entity`, repositories suffixed `Repository` (both internal
  only).
- Permissions: `RESOURCE_ACTION` upper snake-case.
- Module codes: `UPPER_SNAKE_CASE`.
- Events: past-tense PascalCase (`StudentEnrolledEvent`-style).
- DB tables: snake_case, in a schema matching the module name. Flyway owns
  schema creation and migrations: add the schema to `spring.flyway.schemas`
  when needed and create a new `V<n>__...sql` migration under
  `src/main/resources/db/migration/`. Hibernate runs in validation mode; do
  not edit applied migrations or rely on `ddl-auto` to create tables.

## 7. Before calling it done

- Run `ModularityTests.verifyModularStructure()` — this is what actually
  enforces the boundary rules above, not just convention.
- Confirm the new `@RestControllerAdvice` bean name doesn't collide with an
  existing one.
- Confirm nothing in `platform` was made to depend on this new module.

## Explicitly out of scope — do not build speculatively

Per `CLAUDE.md` §10/§7, do not use "adding a module" as an excuse to also:
- Build `PlatformContext` permission checking.
- Add a self-service password/invite flow.
- Introduce a `Person`/`User` split, a `RoleGrant` entity, or a
  `Staff`/`Family` role category.
- Expose `setSchoolEntitlement` or `setSchoolStatus` over REST.

If the new module seems to need one of these, stop and flag it for discussion
rather than resolving it inline — these are explicitly deferred/rejected
decisions in `CLAUDE.md`.
