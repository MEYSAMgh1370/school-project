# Attendance (حضور و غیاب)

Status: **proposed 2026-10-05, not implemented.** Nothing here is current behavior.
Every product choice below is a *proposed default* for the product owner to confirm
or change; the open questions at the end are not decided and are not invented here.

## Verified Baseline

From code and docs:

- There is **no attendance feature**: no table, API, page, event or navigation entry.
  `specs/current-system.md` lists attendance among the things "not implemented".
- The only trace is a catalog row: `IamSeedDataRunner.seedModules()` seeds the
  module `ATTENDANCE` («حضور و غیاب», `SCHOOL` scope). No permission, no entitlement
  and no `@RequiresModule("ATTENDANCE")` use exists.
- Free-tier school creation grants only `ACADEMIC` and `FINANCE`
  (`decisions/2026-07-20-free-tier-entitlements.md`); paid modules are granted through
  the facade only.
- What attendance would build on already exists in `academic`: classes
  (`academic.classes`, optional `teacher_id`), per-year enrollments
  (`academic.enrollments`, `students.status`), parent links
  (`student_guardians`, `AcademicFacade.listWards`), and the school's current
  academic year and school days (`SchoolFacade`, the per-school week start
  `SAT`/`MON`, V8). The class timetable (`TimetableFacade`) knows which teacher
  teaches which class in which period.
- The dashboard already composes cross-module data per role through `web`
  assemblers; the admin «وضعیت شهریه» box (2026-10-05) is the pattern for an
  admin figure. The landing-page hero preview already *depicts* an absence count
  as sample data.

## Proposed Contract

### Module

A new top-level module `attendance` (apply `.agents/skills/new-module` before
coding), because the `ATTENDANCE` module code already exists as its own
entitlement. It depends only on public facades: `AcademicFacade` (classes,
roster, enrollments, wards), `SchoolFacade` (current year, school days) and
`IamFacade` (user/permission checks). Nothing depends on it except `web`
(dashboard assembler) and, later, `notification` through an event.

### Granularity: one roll per class per school day

The unit is **a class on a school day**, not a period. A teacher "takes the roll"
once; every student of the class is then PRESENT unless marked otherwise.
(Per-period attendance is open question 1.)

### Data (Flyway migration, next free version)

- `attendance.rolls`: `school_id`, `class_id`, `day` (date), `taken_by_user_id`,
  `taken_at`. Unique `(class_id, day)`. A roll row means "the roll was taken for
  this class on this day". Without it the class is **«ثبت نشده»**, which is not the
  same as "nobody was absent".
- `attendance.marks`: `school_id`, `class_id`, `day`, `student_id`, `status`
  (`ABSENT`, `LATE`), `excused boolean` (only meaningful for `ABSENT`), `note`
  (≤ 255, optional), `marked_by_user_id`, `marked_at`. Unique
  `(class_id, day, student_id)`. **Only exceptions are stored**; a student of a
  taken roll with no mark is present. This keeps the table small and makes
  "who was absent" a direct query.
- Dates are ISO Gregorian on the wire and in the database; display is Jalali with
  Persian digits (DESIGN.md §8). Students and classes are referenced by opaque id,
  not by foreign key across the module boundary.

### Rules

1. A roll can be taken only for a **school day** of the school (`SAT`–`WED` for a
   `SAT` school, `MON`–`FRI` for a `MON` school; same source as the timetable), and
   not for a future day (`400 invalid-attendance-day`). Holidays are open question 3.
2. The class must belong to the school and to the school's current academic year
   (or be unattributed, as for classes today).
3. A mark's student must be **enrolled in that class** that year at that time
   (`409 student-not-in-class`). A student promoted or removed later keeps the
   history; the roll shows who was in the class on that day.
4. Saving a roll is **one idempotent replace** of the class-day: the request lists
   the absent/late students; the previous marks of that class-day are replaced in
   one transaction, `rolls` is upserted.
5. Who may record: a new permission **`ATTENDANCE_RECORD`**, granted to `TEACHER`
   and `SCHOOL_ADMIN`; a teacher may record only for a class they teach (the class's
   `teacher_id`, or a teacher placed in that class by the timetable) unless they
   also hold `ATTENDANCE_VIEW_ALL`. A same-day-only edit window for teachers is
   open question 5; the admin can always correct.
6. Who may read: new permission **`ATTENDANCE_VIEW_ALL`** (`SCHOOL_ADMIN`,
   assignable to custom roles, e.g. a deputy) for the school-wide views. A student
   sees only their own absences and a parent only their linked children's, through
   self-service endpoints with no permission, like `/api/me/**`.
7. Every protected endpoint carries both `@RequiresModule("ATTENDANCE")` and a
   permission, as elsewhere.

### Endpoints

Admin and teacher, under `/api/admin/schools/{schoolId}/attendance` (tenant header
as in other admin APIs):

- `GET  /classes/{classId}/days/{day}`: the roster of the class that day with each
  student's mark, plus whether the roll was taken. Source for the recording page.
- `PUT  /classes/{classId}/days/{day}`: save the roll (rule 4). Body:
  `{ "marks": [{ "studentId", "status", "excused", "note" }] }`.
- `GET  /days/{day}/absentees`: **per-class daily absentees** for the whole school:
  for each class, whether the roll was taken, its absent/late students with
  excused flag, and the class totals. Needs `ATTENDANCE_VIEW_ALL`.
- `GET  /days/{day}/summary`: **the admin figure** (below). Needs
  `ATTENDANCE_VIEW_ALL`.

Self-service, no permission, own data only:

- `GET /api/me/schools/{schoolId}/attendance?from=&to=`: the caller's own
  absences (active `STUDENT`) and their linked children's (active `PARENT`).

### The admin figure

For a day, over the classes **whose roll was taken**:

- `absent` = students with an `ABSENT` mark (excused ones included, and also
  shown separately as `excusedAbsent`);
- `expected` = students enrolled in those classes that day;
- `presentPercent` = `floor(100 × (expected − absent) / expected)`, `LATE` counts
  as present, rounded down so 99.9% never shows as 100%;
- `classesTaken` / `classesTotal`; classes without a roll are listed as
  «ثبت نشده» and **excluded** from the percentage, never counted as present.

No roll taken yet on a school day: the figure says «هنوز حضور و غیابی ثبت نشده».
A non-school day: no figure.

### Pages

- `/attendance` (teacher/admin): choose class and day (default today), a list of
  the class's students, each with a present/absent/late control and an optional
  note, the single primary action «ثبت حضور و غیاب» at inline-end of the header.
  Empty state teaches («کلاسی برای ثبت پیدا نشد»). Unmarked students are present.
  Joins the `NavCatalog` «آموزش» group for `TEACHER` and `SCHOOL_ADMIN`, with a
  dashboard shortcut for both.
- `/attendance/absentees` (admin): the per-class daily absentees table with a day
  picker, class totals and the figure at the top.
- Dashboard, admin: a «حضور و غیاب امروز» box next to «وضعیت شهریه»: absentee
  count, `presentPercent` as a progress bar, «N کلاس بدون ثبت», link to the
  absentees page. Shown only for the literal `SCHOOL_ADMIN` role and only when the
  school is entitled to `ATTENDANCE`.
- Dashboard, parent and student (phase 3): «غیبت امروز» on the ward card / the
  student's own page.

### Notifications (phase 3, optional)

`AttendanceMarkedEvent` published after the roll is saved; `notification` may
turn an `ABSENT` mark into a Bale message to the child's linked parents through
the existing `NotificationChannel`. Not part of phases 1-2.

## Phases

1. Module, migration, permissions, entitlement, record/read endpoints, the
   `/attendance` recording page.
2. Absentees endpoint and page, the summary endpoint and the dashboard box.
3. Student and parent views, optional parent notification.

## Acceptance Criteria

- A teacher of a class records a roll for today; reopening shows the same marks;
  saving again with fewer marks removes the dropped ones.
- A teacher of another class gets `403`; a non-school day and a future day are
  `400`; a student not in the class is `409`.
- The absentees list shows every class: taken with its absentees, or «ثبت نشده».
- The summary excludes untaken classes from the percentage, counts `LATE` as
  present, and rounds down.
- A student sees only their own absences; a parent only their linked children's;
  no other user's data is reachable.
- The admin dashboard box shows only for `SCHOOL_ADMIN` of an entitled school;
  other roles and unentitled schools get no box and no request.
- Both access annotations on every protected endpoint; `ModularityTests` passes
  (no dependency on another module's internals).

## Tests That Will Prove It

Integration tests for record/replace/idempotency and the three error cases;
summary arithmetic (taken vs untaken classes, late, excused, rounding); permission
and tenant isolation (another school's class, another teacher); self-service
scoping (student, parent, unlinked parent); a render test of the dashboard box
(entitled, unentitled, no roll, non-admin) in the style of
`DashboardTuitionRenderTest`.

## Compatibility And Migration

Purely additive: one migration, no change to existing tables or endpoints. The
`ATTENDANCE` module row already exists. Schools get access only through an
entitlement (question 6).

## Open Questions (not decided)

1. **Granularity:** once per day (proposed) or per period using the timetable?
2. **Statuses:** is `PRESENT` / `ABSENT` / `LATE` plus an excused flag the right
   set? Is "leave early" needed? Does lateness count toward absences after a limit?
3. **Holidays:** school days follow only the weekly pattern today. Is there a
   holiday calendar, or may an admin mark a day as closed?
4. **Who records:** only the class's own teacher, or any teacher of the school
   (e.g. a substitute)? Should an admin be able to record for any class?
5. **Edit window:** may a teacher correct yesterday's roll, or only the same day?
6. **Entitlement:** `ATTENDANCE` is not in the free tier. Grant it free at school
   creation (like `ACADEMIC` and `FINANCE`), keep it paid, or backfill existing
   schools? The dashboard box and pages depend on this.
7. **Parent notification:** should a parent be messaged about an absence, at what
   time of day, and may a parent opt out?
8. **Excuse workflow:** can a parent submit an excuse (note or document) for the
   admin to approve, or does the admin only flag it?
9. **Reports:** a monthly or per-student absence report and its threshold for
   alerts. Not part of this proposal.
