# Current System Specification

Status: current behavior checked against implementation, tests, migrations, and
configuration on 2026-08-06. The web design and finance-display invariants were
updated and verified on 2026-08-16.

## Product Scope

Roshdeandishe is a multi-tenant school management SaaS for Iranian schools. A school is the tenant. One user account can have memberships and roles at multiple schools.

Current user-facing roles include:

- `SCHOOL_ADMIN`
- `TEACHER`
- `FINANCE_ADMIN`
- `STUDENT`
- `PARENT`

`PLATFORM_ADMIN` exists in the domain discussion as future infrastructure, but there is no complete current platform-admin permission path.

## Identity And Login

Users can register and log in with email/password. The OTP login flow is
implemented for an existing registered phone number, but it cannot complete
outside development: `SmsDriver` has no SMS provider integration, and no
configuration supplies one. See Current Limitations below.

IAM canonicalizes phone numbers at the application boundary before storing or
looking them up:

- boundary whitespace matching Java `Character.isWhitespace` or `Character.isSpaceChar` is stripped;
- Persian and Arabic-Indic digits are translated to ASCII digits;
- punctuation, separators, and country-code formatting are otherwise preserved.

Flyway migrations canonicalize existing `iam.users.phone_number` rows and refuse
to guess ownership when multiple rows would collapse to the same canonical phone
number.

Evidence:

- [`PhoneNumberCanonicalizer.java`](../../src/main/java/com/example/roshdeandishe/iam/internal/PhoneNumberCanonicalizer.java)
- [`SmsDriver.java`](../../src/main/java/com/example/roshdeandishe/iam/internal/SmsDriver.java)
- [`V3__canonicalize_iam_phone_numbers.sql`](../../src/main/resources/db/migration/V3__canonicalize_iam_phone_numbers.sql)
- [`V4__canonicalize_iam_phone_boundary_whitespace.sql`](../../src/main/resources/db/migration/V4__canonicalize_iam_phone_boundary_whitespace.sql)
- [`OtpLoginFlowIntegrationTest.java`](../../src/test/java/com/example/roshdeandishe/OtpLoginFlowIntegrationTest.java)
- [`PhoneNumberCanonicalizationMigrationTest.java`](../../src/test/java/com/example/roshdeandishe/PhoneNumberCanonicalizationMigrationTest.java)

## School Creation And Entitlements

Any authenticated user can create a school through the self-service school creation flow. The creator becomes a `SCHOOL_ADMIN` for that school.

On creation, a school receives free entitlements for:

- `ACADEMIC`
- `FINANCE`
- `ATTENDANCE` (free since 2026-10-05, `openspec/changes/attendance.md` decision 6)

Existing schools are backfilled with `ATTENDANCE` at startup: `school.internal.AttendanceEntitlementBackfill` (runs after `IamSeedDataRunner`, in every profile) asks `IamFacade.ensureSchoolEntitlement` for each school, which creates an active row only if the school has **no** `ATTENDANCE` row. It is idempotent per school (a school created later is picked up on the next start), and an existing row, even one an operator switched off, is never touched.

A school also has a week start (`school.schools.week_start_day`, `SAT` or `MON`; `openspec/changes/eu-market-readiness.md` W9). `POST /api/admin/schools` accepts an optional `weekStart` (`SATURDAY`, the default, or `MONDAY`) and the school response returns it; it is chosen at creation (`/schools/new` has the select) and there is no way to change it afterwards. `SchoolFacade.WeekStart` is the single owner of what it means: `SAT` is the Iranian week (school days Saturday to Wednesday), `MON` is Monday to Friday. Every school created before this existed is `SAT`.

The following modules are catalogued but not granted automatically:

- `CURRICULUM`
- `STUDY_PLANNER`
- `EXAM`
- other future/catalog modules seeded by IAM.

Paid or platform-controlled entitlement changes remain facade-only. There is no public REST workflow for billing or platform operators to grant paid modules.

Acceptance criteria:

- School creation succeeds for an authenticated user.
- The returned school is associated with the creator through an active `SCHOOL_ADMIN` membership.
- ACADEMIC, FINANCE and ATTENDANCE endpoints can be used for the new school when the user has the needed permissions.
- Paid modules are not available unless entitlement is granted through non-REST code paths.

Evidence:

- [`SchoolController.java`](../../src/main/java/com/example/roshdeandishe/school/controller/SchoolController.java)
- [`SchoolFacadeImpl.java`](../../src/main/java/com/example/roshdeandishe/school/internal/SchoolFacadeImpl.java)
- [`IamSeedDataRunner.java`](../../src/main/java/com/example/roshdeandishe/iam/internal/IamSeedDataRunner.java)
- [`2026-07-20-free-tier-entitlements.md`](../decisions/2026-07-20-free-tier-entitlements.md)

## Access Control

Protected school-scoped API endpoints usually require:

- authenticated user;
- `X-School-ID`;
- active school membership;
- module entitlement through `@RequiresModule`;
- permission through `@RequiresPermission`.

Known intentional exceptions:

- public pages and static assets;
- auth/account endpoints;
- `/api/me` and related current-user endpoints (no permission; they return the caller's own data and, for `/api/me/schools/{id}/**` and `/api/me/enrollment`, only for the data of a role the caller holds as an **active** membership in that school: own grades, invoices, enrollment, exams and study intervals need `STUDENT`, linked children's grades and invoices need `PARENT`, the timetable views need `STUDENT`/`PARENT`/`TEACHER` respectively);
- Bale webhook endpoints (`/api/bale/webhook`, served by the `notification` module);
- tokenized exam-taking endpoints (the signed token must be unexpired and its student must still have an active `STUDENT` membership in the exam's school; otherwise `401 invalid-exam-token`).

Failure cases:

- a malformed request body, an invalid or missing parameter, an unsupported method or content type answer `400`/`405`/`415` problem details (`malformed-request-body`, `invalid-parameter`, `missing-parameter`, `method-not-allowed`, `unsupported-media-type`), not `500`;
- missing required tenant header returns an error before controller logic;
- tenant/path mismatch is rejected by controllers that accept both;
- missing module entitlement and missing permission are separate authorization failures.

Evidence:

- [`SecurityConfig.java`](../../src/main/java/com/example/roshdeandishe/iam/config/SecurityConfig.java)
- [`TenantResolverFilter.java`](../../src/main/java/com/example/roshdeandishe/platform/config/TenantResolverFilter.java)
- authorization annotations across module controllers.

## Web UI

The current web UI is server-rendered JTE with page-level Alpine.js components and Bulma styling.

Built page areas include:

- public landing (with login, account-registration, and school-registration entry paths that preserve the approved post-authentication destination), login, registration, account activation, school entry, and exam-take; direct anonymous `/schools/new` entry preserves the same approved destination. The landing page carries a four-slide product slider, a questions section and a multi-column footer; the slider is the one carousel in the app and is confined to this page (`DESIGN.md` §5), rendering all four slides stacked and readable when JavaScript does not run;
- پیشخوان (`/dashboard`, called «پیشخوان» in the UI since 2026-10-05; role-gated «دسترسی سریع» shortcut cards, «وضعیت شهریه» for school admins, «فرزندان من» for parents, memberships, school-entry box for admins, phone box) and shared navbar with a grouped sidebar;
- people, roles, classes, students, and grades;
- curriculum, study plans, and exams;
- finance, expenses, invoices, and payments;
- student/parent self-service pages for grades, invoices, exams, and study plan.

Current invariants:

- `layout/main.jte` owns shared head assets.
- `layout/navbar.jte` is reused by authenticated management pages: a top bar
  (school/role switcher, user menu) plus a grouped, collapsible sidebar (a
  drawer below 1024px). Page links come from `web.model.NavCatalog`, which also
  drives the dashboard shortcut cards (system roles by built-in role; a custom role also sees the sidebar link and the shortcut card of every page whose permissions it holds, and its title, not its code, appears in the role switcher); see
  [`changes/navigation-sidebar-and-dashboard-shortcuts.md`](../changes/navigation-sidebar-and-dashboard-shortcuts.md).
- `layout/page-header.jte`, `layout/school-scope.jte`, and
  `layout/empty-state.jte` are shared partials extracted on 2026-09-27;
  `school-scope.jte` owns the "no school selected" warning plus the error and
  success notice areas for every school-scoped page.
- Static JS and CSS are served from Spring Boot static resources, and the
  Vazirmatn web font is a vendored static asset under `static/fonts/`
  (no WebJar exists for it).
- The web UI uses the Vazirmatn font and the Persian-turquoise primary color
  from `app.css`, and owns its own dark colour scheme. This replaced the
  system font stack and the ink-blue primary on 2026-09-27; `DESIGN.md` is
  authoritative for the visual layer.
- The document language and direction follow the request: `LocaleResolverFilter`
  resolves `Accept-Language` against `fa` (the default) and `en` into
  `LocaleContext`, and `layout/main.jte` writes `<html lang dir>` from it
  (`app.css` has an English font override). Page text is still Persian in both;
  externalizing it (W1) is not done.
- Pages go through `window.Calendar` (`static/js/calendar.js`), not `Jalali` or
  `toLocaleDateString('fa-IR')`: `<html lang>` selects Jalali (`fa`) or Gregorian
  with `Intl` names (anything else), and `Calendar.digits()` formats displayed
  digits. `Calendar` also reads the selected school's week start from the
  navbar's `data-week-starts` (`weekStart()`, `weekStartOf`, `weekdayIndex`,
  `weekdayNamesFor`, `schoolDayCodes`); the study-plan, exam and timetable pages
  follow it and re-read it on `school-changed`. The academic-year code
  (`1405-1406`) is still Jalali-derived on the server (W8, undecided).
- Study plans and exams anchor their weeks on the school's week start: the server
  rejects a `weekStart` that is not that weekday with `invalid-week-start`
  (`SchoolFacade.WeekStart.firstDay()`), and the schedule's `Weekday` keys are
  real days, so the suggester walks the school's week order.
- Read-only finance amounts use neutral Persian digits and thousands grouping,
  without a Rial/Toman suffix or numeric conversion because the canonical
  financial unit is unresolved.
- Finance amount inputs normalize Persian and Arabic-Indic digits to ASCII
  during editing and again before request serialization.
- Page links are navigation affordances only; API authorization is authoritative.

Evidence:

- [`src/main/jte/`](../../src/main/jte/)
- [`src/main/resources/static/js/`](../../src/main/resources/static/js/)
- [`src/main/resources/static/css/app.css`](../../src/main/resources/static/css/app.css)
- [`src/main/resources/static/js/money.js`](../../src/main/resources/static/js/money.js)
- [`FRONTEND.md`](../../FRONTEND.md)

## Android V1

The native Android app exists as a separate Gradle/Kotlin/Compose project. Current behavior is a thin read-only student shell:

- authenticate with backend JWT token endpoint;
- persist auth state locally;
- read current user profile;
- list memberships;
- load current student enrollment for the first active student membership.

Current limitations:

- no write workflows;
- no push notification provider;
- no refresh-token flow;
- base URL is currently hardcoded for emulator/local development;
- richer native grades, homework, exams, messaging, and video workflows remain future work.

Evidence:

- [`android/README.md`](../../android/README.md)
- [`android/app/build.gradle.kts`](../../android/app/build.gradle.kts)
- Android source files under `android/app/src/main/java/`.

## Finance And Academic

Academic currently owns classes, students, guardians, grades, the global course catalog, and the class timetable. Finance owns tuition fees, invoices, payments, school expenses, expense attachments, and finance audit behavior.

Grades can be recorded per course (`openspec/changes/grades-by-course.md`): `academic.courses` is a global, per-grade-level catalog (no `school_id`), a grade's `course_id` is optional, and a grade without a course is the overall grade («نمرهٔ کل»; all grades recorded before this feature). Per (student, class, year, term) there is at most one overall grade and one grade per course (two partial unique indexes, migration `V5`). A course must exist and match the class's `grade_level`. `GET /api/admin/schools/{schoolId}/grades` without `courseId` returns only overall grades; `GET .../grades/courses?classId=` lists the class's grade-level courses. `/grades` has a «درس» select (default «نمرهٔ کل»), `/my-grades` a «درس» column. The catalog is read-only in the application: no endpoint or screen creates or edits courses, and none are seeded; rows are managed by the platform operator through data migrations or SQL until a platform actor exists.

Academic years (`openspec/changes/academic-year.md`, phase 0): `school.academic_years` is a global list (code such as `1404-1405`, no dates) that the application creates itself: configuration (`roshdeandishe.academic-year.start-month`/`start-day`, default 1 Mordad, `timezone` default `Asia/Tehran`) sets the Solar date the academic year starts, and from that date each Solar year `Y-(Y+1)` is created at startup and nightly (idempotent, gap-filling); the migration seeds only `1404-1405`. Each school has a current academic year chosen by its admin from the list (`school.schools.current_academic_year_id`, null until chosen). `GET /api/me/schools/{id}/academic-year` returns only the current year to any active member; `GET/PUT /api/admin/schools/{id}/academic-year` need the `ACADEMIC_YEAR_MANAGE` permission (granted to `SCHOOL_ADMIN`) and give the whole list and the choice. `/academic-year` is the admin page; `/grades` prefills its year field from the current year (still editable). Phase 1 (`openspec/changes/academic-year.md`, migration `V9`): the year on a grade is canonicalized (Persian/Arabic digits to ASCII) and must be a listed year (`400 invalid-academic-year`); a startup audit logs a warning for existing grade rows whose year matches no listed year and changes nothing. `academic.courses` has an optional validity range (`valid_from_year`/`valid_until_year`, the start year of an academic year, inclusive, null = always, set by data migrations): the grade-book and timetable course pickers take an optional `academicYear` and hide courses outside their range, and recording a grade or placing a timetable cell for a course outside its range is `400 course-not-valid-for-year`. Whoever holds `ACADEMIC_YEAR_MANAGE` sees and writes any year; everyone else only the school's current year (`403 academic-year-not-allowed` on the grade-book endpoints, `/api/me/schools/{id}/grades` returns only the current year); a school without a current year is not restricted (the «سال تحصیلی» page warns the admin that the first choice starts the restriction). The navbar and the dashboard show the selected school's current year.

Phase 2 (`openspec/changes/academic-year.md`, migration `V10`): `finance.tuition_fees` and `finance.invoices` carry a nullable `academic_year` (canonical code; NULL = not attributed to a year, for every pre-existing row and for a school with no current year, which behaves exactly as before). Tuition is one amount per (school, year); a new year has none (`404 tuition-fee-not-set`); invoices are unique per (school, student, year). The finance endpoints take an optional `academicYear` (default: the current year; a school without one: year-less; an unlisted year is `400 invalid-academic-year`); enrolling a student issues the current year's invoice when that year has tuition; `POST /api/admin/schools/{id}/invoices/backfill` issues the chosen year's invoices to current students who lack one (refused with `409 unattributed-invoices-exist` while year-less invoices exist and a year is given); payments can be recorded on an invoice of any year; `POST .../finance/attribute-year` attributes the year-less tuition and invoices to a year in one transaction (`409 attribution-conflict` on a duplicate). Finance admins see any year; `/api/me/schools/{id}/invoices`, the dashboard's per-child counts and the Bale bot show the current year's invoices, unattributed ones, and an earlier year's invoice only while it still has a balance.

Phase 3 (`openspec/changes/academic-year.md`, migration `V11`): `academic.classes.academic_year` (canonical code, NULL = not attributed) and `academic.students.status` (`ACTIVE`, `PENDING` = promoted but not yet placed, `GRADUATED`, `LEFT`; `class_id` is null outside `ACTIVE`) with `students.class_id` staying the "current class" that grades, the dashboard and the Bale bot read; `academic.enrollments` keeps one row per (student, year) (`ENROLLED`/`PENDING`, class, grade level) and is written only in a "tracked" school (it has a current year and every active student has a row). A class is created in the current year (or a given listed year); `GET /api/admin/schools/{id}/classes?academicYear=` defaults to the current year plus unattributed classes, `all` is for `ACADEMIC_YEAR_MANAGE` holders only. A grade is accepted when the class is the student's current class or the student has an enrollment in that class for the grade's year (`409 student-not-in-class` otherwise); the grade's year is not required to equal the class's own year (grades are classroom-level, the Ministry's system holds the official ones). `POST .../academic-year/attribution` explicitly attributes existing classes and active students to a listed year not after the current one (`409 year-not-chosen`). Promotion (`/api/admin/schools/{id}/promotion/**`, `ACADEMIC_YEAR_MANAGE`, page `/promotion`) always targets the current year: the default rule is the next grade level, the last grade of the school's single level graduates, per-student outcomes are promote, repeat, graduate or leave (the leaver's `STUDENT` membership is deactivated, nothing is deleted); promoted students are `PENDING` with a target grade level until placed in a class of that grade and year; a run is recorded and can be undone while it is the latest and nobody of it was placed. With the school setting `auto_promotion` on (default off, `PUT .../academic-year/auto-promotion`), changing the current year to a later year publishes `school.CurrentAcademicYearChangedEvent` and the academic module runs the default rule after the commit. "Issue the year's invoices" in a tracked school goes to students enrolled in that year; `GET /api/me/enrollment` is `204` for a student without a class.

Class timetable (`openspec/changes/class-timetable.md`): the school admin defines, per school and academic year, the periods (`academic.timetable_periods`, no overlaps, a period with entries cannot be deleted) and fills a grid of the school's school days per class (Saturday–Wednesday for a `SAT` school, Monday–Friday for a `MON` school; `setEntry` rejects any other day with `400 invalid-timetable-entry`; V8 widened the weekday check with `THU`/`FRI`) (`academic.timetable_entries`: exactly one of a catalog course of the class's grade level or an activity, plus an optional teacher who must be an active `TEACHER` of the school and cannot be in two classes in the same weekday and period, `409 teacher-double-booked`, also enforced by a partial unique index). Activities (`academic.activities`) are only ever timetabled, never graded: a school owns and manages its own, and platform defaults (`school_id` null, read-only in the app, none shipped yet) are visible to all. A catalog course with `graded = false` can be timetabled but is hidden from `/grades` and rejected for grading (`400 course-not-gradable`), and grades already recorded for it are hidden from `/api/me/.../grades` (kept in the database). Admin endpoints under `/api/admin/schools/{id}/timetable/**` need the ACADEMIC module and the new `TIMETABLE_MANAGE` permission (granted to `SCHOOL_ADMIN`); `GET /api/me/schools/{id}/timetable` (no permission, own data only) returns the school's current year for the user's own class (active `STUDENT` membership), linked children's classes (active `PARENT`) and own teaching slots (active `TEACHER`). `/dashboard` shows each active `SCHOOL_ADMIN` a «وضعیت شهریه» box for the selected school: the share of the current year's invoiced amount that is paid (an integer percent rounded down, plus a progress bar; a school with no invoices says none were issued; a ratio, never an amount, because the monetary unit is undecided); only the literal `SCHOOL_ADMIN` role gets it, not finance admins or custom roles. There is no absence («غیبت») statistic on the dashboard yet (the attendance backend exists, its dashboard box is not built). Pages: `/timetable` (admin) and `/my-timetable`; the student dashboard shows «برنامهٔ امروز». Copying a timetable to the next year, substitutions and rooms are not implemented.

School calendar (`openspec/changes/attendance.md`, phase 0, migration `V12`): `school.calendar_entries` holds per-school dated entries of kind `CLOSED` (a closed day) or `EVENT` (reserved for a future yearly plan; accepted but used by nothing yet); a day may carry several entries and a title is non-blank, at most 128 characters. `SchoolFacade` exposes `listCalendar(schoolId, from, to)` (closed range), `addCalendarEntry`, `removeCalendarEntry` (an entry of another school is `404 calendar-entry-not-found`) and `isClosedDay(schoolId, day)` (true if any `CLOSED` entry is on that day); invalid input is `400 invalid-calendar-entry`. `POST/GET /api/admin/schools/{id}/calendar` and `DELETE .../calendar/{entryId}` need the new `CALENDAR_MANAGE` permission (granted to `SCHOOL_ADMIN`) and no `@RequiresModule`, like the academic-year endpoints (the school module has no entitlement code); `GET /api/me/schools/{id}/calendar?from=&to=` is open to any active member of the school. Dates are ISO Gregorian on the wire. No page and no national holidays are shipped; attendance is the only consumer (`isClosedDay`, below).

Attendance backend (`openspec/changes/attendance.md`, phases 1 and 2, migration `V13`, new module `attendance`, schema `attendance`): the unit is a class on a school day. `attendance.rolls` (unique `(class_id, day)`) means "the roll was taken"; a class without a roll is «ثبت نشده», not "nobody was absent". `attendance.marks` (unique `(class_id, day, student_id)`) stores only the exceptions, `ABSENT` or `LATE`, with an `excused` flag (kept only for `ABSENT`) and a note of at most 255 characters. `attendance.excuses` (created by `V13`, used by phase 3, below). Classes and students are opaque ids (no cross-module keys). Endpoints under `/api/admin/schools/{id}/attendance` (ISO dates, `@RequiresModule("ATTENDANCE")` on the controller): `GET /classes?day=` (the classes the caller may record for: a teacher's own, or all classes of the current year with `ATTENDANCE_RECORD_ANY`), `GET|PUT /classes/{classId}/days/{day}` (roster with marks and whether the roll was taken; `PUT` replaces the class-day in one transaction under an advisory lock: old marks deleted, new written, roll upserted; body `{"marks":[{studentId,status,excused,note}]}`, an empty list means everyone present, a missing list is `400`), and, with `ATTENDANCE_VIEW_ALL`, `GET /days/{day}/absentees`, `GET /days/{day}/summary` and `GET /report?from=&to=&classId=&name=`. Rules: the day must be a school day (a weekday of the school's `SAT`/`MON` pattern that `SchoolFacade.isClosedDay` does not close) and not in the future (`400 invalid-attendance-day`; "today" is taken in the school's own timezone); the class must belong to the school and to its current academic year or be unattributed (`404 class-not-found`, `409 class-not-in-current-year`; a school with no current year is not restricted); each marked student must be on the class roster, or already marked on that class-day, so a re-save stays idempotent (`409 student-not-in-class`); a bad status, a duplicate student, a note over 255 characters or a missing `marks` is `400 invalid-attendance-mark`. The roster is the students currently in the class plus those with an enrollment row for it (any status), plus any student who already has a mark that day; enrollments are per year, so "enrolled that day" is not tracked finer than that. Permissions (all seeded per code): `ATTENDANCE_RECORD` (`TEACHER`) records only for a class whose `teacher_id` is the caller or where the caller is the teacher of a timetable cell (`403 attendance-not-your-class`), `ATTENDANCE_RECORD_ANY` (`SCHOOL_ADMIN`) for any class, `ATTENDANCE_EDIT_PAST` (`SCHOOL_ADMIN` only) is needed to save any day before today (`403 attendance-past-edit-not-allowed`), `ATTENDANCE_VIEW_ALL` (`SCHOOL_ADMIN`) gates the school-wide views. Because `@RequiresPermission` cannot express "either of two permissions", the three record endpoints carry only the class-level module annotation and check `ATTENDANCE_RECORD_ANY` or `ATTENDANCE_RECORD` in the controller through `IamFacade.checkPermission` (`403 permission-denied` if neither). The summary ("the admin figure") covers only classes whose roll was taken that day: `absent` (excused included, also counted as `excusedAbsent`), `expected` (roster of those classes), `presentPercent = floor(100 × (expected − absent) / expected)` with `LATE` counted present (`null` when `expected` is 0, no roll was taken, or the day is not a school day), `classesTaken`/`classesTotal`. The absentees list covers every class of the current year (plus any class that has a roll that day) and is empty on a non-school day. The report has one row per (student, class) for classes with at least one roll in the range: `absentDays`, `lateDays`, `presentDays = days with a roll for that class − absentDays`; the name filter is a case-insensitive "contains" with no Persian normalization (no helper exists); the range is closed and at most 366 days (`400 invalid-attendance-range`). Not built yet: the `/attendance` pages and the dashboard box.

Attendance phase 3 backend (`openspec/changes/attendance.md`; no new migration, `attendance.excuses` already existed): **excuses.** A parent submits `POST /api/me/schools/{id}/attendance/excuses` with `{studentId, day, note}`; the caller must have an active `PARENT` membership **and** a guardian link to that student (`AcademicFacade.listWards`), else `403 attendance-not-your-child`; the student must have an `ABSENT` mark that day (`LATE` does not count), else `409 no-absence-to-excuse`; the note is non-blank and at most 500 characters (`400 invalid-excuse`, trimmed before saving). There is one excuse per (student, day), status `PENDING`: submitting again while `PENDING` replaces the note (same row), submitting after `APPROVED`/`REJECTED` is `409 excuse-already-decided`. The `/api/me` attendance endpoints have no `TenantContext`, so the `ATTENDANCE` entitlement is checked in the controller with `IamFacade.checkAccess` on the path's school (`403 module-not-entitled`). Admin, under `/api/admin/schools/{id}/attendance` (class-level `@RequiresModule("ATTENDANCE")`, `TenantContext.requireMatches`): `GET /excuses?status=&from=&to=` (all optional, newest day first; `ATTENDANCE_VIEW_ALL`) returns id, student id and name, class id and name, day, note, status, submitted/decided instants; `POST /excuses/{excuseId}/decision` with `{"decision":"APPROVED"|"REJECTED"}` (`ATTENDANCE_RECORD_ANY`) sets status, `decided_by_user_id` and `decided_at` and, when approved, sets the mark's `excused = true` in the same transaction (taken under the same advisory lock as `saveRoll`); rejecting leaves the mark as it is. Deciding a decided excuse is `409 excuse-already-decided`, an unknown (or other school's) id `404 excuse-not-found`, a decision other than `APPROVED`/`REJECTED` `400 invalid-excuse`. Re-saving a roll (which replaces the class-day marks) reconciles excuses in the same transaction: a `PENDING` excuse of a student who is no longer `ABSENT` is deleted, a decided one stays as history but is ignored by every view, and a student who stays `ABSENT` with an `APPROVED` excuse keeps `excused = true` even if the incoming mark says `false` (the approved excuse wins over the incoming flag). **Self-service read.** `GET /api/me/schools/{id}/attendance?from=&to=` (no permission, own data only; the range is required and at most 366 days, `400 invalid-attendance-range`) returns `{"students":[{studentId, studentName, own, days:[{day, status, excused, excuse}]}]}` for the caller's own student record (active `STUDENT`, `own = true`) and linked children (active `PARENT`); only marks are listed, `excuse` is `null` or `{id, status, note}` and appears only on `ABSENT` days; a user with neither role gets an empty list and a parent never sees an unrelated student. **Parent notification.** `saveRoll` publishes `attendance.AbsenceRecordedEvent(schoolId, classId, studentId, day, actorUserId, occurredAt)` for each student **newly** `ABSENT` in that save (not for an unchanged re-save, not for `LATE`; a student whose absence was removed and added back in a later save is new again, a `LATE` student changed to `ABSENT` is new), one event per student, after the marks are written inside the same transaction. The `notification` module's `@ApplicationModuleListener` (after commit, `TenantContext` set at the top and cleared in `finally`) lists the student's linked guardians (`AcademicFacade.listGuardians`) and sends each one a Persian message through `NotificationFacade.sendMessageToUser`, which returns `false` without error for a parent with no linked Bale account, so such parents are skipped silently; the text is «غیبت: {student full name} در مدرسه‌ی {school name} در تاریخ {Jalali date, e.g. ۱۴ مهر ۱۴۰۵} غایب ثبت شد.» (the date comes from `SchoolFacade.formatSolarDate`, Persian digits and a month name). Every send failure is caught and logged, so a Bale outage never fails or rolls back the roll save, and one failing parent does not stop the others; there is no opt-out. The listener does not filter guardians by an active `PARENT` membership (same as the invoice notification). Not built yet: the pages, the dashboard box, and an excuse badge on any page.

Cross-module references are intentionally opaque IDs at module boundaries. When a UI needs names for IDs from another module, the established web pattern is client-side composition through multiple REST calls, unless a backend facade already owns the composition.

Evidence:

- `academic` and `finance` package controllers and facades.
- `Finance*FlowIntegrationTest`, `Expense*FlowIntegrationTest`, `Grade*FlowIntegrationTest`, and related tests.

## Current Limitations

These are not bugs unless a change proposal says otherwise:

- the course catalog (see Finance And Academic) has no administration screen or API and ships empty, because the platform-actor permission model it depends on does not exist yet and the list of courses per grade level is a pending product input;
- no platform-admin actor model or platform permission path — a
  `PLATFORM_ADMIN` role row is seeded, but it is reserved at both boundaries:
  `listRoles` keeps the code out of role pickers, and `createMembership`
  rejects it with the same `RoleNotFound` an undefined code produces, so it
  cannot be assigned. The role also has no permission grants, and
  `PermissionDecisionService` only decides school contexts, so it would confer
  nothing even if a row existed. Whether such rows already exist in the
  deployed database is unverified — see
  [`changes/platform-admin-not-assignable.md`](../changes/platform-admin-not-assignable.md);
- no billing/upgrade UI or REST endpoint for paid entitlements;
- no horizontal web-session scaling support;
- no SMS provider integration exists in `SmsDriver`, so OTP delivery cannot be
  enabled by configuration — a nonblank `roshdeandishe.sms.api-key` still
  reports failure, and `requestOtp` then fails closed;
- OTP rate limiting is not implemented;
- the canonical persisted, entry, and display unit for finance values, plus
  any Rial/Toman conversion and rounding rules, is not defined;
- slug editing, custom domains, and subdomain routing are not implemented;
- school status changes are facade-only;
- native Android app is read-only and local-development oriented.

Attendance and calendar pages: `/calendar` (admin, `CALENDAR_MANAGE`: add and remove closed days by Jalali date), `/attendance` (teachers record their own classes, admins any class: present by default, absent with an optional «موجه» flag and note, late; past days need `ATTENDANCE_EDIT_PAST`) and `/attendance/report` (admin, `ATTENDANCE_VIEW_ALL`: the day's absentees per class with the present-percent figure, and a period report by date range, class and student name). The admin پیشخوان shows a «حضور و غیاب امروز» box for the literal `SCHOOL_ADMIN` of an entitled school. `NavCatalog` links can require any one of several permission sets (`orPermissions`). `/my-attendance` («غیبت‌های من», STUDENT and PARENT): a Jalali date range (default: this month to today) listing the student's own and a parent's children's absences and lateness with excuse state; a parent sends or edits a note on an absence until an admin decides it. `/attendance/report` has a third tab «عذرخواهی‌ها» where an admin filters excuses by status and date and approves or rejects pending ones (approval marks the absence excused). The parent «فرزندان من» card has a «غیبت‌ها» link to `/my-attendance`.
