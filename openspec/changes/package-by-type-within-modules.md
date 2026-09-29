# Package Classes By Type Within Each Module

Status: implemented 2026-09-28. Current behavior is reflected in
[`conventions.md`](../conventions.md) (Backend, Package Layout) and
[`architecture.md`](../architecture.md) (Package Layout). This record is
retained for the rationale, the rejected alternatives, and the visibility
audit trail — not as evidence of a separate deployment.

## Intent

Group classes by what they are — controller, request/response record,
configuration — inside each Spring Modulith module, so opening a module tells
you where a class lives without reading its name. Pure relocation: no renames,
no signature changes, no logic edits, no new tests.

## Verified Baseline

Before this change, every domain module's base package mixed the facade
interface (the module's real exposed API) together with its REST/page
controllers, and every controller's request/response types were nested
records inside the controller class. `iam/shared` additionally mixed four
unrelated concerns — auth page controllers, JWT/session config, AOP aspects,
and the two `@NamedInterface` annotations that are the package's actual
contract.

Evidence (measured against the pre-change tree at commit `76d0220`, the last
commit before Task 1 of this change, and recorded in
[`docs/superpowers/specs/2026-09-28-package-by-type-within-modules-design.md`](../../docs/superpowers/specs/2026-09-28-package-by-type-within-modules-design.md)):

- 47 controllers lived at module root next to the facade (e.g.
  `finance.FinanceController`, `iam.shared.AuthController`).
- 90 request/response records were nested inside 28 files — 27 controllers
  plus `web.NavbarModel` — the largest being `finance/ExpenseController` (12)
  and `iam/shared/AuthController` (10).
- `iam/shared` held `SecurityConfig`, `JwtAuthenticationFilter`,
  `JwtTokenProvider`, `ModuleAccessAspect`, `PermissionAccessAspect`,
  `PostAuthenticationDestination`, four page controllers, `RequiresModule`,
  and `RequiresPermission` together, even though only the last two are the
  package's `@NamedInterface("shared")` contract.
- `platform`'s base package held `OpenApiConfig`, `TenantResolverFilter`, and
  `LoggingAspect` alongside `TenantContext`, `ProblemDetailFactory`,
  `PlatformExceptionHandler`, and `Loggable`.
- `web` held its 19 page controllers, `NavbarModel`/`NavbarModelAssembler`/
  `RoleLabels`, and four extracted-later page models all in one flat package.
- A measurement of every import that crosses a module boundary (grep across
  `src/main/java`) found no controller and no controller DTO referenced from
  outside its own module — the only `XxxController.Yyy` mentions anywhere in
  the codebase were two Persian comments, in
  `academic/internal/ClassEntity.java` and `academic/AcademicFacade.java`.
  This is the evidence that moving controllers out of base packages, into a
  `controller/` sub-package, could not widen any module's exposed contract.
- [`ModularityTests.java`](../../src/test/java/com/example/roshdeandishe/ModularityTests.java)
  `verifyModularStructure()` passed against that baseline, and still passes
  against the current tree — the module boundaries did not change, only what
  sits inside each module's base package.

## Rejected Alternatives

Both were considered and rejected before implementation. Recording them here
so neither is re-proposed without re-deriving the same costs.

**1. A global by-layer split** (top-level `controller/`, `service/`,
`entity/`, `repository/` under `com.example.roshdeandishe`, cutting across all
modules) is not viable in this codebase. Spring Modulith treats each top-level
package directly under the application package as a module boundary; putting
every controller in one `controller/` package and every repository in another
would make each controller → facade → repository call a cross-module access
into a different "module." `ApplicationModules.verify()` in
[`ModularityTests.java`](../../src/test/java/com/example/roshdeandishe/ModularityTests.java)
would fail on essentially every class in the application. Separation of
concerns therefore has to happen *inside* each existing module, not across the
application.

**2. Splitting each module's `internal/` package into `entity/`,
`repository/`, `service/`** was rejected because of a second, independent cost.
A pre-refactor, repository-wide audit in
[`docs/superpowers/specs/2026-09-28-package-by-type-within-modules-design.md`](../../docs/superpowers/specs/2026-09-28-package-by-type-within-modules-design.md)
found 114 of 225 types package-private, concentrated in `internal/` packages.
That 114 also counted 3 types outside `internal/` — `JwtTokenProvider`,
`PostAuthenticationDestination`, `NavbarModelAssembler` — that this refactor
has since widened to `public` for controller extraction (see "Visibility
widenings" below), so the count of package-private types actually inside
`internal/` in the current tree is 111 (for example
[`FinanceFacadeImpl`](../../src/main/java/com/example/roshdeandishe/finance/internal/FinanceFacadeImpl.java)
and
[`InvoiceRepository`](../../src/main/java/com/example/roshdeandishe/finance/internal/InvoiceRepository.java)
are both package-private today). Splitting `internal/` by type would require
widening those 111 to `public`, because a package-private facade
implementation cannot see a package-private repository sitting in a sibling
package.
Package-private visibility inside `internal/` is currently a *second*,
compiler-enforced encapsulation layer underneath Spring Modulith: even if
`ModularityTests` were deleted, the compiler alone still stops `finance` from
reaching into `iam`'s entities. That guarantee was judged worth more than the
filing convenience, so `internal/` stays flat, and entities, repositories, and
services remain identifiable by their existing `*Entity`, `*Repository`, and
`*Impl` suffixes instead of by package.

## Proposed (Delivered) Behavior

Within each module:

```
<module>/
  <Module>Facade.java, enums, events    exposed module API — unchanged
  controller/                           @RestController / @Controller
  dto/                                  request/response records
  config/                               @Configuration, filters, aspects
  internal/                             entities, repositories, services — flat, unchanged
  shared/                               @NamedInterface — unchanged
```

`config/` was created only in `iam` (6 classes) and `platform` (3 classes),
the only modules with such classes. `dto/` was created only where a
controller had nested records to extract, so not in `bale`. `web` has no
`internal/`/`shared` split — it is a pure view-composition module and gained
`controller/` (19 page controllers), `dto/` (4 extracted page models), and
`model/` (`NavbarModel`, `NavbarModelAssembler`, `RoleLabels`).

Two rules keep the module boundary intact, both now recorded in
[`conventions.md`](../conventions.md) and [`architecture.md`](../architecture.md):

1. **Nothing moves into a base package.** The base package is a module's
   exposed API. Moving a class into it silently widens the module's contract,
   and `ApplicationModules.verify()` does **not** catch this — exposing more
   than before is not a boundary violation the test checks for. Only diff
   review catches an accidental base-package addition. (Controllers moved
   *out* of base packages, which is safe by the cross-module-import
   measurement above, and is the one direction this change moves classes
   across that line.)
2. **`internal/` stays flat**, for the reason in Rejected Alternative 2.

Delivered inventory: 47 controllers moved to `<module>/controller/`; 90
nested records extracted to `<module>/dto/`; 6 `iam` classes moved to
`iam/config/` and 3 `platform` classes to `platform/config/`; 3 `web` support
classes moved to `web/model/`, alongside the 19 controllers and 4 records
already counted above.

### Visibility widenings

The split forced two categories of widening — the only non-mechanical edits
in the change, verified by the diff audit below.

**Three package-private types, plus exactly the members their new-package
callers need** (5 members total; everything else in these three classes
stayed package-private because its remaining callers are in the same
destination package):

| Type (destination) | Members widened | Consumer that forces it |
|---|---|---|
| [`PostAuthenticationDestination`](../../src/main/java/com/example/roshdeandishe/iam/config/PostAuthenticationDestination.java) (`iam/config/`) | `from(String)` | `LoginPageController`, `RegisterPageController` (→ `iam/controller/`) |
| [`JwtTokenProvider`](../../src/main/java/com/example/roshdeandishe/iam/config/JwtTokenProvider.java) (`iam/config/`) | `generateToken(String)`, `expirationSeconds()` | `AuthController` (→ `iam/controller/`) |
| [`NavbarModelAssembler`](../../src/main/java/com/example/roshdeandishe/web/model/NavbarModelAssembler.java) (`web/model/`) | `forCurrentUser()`, `assemble(UserProfile, List<MembershipInfo>)` | 17 page controllers (→ `web/controller/`) |

`PostAuthenticationDestination.loginUrlFor(String)` and its three constants
stayed package-private (called only by `SecurityConfig`, which moved to the
same `iam/config/` package); `JwtTokenProvider.validateAndExtractEmail` and
its constructor stayed package-private (called only by
`JwtAuthenticationFilter`, also in `iam/config/`, or by Spring);
`NavbarModelAssembler.schoolName(Long)` and its constructor stayed
package-private (called only from inside the class itself).

**Eight `static from(...)` record factories**, forced by record extraction
rather than controller relocation: while a record is nested inside its
controller, its factory needs no visibility modifier; once the record moves
to `<module>/dto/` and the controller to `<module>/controller/`, the call
crosses a package boundary:

| Module | Factory |
|---|---|
| `finance` | `MyInvoiceResponse.from(InvoiceInfo)` |
| `iam` | `MyMembershipResponse.from`, `PermissionResponse.from`, `RoleResponse.from`, `MembershipResponse.from` |
| `academic` | `MyEnrollmentResponse.from`, `MyGradeResponse.from` |
| `school` | `SchoolResponse.from` |

`exam`, `studyplan`, `curriculum`, and `web` have none — those controllers
build their responses inline. So the full tally is **3 types, 5 members, and
8 record factory methods**, all widened from package-private to `public` and
nothing further.

## Hazard Recorded For Future Refactors

AOP pointcuts name types as **string literals**, which no import rewrite
touches, and a broken pointcut fails **silently** — no compile error, no
failing test. There are exactly three in the codebase:

- [`LoggingAspect`](../../src/main/java/com/example/roshdeandishe/platform/config/LoggingAspect.java)
  names `...platform.Loggable` twice, in its `@Around` expression.
- [`ModuleAccessAspect`](../../src/main/java/com/example/roshdeandishe/iam/config/ModuleAccessAspect.java)
  names `...iam.shared.RequiresModule`.
- [`PermissionAccessAspect`](../../src/main/java/com/example/roshdeandishe/iam/config/PermissionAccessAspect.java)
  names `...iam.shared.RequiresPermission`.

This change deliberately left all three named types where they were.
`Loggable` stayed in `platform/`'s base package even though `LoggingAspect`
moved to `platform/config/`; `RequiresModule` and `RequiresPermission` stayed
in `iam/shared` even though `ModuleAccessAspect` and `PermissionAccessAspect`
moved to `iam/config/`. Zero pointcut string literals were edited anywhere in
this change (verified below). Any future refactor that moves `Loggable`,
`RequiresModule`, or `RequiresPermission` must update these three literals by
hand and confirm the annotations still apply — the compiler will not help.

## Acceptance Criteria

- `ApplicationModules.verify()` (`ModularityTests.verifyModularStructure`)
  passes unchanged — module boundaries did not move.
- Every REST/page controller lives in `<module>/controller/`; every extracted
  request/response record lives in `<module>/dto/`; every `@Configuration`,
  filter, or aspect class in `iam` and `platform` lives in `<module>/config/`.
- `internal/` and `shared/` are untouched in every module.
- No `.java` diff line other than `package`/`import` lines, the 3-type/
  5-member/8-factory visibility widenings, the four documented comment
  refreshes, and record bodies relocated byte-for-byte (see Verification).
- No AOP pointcut string literal changed.
- No `.jte`, `pom.xml`, or `src/main/resources` file changed except the JTE
  `@import` lines needed to follow moved types.
- Full test suite passes with no test file changed.

## Migration And Compatibility Concerns

No REST payload, permission, tenant, database, session, or JTE-rendered
behavior changes — this is a pure package/file relocation plus the forced
visibility widenings above, all of which stay inside their own module. Two
categories of risk were identified and closed during implementation, not left
open:

- **JTE templates import Java types by name and are precompiled.** Five
  `@import` targets across 19 templates needed updating (`web.NavbarModel` →
  `web.model.NavbarModel`, `web.RoleLabels` → `web.model.RoleLabels`,
  `web.SchoolEntryPageController.SchoolEntryModel` →
  `web.dto.SchoolEntryModel`, `web.DashboardPageController.DashboardModel` →
  `web.dto.DashboardModel`,
  `iam.shared.AccountActivationPageController.ActivatePageModel` →
  `iam.dto.ActivatePageModel`). A stale precompiled `target/` does not fail
  the build; it surfaces at runtime as a "Circular view path" 500. Every
  commit touching JTE-referenced types was verified after `./mvnw clean`.
- **AOP pointcut string literals**, covered above.

No database migration, no config file change, and no external contract
change; this is safe to deploy independently of any other in-flight work.

## Tests That Prove It

Per-module commits (see `git log 5f53cd7..d8d5f15` for the 10 module commits)
were each verified with `./mvnw -q compile` and
`./mvnw test -Dtest=ModularityTests`; the `iam` and `web` commits (the ones
touching JTE-referenced types) and the final state were verified with a full
`./mvnw clean verify`.

Final verification for this documentation commit, run 2026-09-28:

- `./mvnw clean verify` — **BUILD SUCCESS**, `Tests run: 190, Failures: 0,
  Errors: 0, Skipped: 0`.
- `git diff master -- '*.java' | grep '^[+-]' | grep -v '^[+-][+-]' | grep -vE '^[+-](package |import |\s*$)'`
  — 618 raw lines. A multiset diff of the `+` and `-` sides (pairing every
  relocated-but-unchanged line) leaves exactly 23 net additions and 23 net
  removals, and every one of them is one of: the 8 widened type/member
  declarations, the 8 widened `from(...)` factory declarations, the 4
  documented comment refreshes (two from Task 4 in `academic`, two from Task
  10 in `web`/`studyplan` naming), or an import-driven qualifier shortening
  that a newly-added `import` statement produces automatically when a nested
  type is extracted into its own file (`SchoolFacade.SchoolLevel` →
  `SchoolLevel` in `school/dto/CreateSchoolRequest.java`;
  `java.util.List<TimeRange>` → `List<TimeRange>` in
  `studyplan/dto/ScheduleResponse.java`, both after their respective files
  gained a matching `import`). Nothing else survives the filter.
- `git diff master -- '*.java' | grep -E '^[+-].*@(Around|Before|After|Pointcut)\('`
  — no output. No pointcut literal changed.
- `git diff master --stat` shows additional non-`.java` files
  (`DESIGN.md`, `src/main/jte/exam-take.jte`, `src/main/jte/students.jte`,
  `src/main/resources/static/css/my-study-plan.css`) that are **not** part of
  this change: `master` advanced past this branch's base commit (`5f53cd7`)
  with an unrelated design-system/dark-mode commit
  (`a95e11b`/`f71317c`/`f2aed28`/`26e2020`) while this branch was in progress.
  `git diff master...HEAD` (against the actual merge-base) isolates this
  branch's real content; restricted to `*.java` files the two forms of the
  diff are identical, since that drift touched no `.java` file.

Last run on 2026-09-28 in
`/home/meysam/Documents/school-project/.claude/worktrees/mystifying-noyce-373587`,
branch `claude/separate-classes-by-type-04c19d`.
