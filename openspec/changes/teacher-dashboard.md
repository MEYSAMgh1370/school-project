# Teacher Dashboard

Status: proposed and implemented 2026-10-12; current behavior is in
[`specs/current-system.md`](../specs/current-system.md) under Identity And Login. This
record keeps the rationale and what was left out.

## Verified Baseline

Walked in a browser on 2026-10-12 with a teacher who had no class, then one class
with two students plus an unassigned class, and read in code.

- The dashboard was identical in both cases: heading «عضویت‌های من», shortcut cards
  led by «دفتر نمرات», a membership table and the phone box. Nothing named the
  teacher's classes, student counts or the day's attendance to take.
  Evidence: [`dashboard.jte`](../../src/main/jte/dashboard.jte).
- A teacher without a class got no explanation anywhere («دفتر نمرات» showed an
  empty class selector).
- `/classes` showed the teacher the «افزودن کلاس جدید» form although the create
  endpoint answers `403` for a teacher.
  Evidence: [`classes.jte`](../../src/main/jte/classes.jte).
- The data needed already exists: `TimetableFacade.listClassesTaughtBy` and
  `AttendanceFacade.listRecordableClasses` (the same «own class» rule `/attendance`
  applies, with a `taken` flag for today).

## Decisions (product owner, 2026-10-12)

In scope: a personal heading, «کلاس‌های من» with today's attendance, the no-class
message, hiding the class form. Deferred then, done 2026-10-13: «برنامه‌ی امروز» for teachers (the
`/api/me/schools/{id}/timetable` API already served a teacher; the dashboard box was
student-only; now it shows the teacher's own `TEACHING` slots for today with each
slot's class name and «امروز کلاسی برای تدریس ندارید.» when there are none). Out of scope: reordering the shortcut cards for a teacher without classes.

## Notes

- `listRecordableClasses` is used when the school has the `ATTENDANCE` module
  (current year, own classes, `taken`); otherwise `listClassesTaughtBy` (all years,
  no state). An `AttendanceException` falls back to the second.
- Student counts count only `ACTIVE` students whose current class is that class.
- `/attendance` and `/grades` have no `?class=` deep link, so the dashboard links
  go to the page and the teacher picks the class there.
- Not changed, noticed: on `/classes` a teacher sees «نام معلم در دسترس نیست» for
  their own class, because the name lookup needs a permission teachers do not hold.
  Re-checked 2026-10-13: this reason is wrong — the seed grants `TEACHER`
  `MEMBER_NAMES_VIEW` and `classes.js` falls back to `/memberships/names`; the message
  can only appear for a teacher who is not an active member (e.g. invite pending).
  Not reproduced for an active teacher; no code change.
