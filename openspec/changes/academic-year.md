# Academic Year (سال تحصیلی) And Year Rollover

Status: proposed 2026-10-02, **not implemented**; four design questions answered
by the product owner the same day (below), the rest still open. Foundational change: several
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

## Decisions Taken By The Product Owner (2026-10-02)

1. **Previous years are read-only** when they hold data: once a school moves to
   a newer year, the older year's data can be viewed but not changed.
2. **Tuition is per school and year only** (not per grade level), and **the
   school admin sets it every year**: a new year starts with no tuition until the
   admin sets it.
3. **Classes are separate per year** (a class belongs to one year).
4. **Promotion is automatic by default, can also be done by hand, and the
   automatic mode can be switched on or off** (a school setting).

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
- **Phase 2 - tuition per year.** `finance.tuition_fees` becomes one amount per
  (school, year) and invoices carry the year (decision 2). A new year has **no
  tuition until the admin sets it** - the same rule as today ("no fee, no
  invoice"). Not per grade level. How students already enrolled in the new year
  get their invoices once the fee is set is question 3 below.
- **Phase 3 - classes, enrollments and promotion.** **Classes belong to a year**
  (decision 3) and a separate **enrollment** record says which student is in
  which class in which year, so history is kept and a student's identity (user,
  guardians, past grades and invoices) persists across years. Promotion is a
  year-rollover action that creates the next year's enrollments.

### Read-only previous years (decision 1)

Once a school's current year moves on, writes to older years are rejected with a
clear error: grades, timetable, enrollments and the year's tuition. Reads stay
open. Paying an old year's **unpaid invoice** is a different matter (a family
may still owe last year's tuition): see question 4. Phase 0 can apply the rule
only to what already carries a validated year (grades), and later phases extend
it as each kind of data becomes year-scoped.

### Promotion (ارتقای پایه) (decision 4)

At the start of a year the school can promote the old year's students into the
new year's classes. Per student the outcome is one of: **promote** (a class of
the next grade level in the new year), **repeat the grade** (a class of the same
level), **graduate** (the school's last grade, `SchoolLevel.maxGrade`), or
**leave** (transfer/withdraw).

- A **school setting turns automatic promotion on or off.**
- **Automatic** runs the rollover for the school without a manual pass, using a
  default rule; **manual** is the same bulk screen with a per-student choice, and
  the admin can use it even when the automatic mode is on, to override outcomes
  or to run it by hand when it is off.
- What "automatic" means exactly - the rule that decides promote/repeat, and
  whether it runs by itself or only pre-fills the admin's screen - is question
  1 below. It is a Ministry/product rule and is not invented here.
- Guardian links stay; last year's grades stay on the old enrollment; new-year
  invoices follow the tuition rule (question 3).

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

1. **What does "automatic" promotion do?** Options: (a) the default rule is
   "everyone moves to the next grade level, repeaters are changed by hand", run
   automatically when the school switches year; (b) the same default, but it only
   pre-fills the promotion screen and the admin confirms with one click
   (recommended: nothing changes students' classes without a person confirming,
   and the school setting then decides whether the screen opens pre-filled);
   (c) a rule from grades (a pass mark, per-term averages), which needs the
   Ministry's rule, an overall average and grades per course, all open. Also: new
   classes for the new year must exist before promoting; are they created by the
   admin, or copied from last year's classes (same name/section, grade level
   kept)? And can an automatic run be undone?
2. **Who defines years and who selects (phase 0).** Years are defined only by the
   platform operator (as proposed) and the school admin chooses the school's
   current year from that list (decision: the school selects). May the admin pick
   any listed year or only the next one? Which permission: a new one (e.g.
   `ACADEMIC_YEAR_MANAGE`, held by `SCHOOL_ADMIN`)? Is a year just a code
   (`1404-1405`) or does it have start/end dates?
3. **Invoices when tuition is set late.** The admin sets the year's tuition
   after students are already enrolled in the year. Are invoices issued at the
   moment the fee is saved (for everyone enrolled that year without one), by a
   separate "issue the year's invoices" action, or only for students enrolled
   afterwards (today's rule)?
4. **Read-only and money.** Should a family still be able to pay last year's
   unpaid invoice (recommended: yes, payments are not "editing the year's
   data")? Are grades/timetable/enrollments the only things frozen? Is there an
   explicit "close year" step, or does switching the current year freeze it?
5. **Existing data.** When the feature is enabled, which year do all current
   classes, enrollments, tuition and grades belong to? One year chosen by the
   admin (as proposed), and existing free-text grade years that match no listed
   year are reported, not guessed.
6. **Order.** Phase 0, then the class timetable, then phases 1-3 (as proposed),
   or should tuition/classes-per-year come first?
