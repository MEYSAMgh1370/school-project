# Roshdeandishe — Moshavere Port Spec

**Historical port proposal.** This document started as a draft for porting
features from a separate, older codebase family ("moshavere":
`agency-panel-master`, `exam-maker-master`, plus two React frontends, all under
`/home/meysam/Downloads/moshavere/`) into this project. The major scoped pieces
below are now largely implemented and marked `DONE` in their sections. Treat
pre-build wording and old "Open" bullets as historical context unless OpenSpec,
source code, or tests confirm they are still current.

Current behavior belongs in [`openspec/specs/current-system.md`](openspec/specs/current-system.md).
Remaining future work belongs in [`openspec/changes/proposed-work.md`](openspec/changes/proposed-work.md).
Before adding a new module or substantially changing one of these areas, create
a focused OpenSpec change proposal and use the `new-module` skill when it
applies.

## 1. Why

Moshavere is an older, separate Django + React project family (an Iranian
exam-prep/tutoring platform, "moshaverbaman.ir") with several mature features
Roshdeandishe doesn't have yet. It is a different stack (Python/Django REST +
React) with no shared runtime — this is a **logic/schema port**, not a network
integration. Four pieces are being ported:

1. Phone/OTP as a second login factor — extends `iam`.
2. A hierarchical topic taxonomy + multiple-choice question bank — new
   `curriculum` module.
3. An exam engine (auto-generated weekly exams + instructor-authored
   collective exams, timed exam-taking sessions, scoring) — new `exam` module.
4. Per-school study plans with course-weighting, plus an auto-scheduling
   algorithm that allocates a student's free time into study blocks — new
   `studyplan` module.

## 2. Already-decided scope (confirmed 2026-07-11)

- **OTP does not weaken the existing phone-number security model.**
  `CLAUDE.md` §4 (`UserEntity.phoneNumber`) deliberately restricts *setting* a
  phone number to an authenticated session (`PUT /api/me/phone`), to prevent
  account takeover by phone-guessing. That restriction is **unchanged**. OTP
  is purely a second *login* factor for a user who already has a phone number
  on file — never a path to unauthenticated account creation. (Moshavere's
  `exam-maker-master/account` used phone+OTP for brand-new self-registration
  too; that part is explicitly **not** being ported.)
- **Curriculum content (topics, questions, plans) is per-school, not a shared
  platform library.** Each school's own teachers own their own topics/
  questions/plans, consistent with how `academic` and `finance` are already
  school-scoped. (Moshavere's `Grade`/`Plan` models were shared across
  agencies; that cross-tenant sharing is **not** being ported.)

## 3. `iam` extension — Phone/OTP login

**Status: DONE (2026-07-12)**, built as scoped below with one deviation:
`SmsDriver` shipped as a **concrete class** (`iam.internal.SmsDriver`), not
the interface sketched below — this codebase's actual convention for a
best-effort external side-channel is one concrete class that logs-only when
unconfigured (`BaleClient`, `InviteEmailSender`), not a speculative interface
for a single implementation (CLAUDE.md §10). Everything else — the entity
shape, the always-insert-a-new-row semantics, the unauthenticated
`/api/auth/otp/*` endpoints, the `IamError` cases, and the "OTP never opens a
registration path" boundary from §2 below — shipped as written. See
`CLAUDE.md` §4's `VerificationCode` subsection and §7 Done for the final
shape and test coverage. OTP rate-limiting remains the flagged, conscious
skip noted below — still not built.

Source: `exam-maker-master/account/{models.py (VerificationCode), sms.py,
serializers.py (VerificationCodeLoginSerializer, ActivatePhoneNumberSerializer),
views.py (SMSViewSet)}`.

- New entity `iam.internal.VerificationCodeEntity`: `userId` (FK), `code`,
  `createdAt`, `expiresAt`, `consumedAt` (nullable) — same shape as the
  existing `InviteTokenEntity` pattern (one-time-use, TTL, nullable
  consumed-marker), not moshavere's OneToOne version (a user may request a
  new code before an old one expires; moshavere's OneToOne shape doesn't
  actually allow that cleanly).
- New pluggable `SmsDriver` interface (`iam.internal` or `iam.shared`,
  whichever the new-module pass decides) with a dev/no-op implementation that
  only logs — same convention as `bale.internal.BaleClient`'s
  empty-token-means-log-only default, for consistency. Real provider is
  deployment config (`roshdeandishe.sms.*`), not missing code.
- `IamFacade` additions: `requestOtp(phoneNumber)` (looks up the user by
  their already-registered phone, generates+sends a code — 404s if no user
  has that phone, since there's nothing to authenticate into) and
  `verifyOtp(phoneNumber, code)` (validates, marks consumed, returns the
  same authenticated-session/JWT result path `AuthController.login` already
  produces).
- New endpoints, alongside the existing `/api/auth/*` set:
  `POST /api/auth/otp/request`, `POST /api/auth/otp/verify` — same
  unauthenticated trust model as `/api/auth/login` (the phone+code pair *is*
  the credential, same reasoning already applied to `/api/auth/activate`).
- New `IamError` cases: `PhoneNotRegistered`, `OtpIncorrect`, `OtpExpired`.
- **Open for the new-module pass:** rate-limiting repeated OTP requests
  (moshavere had none either — flagging so it's a conscious skip, not an
  oversight).

## 4. `curriculum` (new module) — Topics + question bank

**Status: DONE (2026-07-16)**, built as scoped below via its own `new-module`
pass — see `CLAUDE.md` §3/§4 (Topic + Question subsection) and §7 Done for
the final shape and test coverage (`CurriculumFlowIntegrationTest`). The two
open items below were resolved at new-module time: a **new `SCHOOL`-scoped
`CURRICULUM` module code** (none of the seeded codes fit; `EXAM_PREP` is
`USER`-scoped/B2C while this is instructor-authored B2B content — it stays
untouched as the B2C placeholder, its scope question belonging to the `exam`
module's pass, §5), and the permission split —
**`CURRICULUM_MANAGE`/`CURRICULUM_VIEW`, both `SCHOOL_ADMIN` + `TEACHER`**
(authoring the bank is day-to-day teacher work, the `STUDENT_ENROLL`
reasoning; teachers own their own content, §2), neither granted to
`STUDENT`/`PARENT` (questions carry `correctChoice` + the answer explanation;
student access will be mediated by `exam`'s scoped token, never raw bank
reads). Two deviations from the field list below, both toward the actual
source: the `answer` explanation field (present in `models.py`, missing from
this spec's summary) was ported, and `difficulty` became a 5-level enum
(`EDUCATIONAL|EASY|MEDIUM|HARD|VERY_HARD`, port of the source's 0/10/20/30/40
int scale). `topicId` on a question is required (the source's nullable topic
was unreachable through its own required topic filter — a wart, not ported).
The subtree semantics of the topic filter (`descendants()`) shipped as
written, as an in-memory traversal. (Two parallel implementations of this
module were built the same day; the one without subtree semantics and without
`answer` was superseded at merge time — `CLAUDE.md` §7 Done, merge note.)

Source: `exam-maker-master/exam_generator/{models.py, tree.py, filters.py,
views.py}`.

- `TopicEntity`: `schoolId`, `parentId` (nullable, self-FK), `name`, `order`.
  **Deliberately a plain adjacency list, not moshavere's dual
  representation** (Postgres `django_ltree` table *plus* a separately
  maintained pickled `treelib` tree loaded as an in-memory singleton cache).
  That split was flagged in the original review as a wart, not a pattern
  worth preserving — a straightforward recursive query (or, if read
  performance actually demands it later, a materialized closure table) is
  simpler and has one source of truth.
- `QuestionEntity`: `schoolId`, `topicId`, `text`, four choice fields,
  `correctChoice`, `difficulty`, `discriminationIndex`.
- `CurriculumFacade`: `createTopic`, `listTopics(schoolId)`,
  `getTopicTree(schoolId)` (root + children + ancestor path, replacing
  moshavere's pickled-tree read path), `createQuestion`,
  `listQuestions(schoolId, filter)` (topic/difficulty filter, replacing
  moshavere's `django-filter`-based `QuestionFilter`).
- REST: `TopicController`, `QuestionController` — instructor/`SCHOOL_ADMIN`
  write, broader read (exact permission split TBD at new-module time).
- **Open:** which existing seed `Module` code this maps to, if any — none of
  the currently-seeded codes (`ACADEMIC`, `FINANCE`, `LIBRARY`,
  `ATTENDANCE`, `TRANSPORTATION`, ...) fit a question bank; likely needs a
  new `SCHOOL`-scoped code (e.g. `CURRICULUM`), decided at new-module time.

## 5. `exam` (new module) — Exam engine

**Status: DONE (2026-07-17)**, built via its own `new-module` pass as scoped
below — the fourth and last step of this port, the deepest dependency chain
(`curriculum` + `academic` + `studyplan` + `iam`), and the only place this
port introduces a new-module-depends-on-new-module edge. See `CLAUDE.md` §3/§4
(Exam/Section/SectionQuestion/ExamAssignment/Attempt/Submit subsection) and
§7 Done for the final shape and test coverage (`ExamFlowIntegrationTest`).
Both open items below were resolved at new-module time: a **new
`SCHOOL`-scoped `EXAM` module code** (not `EXAM_PREP` — that stays the
untouched `USER`-scoped B2C placeholder; the actual moshavere feature is
instructor-authored, per-school content, a B2B shape), and the permission
split — **`EXAM_MANAGE`/`EXAM_VIEW`, both `SCHOOL_ADMIN` + `TEACHER`** (same
`CURRICULUM_*`/`STUDYPLAN_*` reasoning — authoring/assigning is day-to-day
teacher work), neither granted to `STUDENT`/`PARENT` — student access is
mediated entirely by the "my" pattern (`MyExamsController`, no
`@RequiresPermission`) plus the scoped exam-take token
(`ExamTakeController`), never by the two RBAC permissions.

Source: `exam-maker-master/azmoon/{models.py, token.py, tasks.py, views.py}`.

Depends on: `curriculum` (topics/questions), `academic` (students/classes),
`iam`, and `studyplan` (`StudyPlanFacade.getRecentTopics`, for weekly
auto-generation).

Four deliberate deviations from the shape sketched below, all documented at
the point they're implemented:

- **Scoring**: on-demand in `ExamFacade` (`ExamFacadeImpl.computeResult`), as
  recommended — no materialized view, no Celery-equivalent scheduled job.
  **`rank`/`balance` (cross-student standard deviation) were not ported at
  all** — they're artifacts of moshavere's materialized view, not core exam
  data; per-student `score`/`correctCount`/`incorrectCount`/`unansweredCount`
  are computed and returned, `rank`/`balance` would need a real cross-student
  aggregate query and are deferred until a UI actually asks for them (none
  exists yet — this module is backend-only, same as `curriculum`/`studyplan`).
- **`startAttempt` idempotency**: unlike the source (which raised a
  validation error on any second `POST` for the same exam, "you already
  obtained token for this exam"), a second call while the existing `Attempt`
  hasn't expired yet returns the same still-valid token instead of erroring —
  a small, deliberate UX improvement (a client retry or page refresh
  shouldn't be a hard failure). The **one-shot invariant itself is
  unchanged**: `unique(examId, studentId)` on `AttemptEntity` means there is
  still only ever one `Attempt` per student per exam, ever; once it expires,
  `startAttempt` throws `ExamError.AttemptExpired` — no second chance, same
  as the source's own comment ("we only support single attempt exams").
  Note the actual precedence at call time: the "has the exam itself ended"
  check runs *before* the per-attempt expiry check, so once `now` is past
  `exam.endAt` the caller sees `CannotAttempt`, not `AttemptExpired` — both
  are real, reachable errors, just gated by which condition is true first.
- **Weekly-exam regeneration guard**: the source's `auto-update` action
  unconditionally deleted and rebuilt a student's `WeeklyExam` — including
  wiping any `Attempt`/`Submit` rows already recorded against it via cascade.
  `generateWeeklyExam` ports the delete-and-rebuild behavior but refuses it
  outright (`ExamError.InvalidExam`) once an `Attempt` already exists for
  that exam — silently destroying a student's already-taken exam is a real
  data-integrity concern, not just a style preference, the same class of
  small correctness improvement already applied to `studyplan.copyWeek`.
- **No "start must be ≥ now" validation** on `createCollectiveExam` (the
  source's `validate_start`) — a UX-layer constraint from the source's own
  admin panel, not a domain invariant; skipping it keeps exam creation simple
  for tests/seed callers exactly like `studyplan`/`curriculum` already do for
  their own no-past-of-mind checks.

- Entities: flat `ExamEntity` with an `examType` enum (`WEEKLY` |
  `COLLECTIVE`) instead of moshavere's Django model-inheritance chain (`Exam`
  → `PersonalExam` → `WeeklyExam` / `CollectiveExam`) — matches this
  codebase's existing preference for a flat entity + explicit enum over
  inheritance (`ModuleScope`, `IamError`, etc.). The source's single
  polymorphic `Exam.author` field (student for `WeeklyExam`, instructor for
  `CollectiveExam`) is split into two separate nullable fields instead —
  `studentId` (opaque → `academic` Student, `WEEKLY` only) and `teacherId`
  (opaque → `iam` User, `COLLECTIVE` only, captured from the current
  authenticated caller like `PlanEntity.teacherId`) — clearer than one
  overloaded column. `SectionEntity` (`examId`, `topicId`, nullable `name`,
  `unique(examId, topicId)`), `SectionQuestionEntity` (join: `sectionId`,
  `questionId`, `unique(sectionId, questionId)`), `ExamAssignmentEntity` (a
  real join table — `examId`, `studentId` — replacing the source's
  `CollectiveExam.students` `ManyToMany`, same join-table-over-ORM-relation
  preference as `RolePermissionEntity`/`WeekCourseTopicEntity`),
  `AttemptEntity` (`examId`, `studentId`, `startedAt`, `expireAt` —
  `unique(examId, studentId)`, the one-shot invariant; `expireAt` is
  persisted, not a `@Transient` property like the source's `Attempt.jwt_token`,
  since the token itself is regenerated fresh on every call rather than
  stored), `SubmitEntity` (`sectionQuestionId`, `studentId`, `choice`
  constrained 1–4, `unique(sectionQuestionId, studentId)`).
- **Exam-taking session token** (`exam.internal.ExamTokenService`): a second,
  independently-secreted JWT (`roshdeandishe.exam.token-secret`, its own HMAC
  key), issued per `Attempt`, distinct from both the session cookie and the
  mobile bearer JWT (`roshdeandishe.jwt.secret`) — a faithful port of
  `azmoon/token.py`'s scoped, short-lived token, except expiry is the JWT's
  own native `exp` claim (not a hand-rolled comparison) so an expired token
  fails to decode automatically. Lives in `exam.internal` — every
  `ExamFacade` method that deals with the exam-take flow accepts the raw
  token string and decodes/validates it internally, so `ExamTakeController`
  (module root) never touches the token codec directly.
- **Weekly-exam auto-generation**: `ExamFacade.generateWeeklyExam(schoolId,
  studentId, weekStart)` calls `StudyPlanFacade.getRecentTopics(studentId)`
  directly (a plain synchronous facade call, this codebase's stated default
  — events are for one-way reactions, not this) and, for each recent topic,
  builds one `Section` with up to 5 randomly-picked questions per
  topic/sub-topic (`lazy=true`, same as the source — never errors on too few
  questions).
- REST: `ExamController` (create/list/get collective exams, `assign`
  students, create sections — manual and auto-pick, trigger weekly
  generation — all `@RequiresModule("EXAM")` + `EXAM_MANAGE`/`EXAM_VIEW`),
  `ExamResultController` (instructor-side results, `EXAM_VIEW`),
  `MyExamsController` (`/api/me/schools/{schoolId}/exams` — the "my" pattern:
  list own exams, start an attempt, view own result, no
  `@RequiresPermission`/`@RequiresModule`, `studentId` resolved via
  `AcademicFacade.findMyEnrollment`, never from client input),
  `ExamTakeController` (`/api/exam-take/**`, token-authenticated — `permitAll`
  + CSRF-ignored in `SecurityConfig`, excluded from `TenantResolverFilter`,
  same treatment as `BaleWebhookController`: the token itself is the full
  credential and carries `examId`/`studentId`, so no session, no
  `X-School-ID`, no `@RequiresPermission` at all — its own permission
  mechanism keyed off the exam token, exactly as this section originally
  called for).

## 6. `studyplan` (new module) — Plans + auto-scheduler

**Status: DONE (2026-07-16)**, built as scoped below with one deviation:
course weighting became a real child table (`plan_course_weights`), not
the JSON `course_info` column sketched below — the same join-table-over-
array preference this section already applies to `WeekCourseTopics`
(ported as `WeekCourseTopicEntity`, as written below), extended to course
weighting too once actually building it made the JSON-column plan look
like the odd one out. The two schedule columns (`default_schedule`/
`current_schedule`) did stay `jsonb` as written below — that structure is
a genuinely nested day→interval shape, not a flat list a join table would
simplify. Everything else — the opaque `StudentPlanAssignmentEntity`
(not a `planId` column on `academic.StudentEntity`), the
`SliceScheduleInterval`/`FillSlices`/`Suggestor` algorithm port, and the
new `STUDY_PLANNER` module code + `STUDYPLAN_VIEW`/`STUDYPLAN_MANAGE`
permissions (both granted to `SCHOOL_ADMIN` + `TEACHER`, resolving this
section's open question below) — shipped as written. See `CLAUDE.md` §4's
Plan/WeekCourse/StudentPlanAssignment/StudyInterval/IntervalSchedule
subsection and §7 Done for the final shape and test coverage
(`StudyPlanManagementFlowIntegrationTest`). Two implementation-hygiene
normalizations were made to the algorithm port, neither changing its
actual output: the block-size counter is per-call state instead of
`table_suggester.py`'s module-level mutable dict (which leaked counts
across unrelated calls — a bug, not behavior worth preserving), and
weekdays are walked Saturday→Friday instead of moshavere's own
Friday-first order (a Postgres jsonb key-sort artifact, not a decision).

Source: `agency-panel-master/plan/models.py`,
`exam-maker-master/plan/models.py`,
`exam-maker-master/study_table/{models.py, table_suggester.py,
week_functions.py, views.py}`.

Depends on: `curriculum` (topics), `academic` (students/classes).

- `PlanEntity`: `schoolId`, `teacherId` (opaque `Long` → iam user, same
  no-FK convention as `ClassEntity.teacherId`), `title`, plus per-course
  weighting (port of moshavere's `course_info` JSON schema — a school-scoped
  JSON column with the same `{course, weight, priority}` shape, validated
  the same way).
- `WeekCourseEntity`: `planId`, week-start date, `topicId` — moshavere's
  `ArrayField` of up to 10 topic ids is ported as a real join table
  (`WeekCourseTopicEntity`), matching this codebase's existing preference
  for join tables over array columns (e.g. `RolePermissionEntity`) rather
  than introducing a new array-column pattern.
- **Student ↔ Plan assignment**: kept as an opaque reference *within*
  `studyplan` (a `StudentPlanAssignmentEntity` with a bare `studentId` Long,
  no FK into `academic`) rather than adding a `planId` column to `academic`'s
  `StudentEntity` — same opaque-cross-module-reference convention already
  used by `StudentGuardianEntity.guardianUserId` and
  `ClassEntity.teacherId`. This avoids `academic` needing to know
  `studyplan` exists.
- `StudyIntervalEntity` (`studentId`, `topicId`, sub-topics join table,
  `start`, `end`, `done`, `testCount`), `IntervalScheduleEntity`
  (`studentId`, `defaultSchedule`/`currentSchedule` as `jsonb` columns,
  `lastEditedAt` — replacing moshavere's "edited this week" Persian-calendar
  weekday check with a plain timestamp comparison unless Persian-calendar
  display is already a UI requirement elsewhere in this project).
- **Auto-suggestion algorithm** — direct logic port of
  `study_table/table_suggester.py`'s three pieces, as plain Java service
  classes (no framework magic needed, it's pure scheduling logic):
  `SliceScheduleInterval` (slices free time into fixed blocks: 90/15, 75/15,
  60/10, 45/10, 30/5-minute study/rest pairs), `FillSlices` (allocates blocks
  across topics weighted by the plan's course priority), `Suggestor`
  (orchestrates: builds a week of `StudyInterval` rows from the schedule
  template + the plan's `WeekCourse` topics). Preserve this algorithm's
  behavior exactly when porting — it's the one piece of nontrivial, tested
  business logic in the whole feature set.
- `StudyPlanFacade`: `createPlan`, `assignPlanToStudent`, `suggestWeek`
  (auto-generate), `getWeek`/`clearWeek`/`copyWeek` (instructor bulk-copies
  one student's week to others), `getRecentTopics(studentId)` (the method
  `exam`'s weekly-auto-generation depends on, §5).
- REST: `PlanController`, `WeekCourseController`, `StudyIntervalController`,
  `IntervalScheduleController`.
- **Resolved at new-module time:** a new `SCHOOL`-scoped `STUDY_PLANNER`
  module code, plus `STUDYPLAN_VIEW`/`STUDYPLAN_MANAGE` permissions
  granted to `SCHOOL_ADMIN` + `TEACHER` (same reasoning as
  `CURRICULUM_VIEW`/`CURRICULUM_MANAGE` — see `CLAUDE.md` §4's Permission
  section).

## 7. Non-goals (explicitly not being ported)

- Moshavere's dual topic-tree representation (Postgres ltree + pickled
  in-memory cache) — see §4.
- Phone-only unauthenticated self-registration — see §2.
- Cross-agency/shared curriculum content — see §2.
- Celery/materialized-view scoring infrastructure, unless/until real
  performance data justifies it — see §5.
- The two moshavere React frontends and the Django `agency-panel-master`
  server-rendered admin panel — Roshdeandishe already has its own JTE+Alpine
  frontend and admin surface; only backend domain logic is being ported, not
  any UI.

## 8. Suggested build order

1. `iam` OTP extension (smallest, no new module, unblocks nothing else). **Done.**
2. `curriculum` (no dependencies on the other new modules). **Done.**
3. `studyplan` (depends only on `curriculum` + `academic`). **Done.**
4. `exam` (depends on `curriculum` + `academic` + `studyplan`, the deepest
   dependency chain — built last so `getRecentTopics` already existed). **Done.**

**This closes out the moshavere port** — all four pieces (`iam` OTP, `curriculum`,
`studyplan`, `exam`) are built. Each step went through the `new-module` skill
individually; this document was context for that process, not a replacement
for the module-code/permission-code/dependency decisions that skill forced
explicitly at each step.
