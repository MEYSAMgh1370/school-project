# Class Timetable (برنامهٔ کلاسی)

Status: proposed 2026-10-02, **implemented 2026-10-02** (backend, admin page,
`/my-timetable`, student dashboard section). The product owner answered the
seven original questions and then confirmed the assumptions of questions 3-6
the same day (recorded below). The "current academic year" concept it depends
on is [`academic-year.md`](academic-year.md) phase 0, already in `master`. One
question is still open (platform-default activity names) and is not invented
here: the platform list ships **empty**. The current behavior is described in
[`specs/current-system.md`](../specs/current-system.md).

## Verified Baseline (before implementation)

From code and docs, not from memory:

- There is **no class timetable**: no table, API, page or navigation entry
  (`grep` for timetable/period/زنگ finds nothing in `src/main` or `android/`).
  `HISTORY.md` and `MOBILE_ROADMAP.md` both say "timetable ... do not exist as
  backend features yet"; the mobile roadmap lists it as a daily-use feature and
  ties future video classes to it.
- What *is* called "weekly program" today is the student's **study plan**
  (`studyplan` module, `/study-plans`, `/my-study-plan` tab «برنامه هفتگی»): a
  per-student free-time template and an auto-suggested study table. It is not a
  school timetable and is not related to classes. Its permission labels
  (`STUDYPLAN_*`) already say «برنامه‌ی درسی».
- `academic.classes` has `grade_level`, `name`, `section`, `grading_scheme` and
  an optional `teacher_id` (one teacher per class, not per subject). A class is
  not tied to an academic year; academic years are still free text on grades
  (`"1404-1405"`). **A "current academic year" now exists** (phase 0 of
  [`academic-year.md`](academic-year.md), implemented): a global list of years
  that the application creates from a Solar date in configuration, and each
  school's current year chosen by its admin
  (`SchoolFacade.findCurrentAcademicYear`, `GET /api/me/schools/{id}/academic-year`).
  What it does not do yet: classes and enrollments are not year-scoped, and grades
  are not validated against the list (phases 1-3).
- The global course catalog (`academic.courses`, per grade level, read-only in
  the app, empty until the operator adds rows) is in `master` since
  [`grades-by-course.md`](grades-by-course.md). Every course in it is currently
  gradable.
- Permissions are reference data seeded by `IamSeedDataRunner` in every profile
  (`!prod` only gates demo data); `CLASS_MANAGE` is `SCHOOL_ADMIN`-only.
  Self-service endpoints (`/api/me/**`) carry no permission and return only the
  caller's own data (`MyGradesController`, `MyEnrollmentController`).
- The dashboard already composes cross-module data per role through `web`
  assemblers (`WardsAssembler`, `NavCatalog` shortcuts); a student section would
  follow that pattern.

## Decisions Taken By The Product Owner (2026-10-02)

1. The timetable is for the **school's classes** (not the student's study plan).
2. The **school admin builds it**, and the **school defines the periods** (زنگ‌ها).
3. **Extracurricular subjects** may appear in the timetable without being in the
   report card (کارنامه). The extracurricular list is **kept by each school**,
   but **a few defaults are registered at platform level** so they can be chosen
   without typing.
4. A **main (catalog) course might also need to stay out of the report card**:
   unsure it happens, but kept as a precaution.
5. **A separate timetable per academic year** (not one current timetable). The
   year is the platform's academic year; the school's **current year** (chosen
   by the school, see `academic-year.md`) decides which timetable students,
   parents and teachers see by default.
6. A teacher placed in two classes in the **same weekday and period is
   rejected**.
7. A **new permission** (not `CLASS_MANAGE`).
8. School days are **Saturday to Wednesday**.
9. The UI name is **«برنامهٔ کلاسی»**.
10. A **teacher sees their own hours**; a **student sees the timetable on the
    first page (the dashboard)**.

## Contract

The text below is the proposal; **As Built** at the end of this section lists where
the implementation differs or is more precise.

### Where it lives

Inside the `academic` module: classes and the course catalog already live there,
so there is no new module and no new cross-module dependency. Teachers and
students are referenced by opaque ids as elsewhere. Admin endpoints are gated by
`@RequiresModule("ACADEMIC")` and the new permission.

### Data (Flyway migration, next free version)

- `academic.courses.graded boolean not null default true` (decision 4): a course
  with `graded = false` can be timetabled but is **not offered when grading**;
  recording a grade for it is rejected (`CourseNotGradable`, 400), and the
  `/grades` course select lists only graded courses. Existing grades and rows are
  unaffected (default `true`). Set by the operator, like every catalog row.
- `academic.activities` (decision 3): `id`, `school_id` **nullable**, `name`
  (≤ 128). `school_id` null = a platform default (global, read-only in the app,
  managed by the operator through data migration like the course catalog);
  `school_id` set = owned by that school, created/renamed/deleted by the school
  admin. Unique name per scope (two partial unique indexes, as for grades). An
  activity is only ever in the timetable; it can never be graded or reach the
  report card. A school sees its own activities plus the platform defaults and
  cannot change the defaults. An activity used by an entry cannot be deleted.
- `academic.timetable_periods` (decisions 2, 5): `school_id`, `academic_year`
  (a year from the platform list in `academic-year.md`; its canonical code),
  `id`, `position` (1..n, unique per school
  and year), `label` (e.g. «زنگ اول», ≤ 64), `start_time`, `end_time` (`end >
  start`; periods of one school and year must not overlap). Periods belong to a
  year because changing a school's periods for next year must not rewrite last
  year's timetable. A period with entries cannot be deleted.
- `academic.timetable_entries`: `school_id`, `id`, `class_id`, `period_id`
  (which carries the academic year), `weekday` (`SAT`..`WED`, check constraint,
  decision 8), **exactly one of** `course_id` (catalog course) or `activity_id`,
  `teacher_user_id` (nullable, opaque iam user id).
  - `unique (class_id, weekday, period_id)`: one subject per class and slot.
  - `unique (teacher_user_id, weekday, period_id) where teacher_user_id is not
    null`: a teacher is in at most one class per slot (decision 6, enforced by
    the database as well as validated for a clear error).
  - A catalog course must match the class's grade level; an activity must be the
    school's own or a platform default; the teacher must be an active `TEACHER`
    of the school (validated through `IamFacade`, not a foreign key, per the
    module boundary).

### Permission

New `TIMETABLE_MANAGE` (decision 7), granted to `SCHOOL_ADMIN` and assignable to
custom roles (e.g. a deputy). It gates every admin endpoint below, including the
school's activity list. Reading one's own timetable needs no permission. The
permission is added to `IamSeedDataRunner`'s reference data (all profiles); how
the grant reaches an **already deployed** database must be verified during
implementation (the seeder's idempotence for `role_permissions`).

### Read and write paths

Admin (ACADEMIC module + `TIMETABLE_MANAGE`):

- `GET/POST/PUT/DELETE /api/admin/schools/{schoolId}/timetable/periods?academicYear=`
  - the school's periods for a year.
- `GET/POST/PUT/DELETE /api/admin/schools/{schoolId}/timetable/activities` -
  the school's own activities; `GET` also returns the platform defaults, marked
  as such and not editable.
- `GET /api/admin/schools/{schoolId}/classes/{classId}/timetable?academicYear=` -
  the class's entries for a year.
- `PUT /api/admin/schools/{schoolId}/classes/{classId}/timetable/entries` - set
  one cell (year, weekday, period → course or activity, optional teacher) or
  clear it. Rejected: unknown class/period/course/activity, a weekday outside
  Saturday–Wednesday, a course of another grade level, both or neither of
  course/activity, an inactive or non-teacher user, and a teacher already placed
  in another class in the same weekday and period (`409`, a new
  `teacher-double-booked` error).
- The course list for the cell picker reuses the existing
  `GET .../grades/courses?classId=` shape but **includes non-graded courses**
  (the timetable endpoint, not the grading one) - a separate timetable-side read
  so `/grades` keeps listing graded courses only.

Self-service (no permission, caller's own data, like `/api/me/**`):

- `GET /api/me/schools/{schoolId}/timetable?academicYear=` (defaulting to the
  school's current academic year) - the periods plus the entries of: the user's own class
  (student); the classes of the linked children (parent, tagged with the child
  so the page can show a student picker as `/my-grades` does); and the entries
  where the user is the teacher (teacher, decision 10 - own hours only).

### UI (design shown before implementation, per `design-page`)

- **«برنامهٔ کلاسی»** (`/timetable`, admin): choose the academic year; a
  «زنگ‌ها» part to define periods and a class grid (Saturday–Wednesday ×
  periods, click a cell to choose a course or activity and an optional teacher);
  a small «فعالیت‌های فوق‌برنامه» list for the school's own activities.
- **«برنامهٔ کلاسی من»** (`/my-timetable`): a read-only weekly grid for the
  student's class, the parent's chosen child, or the teacher's own hours.
- **Dashboard (decision 10):** a student sees their class's timetable on
  `/dashboard`: today's lessons first and a link to the full week.
  Parent/teacher on the dashboard: question 3 below.
- Links join `NavCatalog` (single source for the sidebar and dashboard
  shortcuts): admin «برنامهٔ کلاسی» (manage, with a shortcut card);
  student/parent/teacher «برنامهٔ کلاسی من».

### Out of scope

Attendance, substitute teachers and one-off changes for a given week, rooms,
holidays and the school calendar, exam scheduling, change notifications,
import/export, drag-and-drop editing, copying a timetable to the next year (a
natural follow-up), per-school configurable school days, and the Android client.

### As Built (differences and details)

- Where it lives: `TimetableFacade` (public, `academic` base package, separate from
  `AcademicFacade`), `TimetableController` (admin) and `MyTimetableController`
  (`/api/me`) in `academic.controller`; `V7__class_timetable.sql`.
- Admin endpoints (all `@RequiresModule("ACADEMIC")` + `TIMETABLE_MANAGE`, so that one
  permission is enough for the page): `GET .../timetable/years` (all years +
  the school's current one), `GET .../timetable/classes` (the school's classes),
  `GET .../timetable/teachers` (active teachers), `GET|POST .../timetable/periods`,
  `PUT|DELETE .../timetable/periods/{id}`, `GET|POST .../timetable/activities`,
  `PUT|DELETE .../timetable/activities/{id}`, `GET .../timetable/classes/{classId}?academicYear=`
  (entries), `GET .../timetable/classes/{classId}/courses` (all courses of the
  grade, including non-graded), `PUT .../timetable/classes/{classId}/entries`
  (set a cell; replaces whatever the cell held) and
  `DELETE .../timetable/classes/{classId}/entries?periodId=&weekday=` (clear; an
  empty cell is not an error). Times are `"HH:mm"` strings; `weekday` is a string and
  an unknown day (e.g. `THU`) is `400 invalid-timetable-entry`.
- `GET /api/me/schools/{id}/timetable` has **no year parameter**: it always returns
  the school's *current* year (academic-year decision 2: non-admins see only the
  current year; the admin opens other years on `/timetable`). The response is
  `{academicYear, periods, views[]}` where each view is `OWN` (the user's own
  class), `WARD` (a linked child, with the child's name and class) or `TEACHING`
  (the user's own slots, each entry carrying the class name; an active teacher always gets this view, empty when there are no cells or no current year); entries carry the
  teacher's name. A stranger gets `{academicYear: null, periods: [], views: []}` -
  not an error, and not the school's current year. A member with no current year
  chosen gets views without entries (the page says the year is not chosen).
- An activity name must be free in the school **and** among platform defaults, so a
  picker never shows two entries with the same name. A class is shown by its name only
  (the section is already part of the name on the existing pages).
- A race on the teacher unique index (two admins booking the same teacher at once)
  is reported as `409 teacher-double-booked` as well; any other integrity
  violation is not relabeled.
- Creating/updating a period takes a transaction-level PostgreSQL advisory lock per
  (school, year) before the overlap check, so two admins moving periods into the same
  free range cannot both pass (no exclusion constraint: it needs the `btree_gist`
  extension).
- Setting a cell takes an advisory lock per (class, weekday, period) before reading the existing row, so two admins filling the same empty cell are serialized (the second replaces the first) instead of one failing on the unique index.
- `GET /api/me/.../timetable` ties each view to an **active** membership with the
  matching role in that school (`STUDENT` for `OWN`, `PARENT` for `WARD`, `TEACHER` for
  `TEACHING`): enrollments, guardian links and timetable cells survive deactivating a
  membership, so without this a former member kept receiving the timetable.
- If the operator later sets `graded = false` on a course that already has grades, those
  grade rows are kept but hidden from `/api/me/.../grades` (student/parent reads); the
  admin list that asks for that `courseId` explicitly still returns them, and they come
  back if the course is graded again. Overall grades are unaffected.
- Granting the permission on an already deployed database: `IamSeedDataRunner`
  grants per `(role, permission)` pair idempotently at every start, so the next
  deploy adds `TIMETABLE_MANAGE` to `SCHOOL_ADMIN`.
- Migration checked on a database that already held data (V1-V6 applied, a class
  and a course inserted, then V7): the existing course gets `graded = true`; nothing
  else changes.
- UI: `/timetable` has the year select (current year tagged; another year shows a
  note that others only see the current year), tabs «برنامهٔ کلاس‌ها» (class select +
  Saturday–Wednesday × periods grid, a cell opens one modal: course or activity,
  optional teacher, «خالی کردن خانه»), «زنگ‌ها» (add/edit/delete) and «فعالیت‌ها»
  (platform defaults are listed without actions). `/my-timetable` is the read-only
  grid with the same picker as `/my-grades` when there is more than one view.
  Dashboard: «برنامهٔ امروز» for the `STUDENT` role only (Thursday/Friday say there
  is no class), with a link to the week.
- Known, not changed here: sidebar and dashboard links come from `NavCatalog`, which
  decides visibility by built-in role codes (as for every other page); a custom role that
  holds `TIMETABLE_MANAGE` can call the API but gets no link until navigation becomes
  permission-aware (a separate change).
- Known, not changed here: a malformed JSON body (for example an unknown enum value in
  a body field) is answered with `500` by the platform's exception handling on every
  endpoint; the timetable avoids it for `weekday` by taking a string.

## Compatibility And Risk

- Additive: new tables, endpoints, pages and one permission. The only change to
  existing behavior is the `graded` flag: `/grades` hides ungraded courses and
  grading one is rejected; with the default `true` nothing changes until the
  operator sets a flag.
- Tenant isolation: every row except platform-default activities carries
  `school_id`; admin endpoints validate the school with
  `TenantContext.requireMatches` and check that class, period, activity and
  teacher belong to it. The self-service endpoint returns only the caller's own
  class, linked children's classes and own teaching slots.
- The year is the platform's validated academic year (`academic-year.md`), so
  the free-text digit problem on grades does not carry over: the year is chosen
  from a list, never typed.
- It shares the unresolved "course list changes over time" question in
  `grades-by-course.md`: with a timetable per year, a course retired in the
  catalog stays on old years' timetables.

## Acceptance Criteria (to be refined at implementation)

- A school admin can define, edit and delete periods per academic year; periods
  of one school and year do not overlap; a period with entries cannot be
  deleted; another school's data is never visible.
- A cell holds a catalog course of the class's grade level **or** an activity,
  never both and never neither; at most one subject per class, weekday and
  period; weekdays are Saturday–Wednesday only.
- A teacher cannot be placed in two classes in the same weekday and period
  (`409`); the database also refuses it.
- An activity, and a course with `graded = false`, never appear when grading and
  can never reach `/my-grades`.
- A school can manage its own activities; platform defaults are visible to all
  schools and editable by none through the app.
- A student sees their class's timetable on the dashboard and on
  `/my-timetable`; a parent only linked children's; a teacher only their own
  slots; a stranger gets an empty result, not an error and not someone else's
  data.
- Negative cases on every admin endpoint: missing `TIMETABLE_MANAGE`, module not
  entitled, another school's class/period/activity, invalid teacher, invalid
  course, ungraded course used for grading.
- `ModularityTests.verifyModularStructure()` and `ddl-auto=validate` pass.

## Tests

- Unit (no Docker): facade validation (period overlap, `end > start`, course
  grade-level match, exactly-one-subject, weekday range, teacher eligibility,
  double booking, `graded`), and JTE render tests for the grids and the
  dashboard section.
- Integration (CI, Docker): admin CRUD and the permission/entitlement/tenant
  negatives, the partial unique indexes, the self-service endpoint for student,
  parent and teacher, the dashboard model, and the migration on a database that
  already holds data. Locally these can run against a PostgreSQL 16 started
  without Docker, as was done for `grades-by-course`.

## Unresolved Questions

1. **Platform-default activities.** Which defaults are registered for all schools
   (the names)? Product input; not invented here. The platform list ships empty;
   schools create their own, and the operator can add defaults with a data
   migration (rows with `school_id` null).

Resolved by the product owner on 2026-10-02 ("همان‌ها را تأیید می‌کنم"):

- **Current academic year:** students, parents and teachers see the school's current
  year; the school admin can open any year (see academic-year decision 2).
- **Who sees what, where:** a teacher's own hours are on `/my-timetable` (no dashboard
  section); a parent sees the children's timetables on `/my-timetable` with a child
  picker; the dashboard section is for the student only.
- **Periods per year:** periods are defined per school and year; copying from the
  previous year is a later change.
- **School days:** fixed Saturday–Wednesday; a per-school setting is a later change.
- **Teacher names:** students and parents see the teacher's name in a cell.
