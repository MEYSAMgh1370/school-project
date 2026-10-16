# Architecture

Status: current behavior checked against the repository on 2026-08-06.

## Runtime Shape

Roshdeandishe is a Java 21 Spring Boot modular monolith. The repository builds one Maven artifact, not a multi-module Maven reactor. Spring Modulith verifies package-level module boundaries through [`ModularityTests`](../src/test/java/com/example/roshdeandishe/ModularityTests.java).

Evidence:

- [`pom.xml`](../pom.xml) declares Java 21, Spring Boot, Spring Modulith, JTE, Flyway, Testcontainers, and related dependencies.
- [`RoshdeandisheApplication.java`](../src/main/java/com/example/roshdeandishe/RoshdeandisheApplication.java) is the application entry point.
- [`ModularityTests.java`](../src/test/java/com/example/roshdeandishe/ModularityTests.java) verifies module boundaries and can generate Spring Modulith documentation under `target/spring-modulith-docs/`.

## Modules

The base package is `com.example.roshdeandishe`. Important top-level modules include:

- `platform` - shared kernel, tenant context, security helpers, cross-cutting support.
- `iam` - users, memberships, roles, permissions, auth, JWT, OTP, invitations, entitlements.
- `school` - school records, self-service creation, slugs, status.
- `academic` - classes, students, guardians, grades.
- `finance` - tuition fees, invoices, payments, expenses, attachments, audit trail.
- `attendance` - one roll per class per school day (only the absent/late exceptions are stored), per-class absentees, the admin figure and a per-student report; depends only on the public facades of `academic`, `school` and `iam` (see [`changes/attendance.md`](changes/attendance.md)).
- `studentview` - read-only student summary (latest grades, upcoming exams, absence count) and today's attendance for one student, shared by the dashboard and the Bale bot; depends only on the public facades of `academic`, `exam`, `attendance`, `school` and `iam`.
- `notification` - outbound notifications and the inbound Bale bot channel (account linking, webhook handling, invoice notifications). Outbound delivery goes through a package-private `NotificationChannel` abstraction with Bale as the only channel today; see [`changes/rename-bale-module-to-notification.md`](changes/rename-bale-module-to-notification.md) and [`changes/notification-channel-abstraction.md`](changes/notification-channel-abstraction.md).
- `curriculum` - topics and question bank.
- `questionbox` - teachers' own multiple-choice and essay questions (typed or image-only, images stored in `bytea`), reusable print headers and saved printable question sheets; reads the bank only through `CurriculumFacade` and creators' roles only through `IamFacade` (see [`changes/question-box.md`](changes/question-box.md)).
- `studyplan` - study plans and generated schedule blocks.
- `exam` - exams, attempts, tokenized exam-taking.
- `web` - server-rendered page composition for management and student-facing web pages.

Cross-module access should go through a module's public facade or explicitly exposed named interface. Internal packages are not cross-module contracts.

### Package Layout

Within each domain module, classes are grouped by type:

```
<module>/
  <Module>Facade.java, enums, events    exposed module API
  controller/                           @RestController / @Controller
  dto/request/, dto/response/           request and response records
  model/                                JTE page models and row types
  config/                               @Configuration, filters, aspects
  internal/                             entities, repositories, services — flat
  shared/                               @NamedInterface
```

`config/` exists only in `iam` and `platform`, the only modules with
`@Configuration` classes, filters, or aspects to hold. `dto/` exists only where
a controller had nested request/response records to extract; `notification` has no
`dto/`. `web` has no `internal/`/`shared/` split — it is a pure
view-composition module with `controller/` and `model/` (page models, row types,
and the shared navbar/role-label support classes) only.

Two rules keep this shape from widening a module's exposed contract:

- Nothing moves into a base package. The base package is a module's exposed
  API; moving a class into it silently widens that module's contract, and
  `ApplicationModules.verify()` does not catch this, because exposing more is
  not a violation it checks for.
- `internal/` stays flat. Most of its types are package-private, and splitting
  it into `entity/`/`repository/`/`service/` packages would force widening all
  of them to `public`, removing the compiler-enforced encapsulation layer that
  currently sits beneath Spring Modulith inside every module.

See [`changes/package-by-type-within-modules.md`](changes/package-by-type-within-modules.md)
for the full rationale, the alternatives this rejected, and the visibility
widenings the split did require.

## Tenancy

The current tenant is a school. API requests that operate on school-scoped data usually require `X-School-ID`, resolved by `TenantResolverFilter` into `TenantContext`.

Evidence:

- [`TenantResolverFilter.java`](../src/main/java/com/example/roshdeandishe/platform/config/TenantResolverFilter.java) requires `X-School-ID` for protected API routes and excludes public, account, page, Bale webhook, and exam-take routes.
- Controllers with both a `{schoolId}` path variable and tenant-scoped behavior explicitly compare the path value with the header-selected tenant before calling facades.

## Authentication And Authorization

The web app uses session-cookie authentication. Native/mobile API calls can use additive JWT bearer authentication. Both mechanisms feed the same authenticated user model.

Authorization is not based on Spring Security roles. Protected endpoints use the project's two-layer authorization model:

- `@RequiresModule("<MODULE_CODE>")` checks school entitlement.
- `@RequiresPermission("<PERMISSION_CODE>")` checks the authenticated user's active memberships and roles.

Public endpoints, account endpoints, `my` endpoints, Bale webhook endpoints, and exam-take token endpoints intentionally do not all follow the same school-admin authorization shape.

Evidence:

- [`SecurityConfig.java`](../src/main/java/com/example/roshdeandishe/iam/config/SecurityConfig.java) configures session auth, bearer auth, CSRF behavior, and public paths.
- `@RequiresModule` and `@RequiresPermission` annotations are used across protected REST controllers.

## Persistence

PostgreSQL is the target database. Flyway owns schema creation and migrations. Hibernate validates the schema rather than creating or updating it.

Evidence:

- [`application.yml`](../src/main/resources/application.yml) configures Flyway schemas and `spring.jpa.hibernate.ddl-auto: validate`.
- [`V1__baseline_schema.sql`](../src/main/resources/db/migration/V1__baseline_schema.sql) creates the baseline tables.
- [`V2__tenant_finance_consistency_constraints.sql`](../src/main/resources/db/migration/V2__tenant_finance_consistency_constraints.sql) adds consistency constraints.
- [`application-prod.yml`](../src/main/resources/application-prod.yml) requires production database and signing secrets through environment variables.

Existing migrations must not be edited after they have been applied. Add new migrations for schema changes.

## Frontend

The web UI is server-rendered with JTE templates, Alpine.js, Bulma, and WebJars. There is no npm, webpack, Vite, or SPA build.

Evidence:

- [`src/main/jte/`](../src/main/jte/) contains page templates and shared layouts.
- [`layout/main.jte`](../src/main/jte/layout/main.jte) owns the page head, global assets, optional page scripts, and optional page styles.
- [`app.css`](../src/main/resources/static/css/app.css) owns shared design tokens and global CSS rules.
- [`src/main/resources/static/js/`](../src/main/resources/static/js/) contains page-level Alpine components.

Auth/identity pages can live in `iam/controller/`. Cross-module product pages live in `web` and compose data through facades or client-side REST calls. Do not put REST endpoints in `web`.

## Native Android

The `android/` directory is a separate Gradle/Kotlin/Compose Android app. It is not part of the backend Maven artifact. The current native app is a thin read-only student shell that authenticates through `POST /api/auth/token` and reads profile, memberships, and enrollment data.

Evidence:

- [`android/README.md`](../android/README.md)
- [`android/app/build.gradle.kts`](../android/app/build.gradle.kts)
- Android API service and view model files under `android/app/src/main/java/`.

## Deployment

The repository deploys as a Docker application. The image builds the Maven project and runs the Spring Boot jar under the `prod` profile. The deployment target documented in this repository is Liara, but the artifact is a normal Dockerized Spring Boot app.

Evidence:

- [`Dockerfile`](../Dockerfile)
- [`liara.json`](../liara.json)
- [`DEPLOYMENT.md`](../DEPLOYMENT.md)
- [`application-prod.yml`](../src/main/resources/application-prod.yml)

Production should not rely on development secrets or demo data. Web sessions are held in PostgreSQL (`spring-session-jdbc`, tables owned by Flyway), so a restart or deploy no longer ends every signed-in session. Horizontal scaling is still not a current capability, but the reason is no longer sessions: the two `@Scheduled` jobs have no multi-instance guard. See [`changes/jdbc-backed-web-sessions.md`](changes/jdbc-backed-web-sessions.md).

The production JVM is sized explicitly rather than as a percentage of container memory, and the `prod` profile excludes the Spring Modulith runtime bootstrap, which would otherwise import the whole classpath through ArchUnit on every boot to build a module model that production never serves. Module boundaries are verified by `ModularityTests` at test time instead, and the development profile still exposes `/actuator/modulith`. Measured at 537 MB RSS before and 408 MB after, on an empty database. The measurement method, its variance band, and the proposed follow-up are in [`changes/production-memory-footprint.md`](changes/production-memory-footprint.md).
