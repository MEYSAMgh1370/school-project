# Attendance (حضور و غیاب)

Status: proposed 2026-10-05; **the product owner answered all nine questions the same
day (recorded in "Decisions"); phases 0-2 and the phase-3 backend are built, with the
pages `/calendar`, `/attendance`, `/attendance/report` and the admin dashboard box.
The student/parent «غیبت‌های من» page (`/my-attendance`, with the excuse form) and the admin excuses tab on `/attendance/report` are built too, so all phases are implemented.** (phases below). Check each
phase's note for what is built; the current behavior of a built phase belongs to
`specs/current-system.md`.

## Decisions (product owner, 2026-10-05)

1. **Granularity: once per day** per class (not per period).
2. **Statuses:** present (implied), absent, late, plus an excused flag on absent: accepted.
3. **Holidays: a school calendar**, which the future «برنامه‌ی سالانه» (yearly plan)
   feature must be able to reuse. So the calendar is its own shared piece in the
   `school` module (see "School calendar"), not an attendance-private table.
4. **Admin records for any class** with **its own permission** (`ATTENDANCE_RECORD_ANY`);
   a teacher records only for their own class (`ATTENDANCE_RECORD`).
5. **Editing a past day** needs a separate permission, **`ATTENDANCE_EDIT_PAST`**,
   granted by default only to `SCHOOL_ADMIN`; others may edit only the current day.
6. **Entitlement: free.** New schools get `ATTENDANCE` at creation (with `ACADEMIC` and
   `FINANCE`), and existing schools are backfilled.
7. **Parents are notified** of an absence (Bale, through the existing channel).
8. **Excuses:** not required, but included because it is small: a parent submits a
   short note on a child's absence; an admin approves or rejects it.
9. **Report:** a table where the admin queries by **time period** and **student name**
   (optionally class) and gets, per student, the number of absent, late and present days.

Built so far (details in `specs/current-system.md`): the phase 1-3 backend (excuses, the
parent notification and the self-service endpoint included); every page and the dashboard box
are still to do. Phase 3 readings where this document is silent: an `APPROVED` excuse wins over
an incoming `excused=false` when a roll is re-saved; a `PENDING` excuse is deleted when its
absence is removed; the notification date is ISO (no public server-side Jalali formatter); the
`/api/me` endpoints check the `ATTENDANCE` entitlement in the controller. Two readings the backend took where
this document is silent: a roll for a class of an earlier academic year is refused
(`409 class-not-in-current-year`, from rule 2), and the report lists only classes with at least
one roll in the range.

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

### Granularity: one roll per class per school day (decided)

The unit is **a class on a school day**, not a period. A teacher "takes the roll"
once; every student of the class is then PRESENT unless marked otherwise.
(Per-period attendance was declined: decision 1.)

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

1. A roll can be taken only for a **school day** of the school: a weekday of the
   school's pattern (`SAT`–`WED` for a `SAT` school, `MON`–`FRI` for a `MON` school;
   same source as the timetable) that is **not a closed day in the school calendar**,
   and not for a future day (`400 invalid-attendance-day`).
2. The class must belong to the school and to the school's current academic year
   (or be unattributed, as for classes today).
3. A mark's student must be **enrolled in that class** that year at that time
   (`409 student-not-in-class`). A student promoted or removed later keeps the
   history; the roll shows who was in the class on that day.
4. Saving a roll is **one idempotent replace** of the class-day: the request lists
   the absent/late students; the previous marks of that class-day are replaced in
   one transaction, `rolls` is upserted.
5. Who may record: **`ATTENDANCE_RECORD`** (granted to `TEACHER`) records only for a
   class the teacher teaches (the class's `teacher_id`, or a teacher placed in that
   class by the timetable); **`ATTENDANCE_RECORD_ANY`** (granted to `SCHOOL_ADMIN`)
   records for any class. Without **`ATTENDANCE_EDIT_PAST`** (granted by default only
   to `SCHOOL_ADMIN`) a roll can be saved only for **today**; with it, for any past
   school day.
6. Who may read: permission **`ATTENDANCE_VIEW_ALL`** (`SCHOOL_ADMIN`,
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

### School calendar (shared with the yearly plan)

In the `school` module, because the school's week start and current year already live
there. `school.calendar_entries`: `school_id`, `id`, `day` (date), `kind` (`CLOSED`;
`EVENT` is reserved for the yearly plan and is not used by attendance), `title`
(≤ 128). Unique `(school_id, day, kind, title)` is not required; a day may carry
several entries. `SchoolFacade` exposes `listCalendar(schoolId, from, to)` and
`isClosedDay(schoolId, day)`; attendance asks only the second. New permission
`CALENDAR_MANAGE` (`SCHOOL_ADMIN`) for `POST/DELETE
/api/admin/schools/{id}/calendar`; `GET /api/admin/.../calendar` and
`GET /api/me/schools/{id}/calendar` (any active member) list a date range. A page
`/calendar` lets the admin add and remove closed days by Jalali date. No national
holidays are shipped: the admin enters them (a platform default list would be a
separate decision). The yearly plan will add its own `kind` and pages on this table
without a schema change.

### Excuses

An `ABSENT` mark may carry an excuse: `attendance.excuses` (`school_id`, `class_id`,
`day`, `student_id`, `note` ≤ 500, `submitted_by_user_id`, `submitted_at`, `status`
`PENDING`/`APPROVED`/`REJECTED`, `decided_by_user_id`, `decided_at`). A parent (active
`PARENT` of the child) submits through
`POST /api/me/schools/{id}/attendance/excuses`; the admin lists pending excuses and
approves or rejects (`ATTENDANCE_VIEW_ALL` to see, `ATTENDANCE_RECORD_ANY` to
decide). Approving sets the mark's `excused` flag; rejecting leaves it. One excuse per
(student, day); resubmitting while pending replaces the note.

### Notifications

Saving a roll publishes `AbsenceRecordedEvent` for each student **newly** marked
`ABSENT` (not for an unchanged re-save). `notification` listens and sends the linked
parents of that student a Bale message through the existing channel; a parent
without a linked Bale account is skipped silently. No opt-out in this version.

### Report (admin)

`GET /api/admin/schools/{schoolId}/attendance/report?from=&to=&classId=&name=`
(`ATTENDANCE_VIEW_ALL`): one row per student: name, class, **absent days, late days,
present days** over the period, where the base is the school days in the period on
which that student's class had a roll taken; present = base − absent (late counts as
present and is also shown separately). The page `/attendance/report` (the same page
as the per-class absentees, as a second tab) has date-range, class and name filters
and a table; an empty result teaches («در این بازه حضور و غیابی ثبت نشده است»).

## Phases

0. The school calendar (`school.calendar_entries`, `CALENDAR_MANAGE`, `/calendar`).
   **Backend built 2026-10-05** (migration `V12`, `SchoolFacade` calendar methods, the
   four REST endpoints, the permission); the `/calendar` page is not built yet.
1. Module, migration, permissions, free entitlement and backfill, record/read
   endpoints, the `/attendance` recording page.
   **Backend built 2026-10-05** (migration `V13`, module `attendance`, `ATTENDANCE_*`
   permissions, free entitlement at school creation plus a per-school idempotent startup
   backfill, `GET /classes`, `GET|PUT /classes/{classId}/days/{day}`); the `/attendance`
   page is not built yet.
2. Absentees endpoint and page, the report, the summary endpoint and the dashboard box.
   **Backend built 2026-10-05** (`GET /days/{day}/absentees`, `/summary`, `/report`; the
   report range is capped at 366 days); the pages and the dashboard box are not built yet.
3. Student and parent views, excuses, and the parent notification.
   **Backend built 2026-10-05** (no new migration: `GET /api/me/schools/{id}/attendance`, excuse
   submit/list/decide endpoints, `AbsenceRecordedEvent` and the Bale listener in `notification`);
   the pages (parent/student views, excuse form, admin excuse list) and the dashboard
   «غیبت امروز» card are not built yet.

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
`ATTENDANCE` module row already exists. Existing schools are granted `ATTENDANCE`
by a backfill (decision 6).

## Remaining Open Points

- Notification wording and time of day: sent immediately when the roll is saved.
- Excuse attachments (a document or photo): not in this version.
- Absence thresholds and alerts: not in this version.
- A platform-shipped list of national holidays: not in this version.
