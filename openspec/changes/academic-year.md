# Academic Year (سال تحصیلی) And Year Rollover

Status: proposed 2026-10-02, **not implemented**. Foundational change: several
other proposals depend on it ([`class-timetable.md`](class-timetable.md), and the
deferred "course list changes over time" question in
[`grades-by-course.md`](grades-by-course.md)). Nothing in
[`specs/current-system.md`](../specs/current-system.md) changes until it is built.
Every item that is a product decision is a question below, not invented here.

## What The Product Owner Said (2026-10-02)

- The **whole platform has an academic year**.
- Every year, **tuition (شهریه)**, **the courses of each grade level** and **the
  students** change.
- In the Ministry's own systems a **school can choose the academic year**.
- **Students' grade change (ارتقای پایه)** must be thought through.

## Verified Baseline

From code, not from earlier docs:

- **There is no academic-year concept.** The only trace is the free-text column
  `academic.grades.academic_year` (e.g. `"1404-1405"`; `/grades` normalizes
  Persian/Arabic digits to ASCII before sending). Nothing validates it, lists
  the valid years, or says which one is current.
- **Tuition is one amount per school**: `finance.tuition_fees` has
  `unique (school_id)` and no year. `finance.invoices` have no year either.
  An invoice is issued once, when a student is first enrolled
  (`StudentEnrolledEvent` → `FinanceFacade.issueTuitionInvoice`); re-enrolling
  into another class publishes no event.
- **A student has one current class and no history.** `academic.students` has one
  row per (user, school) with a single `class_id` and `enrolled_at`;
  `enrollStudent` overwrites `class_id` when called again ("جابه‌جایی کلاس").
  Who was in which class last year is not recorded; grades do keep
  `class_id` + `academic_year`, so their own history survives.
- **A class is not tied to a year.** `academic.classes` has `grade_level`,
  `name`, `section`, `teacher_id`, `grading_scheme`. A school's classes are the
  same rows year after year.
- **There is no promotion, repetition or graduation.** Nothing moves students
  to the next grade; `SchoolFacade.SchoolLevel` already knows a school's first
  and last grade (`minGrade`/`maxGrade`), which is what graduation would key on.
- **The course catalog has no versioning** (`academic.courses`, per grade level,
  read-only in the app, empty until the operator adds rows).
- No OpenSpec, legacy or roadmap document designs any of this
  (`grep` for promotion/rollover/ارتقا finds nothing).

## Proposed Direction

Make the academic year a first-class platform concept, then make year-scoped
data refer to it, in phases so the foundation can ship before the large
re-modelling.

### The year and the school's current year

- A **global list of academic years** (platform data, managed by the operator
  like the course catalog): a code such as `1404-1405`, plus an optional start
  and end date. Same canonical form as today's grades (ASCII digits).
- Each **school has a current academic year**, chosen by the school admin from
  that list (the Ministry-style "school selects the year"). It is the default
  everywhere a year is needed: grade entry, timetable, tuition, dashboard.
- Reading the current year is a **facade call** (`SchoolFacade` or a small new
  facade) so `academic`, `finance` and `web` can use it without depending on
  each other. Whether the global list lives in `school` or `academic` is decided
  at implementation, by `ModularityTests` and the module-boundary rules.
- Switching the current year does not delete or alter anything; older years
  stay readable.

### What becomes year-scoped (by phase)

- **Phase 0 - the year itself.** Global list, the school's current year,
  a settings screen/endpoint, a validated year on grades (existing free-text
  values are matched to the list; unmatched ones are reported, not dropped), and
  the current year shown in the navbar/dashboard. Smallest slice; unblocks the
  class timetable.
- **Phase 1 - courses per year.** Courses get a validity range in years
  (from/until), so a grade level's list changes over time without rewriting
  history; pickers show only courses valid for the chosen year. This resolves
  the deferred question in `grades-by-course.md`.
- **Phase 2 - tuition per year.** The tuition amount and the invoices carry the
  year; a new year means a new amount and new invoices for the students enrolled
  in it. Whether tuition also varies by grade level is a question below.
- **Phase 3 - classes, enrollments and promotion.** Recommended model: **classes
  belong to a year** and a separate **enrollment** record says which student is in
  which class in which year, so history is kept and a student's identity (user,
  guardians, past grades and invoices) persists across years. Promotion is then
  a year-rollover action that creates the next year's enrollments.

### Promotion (ارتقای پایه), shape only

At the start of a year the school admin runs a rollover for the old year's
classes. Per student the outcome is one of: **promote** (a class of the next
grade level in the new year), **repeat the grade** (a class of the same level),
**graduate** (the school's last grade, `SchoolLevel.maxGrade`), or **leave**
(transfer/withdraw). It is a bulk screen with per-student overrides, not an
automatic rule: how a student "passes" is a Ministry/product rule and is not
invented here (question 5). Guardian links stay; new-year invoices are issued
through the existing enrollment event; last year's grades stay on the old
enrollment.

### Out of scope here

The Ministry's report-card format, semester/term calendars beyond the existing
`FIRST`/`SECOND`, public holidays, importing years or students from external
systems, and multi-year transcripts.

## Compatibility And Risk

- Phase 0 is additive. Phases 1-3 change existing models and need data
  migrations: existing classes, enrollments, tuition and grades must all be
  attributed to **one** year chosen by the admin when the feature is enabled
  (question 4); nothing may be guessed silently.
- Phase 3 is the large one: `StudentEntity.classId` is read by grades, finance,
  the dashboard, the Bale bot and `WardsAssembler`; enrollments must keep a
  "current enrollment" accessor so those callers do not all change at once.
- Tenant isolation is unchanged: the global lists carry no `school_id`, the
  school's current year is school-scoped, and year-scoped rows keep `school_id`.
- Because years are keys, the digit normalization already done on `/grades`
  becomes a server-side rule (one canonical form) instead of a page habit.

## Acceptance Criteria (phase 0; later phases refined when scheduled)

- The operator-managed list of years is readable by every school; a school admin
  (new permission, see question 1) can set the school's current year to a listed
  year and no other.
- Anyone in the school can read the school's current year; another school's
  setting is never visible.
- Recording a grade with a year that is not in the list is rejected; existing
  grades whose year does not match a listed year remain readable and are
  reported by the migration, not deleted.
- Changing the current year changes the default year of the pickers and the
  dashboard and nothing else; old data is untouched.
- `ModularityTests.verifyModularStructure()` and `ddl-auto=validate` pass.

## Tests

- Unit (no Docker): year normalization and matching, the current-year rule
  (selectable years only), grade validation against the list.
- Integration (CI, Docker): the settings endpoint's permission/tenant
  negatives, the migration on a database that already holds free-text years, and
  the default-year behavior of the grade pages' API.

## Unresolved Questions

1. **Who defines years, who selects.** Are years defined only by the platform
   operator (as proposed), with the school choosing its current year? May a
   school pick any listed year (past or future) or only the next one? Which
   permission: a new one (e.g. `ACADEMIC_YEAR_MANAGE`, `SCHOOL_ADMIN`)?
2. **What is a year, exactly?** Just a code (`1404-1405`), or with start/end
   dates (so "today" can suggest the year, and a year can close automatically)?
   Are the two terms (`FIRST`/`SECOND`) always the whole year?
3. **Tuition.** Per school and year only, or also per grade level (today it is
   one amount per school)? Does a new year re-invoice every student or only new
   enrollments?
4. **Classes: per year or persistent?** Recommended: classes belong to a year
   with enrollments as history (phase 3). The alternative is persistent classes
   and moving students (simpler, but last year's roster is lost). And for
   existing data: which year do all current classes, enrollments, tuition and
   grades belong to?
5. **Promotion rules.** Fully manual per student with the admin choosing, or
   suggested from grades (and by what rule)? Who may run it (`SCHOOL_ADMIN`
   only)? Are "repeat", "graduate" and "leave" the right outcomes? Can a
   rollover be undone?
6. **Closing a year.** After the school switches to the next year, can the old
   year's grades, timetable and invoices still be edited (corrections), or are
   they read-only? Is there an explicit "close year" step?
7. **Order.** Phase 0 unblocks the timetable at low cost; phases 1-3 are
   progressively larger. Confirm the order (0, then the timetable, then 1, 2, 3),
   or say if tuition/classes-per-year should come before the timetable.
