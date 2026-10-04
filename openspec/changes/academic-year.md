# Academic Year (سال تحصیلی) And Year Rollover

Status: proposed 2026-10-02. **Phase 0 is implemented** (migration `V6`, years
created by the application, the school's current year, the «سال تحصیلی» page) and
**phase 1 is implemented 2026-10-03** (migration `V9`, see "Phase 1 design"); their
behavior is current and recorded in
[`specs/current-system.md`](../specs/current-system.md). **Phases 2-3 are not
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

- **Q8 - placing promoted students (decided 2026-10-03, option a).** The automatic run
  records each student as promoted to the next *grade level* with the class still
  **pending**; the admin places pending students once the new year's classes exist.
  **An automatic run can be undone.**
- **Graduation (decided 2026-10-03).** A student in the last grade the school offers
  graduates in the automatic run (history, grades and invoices kept, no new-year
  enrollment or invoice, undone by the same undo); the admin can mark a student as
  repeating instead.
- **Leaving (decided 2026-10-03).** A student who moves to another school is marked as
  *leaving*: removed from the school's list for the next year, membership deactivated,
  nothing deleted.
- **Phase 2 - a family sees an earlier year's invoice while it has a balance (decided
  2026-10-03).** A debt is never hidden; a settled earlier invoice is.
- **Phase 1 - a school without a current year (decided 2026-10-03, option c).** Not
  restricted until it chooses a year; the page warns the admin before the first choice.
- **Withdrawn / deferred (2026-10-03).** Identity data that outlives one school and
  transfers between schools were withdrawn. A school offering several levels was
  deferred: promotion uses today's single level per school (question 10).

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
level), **graduate** (the last grade the school offers), or
**leave** (transfer/withdraw; removed from the next year's list, data kept).

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
  the run can be undone (question 8, answered 2026-10-03). The last grade of the school's
  school's last grade graduates automatically (with the single school level of today;
  several levels per school is deferred, question 10); **leave** is a deliberate per-student choice - removed from the
  next year's list, never deleted.
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

### Phase 1 design (2026-10-03) - implemented

Built on the decisions above; the assumptions are listed so they can be overturned.
Five slices, each with its own tests (all five are built; "As built" below):

1. **The year on a grade is validated.** `recordGrade` canonicalizes the year (Persian
   and Arabic digits to ASCII, trimmed) and rejects any year that is not in
   `SchoolFacade.listAcademicYears()` with `400 invalid-academic-year`. Reads only
   canonicalize the digits. Existing grades are never rewritten or dropped; a startup
   audit logs a WARNING with the count per school of grade rows whose year matches no
   listed year (reported, not guessed - the admin still sees them).
2. **Courses have a validity range.** `academic.courses` gets `valid_from_year` and
   `valid_until_year` (nullable integers, the *start year* of an academic year, inclusive,
   `from <= until`); both null means "always" (every existing course). A course is
   offered for the year `Y-(Y+1)` iff `from <= Y` (or null) and `Y <= until` (or null).
   The ranges are data, managed by migrations like the rest of the catalog. Used by:
   the grade-book course picker (`GET .../grades/courses?classId=&academicYear=`; without
   the year parameter the whole grade-level list, as before), `recordGrade` and the
   timetable (`setEntry` and the timetable course picker take the timetable's year).
   A course outside its range is `400 course-not-valid-for-year`. Rows that already
   exist (grades, timetable cells) are not touched when a course is closed.
3. **Who sees which year (decisions 1, 2 and 5).** Whoever holds
   `ACADEMIC_YEAR_MANAGE` (the school admin by default) sees and may correct any year;
   everyone else sees only the school's current year: `/api/me/schools/{id}/grades`
   returns only the current year's grades, and the grade-book endpoints
   (`GRADES_EDIT`, used by teachers) read and write only the current year, otherwise
   `403 academic-year-not-allowed`. **Decided 2026-10-03 (option c):** a school that has
   not chosen a current year yet is not restricted, because restricting would hide every
   existing school's grades until the admin picks a year; the restriction starts the day
   a year is chosen, and the «سال تحصیلی» page shows a warning about exactly that while the
   school has no current year (what non-admins will stop seeing, that years outside the
   list stay visible to the admin only, and that earlier report cards come from the admin).
4. **The current year is visible.** The navbar and the dashboard show the selected
   school's current year (or nothing if none is chosen). The `/grades` year field
   becomes a select of the listed years for the admin and a fixed current year for
   others.
5. **Nothing else changes in phase 1:** classes, enrollments, tuition and promotion are
   phases 2-3; the timetable already stores its year.

**As built.** `AcademicYearCodes` (public, `academic.shared`) is the canonical form;
`AcademicFacadeImpl.requireListedYear` returns the year's start year for the range check;
`GradeYearAudit` logs a WARNING at startup per (school, unlisted year) with the row count
and touches nothing. `CourseEntity.isOfferedIn(startYear)` is the range rule (both ends
inclusive). Course rules apply in `recordGrade`, `listCoursesForClass(…, year)`,
`listCoursesForTimetable(…, year)` and `setEntry` (the range is checked against the
period's own year); a course outside its range is `400 course-not-valid-for-year`, an
unlisted year `400 invalid-academic-year`. `AcademicYearVisibility` (controller layer)
decides "any year or only the current one" from `ACADEMIC_YEAR_MANAGE`; a non-holder
asking for another year gets `403 academic-year-not-allowed` on the grade-book endpoints,
and `/api/me/schools/{id}/grades` returns only the current year's rows (legacy free-text
years are hidden from non-admins once the school has a current year). The `/grades` page
shows a select of the listed years for the admin, the current year alone for others, and
the old text field only when there is neither; its course picker and the timetable's
follow the chosen year. The navbar tags the selected school with its current year and the
dashboard says «سال تحصیلی جاری: …» (Persian digits, rendered on the server because the
navbar is on pages without `calendar.js`). Verified: unit and real-database tests (see
Tests), and the `/grades` page in a browser against mocked APIs in three modes (admin,
teacher, school without a current year).

### Phase 2 design (2026-10-03) - implemented

Built on the decisions above (Q2 invoices by a separate action, Q3 old invoices stay
payable, Q4 existing data attributed by the admin). Guiding rule, the same as phase 1: **a
school without a current year behaves exactly as today** (year-less tuition and invoices);
once it has one, tuition and invoices are scoped by year. Assumptions are listed so they can
be overturned.

1. **Data (`V10`).** `finance.tuition_fees.academic_year` and `finance.invoices.academic_year`
   (`varchar(16)`, the canonical year code like grades and the timetable, no cross-module
   foreign key), nullable: `NULL` = "not attributed to a year" (every existing row, and rows
   of a school with no current year). Uniqueness: tuition one per (school, year) and one
   `NULL` per school; invoices one per (school, student, year) and one `NULL` per (school,
   student) - partial unique indexes replace `uq_invoices_school_student`.
2. **Which year an operation uses.** The finance admin endpoints take an optional
   `academicYear`; absent means the school's current year, and when the school has none,
   `NULL` (today's behavior). A given year must be a listed year (`400 invalid-academic-year`).
3. **Tuition per year.** Set and read per year; a new year has no tuition until the admin
   sets it ("no fee, no invoice" is unchanged); not per grade level.
4. **Invoices.** Enrolling a student issues an invoice for the *current* year if that year's
   tuition is set (a school with no current year: the `NULL` tuition, as today). The
   existing backfill becomes "issue the year's invoices" (decision Q2): for the chosen year it
   issues one invoice to every current student of the school who has none for that year, at
   that year's tuition; `404 tuition-fee-not-set` if that year has no tuition, and `409
   unattributed-invoices-exist` while the school still has year-less invoices and a specific year is
   asked (the student with an old invoice would be billed twice; attribute first). Classes and
   enrollments are not year-scoped before phase 3, so "enrolled in that year" means the
   school's current students.
5. **Payments.** Recording a payment works on an invoice of any year (decision Q3); the
   balance rules are unchanged.
6. **Attributing existing data (Q4 default).** `POST /api/admin/schools/{id}/finance/attribute-year`
   (`FINANCE_MANAGE`, body `{academicYear}`) sets `NULL` to that year on the school's tuition
   and invoices in one transaction; `409 year-already-has-tuition` if legacy tuition exists
   and that year already has its own; nothing legacy means nothing changes. The finance page
   shows a banner offering it while unattributed rows exist and the school has a current year.
7. **Who sees which year in finance.** Holders of `FINANCE_VIEW` (finance admin, school
   admin) see any year - they need earlier years' invoices to record payments (Q3), which is
   a narrowing of "only whoever holds `ACADEMIC_YEAR_MANAGE`" for administrative finance
   endpoints. Self-service `/api/me/schools/{id}/invoices` (and the dashboard's per-child
   counts) return the **current year's** invoices plus unattributed (`NULL`) ones, **and
   an earlier year's invoice while it still has a balance** (decided 2026-10-03, the
   proposal accepted: a debt must never be hidden from the family; a settled earlier
   invoice is hidden, decision Q5). Each invoice carries its year so the family can tell an
   old debt from the current one.
8. **Not in phase 2:** classes/enrollments per year and promotion (phase 3), invoice
   history of tuition amounts, per-grade tuition, discounts.

**As built.** `V10` adds the nullable `academic_year` columns and the four partial unique
indexes (the old `unique (school_id)` on tuition is dropped by its columns, as `V5` does).
`FinanceFacadeImpl.resolveYear` is the single rule for "which year": the requested year (canonical,
listed, else `400 invalid-academic-year`), else the school's current year, else `NULL`; a school id
with no school row counts as "no current year" (finance used to treat it as an opaque id).
The insert uses `on conflict do nothing` without a target, since uniqueness is now two partial
indexes. The year-less overloads (`setTuitionFee(school, amount)`, `getTuitionFee(school)`,
`backfillInvoices(school)`) live in the implementation, not as interface defaults: a default
method called through the Spring proxy runs the two-argument method on the target and skips
`@Transactional`. Endpoints: `GET/PUT /tuition-fee?academicYear=`, `GET /invoices?academicYear=`
(the school-wide list returns the year plus unattributed invoices; a parent still gets the
family view), `POST /invoices/backfill?academicYear=`, `GET /finance/years` (`FINANCE_VIEW`),
`GET /finance/unattributed` and `POST /finance/attribute-year` (`FINANCE_MANAGE`). The Bale bot's
per-child invoice count now uses the family view. The `/finance` page has a year select (current
year default, hidden when there is no current year), a warning with an "attribute to year" action
while unattributed rows exist, a «بدون سال» tag on such invoices, and the backfill button works on
the selected year. Verified in a browser against mocked APIs in both modes (a school with a
current year and unattributed data; a school without one).

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

Phase 1 (met 2026-10-03):

- A grade is recorded only for a listed year, in canonical form (`400 invalid-academic-year`
  otherwise, nothing saved); reading by year canonicalizes the digits; existing rows are
  never changed and unmatched ones are logged at startup.
- A course outside its validity range is absent from the year's pickers (grade book and
  timetable) and rejected for grades and timetable cells (`400 course-not-valid-for-year`);
  rows that already exist are untouched; both ends are inclusive, null means unbounded, and
  the database rejects `from > until`.
- A holder of `ACADEMIC_YEAR_MANAGE` reads and writes any year; others get only the current
  year (`403 academic-year-not-allowed`; `/my-grades` shows only the current year); a school
  with no current year is unrestricted.
- The navbar and the dashboard show the selected school's current year, and show nothing
  when there is none.
- While a school has no current year, the «سال تحصیلی» page shows a warning that the first
  choice restricts non-admins to that year.

Phase 2 (met 2026-10-03):

- A school without a current year behaves exactly as before: tuition and invoices carry no year.
- With a current year, tuition is set and read per year (a new year has none: `404
  tuition-fee-not-set`), the backfill issues one invoice per current student per year at that
  year's tuition (idempotent), and an enrollment issues the current year's invoice when that year
  has tuition.
- A payment can be recorded on an invoice of any year; the finance admin lists any year.
- Existing year-less tuition and invoices are attributed to one year by an explicit action
  (transactional; refused when it would duplicate that year's tuition or a student's invoice);
  the backfill for a specific year is refused until then.
- A family (and the dashboard counts, and the Bale bot) sees the current year's invoices,
  unattributed ones, and an earlier year's invoice only while it has a balance.

Phase 3 (not built): classes/enrollments per year and promotion.

## Tests

- Unit (no Docker): `SolarCalendarTest` (Nowruz of 1400-1406, 30 Esfand 1403,
  1 Mordad and the day before, the Shahrivar/Mehr boundary),
  `AcademicYearProvisionerTest` (the 1 Mordad boundary, nothing when the year
  exists, gap fill, empty list, a concurrent insert, another start date from
  configuration, invalid dates), `AcademicYearFacadeTest`,
  `AcademicYearControllerTest` (any active membership counts) and
  `AcademicYearPageRenderTest`.
- Phase 1: `GradeCourseFacadeTest`, `TimetableFacadeTest`, `GradeYearAuditTest`,
  `AcademicYearVisibilityTest` (unit); `GradeByCourseFlowIntegrationTest` (range, picker,
  invalid year, the database check), `AcademicYearVisibilityFlowIntegrationTest`
  (teacher/admin/student, the unrestricted school, the dashboard year),
  `GradesCoursePickerRenderTest`, `NavSharedSourceRenderTest`.
- Phase 2: `FinancePerYearFlowIntegrationTest` (year-less school, per-year tuition and
  backfill, the enrollment event, attribution and its conflicts, the family view and an old
  year's payment), `FinancePageRenderTest`; the existing finance, expense, Bale and
  `WardsAssemblerTest` suites run unchanged.
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
   **Answered 2026-10-03 (recorded under "Decisions Taken ... 2026-10-03"):** option (a). The automatic run records each student as
   promoted to the next *grade level* with the class still **pending**; once the
   admin has created the new year's classes, the admin places the pending students
   (a "pending placement" list). **An automatic run can be undone** (answered
   2026-10-03: yes). **Graduation answered 2026-10-03:** a student in the **last grade
   the school offers** becomes "graduated" in the automatic run: no new-year enrollment
   or invoice, history, grades and invoices kept, reversible by the same undo; the admin
   can mark a student as repeating instead before or after. (A school offering several
   levels was raised the same day and **deferred** - question 10; until it is taken up,
   "last grade the school offers" is the last grade of the school's single level.)
   **Leaving the school (answered 2026-10-03):** a student who leaves is marked as
   **leaving** (the fourth outcome); they are removed from the school's list for the
   next year only - nothing is deleted - and the school membership is deactivated
   through the existing mechanism.
10. **Deferred 2026-10-03 ("forget it for now"): which grades does a school offer?** *Code and documentation conflict with the
   product owner's statement (2026-10-03):* a school may have different grades, and a
   student moves up from a lower grade of the same school (for example 9 to 10 in a
   school with grades 7-12). Today a school has **exactly one level**
   (`school_level`, a single value with a database check: `ELEMENTARY` 1-6,
   `LOWER_SECONDARY` 7-9, `UPPER_SECONDARY` 10-12), classes are validated against it
   (`InvalidGradeLevel`), and the registration page picks one level, so such a school
   cannot exist yet. Promotion needs the school's last grade to decide who graduates.
   Options: (a) a school offers **one or more levels** (a set; its grades are the union
   and the last grade is the highest); (b) a school declares a **grade range** (first
   and last grade) directly; (c) the offered grades are **derived from the classes** the
   school has in the year (no declaration, but a new year with no classes yet has no
   last grade). Also: who may change it (the registering admin, or the platform), and
   what happens to existing schools (keep their one level). Recommended: (a), because
   it reuses the three levels everyone already knows and keeps existing schools valid.
   It is a small change of its own and should be done before the promotion phase.
11. **Dropped 2026-10-03: identity data that outlives one school, and transfer between
   schools.** The product owner withdrew it. For the record, the system stores only
   name, email and phone on the user and has no identity fields; promotion still must
   not delete a graduate's or leaver's user record.
9. **Closed: a version column for the school's current year ("ABA").** The expected
   current year is compared by value in the write itself; a revision counter would
   only reject requests that are not wrong. Revisit only if an audit trail of
   changes is ever needed (see "The year and the school's current year").
