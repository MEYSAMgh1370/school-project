# Class Timetable (برنامهٔ کلاسی / هفتگی مدرسه)

Status: proposed 2026-10-02, **not implemented**. Nothing in
[`specs/current-system.md`](../specs/current-system.md) changes until this is
approved and built. The contract below is a recommendation; every item that is a
product decision is listed under Unresolved Questions and is not invented here.

## Verified Baseline

From code and docs, not from memory:

- There is **no class timetable**: no table, API, page or navigation entry
  (`grep` for timetable/period/زنگ finds nothing in `src/main` or `android/`).
  `HISTORY.md` and `MOBILE_ROADMAP.md` both say "timetable ... do not exist as
  backend features yet"; the mobile roadmap lists it as a daily-use feature and
  ties future video classes to it. No proposal or design exists for it.
- What *is* called "weekly program" today is the student's **study plan**
  (`studyplan` module, `/study-plans`, `/my-study-plan` tab «برنامه هفتگی»): a
  per-student free-time template and an auto-suggested study table. It is not a
  school timetable and is not related to classes. Note the naming clash: the
  permission labels `STUDYPLAN_VIEW`/`STUDYPLAN_MANAGE` already say
  «برنامه‌ی درسی» for the study plan.
- `academic.classes` has `grade_level`, `name`, `section`, `grading_scheme` and
  an optional `teacher_id` (one teacher per class, not per subject).
- The global course catalog (`academic.courses`, per grade level, read-only in
  the app) is introduced by [`grades-by-course.md`](grades-by-course.md). Its
  courses carry no "graded" flag: every course is a gradable course.
- Roles/permissions: `CLASS_MANAGE` (class creation, `SCHOOL_ADMIN` only),
  `CLASS_VIEW` (`SCHOOL_ADMIN`, `TEACHER`). There is no timetable permission.
  Self-service endpoints (`/api/me/**`) carry no permission and return only the
  caller's own data (`MyGradesController`, `MyEnrollmentController`).
- The study plan already models weeks as Saturday-first with seven weekdays
  (`StudyPlanFacade.Weekday`), Gregorian server-side.

## Decisions Already Taken By The Product Owner (2026-10-02)

1. The timetable is for the **school's classes** (not the student's study plan).
2. The **school admin builds it**.
3. The **school defines the periods** (زنگ‌ها) itself.
4. Some **extracurricular subjects may not appear in the report card (کارنامه)
   but do appear in the weekly timetable.**

## Proposed Contract

### Where it lives

Inside the `academic` module: classes and the course catalog already live there,
so there is no new module and no new cross-module dependency. Teachers and
students are referenced by opaque ids as elsewhere. Gated by
`@RequiresModule("ACADEMIC")` on the admin endpoints, like the rest of academic.

### Data (Flyway migration, next free version)

- `academic.timetable_periods`: `school_id`, `id`, `position` (1..n, unique per
  school), `label` (e.g. «زنگ اول», ≤ 64), `start_time`, `end_time` (local
  times, `end > start`, periods of a school must not overlap). Each school owns
  its list; there is no global default.
- `academic.timetable_entries`: `school_id`, `id`, `class_id`, `weekday`,
  `period_id`, **exactly one of** `course_id` (catalog course) or
  `activity_title` (≤ 128, free text written by the school), `teacher_user_id`
  (nullable, opaque iam user id). `unique (class_id, weekday, period_id)`: a
  class has at most one subject per slot. `period_id` references the period
  with `on delete restrict`: a period that has entries cannot be deleted.
- A teacher must be an active `TEACHER` of the school (validated through
  `IamFacade`, same pattern as the guardian check); not enforced by a foreign
  key, per the module boundary.

### Report card vs timetable (decision 4)

Two kinds of subject can be in a timetable cell:

- **Catalog course** (`course_id`): gradable, appears in the `/grades` course
  select and in `/my-grades`. Course must match the class's grade level.
- **Activity** (`activity_title`): a school-defined extracurricular subject. It
  exists only in the timetable and is **never** offered when grading, so it can
  never reach the report card. No catalog row, no platform administration.

This keeps "not in the report card" a structural fact instead of a flag that
someone must remember to set. If some catalog courses (ministry courses that are
timetabled but not graded) also need to stay out of the report card, a
`graded` flag on `academic.courses` is needed; see question 1.

### Read and write paths

Admin (ACADEMIC module + permission, question 4):

- `GET/POST/PUT/DELETE /api/admin/schools/{schoolId}/timetable/periods` -
  manage the school's periods; deleting a period with entries is rejected.
- `GET /api/admin/schools/{schoolId}/classes/{classId}/timetable` - the class's
  entries.
- `PUT /api/admin/schools/{schoolId}/classes/{classId}/timetable/entries` -
  set one cell (weekday + period → subject, optional teacher) or clear it.
  Rejected: unknown period/class/course, a course of another grade level, both
  or neither of `courseId`/`activityTitle`, an inactive or non-teacher user, and
  (proposed, question 3) a teacher already placed in another class in the same
  weekday and period.

Self-service (no permission, caller's own data, like `/api/me/**` today):

- `GET /api/me/schools/{schoolId}/timetable` - the periods plus the entries of:
  the user's own class (student), the classes of the linked children (parent,
  tagged with the child so the page can show a student picker as `/my-grades`
  does), and the entries where the user is the teacher (teacher).

### UI (design shown before implementation, per `design-page`)

- Admin: a timetable page with two parts, «زنگ‌ها» (define periods) and a
  class grid (weekdays × periods, click a cell to choose a subject and teacher).
- Student/parent/teacher: a read-only weekly grid (a student picker for
  parents, same pattern as `/my-grades`).
- Links join `NavCatalog` (single source for the sidebar and dashboard
  shortcuts). Name vs the study plan's «برنامه هفتگی»: see question 6.

### Out of scope

Attendance, substitute teachers and one-off changes for a given week, rooms,
holidays and the school calendar, exam scheduling, notifications of changes,
import/export, drag-and-drop editing, and the Android client.

## Compatibility And Risk

- Purely additive: new tables, new endpoints, new pages. No existing endpoint,
  permission or table changes (a `graded` column on `academic.courses`, if
  chosen in question 1, is additive with default `true`).
- Tenant isolation: every row carries `school_id`; admin endpoints validate the
  school with `TenantContext.requireMatches` and check that class, period and
  teacher belong to it. The self-service endpoint returns only the caller's
  own class, linked children's classes and own teaching slots.
- The timetable of a class is a single current schedule; it is not versioned by
  academic year or term (question 2). It shares the unresolved "course list
  changes over time" question recorded in `grades-by-course.md`.

## Acceptance Criteria (to be refined once the questions are answered)

- A school admin can define, edit and delete periods; periods of one school do
  not overlap; a period with entries cannot be deleted; another school's periods
  are never visible.
- A cell can hold a catalog course of the class's grade level or a free-text
  activity, never both and never neither; at most one subject per class,
  weekday and period.
- An activity never appears in the grades course select or `/my-grades`.
- A student sees the timetable of their class, a parent the timetables of their
  linked children only, a teacher their own teaching slots; a stranger gets an
  empty result, not an error and not someone else's data.
- Negative cases for the admin endpoints: missing permission, module not
  entitled, another school's class/period, invalid teacher, invalid course.
- `ModularityTests.verifyModularStructure()` and `ddl-auto=validate` pass.

## Tests

- Unit (no Docker): facade validation (period overlap, `end > start`, course
  grade-level match, exactly-one-subject, teacher eligibility, double booking
  if adopted), and JTE render tests for the grids.
- Integration (CI, Docker): admin CRUD and the permission/entitlement/tenant
  negatives, the self-service endpoint for student, parent and teacher, and the
  migration on a database that already holds data.

## Unresolved Questions

1. **Non-graded catalog courses.** Is the free-text activity enough for the
   extracurricular subjects, or must some *catalog* courses (ministry-defined,
   timetabled but not in the report card) be marked as such? If yes: a `graded`
   flag on `academic.courses` (default `true`), and the `/grades` course select
   lists only graded courses. Who owns the extracurricular list - each school
   (free text, as proposed) or the platform (global rows)?
2. **Time dimension.** One current timetable per class (proposed, simplest), or
   a timetable per academic year/term so a school can prepare next term's
   schedule and keep the old one? Ties into the course-versioning question.
3. **Teacher double booking.** Reject placing the same teacher in two classes at
   the same weekday and period (proposed), only warn, or allow?
4. **Permission.** A new `TIMETABLE_MANAGE` (granted to `SCHOOL_ADMIN`, and
   assignable through custom roles, e.g. for a deputy) as proposed, or reuse
   `CLASS_MANAGE`/`CLASS_VIEW`? A new permission code is a domain decision and is
   not invented here.
5. **The school week and the periods.** Which weekdays does a school run
   (Saturday–Wednesday, to Thursday, Friday)? Are breaks (تفریح/نماز/ناهار)
   periods with a label, or just gaps between periods? Can different classes of
   one school (e.g. morning and afternoon shifts) use different period sets? The
   proposal assumes one period set per school and seven allowed weekdays.
6. **Name.** The study plan's tab is already «برنامه هفتگی» and its permission
   labels say «برنامه‌ی درسی». What should the school timetable be called in the
   UI and navigation (e.g. «برنامهٔ کلاسی»)?
7. **Who sees what.** Should a teacher also see the full timetable of any class
   (not only their own slots), and may parents/students see the teacher's name?
