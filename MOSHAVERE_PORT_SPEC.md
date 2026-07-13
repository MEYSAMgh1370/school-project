# Roshdeandishe — Moshavere Port Spec

**Status: DRAFT.** Not resolved architecture the way `CLAUDE.md` is — it governs
the proposed shape of a port from a separate, older codebase family ("moshavere":
`agency-panel-master`, `exam-maker-master`, plus two React frontends, all under
`/home/meysam/Downloads/moshavere/`) into this project, the same way
`UX_ROADMAP.md`/`MOBILE_ROADMAP.md` govern their respective areas — subordinate
to `CLAUDE.md`, which remains the source of truth and wins on any conflict.
**Before building any of this, invoke the `new-module` skill per module** (per
`CLAUDE.md`'s standing instruction) — this document is input to that process,
not a substitute for it. Nothing here is committed until each module's own
new-module pass resolves its final shape; several open questions are called
out explicitly below and must be resolved (or re-confirmed) at that time.

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

Source: `exam-maker-master/azmoon/{models.py, token.py, tasks.py, views.py}`.

Depends on: `curriculum` (topics/questions), `academic` (students/classes),
`iam`, and `studyplan` (see dependency note below).

**Consumers of the exam-take API — updated 2026-07-11:** both the native
Android app (`MOBILE_ROADMAP.md` §5 item 3/7) and a new student-facing
JTE+Alpine web page (`MOBILE_ROADMAP.md` §4, "Student web channel") will call
`ExamTakeController` against the same scoped exam-take token — the token
scheme is client-agnostic by design, so this doesn't add work to this
module, just an additional caller.

- Entities: single `ExamEntity` with an `examType` enum (`WEEKLY` |
  `COLLECTIVE`) rather than moshavere's Django model-inheritance chain
  (`Exam` → `PersonalExam` → `WeeklyExam` / `CollectiveExam`) — matches this
  codebase's existing preference for a flat entity + explicit enum/sealed
  type over inheritance tricks (see `ModuleScope`, `IamError` etc.).
  `SectionEntity` (`examId`, `topicId`, `order`), `SectionQuestionEntity`
  (join table), `AttemptEntity` (`examId`, `studentId`, `startedAt`,
  `expireTime`), `SubmitEntity` (`attemptId`, `sectionQuestionId`,
  `chosenOption`, constrained 1–4 same as source).
- **Exam-taking session token**: a second, independently-secreted JWT
  (own config key, e.g. `roshdeandishe.exam.token-secret`), issued per
  `Attempt`, distinct from both the session cookie and the mobile bearer
  JWT — directly porting `azmoon/token.py`'s pattern of a scoped,
  short-lived token for a bounded task. Lives in `exam.internal` or
  `exam.shared` (not `iam` — `iam` must not depend on `exam`).
- **Scoring**: moshavere precomputed scores via a Postgres materialized view
  refreshed by a Celery task (`azmoon/tasks.py refresh_exam_results`). This
  project has no Celery-equivalent async task runner yet. **Recommend**
  computing scores on demand in `ExamFacade` first, and only introducing a
  materialized view + a Spring `@Scheduled` refresh job if/when real
  performance data justifies it — don't stand up new async infra
  speculatively for a feature that doesn't exist yet.
- **Weekly-exam auto-generation dependency**: moshavere's `WeeklyExam` builds
  its sections from the student's recent study-table topics
  (`study_table.week_functions.get_week_intervals_topics`). In this port that
  means `exam` needs the student's recently-studied topics from `studyplan`
  — call `StudyPlanFacade.getRecentTopics(studentId)` directly (a plain
  facade dependency, matching this codebase's stated default of
  synchronous facade calls between modules, reserving events for one-way
  reactions) rather than an event. This makes `exam` depend on `studyplan`
  in addition to `curriculum`/`academic`/`iam` — flag this dependency
  explicitly during `exam`'s new-module pass since it's the one place this
  port introduces a new-module-depends-on-new-module edge.
- REST: `ExamController` (create/list collective exams, `assign` students),
  `AttemptController` (start attempt → issues exam token), `ExamTakeController`
  (token-authenticated: list sections, get questions, submit answers — its
  own permission mechanism keyed off the exam token, not the normal
  `@RequiresPermission`, same as moshavere's `HasValidExamToken`/
  `ExamTokenNotExpired`/`ExamTokenIsUsers`), `ExamResultController`.
- **Open:** does this map to the already-seeded `EXAM_PREP` module code
  (currently `USER`-scoped/B2C, a placeholder) or does it need its own new
  `SCHOOL`-scoped code? Moshavere's product was itself exam-prep coaching,
  so `EXAM_PREP` is a plausible fit by name — but the actual moshavere
  feature is instructor-authored, per-class/per-school, which is a B2B
  shape, not B2C. **Resolve this at new-module time**, don't silently reuse
  `EXAM_PREP`'s scope without confirming it.

## 6. `studyplan` (new module) — Plans + auto-scheduler

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
- **Open:** new `Module`/`Permission` codes needed (no existing seed fits);
  likely a new `SCHOOL`-scoped `STUDY_PLANNER` module code plus
  `STUDYPLAN_MANAGE`/`STUDYPLAN_VIEW` permissions, decided at new-module
  time alongside `curriculum`'s and `exam`'s.

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
2. `curriculum` (no dependencies on the other new modules).
3. `studyplan` (depends only on `curriculum` + `academic`).
4. `exam` (depends on `curriculum` + `academic` + `studyplan`, the deepest
   dependency chain — build last so `getRecentTopics` already exists).

Each step still goes through the `new-module` skill individually; this
document is context for that process, not a replacement for the module-code/
permission-code/dependency decisions that skill is meant to force explicitly.
