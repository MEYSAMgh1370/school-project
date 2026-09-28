# Package Classes By Type Within Each Module

Status: approved design, not yet implemented. Created 2026-09-28.

## Intent

Separate classes by what they are — controller, request/response, config — so
that opening a module tells you where a class lives without reading its name.

Two constraints shaped the final scope, and both narrowed it from the original
request. They are recorded here because they are the whole reason this design
looks the way it does.

**Constraint 1: the package layout *is* the architecture.** This repository is a
Spring Modulith modular monolith, and `ApplicationModules.verify()` enforces the
module boundaries. A global by-layer split
(`com.example.roshdeandishe.controller`, `.entity`, …) was considered and
rejected: Modulith treats each top-level package under the application package as
a module, so every controller-to-facade-to-repository call would become a
cross-module access into another module's internals, and the verification test
could not pass. Separation therefore happens *inside* each module.

**Constraint 2: package-private visibility is a second enforcement layer.**
114 of 225 types are package-private, concentrated in the `internal/` packages.
Splitting `internal/` into `entity/`, `repository/`, and `service/` would require
widening all 114 to `public`, because a package-private `FinanceFacadeImpl`
cannot see a package-private `InvoiceRepository` in a sibling package. Today the
*compiler* prevents cross-module leakage even if `ModularityTests` were deleted.
That guarantee is worth more than the filing convention, so `internal/` stays
flat. Entities, repositories, and services remain identifiable by their existing
`*Entity`, `*Repository`, and `*Impl` suffixes.

Success criteria:

- Controllers, request/response records, and configuration each live in a package
  named for what they are.
- `ModularityTests.verifyModularStructure` passes unchanged.
- No behavior changes. `package` lines, `import` lines, file locations, JTE
  `@import` lines, and exactly three visibility widenings — nothing else.

## Current Structure

Modules are the top-level packages under `com.example.roshdeandishe`: `academic`,
`bale`, `curriculum`, `exam`, `finance`, `iam`, `school`, `studyplan`, plus
`platform` and `web`. Each domain module declares itself with
`@ApplicationModule` in `package-info.java` and splits into:

- the **base package** — the facade interface, enums, events, *and currently the
  controllers*. This is the module's exposed API.
- **`internal/`** — entities, repositories, facade implementations, services, and
  event listeners in one flat package, mostly package-private. Hidden
  automatically by Modulith's `internal` naming convention.
- **`shared/`** — carries `@NamedInterface("shared")`, so other modules may
  depend on it explicitly.

`platform` and `web` have no `internal`/`shared` split; `web` has no
`package-info.java` and is an implicit module.

Evidence: [`ModularityTests.java`](../../../src/test/java/com/example/roshdeandishe/ModularityTests.java),
the ten `package-info.java` files, `openspec/architecture.md`.

### What the modules actually expose

Measured from imports in `src/main/java` that cross a module boundary:

| Exposed symbol | Files using it outside its module |
|---|---|
| `iam.shared.RequiresPermission` | 13 |
| `iam.shared.RequiresModule` | 13 |
| `iam.shared.exception.IamException` | 6 |
| `iam.shared.exception.IamError` | 1 |
| `platform.TenantContext` | 20 |
| `platform.ProblemDetailFactory` | 8 |
| `<module>.<Module>Facade` + nested types | `web` and sibling modules |

Nothing else crosses a module boundary. In particular **no controller and no
controller DTO is referenced from outside its own module** — the only
`XxxController.Yyy` mentions anywhere are two Persian comments, in
`academic/internal/ClassEntity.java:29` and `academic/AcademicFacade.java:47`.
This is what makes moving controllers out of base packages safe.

### Request/response today

There is no request/response layer. All 91 request and response records are
nested inside other classes, across 28 files — 27 controllers plus
`web/NavbarModel`. The largest are `finance/ExpenseController` (12),
`iam/shared/AuthController` (10), `finance/FinanceController` (6), and
`exam/ExamController` (6).

Separately, 59 records are nested inside `*Facade` interfaces. These are a
different category and are out of scope; see below.

## Target Structure

```
<module>/
  <Module>Facade.java, enums, events        exposed module API — unchanged
  controller/                               @RestController / @Controller
  dto/                                      extracted request/response records
  config/                                   @Configuration, filters, aspects
  internal/                                 unchanged, flat, package-private
  shared/                                   @NamedInterface — unchanged
```

`config/` is created only in `iam` and `platform`, the only modules with such
classes. `dto/` is created only where a controller has nested records, so not in
`bale`.

Two rules preserve the module boundaries:

1. **Nothing leaves a base package except controllers.** Base packages are the
   exposed API. Controllers are the one safe exception, proven by the measurement
   above: nothing outside a module references them.
2. **Nothing enters a base package.** Moving a class in exposes it, widening the
   module's contract by accident.

`internal/`, `shared/`, and `iam/shared/exception/` are untouched.

### Per-module mapping

| Module | `controller/` | `dto/` records | `config/` |
|---|---|---|---|
| `finance` | 3 | 19 | — |
| `iam` | 7 | 22 | 6 |
| `exam` | 4 | 14 | — |
| `academic` | 5 | 10 | — |
| `studyplan` | 5 | 14 | — |
| `curriculum` | 2 | 5 | — |
| `school` | 1 | 2 | — |
| `bale` | 1 | 0 | — |
| `platform` | — | — | 4 |
| `web` | 19 | 4 | — |

Notable specifics:

**iam** — the largest cleanup, because `iam/shared` currently mixes four
unrelated concerns. `config/` takes `SecurityConfig`, `JwtAuthenticationFilter`,
`JwtTokenProvider`, `ModuleAccessAspect`, `PermissionAccessAspect`, and
`PostAuthenticationDestination`. `controller/` takes `MeController`,
`MembershipController`, `RoleController`, and — moved out of `shared/` —
`AuthController`, `LoginPageController`, `RegisterPageController`,
`AccountActivationPageController`.

After this, `iam/shared` contains only `RequiresModule` and `RequiresPermission`
— exactly the cross-module contract measured above, so the package will finally
mean what its `@NamedInterface` claims.

`PostAuthenticationDestination` must move rather than stay: it has to become
`public` (see widenings below), and a `public` type in a `@NamedInterface`
package would join the module's *exposed* contract. In `config/`, which carries
no `@NamedInterface`, it is public to `iam` and invisible to everyone else.

**platform** — `TenantContext` and `ProblemDetailFactory` stay in the base
package; they have 28 cross-module imports between them. `PlatformExceptionHandler`
also stays, consistent with every other module's exception handler staying put.
`config/` takes `OpenApiConfig`, `TenantResolverFilter`, `LoggingAspect`,
`Loggable`.

**web** — `controller/` takes the 19 page controllers; `model/` takes
`NavbarModel`, `NavbarModelAssembler`, `RoleLabels`; `dto/` takes the four
extracted page models from `DashboardPageController` and
`SchoolEntryPageController`. `NavbarModel`'s own nested record moves with it into
`model/`.

### The three visibility widenings

These are forced by the split and are the only non-mechanical edits in the change.
Each is a package-private type whose consumers end up in two different packages:

| Type | Consumers after the split | Becomes |
|---|---|---|
| `PostAuthenticationDestination` (→ `config/`) | `SecurityConfig` (→ `config/`), `LoginPageController` + `RegisterPageController` (→ `controller/`) | `public` |
| `JwtTokenProvider` (→ `config/`) | `JwtAuthenticationFilter` + `SecurityConfig` (→ `config/`), `AuthController` (→ `controller/`) | `public` |
| `NavbarModelAssembler` (→ `web/model/`) | 17 page controllers (→ `web/controller/`) | `public` |

All three stay inside their own module, so no module boundary widens. The
`exam/internal/ExamTokenService` mentions of `JwtTokenProvider` are comments, not
imports — checked, not a boundary violation.

### Out of scope

- **Splitting `internal/`** into `entity/`, `repository/`, `service/`. Costs 114
  visibility widenings and the compiler-enforced encapsulation layer. Rejected;
  see Constraint 2.
- **Facade nested types** (`IamFacade.UserProfile`, `SchoolFacade.SchoolInfo`, and
  57 others). These are the exposed cross-module API. A `dto/` sub-package is
  *not* exposed by Modulith, so extracting them there would break every module
  that consumes a facade. They stay nested in their facade interface, where they
  read as the contract they are.
- **`*ExceptionHandler` classes.** They live in `<module>/shared` beside their
  `*Error` and `*Exception` types, which is already a by-type grouping, and
  `shared` is a `@NamedInterface`. Moving them buys nothing and costs exposure.
- **Renames, signature changes, logic edits, new tests.**

## Hazards

**JTE templates import Java types by name.** Five `@import` targets across the 27
templates in `src/main/jte` must change, covering 20 import lines:

| From | To | Lines |
|---|---|---|
| `web.NavbarModel` | `web.model.NavbarModel` | 17 |
| `web.RoleLabels` | `web.model.RoleLabels` | 2 |
| `web.SchoolEntryPageController.SchoolEntryModel` | `web.dto.SchoolEntryModel` | 1 |
| `web.DashboardPageController.DashboardModel` | `web.dto.DashboardModel` | 1 |
| `iam.shared.AccountActivationPageController.ActivatePageModel` | `iam.dto.ActivatePageModel` | 1 |

**JTE is precompiled** by `jte-maven-plugin` (`pom.xml:267`). A stale `target/`
does not fail the build — it surfaces at runtime as a "Circular view path" 500.
The `iam` and `web` commits must be verified after `./mvnw clean`, never against
a reused `target/`.

**Controllers leave their base packages**, making them module-internal. Verified
safe by the measurement above. If a cross-module controller reference is
introduced later, `ModularityTests` will catch it.

**Non-hazards, checked:** no package names appear as strings in
`src/main/resources` or `pom.xml`; entity and component scanning derive from
`@SpringBootApplication`'s package and every move stays beneath it; bean names
derive from class names, which do not change; Flyway migrations reference tables,
not packages.

## Delivery

One branch, one commit per module, each compiling and passing `ModularityTests`
on its own:

1. `finance` 2. `iam` 3. `exam` 4. `academic` 5. `studyplan` 6. `curriculum`
7. `school` 8. `bale` 9. `platform` 10. `web` 11. documentation

Commit 11 updates `openspec/conventions.md` with the layout rule and
`openspec/architecture.md` with the revised package diagram, and adds a proposal
under `openspec/changes/` recording this restructure and its rationale — including
the rejected alternatives, so the next person does not re-propose them.

## Verification

Per commit:

```
./mvnw -q compile
./mvnw test -Dtest=ModularityTests
```

For the `iam` and `web` commits, and as the final gate:

```
./mvnw clean verify
```

Docker is available, so the 34 `@SpringBootTest` classes run against
Testcontainers as normal.

Acceptance: `ApplicationModules.verify()` passes; the full suite passes; `git
diff` over the branch shows changes confined to `package` lines, `import` lines,
file locations, JTE `@import` lines, the three documented widenings, and the
documentation commit.
