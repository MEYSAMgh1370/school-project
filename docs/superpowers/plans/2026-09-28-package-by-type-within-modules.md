# Package Classes By Type Within Each Module — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move controllers into `<module>/controller/`, extract 90 nested request/response records into `<module>/dto/`, and move configuration classes into `iam/config/` and `platform/config/`, without changing any behavior.

**Architecture:** This is a Spring Modulith modular monolith where the package layout *is* the architecture, enforced by `ApplicationModules.verify()`. Separation happens *inside* each module, never across modules. Module base packages are the exposed API; `internal/` and `shared/` are untouched. One commit per module, each independently compiling and passing `ModularityTests`.

**Tech Stack:** Java, Spring Boot, Spring Modulith, Spring Data JPA, JTE (precompiled via `jte-maven-plugin`), Maven wrapper (`./mvnw`), JUnit 5, Testcontainers.

**Spec:** [`docs/superpowers/specs/2026-09-28-package-by-type-within-modules-design.md`](../specs/2026-09-28-package-by-type-within-modules-design.md)

## Global Constraints

- **No behavior changes.** Only `package` lines, `import` lines, file locations, JTE `@import` lines, the documented visibility widenings (3 types and 5 members, enumerated in Tasks 2 and 10), and the two comment refreshes in Task 4. Nothing else.
- **Widen the minimum.** A type or member becomes `public` only when a consumer lands in a *different* package. If the caller moves to the same destination package, it stays package-private. Each task names exactly which members qualify; do not widen others for consistency or convenience.
- **Never edit an AOP pointcut string.** Three pointcuts name types as string literals (`LoggingAspect:36`, `ModuleAccessAspect:52`, `PermissionAccessAspect:36`). All three stay valid under this plan because the types they name do not move. If you find yourself needing to edit one, something has been moved that this plan says stays put.
- **Never move a class into a module base package.** Base packages are the exposed module API; moving a class in silently widens the module's contract. `ModularityTests` does **not** catch this — only diff review does.
- **Never move a class out of `internal/` or `shared/`.** The one exception is the ten `iam/shared` classes named in Task 2 — four page/API controllers and six config classes — which are module-internal despite their location. `RequiresModule` and `RequiresPermission` stay.
- **Do not rename anything.** Record names, class names, and record component names stay byte-identical, or JSON contracts change.
- **Do not split `internal/`.** 114 of 225 types are package-private and would need widening. Out of scope per the spec.
- **Record extraction is mechanical:** a nested `public record Foo(...)` becomes a top-level `public record Foo(...)` in `<module>/dto/Foo.java`. Same name, same components, same order.
- **Zero test file changes are expected.** No test imports any controller or controller DTO (verified). If a task requires editing a file under `src/test/`, stop and report it — it means an assumption in the spec is wrong.
- Every task ends green: `./mvnw -q compile` and `./mvnw test -Dtest=ModularityTests` both pass before committing.

## Review Focus

These are the failure modes this refactor can introduce that the per-task compile check will *not* catch. Each has a step in the task that owns it.

1. **Stale JTE precompilation** — `jte-maven-plugin` precompiles templates; a reused `target/` yields a runtime "Circular view path" 500 rather than a build failure. Tasks 2 and 10 run `./mvnw clean verify`, not incremental verify.
2. **A moved type named by an AOP pointcut string** — `LoggingAspect:36`, `ModuleAccessAspect:52`, and `PermissionAccessAspect:36` name annotation types as string literals, which import rewriting cannot fix. A broken pointcut produces no compile error and no test failure; the aspect just silently stops advising. This plan keeps all three named types (`Loggable`, `RequiresModule`, `RequiresPermission`) where they are, so no literal needs editing — Task 9 Step 2b and Task 11 Step 5 grep to prove none changed.
3. **Accidentally widening a module's exposed API** — moving a class *into* a base package is not a Modulith violation, so `verify()` stays green while the module's contract silently grows. Every task has an explicit diff-review step asserting no file landed in a base package.
4. **JSON contract drift during record extraction** — a mistyped component name or reordered component changes the API shape, and only the HTTP-level integration tests catch it. Each module task runs its own integration tests, not just `ModularityTests`.
5. **Spring bean name collision** — bean names derive from simple class names, which do not change here, but moving `@Component`-annotated classes between packages is when collisions surface. Any `@SpringBootTest` failing to load the context is the signal; each task runs at least one.
6. **Open-redirect allow-list regression in `PostAuthenticationDestination`** — it becomes `public` and moves packages in Task 2. `LoginDashboardFlowIntegrationTest.loginAndRegistrationPagesRejectExternalPostAuthenticationDestinations` (line 95) is the pin; Task 2 runs it by name.

---

## Shared Procedure

Every module task below follows this identical six-phase procedure. The task
then supplies the exact file names, record names, and test classes. Nothing in
this procedure varies between tasks.

**Phase A — create the `dto/` package and extract records.**

For each nested record listed in the task, cut it from the controller and paste
it into its own file. Given this in `finance/FinanceController.java`:

```java
public class FinanceController {
    // ... handler methods ...

    public record SetTuitionFeeRequest(BigDecimal amount) {
    }
}
```

create `finance/dto/SetTuitionFeeRequest.java`:

```java
package com.example.roshdeandishe.finance.dto;

import java.math.BigDecimal;

public record SetTuitionFeeRequest(BigDecimal amount) {
}
```

Carry over every import the record's components need (`java.math.BigDecimal`,
`java.time.LocalDate`, `java.util.List`, module enums such as
`com.example.roshdeandishe.finance.PaymentMethod`). Preserve any Javadoc or
Persian comment attached to the record. Where one extracted record references
another (for example `ExamResponse` referencing `SectionResponse`), both land in
the same `dto/` package and need no import of each other.

**Phase B — move the controllers.**

`git mv` each controller into `<module>/controller/`, change its `package` line
to `com.example.roshdeandishe.<module>.controller`, and add
`import com.example.roshdeandishe.<module>.dto.*;` — or explicit per-record
imports if the project style prefers them; match whatever the surrounding files
already do. Add imports for anything the controller used unqualified from its
old package: the module facade, enums, and events now need explicit imports
because the controller is no longer in the base package.

**Phase C — compile.**

```bash
./mvnw -q compile
```

Expected: BUILD SUCCESS. A failure here is almost always a missing import from
Phase B — the controller left a base package and lost its implicit access to
the facade and enums that live there.

**Phase D — verify module boundaries.**

```bash
./mvnw test -Dtest=ModularityTests
```

Expected: 3 tests pass, including `verifyModularStructure`. A failure means a
class landed somewhere that crosses a module boundary.

**Phase E — run the module's integration tests, then review the diff.**

Run the test classes named in the task. Then:

```bash
git status --short
```

Expected: every added file sits under `<module>/controller/`, `<module>/dto/`,
or `<module>/config/`. **No file may appear directly in a module base package**
(`<module>/Foo.java`). If one does, it has just been added to the module's
exposed API — move it back.

**Phase F — commit.**

```bash
git add -A
git commit -m "<message from the task>"
```

---

## Task 1: finance

**Files:**
- Move: `finance/FinanceController.java`, `finance/ExpenseController.java`, `finance/MyInvoicesController.java` → `finance/controller/`
- Create: 19 files under `finance/dto/`
- Test: no changes

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: the `<module>/controller/` + `<module>/dto/` layout that Tasks 3–8 replicate. `com.example.roshdeandishe.finance.dto` holds the 19 records below as top-level public records.

Base package keeps `FinanceFacade`, `ExpenseFacade`, `InvoiceStatus`, `ExpenseStatus`, `PaymentMethod`, `PaymentRecordedEvent`, `InvoiceIssuedEvent`, `ExpenseStatusChangedEvent`. `finance/internal/` and `finance/shared/` are untouched.

- [ ] **Step 1: Phase A — extract 19 records to `finance/dto/`**

From `ExpenseController` (12): `CategoryRequest`, `CategoryResponse`, `SeedDefaultsResponse`, `VendorRequest`, `VendorResponse`, `ExpenseRequest`, `ExpenseResponse`, `AttachmentResponse`, `CategorySummaryResponse`, `VendorSummaryResponse`, `MethodSummaryResponse`, `FinanceSummaryResponse`

From `FinanceController` (6): `SetTuitionFeeRequest`, `TuitionFeeResponse`, `InvoiceResponse`, `RecordPaymentRequest`, `PaymentResponse`, `BackfillResponse`

From `MyInvoicesController` (1): `MyInvoiceResponse`

- [ ] **Step 2: Phase B — move the 3 controllers to `finance/controller/`**
- [ ] **Step 3: Phase C — compile**
- [ ] **Step 4: Phase D — ModularityTests**
- [ ] **Step 5: Phase E — integration tests, then diff review**

```bash
./mvnw test -Dtest='FinanceFlowIntegrationTest,FinanceManagementFlowIntegrationTest,FinancePaymentFlowIntegrationTest,ExpenseManagementFlowIntegrationTest,ExpenseReportFlowIntegrationTest,ExpenseAttachmentFlowIntegrationTest,MyInvoicesPageFlowIntegrationTest'
```

Expected: all pass. These exercise the JSON shape of all 19 extracted records over HTTP — this is the Review Focus item 4 check for finance.

- [ ] **Step 6: Phase F — commit**

```bash
git add -A
git commit -m "Move finance controllers and extract request/response records

Controllers move to finance/controller/; the 19 nested request and response
records become top-level records in finance/dto/. No behavior change."
```

---

## Task 2: iam

The largest task, and the only one besides Task 10 that touches JTE. It carries two of the three visibility widenings.

**Files:**
- Move to `iam/controller/`: `iam/MeController.java`, `iam/MembershipController.java`, `iam/RoleController.java`, `iam/shared/AuthController.java`, `iam/shared/LoginPageController.java`, `iam/shared/RegisterPageController.java`, `iam/shared/AccountActivationPageController.java`
- Move to `iam/config/`: `iam/shared/SecurityConfig.java`, `iam/shared/JwtAuthenticationFilter.java`, `iam/shared/JwtTokenProvider.java`, `iam/shared/ModuleAccessAspect.java`, `iam/shared/PermissionAccessAspect.java`, `iam/shared/PostAuthenticationDestination.java`
- Create: 22 files under `iam/dto/`
- Modify: `src/main/jte/activate.jte:1`
- Test: no changes

**Interfaces:**
- Consumes: the layout established in Task 1.
- Produces: `com.example.roshdeandishe.iam.config.JwtTokenProvider` and `...iam.config.PostAuthenticationDestination`, both now `public`. `iam/shared` is left holding exactly `RequiresModule` and `RequiresPermission` — the two annotations 13 files in other modules import. Do not move those two; they are the module's real cross-module contract.

- [ ] **Step 1: Phase A — extract 22 records to `iam/dto/`**

From `AuthController` (10): `LoginRequest`, `LoginResponse`, `TokenResponse`, `ActivateAccountRequest`, `ActivateAccountResponse`, `RegisterRequest`, `RegisterResponse`, `OtpRequestRequest`, `OtpRequestResponse`, `OtpVerifyRequest`

From `MembershipController` (5): `CreateMembershipRequest`, `SetActiveRequest`, `MemberNameResponse`, `MembershipResponse`, `InviteTokenResponse`

From `RoleController` (3): `PermissionResponse`, `RoleResponse`, `CreateRoleRequest`

From `MeController` (3): `MeResponse`, `SetPhoneRequest`, `MyMembershipResponse`

From `AccountActivationPageController` (1): `ActivatePageModel`

- [ ] **Step 2: Move the 6 config classes to `iam/config/` and widen two**

`git mv` all six, set `package com.example.roshdeandishe.iam.config;` in each.

Two must change visibility, because some of their callers land in `iam/controller/` while the classes themselves land in `iam/config/`. Widen the type **and only the members actually called across that boundary**:

```java
// JwtTokenProvider.java
public class JwtTokenProvider {        // was: class JwtTokenProvider {
    public long expirationSeconds()    // line 43, was: long expirationSeconds()
    public String generateToken(...)   // line 47, was: String generateToken(...)
```

`AuthController:71-72` calls exactly these two. Leave `validateAndExtractEmail`
(line 57) and the constructor (line 35) package-private — `JwtAuthenticationFilter`
calls the former and Spring calls the latter, and both live in `iam/config/`
alongside the class.

```java
// PostAuthenticationDestination.java
public final class PostAuthenticationDestination {        // was: final class ...
    public static String from(String requestedDestination)  // line 16, was: static String from(...)
```

`LoginPageController:20` and `RegisterPageController:21` call `from`. Leave
everything else package-private: `loginUrlFor` (line 28) is called only by
`SecurityConfig:171`, which moves into the same `iam/config/` package, and the
three constants (lines 9-11) are read only inside the class itself. The private
constructor at line 13 stays private.

Do not change any method body — the allow-list logic in `from` and `loginUrlFor`
is exactly what Review Focus item 6 protects.

This class moves to `config/` rather than staying in `shared/` deliberately: a
`public` type inside a `@NamedInterface` package joins the module's *exposed*
cross-module API. In `config/` it is public to `iam` and invisible elsewhere.

- [ ] **Step 3: Phase B — move the 7 controllers to `iam/controller/`**

The four coming from `iam/shared/` will need imports they previously got for
free from that package — including `JwtTokenProvider` and
`PostAuthenticationDestination`, now at `com.example.roshdeandishe.iam.config`.

- [ ] **Step 4: Update the JTE import**

`src/main/jte/activate.jte` line 1:

```
@import com.example.roshdeandishe.iam.dto.ActivatePageModel
```

was `@import com.example.roshdeandishe.iam.shared.AccountActivationPageController.ActivatePageModel`.

- [ ] **Step 5: Phase C — compile**
- [ ] **Step 6: Phase D — ModularityTests**

Expected: pass. This is the sharpest boundary check in the plan — `iam/shared`
is a `@NamedInterface` and 13 files in other modules depend on it. A failure
means one of the two annotations was moved by mistake.

- [ ] **Step 7: Confirm `iam/shared` is down to two annotations**

```bash
ls src/main/java/com/example/roshdeandishe/iam/shared/
```

Expected exactly: `RequiresModule.java`, `RequiresPermission.java`, `exception/`, `package-info.java`

- [ ] **Step 8: Clean verify — JTE was touched**

```bash
./mvnw clean verify
```

Expected: BUILD SUCCESS, full suite green. `clean` is mandatory: `activate.jte`
changed, and precompiled JTE classes in a stale `target/` would mask the edit
and surface later as a runtime "Circular view path" 500. Do not substitute
`./mvnw verify`.

- [ ] **Step 9: Confirm the open-redirect pin passed**

```bash
./mvnw test -Dtest='LoginDashboardFlowIntegrationTest#loginAndRegistrationPagesRejectExternalPostAuthenticationDestinations'
```

Expected: PASS. This is Review Focus item 6 — it proves widening
`PostAuthenticationDestination` to `public` did not disturb the allow-list that
stops `?next=` from becoming an open redirect.

- [ ] **Step 10: Phase E diff review, then commit**

```bash
git status --short
git add -A
git commit -m "Move iam controllers and config, extract request/response records

Controllers move to iam/controller/, security and JWT plumbing to iam/config/,
and the 22 nested records to iam/dto/. JwtTokenProvider and
PostAuthenticationDestination widen to public because their consumers now span
two packages; PostAuthenticationDestination moves to config/ rather than
staying in the @NamedInterface shared/ package, so it does not join iam's
exposed cross-module API.

iam/shared is now exactly RequiresModule and RequiresPermission, which is the
cross-module contract other modules actually import."
```

---

## Task 3: exam

**Files:**
- Move: `exam/ExamController.java`, `exam/ExamResultController.java`, `exam/ExamTakeController.java`, `exam/MyExamsController.java` → `exam/controller/`
- Create: 14 files under `exam/dto/`
- Test: no changes

**Interfaces:**
- Consumes: the layout from Task 1.
- Produces: `com.example.roshdeandishe.exam.dto` with the 14 records below. Base package keeps `ExamFacade` and `ExamType`.

- [ ] **Step 1: Phase A — extract 14 records to `exam/dto/`**

From `ExamController` (6): `CreateExamRequest`, `AssignStudentsRequest`, `CreateSectionRequest`, `CreateSectionAutoRequest`, `ExamResponse`, `SectionResponse`

From `ExamTakeController` (4): `SubmitRequest`, `ExamTakeResponse`, `SectionTakeResponse`, `TakeQuestionResponse`

From `ExamResultController` (3): `AttemptedStudentResponse`, `SectionResultResponse`, `ExamResultResponse`

From `MyExamsController` (1): `AttemptResponse`

`ExamResponse` references `SectionResponse`, and `ExamResultResponse` references `SectionResultResponse`. Both pairs land in the same `dto/` package, so neither needs an import of the other.

- [ ] **Step 2: Phase B — move the 4 controllers to `exam/controller/`**
- [ ] **Step 3: Phase C — compile**
- [ ] **Step 4: Phase D — ModularityTests**
- [ ] **Step 5: Phase E — integration tests, then diff review**

```bash
./mvnw test -Dtest=ExamFlowIntegrationTest
```

- [ ] **Step 6: Phase F — commit**

```bash
git add -A
git commit -m "Move exam controllers and extract request/response records

Controllers move to exam/controller/; the 14 nested records become top-level
records in exam/dto/. No behavior change."
```

---

## Task 4: academic

**Files:**
- Move: `academic/ClassController.java`, `academic/GradeController.java`, `academic/StudentController.java`, `academic/MyEnrollmentController.java`, `academic/MyGradesController.java` → `academic/controller/`
- Create: 10 files under `academic/dto/`
- Test: no changes

**Interfaces:**
- Consumes: the layout from Task 1.
- Produces: `com.example.roshdeandishe.academic.dto` with the 10 records below. Base package keeps `AcademicFacade`, `GradingScheme`, `GradingTerm`, `DescriptiveGradeLevel`, `StudentEnrolledEvent`.

- [ ] **Step 1: Phase A — extract 10 records to `academic/dto/`**

From `StudentController` (4): `EnrollStudentRequest`, `StudentResponse`, `LinkGuardianRequest`, `GuardianLinkResponse`

From `ClassController` (2): `CreateClassRequest`, `ClassResponse`

From `GradeController` (2): `RecordGradeRequest`, `GradeResponse`

From `MyEnrollmentController` (1): `MyEnrollmentResponse`

From `MyGradesController` (1): `MyGradeResponse`

- [ ] **Step 2: Phase B — move the 5 controllers to `academic/controller/`**
- [ ] **Step 3: Update two stale comment references**

`academic/internal/ClassEntity.java:29` and `academic/AcademicFacade.java:47` both name `ClassController.CreateClassRequest` in Persian prose. Update both to `CreateClassRequest` so the comments still point somewhere real. These are the only two `XxxController.Yyy` mentions in the repository, and they are comments — no code depends on them.

- [ ] **Step 4: Phase C — compile**
- [ ] **Step 5: Phase D — ModularityTests**
- [ ] **Step 6: Phase E — integration tests, then diff review**

```bash
./mvnw test -Dtest='StudentManagementFlowIntegrationTest,ClassManagementFlowIntegrationTest,GradeManagementFlowIntegrationTest,MyEnrollmentFlowIntegrationTest,MyGradesPageFlowIntegrationTest,GradesPageFlowIntegrationTest'
```

- [ ] **Step 7: Phase F — commit**

```bash
git add -A
git commit -m "Move academic controllers and extract request/response records

Controllers move to academic/controller/; the 10 nested records become
top-level records in academic/dto/. Also refreshes two comments that named
ClassController.CreateClassRequest. No behavior change."
```

---

## Task 5: studyplan

**Files:**
- Move: `studyplan/PlanController.java`, `studyplan/StudyIntervalController.java`, `studyplan/MyStudyIntervalController.java`, `studyplan/WeekCourseController.java`, `studyplan/IntervalScheduleController.java` → `studyplan/controller/`
- Create: 14 files under `studyplan/dto/`
- Test: no changes

**Interfaces:**
- Consumes: the layout from Task 1.
- Produces: `com.example.roshdeandishe.studyplan.dto` with the 14 records below. Base package keeps `StudyPlanFacade`.

- [ ] **Step 1: Phase A — extract 14 records to `studyplan/dto/`**

From `StudyIntervalController` (5): `CreateIntervalRequest`, `UpdateIntervalRequest`, `CopyWeekRequest`, `IntervalResponse`, `CopyResultResponse`

From `PlanController` (4): `CreatePlanRequest`, `AssignPlanRequest`, `PlanResponse`, `AssignmentResponse`

From `WeekCourseController` (2): `CreateWeekCourseRequest`, `WeekCourseResponse`

From `MyStudyIntervalController` (2): `UpdateMyIntervalRequest`, `MyIntervalResponse`

From `IntervalScheduleController` (1): `ScheduleResponse`

Several of these carry `Weekday` and `TimeRange` components from `StudyPlanFacade`. Those types stay nested in the facade — import them as `com.example.roshdeandishe.studyplan.StudyPlanFacade.Weekday` and do not extract them.

- [ ] **Step 2: Phase B — move the 5 controllers to `studyplan/controller/`**
- [ ] **Step 3: Phase C — compile**
- [ ] **Step 4: Phase D — ModularityTests**
- [ ] **Step 5: Phase E — integration tests, then diff review**

```bash
./mvnw test -Dtest='StudyPlanManagementFlowIntegrationTest,MyStudyPlanFlowIntegrationTest'
```

- [ ] **Step 6: Phase F — commit**

```bash
git add -A
git commit -m "Move studyplan controllers and extract request/response records

Controllers move to studyplan/controller/; the 14 nested records become
top-level records in studyplan/dto/. Facade-nested Weekday and TimeRange stay
in StudyPlanFacade as the exposed cross-module contract. No behavior change."
```

---

## Task 6: curriculum

**Files:**
- Move: `curriculum/TopicController.java`, `curriculum/QuestionController.java` → `curriculum/controller/`
- Create: 5 files under `curriculum/dto/`
- Test: no changes

**Interfaces:**
- Consumes: the layout from Task 1.
- Produces: `com.example.roshdeandishe.curriculum.dto` with the 5 records below. Base package keeps `CurriculumFacade` and `QuestionDifficulty`.

- [ ] **Step 1: Phase A — extract 5 records to `curriculum/dto/`**

From `TopicController` (3): `CreateTopicRequest`, `TopicResponse`, `TopicTreeResponse`

From `QuestionController` (2): `CreateQuestionRequest`, `QuestionResponse`

`TopicTreeResponse` is self-referential (a topic tree contains child topics). Keep the recursive component type as-is; it resolves within the same `dto/` package.

- [ ] **Step 2: Phase B — move the 2 controllers to `curriculum/controller/`**
- [ ] **Step 3: Phase C — compile**
- [ ] **Step 4: Phase D — ModularityTests**
- [ ] **Step 5: Phase E — integration tests, then diff review**

```bash
./mvnw test -Dtest=CurriculumFlowIntegrationTest
```

- [ ] **Step 6: Phase F — commit**

```bash
git add -A
git commit -m "Move curriculum controllers and extract request/response records

Controllers move to curriculum/controller/; the 5 nested records become
top-level records in curriculum/dto/. No behavior change."
```

---

## Task 7: school

**Files:**
- Move: `school/SchoolController.java` → `school/controller/`
- Create: 2 files under `school/dto/`
- Test: no changes

**Interfaces:**
- Consumes: the layout from Task 1.
- Produces: `com.example.roshdeandishe.school.dto` with `CreateSchoolRequest` and `SchoolResponse`. Base package keeps `SchoolFacade` and its nested `SchoolInfo`, `SchoolId`, `SchoolStatus`, `SchoolLevel` — all of which `web` and the test suite import. Do not extract those.

- [ ] **Step 1: Phase A — extract 2 records to `school/dto/`**

From `SchoolController` (2): `CreateSchoolRequest`, `SchoolResponse`

- [ ] **Step 2: Phase B — move `SchoolController` to `school/controller/`**
- [ ] **Step 3: Phase C — compile**
- [ ] **Step 4: Phase D — ModularityTests**
- [ ] **Step 5: Phase E — integration tests, then diff review**

```bash
./mvnw test -Dtest='SchoolEntryFlowIntegrationTest,SchoolSelectionWarningFlowIntegrationTest'
```

- [ ] **Step 6: Phase F — commit**

```bash
git add -A
git commit -m "Move school controller and extract request/response records

SchoolController moves to school/controller/; CreateSchoolRequest and
SchoolResponse become top-level records in school/dto/. SchoolFacade's nested
types stay put as the exposed cross-module contract. No behavior change."
```

---

## Task 8: bale

The smallest task: one controller, no records, so no `dto/` package.

**Files:**
- Move: `bale/BaleWebhookController.java` → `bale/controller/`
- Test: no changes

**Interfaces:**
- Consumes: the layout from Task 1.
- Produces: nothing new. Base package keeps `BaleFacade`. `bale/shared/` holds only a `package-info.java` and stays exactly as it is — do not delete it, the empty `@NamedInterface` is intentional.

- [ ] **Step 1: Phase B — move `BaleWebhookController` to `bale/controller/`**

No Phase A: `BaleWebhookController` has no nested records. Do not create an empty `bale/dto/` package.

- [ ] **Step 2: Phase C — compile**
- [ ] **Step 3: Phase D — ModularityTests**
- [ ] **Step 4: Phase E — integration tests, then diff review**

```bash
./mvnw test -Dtest=BaleFlowIntegrationTest
```

- [ ] **Step 5: Phase F — commit**

```bash
git add -A
git commit -m "Move bale controller to bale/controller/

BaleWebhookController has no nested records, so no dto/ package is created.
No behavior change."
```

---

## Task 9: platform

No controllers and no records — this task only creates `platform/config/`.

**Files:**
- Move to `platform/config/`: `platform/OpenApiConfig.java`, `platform/TenantResolverFilter.java`, `platform/LoggingAspect.java`
- Test: no changes

**Interfaces:**
- Consumes: the layout from Task 2's `config/` package.
- Produces: nothing other modules consume. **`TenantContext` and `ProblemDetailFactory` must stay in the base package** — 20 and 8 files in other modules import them respectively, and moving them to `config/` would un-expose them and break every one. `PlatformExceptionHandler` also stays, consistent with every other module's exception handler staying put.

**`Loggable` stays in the base package. Do not move it.** `LoggingAspect:36`
names it twice as a string literal inside its `@Around` expression:

```java
@Around("@annotation(com.example.roshdeandishe.platform.Loggable) || @within(com.example.roshdeandishe.platform.Loggable)")
```

String literals are not touched by import rewriting, so moving `Loggable` to
`platform/config/` would leave this pointcut naming a class that no longer
exists — Spring would either fail to resolve it or silently stop advising every
`@Loggable` method, with no compile error and no test failure. Leaving the
annotation in place keeps the literal correct and costs nothing: `Loggable` is a
public annotation meant to be applied by other code, so the exposed base package
is where it belongs. This mirrors `RequiresModule`/`RequiresPermission`, which
stay in `iam/shared` while their aspects move to `iam/config`.

- [ ] **Step 1: Move the 3 classes to `platform/config/`**

`git mv` each, set `package com.example.roshdeandishe.platform.config;`.
`LoggingAspect` now needs `import com.example.roshdeandishe.platform.Loggable;`
for its Javadoc `{@link Loggable}` reference at line 12 — the `@Around` string
at line 36 already carries the fully-qualified name and must be left byte-identical.

- [ ] **Step 2: Confirm the exposed types did not move**

```bash
ls src/main/java/com/example/roshdeandishe/platform/
```

Expected exactly: `TenantContext.java`, `ProblemDetailFactory.java`, `PlatformExceptionHandler.java`, `Loggable.java`, `config/`, `package-info.java`

- [ ] **Step 2b: Confirm no pointcut literal changed**

```bash
git diff -- '*Aspect.java' | grep -E '^[+-].*@(Around|Before|After|Pointcut)'
```

Expected: no output. If any pointcut line appears in the diff, a type named by a
string literal was moved — revert it. This is the one failure mode in this plan
that neither the compiler nor the test suite catches.

- [ ] **Step 3: Phase C — compile**

Expected: BUILD SUCCESS. If this fails with "cannot find symbol: TenantContext" in another module, `TenantContext` was moved by mistake — revert it to the base package.

- [ ] **Step 4: Phase D — ModularityTests**
- [ ] **Step 5: Phase E — integration tests, then diff review**

```bash
./mvnw test -Dtest=PlatformExceptionHandlerFlowIntegrationTest
```

- [ ] **Step 6: Phase F — commit**

```bash
git add -A
git commit -m "Move platform infrastructure classes to platform/config/

OpenApiConfig, TenantResolverFilter, and LoggingAspect move to platform/config/.

Loggable deliberately stays in the base package: LoggingAspect names it twice as
a string literal in its @Around pointcut, and moving it would break the pointcut
silently, with no compile error and no failing test. TenantContext and
ProblemDetailFactory stay because 28 files across other modules import them, and
PlatformExceptionHandler stays alongside them. No behavior change."
```

---

## Task 10: web

The second JTE-touching task, and the third visibility widening. 21 of the 22 application `@import` lines in the template tree change here.

**Files:**
- Move to `web/controller/`: all 19 `*PageController.java` files
- Move to `web/model/`: `web/NavbarModel.java`, `web/NavbarModelAssembler.java`, `web/RoleLabels.java`
- Create: 4 files under `web/dto/`
- Modify: 18 `.jte` files (21 import lines)
- Test: no changes

**Interfaces:**
- Consumes: the layout from Task 1.
- Produces: `com.example.roshdeandishe.web.model.NavbarModel` (public, unchanged visibility), `...web.model.RoleLabels`, `...web.model.NavbarModelAssembler` (widened to public), and `com.example.roshdeandishe.web.dto` with 4 records.

- [ ] **Step 1: Phase A — extract 4 records to `web/dto/`**

From `DashboardPageController` (3): `DashboardModel`, `MembershipRow`, `SchoolEntryRow`

From `SchoolEntryPageController` (1): `SchoolEntryModel`

`NavbarModel`'s nested `SchoolOption` record does **not** move to `dto/` — it travels with `NavbarModel` into `web/model/` and stays nested. No JTE template references `SchoolOption` directly (verified), so nothing else changes.

- [ ] **Step 2: Move the 3 model classes to `web/model/` and widen one**

`git mv` all three, set `package com.example.roshdeandishe.web.model;`.

`NavbarModelAssembler` is package-private and is called by 17 page controllers that are about to land in `web/controller/`. Widen the type and exactly the two methods those controllers call:

```java
// NavbarModelAssembler.java
public class NavbarModelAssembler {                          // was: class NavbarModelAssembler {
    public NavbarModel forCurrentUser()                      // line 34, was: NavbarModel forCurrentUser()
    public NavbarModel assemble(UserProfile, List<MembershipInfo>)  // line 44, was: NavbarModel assemble(...)
```

Leave `schoolName(Long)` (line 63) and the constructor (line 26)
package-private. `schoolName` is called only from line 53 inside
`NavbarModelAssembler` itself, and the constructor only by Spring.

Do not be misled by `DashboardPageController.schoolName(Optional<SchoolInfo>, Long)`
at line 120 — that is a different, `private` method with a different signature
that travels with its controller into `web/controller/`. The two are unrelated
despite the shared name; neither needs the other.

`NavbarModel` and `RoleLabels` are already public — do not touch their visibility.

- [ ] **Step 3: Phase B — move the 19 page controllers to `web/controller/`**

`ClassesPageController`, `CurriculumPageController`, `DashboardPageController`, `ExamTakePageController`, `ExamsPageController`, `ExpensesPageController`, `FinancePageController`, `GradesPageController`, `LandingPageController`, `MyExamsPageController`, `MyGradesPageController`, `MyInvoicesPageController`, `MyStudyPlanPageController`, `PeoplePageController`, `RolesPageController`, `SchoolEntryPageController`, `SchoolRegistrationPageController`, `StudentsPageController`, `StudyPlansPageController`

Each needs `import com.example.roshdeandishe.web.model.NavbarModel;` and `...model.NavbarModelAssembler;` where used — previously implicit, now required.

- [ ] **Step 4: Update 21 JTE import lines across 18 templates**

`web.NavbarModel` → `web.model.NavbarModel` in 17 templates: `people.jte:1`, `school-new.jte:1`, `classes.jte:1`, `roles.jte:1`, `expenses.jte:1`, `finance.jte:1`, `grades.jte:1`, `my-exams.jte:1`, `my-invoices.jte:1`, `my-grades.jte:1`, `exams.jte:1`, `study-plans.jte:1`, `students.jte:1`, `curriculum.jte:1`, `my-study-plan.jte:1`, `dashboard.jte:2`, `layout/navbar.jte:1`

`web.RoleLabels` → `web.model.RoleLabels` in 2: `dashboard.jte:3`, `layout/navbar.jte:2`

`web.DashboardPageController.DashboardModel` → `web.dto.DashboardModel` in `dashboard.jte:1`

`web.SchoolEntryPageController.SchoolEntryModel` → `web.dto.SchoolEntryModel` in `school-entry.jte:8`

Verify none were missed:

```bash
grep -rn "roshdeandishe.web.NavbarModel\|roshdeandishe.web.RoleLabels\|PageController\." src/main/jte
```

Expected: no output.

- [ ] **Step 5: Clean verify — JTE was touched**

```bash
./mvnw clean verify
```

Expected: BUILD SUCCESS, full suite green. `clean` is mandatory — 18 templates changed, and stale precompiled JTE classes would let the build pass while pages 500 at runtime. This is the Review Focus item 1 gate.

- [ ] **Step 6: Confirm pages actually render**

```bash
./mvnw test -Dtest='MoshavereFrontendPageFlowIntegrationTest,EmptyStatePartialRenderTest,GradesPageFlowIntegrationTest,MyInvoicesPageFlowIntegrationTest'
```

Expected: all pass. A "Circular view path" error here means a template import is still stale.

- [ ] **Step 7: Phase E diff review, then commit**

```bash
git status --short
git add -A
git commit -m "Move web page controllers, models, and extract page DTOs

The 19 page controllers move to web/controller/, NavbarModel/RoleLabels/
NavbarModelAssembler to web/model/, and 4 nested page models to web/dto/.
NavbarModelAssembler widens to public because its 17 callers are now in a
sibling package. Updates 21 JTE @import lines across 18 templates."
```

---

## Task 11: documentation

**Files:**
- Modify: `openspec/conventions.md`, `openspec/architecture.md`
- Create: `openspec/changes/package-by-type-within-modules.md`
- Test: no changes

**Interfaces:**
- Consumes: the finished layout from Tasks 1–10.
- Produces: nothing code-facing.

Per `openspec/project.md`, a proposal under `changes/` records substantial restructures, and implemented proposals are retained for their rationale.

- [ ] **Step 1: Add the layout rule to `openspec/conventions.md`**

Document the target shape and the two rules that keep Modulith green:

```
<module>/
  <Module>Facade.java, enums, events    exposed module API
  controller/                           @RestController / @Controller
  dto/                                  request/response records
  config/                               @Configuration, filters, aspects
  internal/                             entities, repositories, services — flat
  shared/                               @NamedInterface
```

State both rules: nothing moves into a base package (it silently widens the
module's exposed contract, and `ModularityTests` will not catch it); `internal/`
stays flat because 114 of 225 types are package-private and splitting it would
require widening them all to `public`.

- [ ] **Step 2: Update the package diagram in `openspec/architecture.md`**

Replace the module-layout description with the shape above. Leave the module
boundary and event-flow content alone — neither changed.

- [ ] **Step 3: Write `openspec/changes/package-by-type-within-modules.md`**

Follow the change-workflow template in `openspec/project.md`: current behavior
with code evidence, proposed behavior, acceptance criteria, migration concerns,
and the tests that prove it. Mark it `Status: implemented 2026-09-28`.

Record both rejected alternatives with their costs, so the next person does not
re-propose them:

- A global by-layer split cannot pass `ApplicationModules.verify()`, because
  Modulith treats each top-level package as a module.
- Splitting `internal/` by type costs 114 visibility widenings and the
  compiler-enforced encapsulation layer beneath Modulith.

- [ ] **Step 4: Final whole-branch verification**

```bash
./mvnw clean verify
```

Expected: BUILD SUCCESS, all 34 `@SpringBootTest` classes green.

- [ ] **Step 5: Confirm the diff contains no surprises**

```bash
git diff master --stat
git diff master -- '*.java' | grep '^[+-]' | grep -v '^[+-][+-]' | grep -vE '^[+-](package |import |\s*$)' | head -50
```

Expected: the surviving lines are only the documented visibility widenings (3
types and 5 members), the two refreshed comments from Task 4, and the record
declarations moved between files. Anything else is an unintended behavior change
— investigate before proceeding.

Confirm no pointcut literal changed anywhere on the branch:

```bash
git diff master -- '*.java' | grep -E '^[+-].*@(Around|Before|After|Pointcut)\('
```

Expected: no output.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "Document the by-type package layout within modules

Adds the layout rule to conventions.md, updates the architecture diagram, and
records the change proposal with both rejected alternatives and their costs."
```
