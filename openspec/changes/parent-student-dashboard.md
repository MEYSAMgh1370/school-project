# Parent And Student Dashboard

Status: proposed and implemented 2026-10-13; current behavior is in
[`specs/current-system.md`](../specs/current-system.md) under Identity And Login.

## Verified Baseline

Walked in a browser on 2026-10-12 with an invited student and a parent linked to
that student, and read in code.

- The parent's main content, «فرزندان من» (name, class, finance state, links), sat
  below four generic shortcuts («برنامه‌ی کلاسی من» …) that already aggregate the
  children, so it started below the first screen.
  Evidence: [`dashboard.jte`](../../src/main/jte/dashboard.jte).
- A child's card did not say whether the child is present today, the first
  thing a parent asks. The data exists (`AttendanceFacade.getClassDay`).
- With no child linked the parent was told how the *admin* could link one from
  the «دانش‌آموزان» page. This is the normal state right after an invited
  parent activates, because linking a child is a separate admin step.
- The student's «برنامه‌ی امروز» said «مدیر مدرسه هنوز سال تحصیلیِ جاری را انتخاب
  نکرده است»: an admin-facing sentence the student cannot act on; the same text on
  `/my-timetable`. The day line also rendered with a stray space («دوشنبه ، کلاس …»).

## Decisions (product owner, 2026-10-13)

In scope: the parent ordering, today's attendance on each child's card, the
parent's no-child message, the student wording and the spacing. Out of scope: a
summary of exams, latest grades and absences on the student's dashboard.

## Notes

- `getClassDay` returns the whole class, so `WardAttendanceResolver` keeps only the
  child's own row; nothing about other students reaches the page model.
- A class whose roll has not been taken is `PENDING`, never "present" (the same
  rule as the admin's figure). `recordable == false` is a non-school day.
- The new chips are rendered on the server from the state; no client request.
- The existing tests that fixed the old order («shortcuts, wards, memberships») and
  the old empty-state wording were updated to the new behavior on purpose.
