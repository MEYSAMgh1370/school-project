# Grades By Course (درس)

Status: proposed 2026-10-02. **Phase 1 approved by the product owner the same
day** ("catalog is read-only for now") and is implemented: migration `V5`,
facade, API, the `/grades` course select and the `/my-grades` course column.
The current-state statement lives in [`specs/current-system.md`](../specs/current-system.md)
(Finance And Academic, Current Limitations). Phase 2 (platform actor and course
administration) is partly done by the Moshavere import work: a platform
administrator exists (`PLATFORM_HOLIDAY_MANAGE`), and platform-only code writes
the catalog (`POST /api/platform/academic/courses`, upsert by grade level and
name, and the Moshavere import); there is still no administration screen, and
no rename or deactivation code was found (checked 2026-10-18). The initial
course list is still a pending product input. Raised while reviewing `/my-grades`: a grade is one number
per student, class, academic year and term, so a parent cannot see how a child
did in each subject.

## Verified Baseline

From code, not from earlier docs:

- `academic.grades` has `student_id`, `class_id`, `academic_year`, `term`
  (`FIRST`/`SECOND`), `scheme` (copied from the class at write time),
  `numeric_score` (0–20) or `descriptive_level` (4 levels), `comment`,
  `recorded_by_user_id`, `recorded_at`, and
  `unique (student_id, class_id, academic_year, term)`.
  There is no course or subject column (`V1__baseline_schema.sql:189`,
  `GradeEntity`).
- `AcademicFacadeImpl.recordGrade` upserts on that key and validates the
  class's grading scheme (`classes.grading_scheme`, default `NUMERIC`).
- The grade is therefore, in effect, a single overall grade per term
  («نمرهٔ کل»). It is not linked to the exam module.
- `academic.classes` has `grade_level` (int), `teacher_id`, `grading_scheme`,
  `section`, `name`. There is no link from a class to a course.
- The only course-like data is `curriculum.topics` root nodes (school-level
  tree; the study plan treats roots as "courses"). It is per school, free-text,
  and not a platform catalog, so it is **not** reused here.
- `GradeController` (`/api/admin/schools/{schoolId}/grades`) is gated by
  `GRADES_EDIT` for both write and read; `MyGradesController`
  (`/api/me/schools/{schoolId}/grades`) returns the student's and the linked
  children's rows. Flyway owns the schema, Hibernate runs in `validate`
  (see [`../decisions/2026-07-30-flyway-schema-ownership.md`](../decisions/2026-07-30-flyway-schema-ownership.md)),
  so a new column needs a migration (`V5`).

## Decisions Already Taken By The Product Owner (2026-10-02)

1. The course list is **per grade level (پایه)** and **global for all schools**.
2. It is maintained by the **platform admin**, not by each school.
3. Existing grades stay as they are and read as «نمرهٔ کل»: the course on a
   grade is optional.
4. `#91` (parent dashboard) merges first; this is a separate PR.

## Proposed Contract

### Data

- New global table `academic.courses` (no `school_id`):
  `id`, `grade_level` (1–12, same domain as `classes.grade_level`), `name`
  (≤ 128), `unique (grade_level, name)`. Global like `iam.permissions`; it is
  not tenant data and must not go through tenant filtering.
- `academic.grades.course_id bigint null references academic.courses(id)`
  (`on delete restrict`). `null` = «نمرهٔ کل»; existing rows keep `null`.
- The old unique key is replaced by two partial unique indexes, because a plain
  unique over a nullable column lets several «نمرهٔ کل» rows coexist in
  PostgreSQL:
  - `unique (student_id, class_id, academic_year, term) where course_id is null`
  - `unique (student_id, class_id, academic_year, term, course_id) where course_id is not null`
- Module placement: inside `academic` (it already owns classes and
  `grade_level`). No new module and no cross-module dependency.

### Write path

- `RecordGradeRequest` gains optional `courseId`. Omitted/`null` keeps today's
  behavior exactly (upsert of the «نمرهٔ کل» row).
- With `courseId`, `recordGrade` requires the course to exist and
  `course.grade_level == class.grade_level`; otherwise a new `AcademicError`
  case through the module's existing handler: `CourseNotFound` (`404`,
  `course-not-found`) or `CourseNotForClass` (`400`, `course-not-for-class`). Scheme validation and the 0–20 range are unchanged and
  apply per course grade; the class's `grading_scheme` applies to all its
  courses.
- Permission is unchanged: `GRADES_EDIT` on the existing endpoint. Any holder
  may grade any course of a class they can already grade (no course-teacher
  assignment is introduced).

### Read path

- `GradeResponse` / the facade `GradeInfo` gain `courseId` and `courseName`
  (nullable). Additive, so existing clients keep working (the Android shell does
  not read grades today).
- `GET /api/admin/schools/{schoolId}/grades` gains optional `courseId`.
  **Omitted = only «نمرهٔ کل» rows** (what it returns today); with a value = only
  that course. This keeps the current page and any client unchanged.
- New read endpoint for the course picker, inside `GradeController` so it
  shares its `ACADEMIC` module gate and `GRADES_EDIT` permission:
  `GET /api/admin/schools/{schoolId}/grades/courses?classId=` → the global
  courses whose `grade_level` equals the class's, ordered by name. The school is
  checked with `TenantContext.requireMatches` and the class must belong to it
  (`ClassNotFound` otherwise).
- `MyGradesController` returns the new fields; no other change.

### UI (design shown before implementation, per `design-page`)

- `/grades` (teacher/admin): a «درس» select between class and academic year,
  listing «نمره‌ی کل» (default) plus the class's courses (from the endpoint
  above). It is disabled until a class is chosen, resets to «نمره‌ی کل» when the
  class changes, and reloads an already loaded table when changed, so the rows
  always belong to the course shown in the «نمره‌های: …» tag above the table.
  Saves send that course. Empty catalog → only «نمره‌ی کل» plus a hint that no
  courses are defined for the class's grade level.
- `/my-grades` (student/parent): inside the chosen student, rows gain a «درس»
  column (muted «نمره‌ی کل» when null), sorted by year, term, then «نمره‌ی کل»
  before the courses by name. Grouping by course instead of a column is not
  built; revisit if the table gets long.

### Catalog maintenance (scope split)

The product owner wants the platform admin to maintain the list. That actor
**does not exist**: `PLATFORM_ADMIN` is a seeded role with no permission grants,
`PermissionDecisionService` denies every non-school context, and
[`platform-admin-not-assignable.md`](platform-admin-not-assignable.md)
deliberately made the role non-assignable until a platform permission path is
designed. Building that path is a security-model change in its own right.

Proposed split:

- **This change (phase 1):** the catalog table, the course dimension on grades,
  the read endpoint and both UIs. The catalog is read-only through the app;
  rows are managed by a Flyway data migration (or an operator SQL script) that
  the platform operator controls. No new permission, no new role path, no
  change to `platform-admin-not-assignable`.
- **Phase 2 (separate proposal, depends on it):** a platform actor and
  permission model (see `proposed-work.md`, "Platform And Billing") and a
  platform-admin screen/API to add, rename and deactivate courses.

An alternative is to build the platform actor first and ship the admin UI
together with the grades change. It is larger, touches authentication and
`PermissionDecisionService`, and delays per-course grades behind an unrelated
security design, so it is not recommended here.

## Compatibility And Risk

- Existing API callers see identical results: omitted `courseId` means the
  same rows as today; new response fields are additive.
- Migration `V5` must drop the old unique constraint by its real generated name
  (verify against a database created from `V1`, not from memory) and recreate
  the two partial indexes; existing rows (all `course_id is null`) satisfy the
  new index trivially. The migration must not edit `V1`–`V4`.
- `course_id` is `restrict`: a course referenced by a grade cannot be deleted.
  Retiring a course would need an `active` flag, which belongs to the phase 2
  maintenance design, not here.
- Tenant isolation: the catalog is global and read-only for schools; grade rows
  stay school-scoped by `school_id` and by the class/student checks that exist
  today. A course from another grade level is rejected.

## Acceptance Criteria

- Recording a grade without `courseId` behaves exactly as before (including
  upsert).
- Recording with a valid `courseId` stores a separate row per course for the
  same student/class/year/term; re-recording updates that row only.
- A `courseId` that does not exist, or whose `grade_level` differs from the
  class's, is rejected with a module error and stores nothing.
- Two «نمرهٔ کل» rows for the same student/class/year/term cannot exist
  (partial index), and neither can two rows for the same course.
- `GET .../grades` without `courseId` returns only «نمرهٔ کل» rows; with
  `courseId` only that course's rows.
- The course list endpoint returns only the class's grade level's courses and
  rejects a class from another school.
- `/my-grades` shows the course for each row; legacy rows read «نمرهٔ کل».
- `ModularityTests.verifyModularStructure()` still passes; the schema validates
  under `ddl-auto=validate`.

## Tests

- Unit (no Docker): facade validation of the course/grade-level match and the
  scheme rules; JTE render tests for the `/grades` course select and the
  `/my-grades` course column (same style as `MyPagesPerStudentRenderTest`).
- Integration (CI, Docker): extend `GradeManagementFlowIntegrationTest` for the
  per-course upsert, the rejected course, the backward-compatible list, and a
  migration check that existing rows survive `V5`.
- Authorization: the existing negative scenarios (student/parent cannot call
  the admin endpoints; another school's class is rejected) are repeated for the
  new endpoint.

## Verification (phase 1 backend)

- `V5` was applied by hand to a database created from `V1`–`V4` holding an
  existing grade: it survives with `course_id` null; a second «نمرهٔ کل» row, a
  second row for the same course and deleting a referenced course are all
  rejected; a course row coexists with the overall row. On a table whose unique
  key has a different generated name (a database created by `ddl-auto=update`),
  only the matching unique constraint is dropped and an unrelated one is kept.
- Both pages were driven in a browser against the compiled templates with
  mocked APIs: course list per class, reset on class change, auto-reload,
  `courseId` in the request and the POST body (and `null` for overall), the empty
  catalog hint, and the `/my-grades` column and ordering. Render tests:
  `GradesCoursePickerRenderTest`.
- `GradeCourseFacadeTest` (10, no Docker) and `GradeByCourseFlowIntegrationTest`
  (4) plus the existing `GradeManagementFlowIntegrationTest` (10) pass against a
  real PostgreSQL 16 with Flyway migrating an empty database from `V1` to `V5`
  and `ddl-auto=validate`. CI runs them against `postgres:18` in Testcontainers.

## Unresolved Questions

1. **Initial catalog content.** Which courses exist for each grade 1–12 (and
   their exact Persian names) is a product/curriculum decision. It is not
   invented here; the phase 1 migration needs this list from the product
   owner. Without it the catalog ships empty and every grade stays «نمرهٔ کل».
2. **Platform actor.** Confirm the phase 1 / phase 2 split above, or choose to
   design the platform permission path first. Either way it overrides the
   scope of `platform-admin-not-assignable.md`, which should be revisited when
   the actor is designed.
3. **Who may grade which course.** This proposal keeps "any `GRADES_EDIT`
   holder". If a teacher should only grade the courses they teach, a
   class-course-teacher assignment (new data) is needed.
4. **Overall average.** No computed average («معدل») across courses is
   introduced; whether and how one is shown (weighting, rounding, descriptive
   classes) is a separate decision.
5. **Versioning the course list over time (product input 2026-10-02: a grade
   level's courses change every few years for schools).** Deferred by the
   product owner: record it now, build it later. What phase 1 already gives:
   courses are never deleted (`on delete restrict`), so old grades keep the old
   course's name; the operator can rename a row or add the new courses with a
   data migration. What it does **not** give: the «درس» select on `/grades`
   lists every course of the grade level, old and new mixed, for every academic
   year, and nothing records which list applies to which year. Options for the
   later change, none chosen:
   - an optional validity range per course (from/until the start year of the
     academic year, e.g. `1404` for `1404-1405`), the select filtered by the
     chosen year and recording a course outside its range rejected. Smallest;
     the academic year on a grade is free text, so the start year is read from
     its leading four digits and an unparsable year shows all courses;
   - a curriculum edition that groups courses and is attached to academic
     years: more exact, needs a new table and concept;
   - an `active` flag only: cannot express «this list applied in 1404».
   Whether recording a grade outside the validity range should be rejected or
   only hidden in the picker, and how a renamed course shows on historical
   grades, are part of that change.
   Update 2026-10-02: the platform-wide academic year this needs is proposed in
   [`academic-year.md`](academic-year.md); the validity range becomes phase 1 of
   that proposal.
6. **Scheme per course.** The class's `grading_scheme` applies to all of its
   courses. If some courses in the same class need the other scheme, that is a
   different data model.
