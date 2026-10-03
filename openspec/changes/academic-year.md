# Academic Year (سال تحصیلی) And Year Rollover

Status: proposed 2026-10-02. **Phase 0 is implemented** (migration `V6`, years
created by the application, the school's current year, the «سال تحصیلی» page) and
its behavior is current: it is recorded in
[`specs/current-system.md`](../specs/current-system.md). **Phases 1-3 are not
implemented**, and nothing about them changes the current specification until
they are built. Several design questions were answered by the product owner on
2026-10-02 and more on 2026-10-03 (below); the rest are still open. Foundational change: other proposals
depend on it ([`class-timetable.md`](class-timetable.md), and the deferred
"course list changes over time" question in
[`grades-by-course.md`](grades-by-course.md)). Every item that is a product
decision is a question below, not invented here.

## What The Product Owner Said (2026-10-02)

- The **whole platform has an academic year**.
- Every year, **tuition (شهریه)**, **the courses of each grade level** and **the
  students** change.
- In the Ministry's own systems a **school can choose the academic year**.
- **Students' grade change (ارتقای پایه)** must be thought through.

## Decisions Taken By The Product Owner (2026-10-02)

1. **Previous years' data stays editable** (corrections are allowed): moving the
   school to a newer year freezes nothing.
2. **Who sees which year.** The **school admin can pick any year to look at it;
   everyone else sees only the current year.**
   Interpretation to confirm (question 6): a past year that holds data is for
   *viewing* by the admin, not something the school's current year is switched
   back to.
3. **Nobody creates the years - the system does.** A Solar-calendar date is set
   in configuration (for example 1 Mordad); every year, from that date on, the
   application creates that Solar year's academic year and stores it (in 1405 the
   year `1405-1406`). Neither the platform operator nor a school admin has to
   create or open it. A year has **no start or end date** of its own: when a year
   starts and ends *for a school* is the **school admin's choice** (choosing the
   next year is what ends the previous one). This replaces the earlier idea that
   the operator defines years and opens them for selection.
4. **Tuition is per school and year only** (not per grade level), and **the
   school admin sets it every year**: a new year starts with no tuition until the
   admin sets it.
5. **Classes are separate per year** (a class belongs to one year).
6. **Promotion is automatic by default, can also be done by hand, and the
   automatic mode can be switched on or off** (a school setting).
7. **Build order**: phase 0, then the class timetable, then phases 1-3.

## Decisions Taken By The Product Owner (2026-10-03)

Answers to the first unresolved questions (numbering is the one the questions had).

- **Q1 - automatic promotion is fully automatic.** Default rule: when the school
  switches to a new year and automatic promotion is on, every student moves to the
  next grade level; repeaters (and any other exception) are changed by hand
  afterwards. The new year's **classes are created from scratch** - they are not
  copied from last year. *Consequence recorded as a new question (8):* the target
  classes do not exist at the moment of the switch.
- **Q2 - invoices when tuition is set late: a separate action.** Saving the year's
  tuition issues nothing by itself; a separate "issue the year's invoices" action
  (the existing backfill, now per year) issues them for students enrolled in that
  year who have none.
- **Q3 - paying an earlier year's invoice is allowed** (decision 1 covers payments).
- **Q5 - past years are hidden from non-admins, confirmed.** Teachers, students and
  parents see only the current year (this removes the all-years view of
  `/my-grades`). **Report cards of earlier years are obtained through the school
  admin**; no student/parent self-service view of them is built.

Proposed defaults, **not yet confirmed** (they stand unless the product owner
objects): Q4 existing data is attributed to one year chosen by the admin when the
feature is enabled, unmatched free-text grade years are reported not guessed; Q6 the
admin may switch the current year back to an earlier year; Q7 no platform-operator
work is needed for years; the "ABA" question is closed (below).

## Verified Baseline (before phase 0)

From code, not from earlier docs, as it stood when this proposal was written.
Phase 0 has since added the year list and the school's current year (see
"The year and the school's current year"); the points below that phase 0 did not
touch (tuition, enrollments, classes, promotion, the course catalog) are still
accurate.

- **There was no academic-year concept.** The only trace is the free-text column
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

### The year and the school's current year (phase 0, implemented)

- **A global list of academic years** (`school.academic_years`): a `code` such as
  `1404-1405` (ASCII digits, the same shape grades use today) and a `start_year`
  (unique, for ordering). No dates, no flags. **The application creates the
  rows itself** (`AcademicYearProvisioner`): configuration holds the Solar date
  the academic year starts (`roshdeandishe.academic-year.start-month` /
  `start-day`, default month 5 day 1 = 1 Mordad, overridable by environment
  variables, with `timezone` default `Asia/Tehran` as the basis of "today"). From
  that date in each Solar year the year `Y-(Y+1)` exists; before it, the newest
  year is last year's. It runs once at startup and every night (00:15), is
  idempotent (`insert ... on conflict do nothing`, safe with several instances),
  and fills years missed while the application was down one by one, without gaps.
  An invalid date (month outside 1-12, a day the month does not have, 30 Esfand)
  fails at startup. The migration seeds only the base year `1404-1405`, which
  existing grades already use; every later year comes from the provisioner.
  Read-only for schools; the Gregorian→Solar conversion is a dependency-free port
  of the algorithm in `static/js/jalali.js` (`SolarCalendar`), checked against it
  on every day from 1990 to 2060.
- Each **school has a current academic year** (`school.schools.current_academic_year_id`),
  **chosen by its admin among the listed years**. It is null until chosen: nothing
  is guessed for existing schools, and creating a new year changes no school. It is the default year wherever a year is
  needed; today only `/grades` uses it (the year field is prefilled and stays
  editable, so earlier years can still be corrected).
- **New permission `ACADEMIC_YEAR_MANAGE`** (a named right, like `GRADES_EDIT`),
  granted to `SCHOOL_ADMIN` and assignable to custom roles (e.g. a deputy). It
  gates reading the full year list and choosing the current year. Seeded with the
  other reference data, so it reaches already deployed databases.
- **Two views, by decision 2:** `GET /api/me/schools/{id}/academic-year` returns
  **only the current year** to any active member of the school;
  `GET /api/admin/schools/{id}/academic-year` returns the **whole list**
  and the current year, and `PUT` chooses the current year; both
  need `ACADEMIC_YEAR_MANAGE`. A year not in the list (including next year's,
  before its start date) is `404 academic-year-not-found`. Choosing changes only
  that one value; nothing is deleted or altered.
- **Stale choices are rejected by the database, not only hidden in the browser.**
  `PUT` carries `expectedCurrentAcademicYearId` (the current year the page sees,
  `null` = not chosen yet; always sent) and the write is one atomic
  compare-and-set `UPDATE ... WHERE current_academic_year_id IS NOT DISTINCT FROM
  :expected`. If the school's current year is no longer what the caller saw, the
  answer is `409 academic-year-changed` and nothing is written, so two requests
  that finish in the wrong order (the same admin going A→B→A in the browser, or
  two admins) can never silently put an older choice over a newer one. The page
  then reloads and says the year changed. The check compares **the value**, not a
  revision counter, on purpose: the current year *is* the whole state, so "it is
  still what you saw" is exactly the condition under which the caller's decision
  still holds. If another admin goes A→B→A in between, the page's premise ("the
  year is A") is still true and applying the caller's choice is the same outcome
  as the two admins acting one after the other; a version column would add a
  migration and an API field to reject a request that is not wrong. Revisit it
  only if the history of changes ever matters (an audit trail, or a setting with
  more than one field).
- The page **«سال تحصیلی»** (`/academic-year`, `SCHOOL_ADMIN`, sidebar group
  «مدرسه» and a dashboard shortcut) shows the current year, a select of the
  years and a save button.
- Reading the current year is a facade call (`SchoolFacade.findCurrentAcademicYear`)
  so other modules can use it without depending on each other; the list lives in
  `school`, which already owns school settings.
- **Not in phase 0:** validating the year on grades against the list (existing
  grades carry free text that must first be matched), and restricting what
  non-admins see by year. Both come with phase 1, when each kind of data is
  year-scoped (see "Who sees which year").

### Who sees which year (decisions 1 and 2, applied from phase 1)

As each kind of data becomes year-scoped, the **school admin** can open any year
to look at it and still correct it (decision 1); **teachers, students and
parents only see the current year's data**. Concretely this will change
`/my-grades` (today it shows all years) and `/grades` for teachers (today the
year is free text). It is not applied in phase 0; it removes something users have
today, and the product owner confirmed it on 2026-10-03 (question 5: earlier years'
report cards come from the school admin).

### What becomes year-scoped (by phase)

- **Phase 0 - the year itself (implemented).** The global list created by the
  application, the school's current year, the settings page and endpoints, and
  the `/grades` year prefill. Smallest slice; unblocks the class timetable.
  **Not built yet and moved to phase 1:** validating the year on grades against
  the list (existing free-text values are matched to it; unmatched ones are
  reported, not dropped), showing the current year in the navbar and the
  dashboard, and the role-by-year visibility.
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

### Promotion (ارتقای پایه) (decision 6)

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
- **Automatic** (decided 2026-10-03): runs by itself when the school switches its
  current year and the setting is on; the default rule moves every student to the
  next grade level, and repeaters are changed by hand. It is *not* a rule from
  grades (no pass mark). A promoted student is recorded with the next grade level and a
  **pending class**, which the admin fills in once the new year's classes exist, and
  the run can be undone (question 8, answered 2026-10-03); what counts as graduating
  is still open there.
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

## Acceptance Criteria

Phase 0 (met; later phases are refined when scheduled):

- The application creates the academic year itself from the configured Solar
  date: on and after it (1 Mordad by default) the year of that Solar year exists,
  the day before it does not; years missed while down are filled without gaps;
  running it again creates nothing; an impossible date fails at startup.
- A school admin (`ACADEMIC_YEAR_MANAGE`) reads the whole list of years and sets
  the school's current year to a listed year and no other; an unknown year is
  `404`, with nothing changed. A save whose expected current year is stale is
  `409` and writes nothing; of two concurrent saves with the same expectation
  exactly one wins.
- Every active member of the school reads **only** the current year (no list);
  a user without the permission gets `403` on the admin endpoints; a stranger who
  is not a member gets nothing.
- A school that has not chosen a year has none (null), not a guessed one.
- Choosing a year changes only that value; the `/grades` year field is prefilled
  from it and stays editable; nothing else changes and no data is touched.
- `ModularityTests.verifyModularStructure()` and `ddl-auto=validate` pass.

Phase 1 and later (not built): grades validated against the list; the
role-by-year visibility of "Who sees which year"; year-scoped courses, tuition,
classes/enrollments and promotion.

## Tests

- Unit (no Docker): `SolarCalendarTest` (Nowruz of 1400-1406, 30 Esfand 1403,
  1 Mordad and the day before, the Shahrivar/Mehr boundary),
  `AcademicYearProvisionerTest` (the 1 Mordad boundary, nothing when the year
  exists, gap fill, empty list, a concurrent insert, another start date from
  configuration, invalid dates), `AcademicYearFacadeTest`,
  `AcademicYearControllerTest` (any active membership counts) and
  `AcademicYearPageRenderTest`.
- Integration: `AcademicYearFlowIntegrationTest` (list, choose, the two views,
  permission and stranger negatives, an unknown year, and that the application
  created the years itself without gaps or duplicates). Verified locally against
  PostgreSQL 16 with Flyway `V1`→`V6` on an empty database and
  `ddl-auto=validate` (the full suite: 291 tests, one error that only needs
  Docker; the database ends with `1404-1405` from the migration and
  `1405-1406` created by the application); CI runs them on Testcontainers
  `postgres:18`.
- Browser (compiled templates, mocked APIs): the page (no year, choose and save,
  save disabled when unchanged, `403` message) and the `/grades` prefill (current
  year, a typed year is kept, a school switch while the request is pending, no
  year, API error).

## Unresolved Questions

1. ~~What does "automatic" promotion do?~~ **Answered 2026-10-03:** fully automatic
   at the year switch, everyone to the next grade level, repeaters by hand; new
   classes are created from scratch (see question 8 for what follows from that).
2. ~~Invoices when tuition is set late.~~ **Answered 2026-10-03:** a separate action.
3. ~~Paying last year's invoice.~~ **Answered 2026-10-03:** yes, allowed.
4. **Existing data.** When phases 1-3 are enabled, which year do all current
   classes, enrollments, tuition and grades belong to? One year chosen by the
   admin (as proposed), and existing free-text grade years that match no listed
   year are reported, not guessed.
5. ~~Hiding past years from non-admins.~~ **Answered 2026-10-03:** yes, hidden; earlier
   years' report cards come from the school admin.
6. **Admin viewing versus switching back.** The admin can look at any year, and
   the school's *current* year is what everyone else sees. May the admin switch
   the current year back to an earlier year (e.g. a mistaken choice), or is a
   past year that already holds data view-only and not selectable as current?
   Phase 0 allows any listed year to be chosen (only grades carry a year so far).
7. **The platform-operator actor.** Years need none (the system creates them).
   Course catalog rows still come from data migrations because no platform actor
   or permission exists (`platform-admin-not-assignable.md`); that deferred
   platform work is unchanged.
8. **Placing promoted students when the new year's classes do not exist.**
   **Answered 2026-10-03:** option (a). The automatic run records each student as
   promoted to the next *grade level* with the class still **pending**; once the
   admin has created the new year's classes, the admin places the pending students
   (a "pending placement" list). **An automatic run can be undone** (answered
   2026-10-03: yes). *Still open:* what makes a student a graduate when the school's
   last grade is passed (the levels are 1-6, 7-9 and 10-12, and a school stays in one
   level) - proposed: students in the last grade of the school's level become
   "graduated" in the automatic run (history, grades and invoices kept, no new-year
   enrollment or invoice), reversible by the same undo, and the admin can mark a
   student as repeating instead before or after.
9. **Closed: a version column for the school's current year ("ABA").** The expected
   current year is compared by value in the write itself; a revision counter would
   only reject requests that are not wrong. Revisit only if an audit trail of
   changes is ever needed (see "The year and the school's current year").
