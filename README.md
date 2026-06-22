# Roshdeandishe - School Management System (Modular Monolith)

## Identity & Entitlement Model (current)

This was refactored from an early "school owns modules" design into a
user-centric, multi-scope entitlement model. See the structure below.

```
com.example.roshdeandishe
├── RoshdeandisheApplication.java   ← @Modulithic
│
├── iam/
│   ├── package-info.java            ← @ApplicationModule
│   ├── IamFacade.java                ← public API (UserId, ModuleCode, AccessContext, AccessDecision)
│   ├── AccessContext.java            ← sealed: SchoolContext | PlatformContext
│   ├── AccessDecision.java           ← sealed: Granted | Denied
│   ├── internal/
│   │   ├── UserEntity.java               ← single identity root (no Teacher/Student/Parent types)
│   │   ├── RoleEntity.java               ← roles as data, not user types
│   │   ├── SchoolMembershipEntity.java   ← (User, School, Role) — replaces old type hierarchy
│   │   ├── ModuleEntity.java             ← Module + ModuleScope (PLATFORM | SCHOOL | USER)
│   │   ├── SchoolEntitlementEntity.java  ← B2B: school subscribes to a SCHOOL-scoped module
│   │   ├── UserEntitlementEntity.java    ← B2C: user subscribes to a USER-scoped module
│   │   ├── AccessDecisionService.java    ← "can user X access module Y in context Z?"
│   │   ├── IamFacadeImpl.java
│   │   ├── IamSeedDataRunner.java        ← dev-only seed data (no Flyway yet)
│   │   └── *Repository.java
│   └── shared/                       ← @NamedInterface("shared")
│       ├── TenantContext.java
│       ├── TenantResolverFilter.java
│       ├── RequiresModule.java
│       ├── ModuleAccessAspect.java       ← now calls IamFacade.checkAccess(...)
│       ├── IamError.java                 ← sealed domain error hierarchy
│       ├── IamException.java             ← carries an IamError
│       └── GlobalExceptionHandler.java   ← ProblemDetail, exhaustive switch over IamError
│
├── academic/   (unchanged by this refactor — never depended on iam.internal)
└── finance/    (unchanged by this refactor — never depended on iam.internal)
```

## What changed and why

**Identity**: `UserEntity` is now the only identity root. Teacher/Student/Parent/
SchoolAdmin are no longer types — they're `RoleEntity` rows linked through
`SchoolMembershipEntity`. The same user can be a teacher at one school and a
student at another, simultaneously, with no schema change needed.

**Module scope**: `ModuleEntity.scope` (`PLATFORM`/`SCHOOL`/`USER`) decides
which entitlement table `AccessDecisionService` checks. `ACADEMIC`/`FINANCE`
are `SCHOOL`-scoped (checked against `SchoolEntitlementEntity` — the direct
replacement for the old `school_modules` table). `AI_TUTOR`/`TEACHER_MARKETPLACE`
are `USER`-scoped (checked against `UserEntitlementEntity`, independent of
any school).

**Access check**: `IamFacade.checkAccess(UserId, ModuleCode, AccessContext)`
replaces `isModuleEnabled(schoolId, moduleName)`. `AccessContext` is sealed
(`SchoolContext` | `PlatformContext`), so `AccessDecisionService` can branch
on it with a compiler-checked switch — no module can silently fall through
unhandled.

**Errors**: `IamError` is a sealed interface; `GlobalExceptionHandler` switches
over it exhaustively to build `ProblemDetail` (RFC 7807) responses. Adding a
new `IamError` variant later is a compile error everywhere it isn't handled.

## Migration approach: big-bang (dev phase, no production data)

Since the project has no production data yet, this was a direct in-place
replacement — no shadow tables, no dual-write period, no compatibility shim.
Old `SchoolModuleEntity`/`SchoolModuleRepository`/`ModuleNotEnabledException`
were deleted outright. `IamSeedDataRunner` inserts the initial Role/Module rows
on startup; `ddl-auto` is temporarily `update` instead of `validate` to let
Hibernate create the new tables.

## ⚠️ Still open (deliberately deferred)

1. **Flyway/Liquibase**: `IamSeedDataRunner` and `ddl-auto: update` are dev-only
   stand-ins. Before production, replace both with versioned migrations and
   set `ddl-auto` back to `validate`.
2. **Multi-DataSource**: still a single shared datasource; per-module
   DataSource routing is a separate, not-yet-done phase.
3. **Spring Security configuration**: `IamFacadeImpl.currentUserId()` assumes
   the authenticated principal name is the user's email — this needs a real
   Security config (JWT/session) to back that assumption.
4. **`School` as a first-class entity**: `schoolId` is still treated as an
   opaque `Long` everywhere (matching how `TenantContext` already worked) —
   no `SchoolEntity` was introduced in this refactor.
5. **Angular project**: out of scope for this backend repo.

## Running the modularity check

```bash
mvn test -Dtest=ModularityTests
```
