# Attendance Reports: Percent, Class And Teacher Views, Printable Student Sheet

Status: proposed and implemented 2026-10-14; current behavior is in
[`specs/current-system.md`](../specs/current-system.md) under Attendance. This record keeps the
product-owner answers and the rationale.

## Verified Baseline

Read in code on 2026-10-14 (master @ `53514ef`).

- The only report is `GET /api/admin/schools/{id}/attendance/report` (`ATTENDANCE_VIEW_ALL`): one
  row per (student, class) with `absentDays`, `lateDays`, `presentDays` over a range of at most
  366 days. It is built in memory in `AttendanceFacadeImpl.report` from rolls, marks and roll
  rosters; the base is the roster of each roll in the range, `presentDays = base - absentDays`,
  late counts as present. No percentage is returned for a range (only the day figure has one).
  Evidence: [`AttendanceFacadeImpl.java`](../../src/main/java/com/example/roshdeandishe/attendance/internal/AttendanceFacadeImpl.java),
  [`AttendanceMath.java`](../../src/main/java/com/example/roshdeandishe/attendance/internal/AttendanceMath.java).
- `/attendance/report` has three tabs (day, range, excuses); the only "bar" is a native
  `<progress>` for the day figure. There is no chart library and no PDF library; the repository's
  printing approach is the browser (`window.print()` with `@media print`), used by `/question-sheets`.
- Granularity is once per day per class (`attendance.md` decision 1); that is not changed here.
- A class has at most one `teacherId` (a user id) and the timetable can name further teachers per
  period; there is no facade method that lists teachers.

## Why (compared with Class Plus, 2026-10-14)

Class Plus reports attendance per teacher, per class, per student and for the whole school, with a
«درصد حضور» chart and a PDF of the student report. Here the admin can only list students with
three day counts and has no percentage, no class or teacher view and no printable sheet.

## Decisions (product owner, 2026-10-14)

1. Build, in this order: more reports (class, teacher, percent chart), then the printable student
   sheet. Per-period attendance is **not** part of this change (it reopens decision 1).
2. A teacher's classes are **the classes they are the teacher of, or teach in the class timetable**
   (the rule that already decides who may record a class). A class with several teachers appears
   under each of them.
3. The student «PDF» is the **browser print page saved as PDF** (as the question box does); no PDF
   library and no server-side rendering.

## Design

All new endpoints are under `/api/admin/schools/{id}/attendance`, with the controller's
`@RequiresModule("ATTENDANCE")` and `@RequiresPermission("ATTENDANCE_VIEW_ALL")`, and take the same
`from`/`to` range as `/report` (required, ordered, at most 366 days, `400 invalid-attendance-range`).

- **Percent.** `presentPercent = floor(100 x (base - absent) / base)` where `base` is the roster
  count summed over the rolls in the range (the same base as `presentDays + absentDays`); `null` when
  `base` is 0. Late counts as present and floors like the day figure, so 99.9% never shows 100%.
  `ReportRow` gains `presentPercent`.
- **Class report** `GET /report/classes?from&to&classId`: one row per class that has at least one
  roll in the range: `classId`, `className`, `daysTaken`, `absentDays`, `lateDays`, `presentDays`,
  `presentPercent`, sorted by class name. Classes with no roll are not listed (never counted present).
- **Teacher report** `GET /report/teachers?from&to`: one row per teacher of at least one class that
  has a roll in the range: `teacherUserId`, `teacherName`, totals over their classes, and `classes`
  (the class rows of the teacher). A class's numbers are never added across teachers, so the sum of
  teacher totals can exceed the school total; the page says so. Names come from the school's
  memberships; an unknown id has a null name.
- **Student sheet** `GET /report/students/{studentId}?from&to`: the student's rows (one per class),
  `presentPercent`, and the dated list of absences and lateness in the range with `excused` and the
  note. A student outside the school is `404`.
- **Pages.** `/attendance/report` gets a percent column with a bar per row in the range tab and two
  new tabs, «کلاس‌ها» (a percent bar per class) and «معلم‌ها» (a teacher table that expands to its
  classes). Each student row links to `/attendance/report/student/{id}?from=&to=`, a print page
  (A4, no navbar, `@media print`) with the school, student, class, range, percent, the absence list
  and a «چاپ / ذخیره PDF» button that is hidden in print. Bars are native `<progress>` elements
  with a text value, never colour alone.

## Out Of Scope

Per-period attendance; attendance per course; a teacher viewing their own report; staff attendance;
an AI analysis; CSV export; server-side PDF.

## Open Questions

- Should a teacher see their own class/teacher numbers (needs an own-data endpoint or a new
  permission)? Not built; `ATTENDANCE_VIEW_ALL` only.
- Should the percent exclude excused absences? This change counts every absence, as the day figure does.
