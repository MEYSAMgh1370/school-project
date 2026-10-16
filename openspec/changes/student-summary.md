# Student And Ward Summary On The Dashboard

Status: proposed (product owner approved the shape, 2026-10-13) and implemented
2026-10-13; current behavior is in [`specs/current-system.md`](../specs/current-system.md)
under Identity And Login.

## Baseline

The student's dashboard had «برنامه‌ی امروز» and shortcuts only; a parent's child
card had today's attendance and finance state only. Grades, exams and absences
each needed a separate page (`/my-grades`, `/my-exams`, `/my-attendance`).

## Decisions (product owner, 2026-10-13)

- One «خلاصه‌ی من» box for the student, and the same summary inside **each child's
  card** for a parent. Three rows:
  1. **Latest grades**: the 3 most recently recorded grades of the school's
     current academic year (course name or «نمره‌ی کل», term, value; descriptive
     grades show their level), plus a link to the full grades page. **No average**
     (its definition is a product decision that has not been made).
  2. **Upcoming exams**: up to 3 exams that have not ended, soonest first, with
     the Jalali start date; no time-window limit.
  3. **Absences this year**: from the current academic year's start to today,
     three counts: unexcused absences, excused absences, late arrivals. Only days
     whose roll was taken count; an untaken day is neither absent nor present.
     The row is hidden when the school lacks the `ATTENDANCE` module or has no
     current year. «دیر آمده» is not counted as absence.
- Each section degrades on its own (an error hides that section's data, never the
  dashboard); empty sections say nothing was recorded.

## Implementation

`web.model.StudentSummaryAssembler` (moved to `studentview` by bale-smart-bot stage 1 (2026-10-16)) composes the summary from the public facades
(`AcademicFacade.listGradesForStudents`, `ExamFacade.listExamsForStudent`,
`AttendanceFacade.listMyAttendance`, `SchoolFacade`), shared template
`layout/student-summary.jte`. `WardRow.summary` carries each child's; 
`DashboardModel.ownSummaries` carries the student's (one per school where the user
is an active enrolled `STUDENT`). The caller guarantees the relationship (own
enrollment or `listWards`). No new endpoint or migration.

## Not done

Averages, a time window for exams, per-subject trends, an exam result link.
