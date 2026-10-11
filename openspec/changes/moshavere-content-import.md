# Moshavere Content Import (ورود محتوای «مشاور با من»)

Status: proposed 2026-10-09; revised 2026-10-11 after a code review. Product-owner
decisions D1–D13 and answers O1–O13 are recorded (see **Decisions** and **Answers**).
Six new design defaults from the review (P1–P6) are marked **Proposed** and need
confirmation before implementation. Not implemented.

The `curriculum` and `studyplan` modules were ported from Moshavere in July
2026 ([`../../MOSHAVERE_PORT_SPEC.md`](../../MOSHAVERE_PORT_SPEC.md) §4, §6),
but only the logic and schema came across. The content did not: a school
starts with an empty topic tree, an empty question bank and no study plans.

The decisions below change one of the port's rules. The port spec (§2) and
the current code keep topics and questions per school. D3 and D4 make topics,
courses and the Moshavere questions platform-owned. The current code must
change before those decisions take effect; this proposal does not change the
spec itself until the implementation lands.

The source files are not in the repository. They are referred to below by
content and by the ID prefix they were uploaded with.

## Decisions

Recorded from the product owner's answers on 2026-10-09.

| ID | Decision |
|---|---|
| D1 | The Moshavere data may be stored in the repository and given to schools. |
| D2 | Tree v2 (`69df89ac`, 23 root courses) is the canonical tree. |
| D3 | Topics and courses are **platform-owned and global**. They are not per school. |
| D4 | The Moshavere questions are **platform-owned**, visible to every school. A school may add its own questions under the global topics. A school's own questions belong only to that school. |
| D5 | A teacher selects a **grade**, then a **course**. Only that course's tree is loaded, not the whole tree. |
| D6 | A **track** (رشته) is stored on each academic year's enrollment. A course may carry a track; a course without one is common to all tracks. |
| D7 | The course list is the same for every school. It is organised by grade (1–12). Middle school and high school are not modelled separately; they are grade ranges. |
| D8 | Any teacher, school admin or student can report a problem with a question. |
| D9 | A report goes to a platform-admin queue. The reporting school can see its reports and their status: open, reviewed, fixed or closed. |
| D10 | A report never changes a question. Only the platform admin edits platform questions. |
| D11 | The Zn–SHE verdict (questions 1, 6, 9) waits for a chemistry teacher's ruling. The current key stays until then. |
| D12 | Biology code `22254` is checked against the source book before it is changed. |
| D13 | A teacher checks each repeated code before it is kept or removed. |

## Answers

Recorded from the product owner's answers on 2026-10-10.

| ID | Answer | Status |
|---|---|---|
| O1 | Keep the generic stem for all 10 questions (option b), from the answer «همان شکل باشد». | Decided |
| O2 | Render math in the browser with KaTeX from a WebJar. The TeX stays as plain text; no server conversion. | Decided; WebJar needs approval |
| O3 | Tir–Shahrivar 1406, the next summer. | Decided |
| O4 | The week containing 1 Tir. | Decided |
| O5 | Each school's own week-start day. | Decided |
| O6 | In sheet order: code 26522 goes to the 3rd Mordad week, and 26523 to the 4th Mordad week. | Decided |
| O7 | Skip the «ازمون» row. | Decided |
| O8 | Drop the Physics 1 codes (28733, 28734, 28766) until their weights exist. | Decided |
| O9 | Week courses use the lesson level, matching the weights. | Decided |
| O10 | The school enters course weights in the app. | Decided |
| O11 | Topic-level weights only. The sub-topic weights from `b33fbf93` are not stored. | Decided |
| O12 | Tracks are ریاضی, تجربی and انسانی. Courses without a track are common to all. | Decided |
| O13 | No school has created topics or questions yet, so no existing school-owned rows need a migration. Re-check before the migration runs. | Decided; re-check |

Still open outside the product decisions: the source-book check for `22254`
(D12) and the teacher review of repeated codes (D13). Both are checks, not
decisions, and both must be done before the affected rows are changed.

## Proposed Defaults From The Review (P1–P6)

These close gaps found in review. Each needs confirmation.

- **P1 — Who sets a student's track.** The school admin sets `track` on the
  student's enrollment for the academic year, in the existing enrollment
  flow. Promotion does not copy the track; the admin sets it for the new year.
- **P2 — Enrollment required.** A plan or exam cannot be assigned to a student
  who has no enrollment row for the academic year that contains the date. The
  assignment fails with an explicit error. The system never guesses a grade.
- **P3 — Null track.** A course with no track is shown to everyone in its grade.
  A course with a track is shown only to students with that track. A student with
  no track sees only common courses. Null never means "all tracks".
- **P4 — Week courses are per school.** The platform import creates topics,
  courses and platform questions only. Week courses and weights belong to a
  school's plans, so each school imports or enters its own weekly plans (Phase 4).
  The bundle's `weekCourses` is not used by the platform run.
- **P5 — Academic year for a week.** The enrollment and track used for a week
  course are those of the academic year that contains the week's start date.
  Academic years start on 1 Mordad, so the week of 1 Tir 1406 uses 1405–1406,
  and the week of 3 Mordad 1406 uses 1406–1407.
- **P6 — Who may report, and where reports go.**
  - Teachers and school admins report from the bank (`CURRICULUM` module).
  - Students report from the exam view, where they see the question
    (`EXAM` module). A new permission, `QUESTION_REPORT`, is granted to all three
    roles.
  - A report on a platform question goes to the platform queue (D9).
  - A report on a school's own question goes to that school's own queue, since
    the school edits that question and D10 applies only to platform questions.

## Verified Baseline

From code, not from earlier docs. Items marked **changes** are what the decisions
change.

- `curriculum.topics` has `id`, `school_id` (not null), `parent_id`, `name`
  (`varchar(250)`) and `sort_order`. There is no source-code column
  ([`V1__baseline_schema.sql:322`](../../src/main/resources/db/migration/V1__baseline_schema.sql)).
  The entity maps `school_id` as not nullable
  ([`TopicEntity.java:28`](../../src/main/java/com/example/roshdeandishe/curriculum/internal/TopicEntity.java)).
  **Changes:** topics become global.
- Topics are per school, as `MOSHAVERE_PORT_SPEC.md` §2 records (confirmed
  2026-07-11). **Changes:** D3 supersedes §2 for topics and questions.
- `CurriculumFacadeImpl.createTopic` rejects a blank name and a parent from
  another school. It does not enforce unique sibling names
  ([`CurriculumFacadeImpl.java:32`](../../src/main/java/com/example/roshdeandishe/curriculum/internal/CurriculumFacadeImpl.java)).
- `requireTopicInSchool` compares `topic.getSchoolId()` with the caller's school
  (`CurriculumFacadeImpl.java:159`). With a null school this comparison fails.
  Question listing and the topic-subtree filter query by school only
  (`CurriculumFacadeImpl.java:108–111`). Both need changes (see Phase 0).
- The topic and question APIs are `POST`/`GET` only, under
  `/api/admin/schools/{schoolId}/topics` and `.../questions`. There is no edit
  or delete. They require the `CURRICULUM` module, `CURRICULUM_MANAGE` to write
  and `CURRICULUM_VIEW` to read (`TopicController.java:29,39`,
  `QuestionController.java:27,37`).
- `curriculum.questions` requires `text` and `choice1`–`choice4`;
  `correct_choice` must be 1–4; `topic_id` is required; `school_id` is not null
  (`V1__baseline_schema.sql:331`, `CurriculumFacadeImpl.java:68`). It has no
  unique key except `id`, so re-running an import would duplicate questions.
  **Changes:** `school_id` becomes nullable and a `source_code` key is added.
- Question text is rendered with Alpine `x-text` at `curriculum.jte:104`,
  `exam-take.jte:63,71` (question and choices), `exams.jte:120`. No template uses
  `x-html`, and no math renderer exists.
- The course catalog `academic.courses` (`V5`) is global, has `grade_level`
  (1–12) and `name`, is unique per `(grade_level, name)`, and is empty by design.
  Its only read endpoint needs a class: `GET …/grades/courses?classId=`
  (`GradeController.java:78`). **Changes:** it gains a track and a link to a
  topic node.
- The existing course picker filters courses by their validity range
  (`valid_from_year`, `valid_until_year`, added by `V9`), in
  `AcademicFacadeImpl.java:384`. The new grade list must apply the same filter.
- A student's grade is in `academic.enrollments.grade_level` for each academic
  year (`V11`). Enrollment rows are written only for schools with a current
  academic year (`EnrollmentLedger`). There is no track field anywhere in the
  code, the migrations or the UI. `classes.name` is free text.
- A plan needs at least one course weight; `createPlan` rejects an empty list
  ([`StudyPlanFacadeImpl.java:82`](../../src/main/java/com/example/roshdeandishe/studyplan/internal/StudyPlanFacadeImpl.java)).
  There are no sub-topic weights anywhere
  ([`FillSlices.java:96`](../../src/main/java/com/example/roshdeandishe/studyplan/internal/FillSlices.java)).
- A week course is a plan, a `week_start` date, a `course_topic_id` and up to
  10 sub-topic IDs. `createWeekCourse` requires the week start to be the school's
  week-start day, and checks topic membership against the school's topics
  (`StudyPlanFacadeImpl.java:128`, `:529`). It does not check that sub-topics
  descend from the course topic.
- The suggester attaches a week course's sub-topics only when its
  `course_topic_id` equals a weighted course of the plan
  ([`Suggestor.java:70`](../../src/main/java/com/example/roshdeandishe/studyplan/internal/Suggestor.java)).
- The curriculum, exam and study-plan pages each fetch the whole topic tree
  (`curriculum.js:174`, `exams.js:139`, `study-plans.js:231`). The tree endpoint
  has no root or subtree parameter (`TopicController.java:67`). The filter
  `subtreeTopicIds` loads every topic of the school to find descendants
  (`CurriculumFacadeImpl.java:128`). No server cache exists.
- The application has no spreadsheet or CSV parser; `pom.xml` has no library for
  one.
- Platform-admin code exists for platform holidays (`/platform/holidays`,
  `PLATFORM_ADMIN_EMAILS`, [`yearly-plan.md`](yearly-plan.md)). Nothing yet
  manages topics, courses or questions at platform level.
- [`scripts/seed-demo-data.py`](../../scripts/seed-demo-data.py) fills a school
  through the public REST API. It records that the paid modules (`CURRICULUM`,
  `STUDY_PLANNER`, `EXAM`) cannot be granted over HTTP.

## Source Review (2026-10-09)

| Source (upload prefix) | Content | Use |
|---|---|---|
| `69df89ac` tree_expanding_view_v2 (`399ad317` is a byte-identical copy) | «درخت مشاور با من»: 5,656 topics, 23 root courses, depth 0–5, 4,206 leaves, every node labelled `name(code)` | **Import** (Phase 1) |
| `a4821972` tree_expanding_view | Older tree with the same 5,656 codes, 24 roots, 104 nodes renamed or moved | Superseded by v2 (D2) |
| `f12e8f54` html1 | Chemistry 3 multiple-choice export: 10 questions, verdicts, explanations, topic paths | **Import after cleanup** (Phase 2) |
| `0ab2c2a9`, `e71a6871`, `e7fde81a`, `84c4a8d4`, `85877446`, `82a3abf4` | Summer weekly plans: one Moshavere code per row under a Persian week label such as «اول تیر» | **Import per school** (Phase 4) |
| `b33fbf93` | Physics 3 (math track) sub-topic weights within each topic | Not stored (O11) |
| `2becf8a4` shimis.csv | Chemistry true/false statements with corrections | Excluded: 43 of 44 rows are corrupted (legacy font encoding), 15 rows have the wrong field count, and there is no true/false model |
| `aae19a4c` / `998ab131` gosaste-4, `65ddee37` DIF_2 | Saved pages from the third-party Monta site | Excluded: third-party content and scripts |
| `9405e3a9` | Grade-11 date × subject grid | Excluded: its codes (`F11_6`, `Sh11_nalk`) are not tree codes |
| `a3dfd51b` docx | Codes listed per track | Excluded: purpose unclear; `2486` is not in the tree |
| `cc722107` plan-template | Empty weekly-plan template | Excluded: no data |
| Postman collection, API doc | Legacy Moshavere API | Excluded on request (2026-10-09) |

Checks that passed:

- Tree codes are unique.
- No two siblings share a name, so a (parent, name) path identifies a topic.
- The longest name is 93 characters.
- Every code in the six weekly plans resolves to a tree code except `22254`.
- The ten chemistry topic codes in the excluded CSV resolve to the same topics as
  the html1 questions.
- In html1, every question has exactly one choice marked «درست», and it matches
  the «پس گزینه‌ی N صحیح است» line.
- No week in a plan sheet has more than 10 codes.
- All 32 topics in the physics 3 weights file sum to 1.0.

### Corrections Required Before Import

| Source | Item | Correction | Status |
|---|---|---|---|
| `b33fbf93` | Sub-topic `301222` | `30122` (Newton's third law; siblings 30120 and 30121) | High confidence |
| `e7fde81a` biology | Code `22254` (not in the tree) | `26254` (Biology 2, chapter 1, lesson 2; between 26248 and 26267) | D12: check the source first |
| `e7fde81a` biology | «ازمون» row in the code column | Skipped | O7 |
| `84c4a8d4` chemistry | «سوم و چهارم مرداد» spans two weeks | 26522 → 3rd Mordad; 26523 → 4th Mordad | O6 |
| `0ab2c2a9`, `e7fde81a`, `85877446` | Codes 30102, 22736, 19948, 19951, 19952 in more than one week | Confirm intended | D13 |
| Plan sheets | Trailing spaces in week labels | Trim | High confidence |
| `f12e8f54` | 13 explanations are the literal `nan` | Store no explanation | High confidence |
| `f12e8f54` | Two choices end with a stray `C40` | Delete | High confidence |
| `f12e8f54` | All 10 questions share the stem «کدام یک از گزینه‌های زیر صحیح است.» | Keep the generic stem | O1 |
| `f12e8f54` | «در سلول گالوانی Zn-SHE هیدروژن نقش کاتد را ایفا می‌کند» is marked false in questions 1, 6 and 9. In a Zn–SHE cell the SHE is the cathode, so it is arguably true, which would give those questions two correct answers | Teacher ruling; key unchanged meanwhile | D11 |
| `f12e8f54` | Text contains HTML tags and TeX (`$\displaystyle SO_{3}^{2-} $`) | Keep TeX in the stored text; render in the browser | O2 |

## Proposed Design

Plans, week courses, exams and student data stay per school. Only topics, courses
and platform questions become global.

### 0. Math rendering (O2)

- Question, choice and explanation text keep their TeX inside `$…$` in the database.
- The browser renders it. A small Alpine directive splits the text on `$…$`:
  plain segments are shown with `x-text`, and each formula is rendered by KaTeX
  into its own elements.
- The directive is used everywhere a question is shown: the bank
  (`curriculum.jte:104`), exam taking (`exam-take.jte:63,71`, question and
  choices), exam results and the explanation text.
- A literal dollar sign is written `\$`. A `$` without a closing `$` is shown as
  text. Only inline `$…$` is recognised; `$$` is not.
- No `x-html` and no HTML sanitisation are needed, because KaTeX builds its own
  elements and no arbitrary HTML is inserted. KaTeX's `trust` option, which
  enables links, stays off.
- The library comes as a WebJar and needs approval before it is added (the
  frontend rules in `openspec/conventions.md`).

### 1. Data model (a new migration; applied migrations are not edited)

- `curriculum.topics`: `school_id` becomes nullable (null = platform topic).
  `TopicEntity` changes to match. Add `source_code varchar(32)`, unique where not
  null, so the Moshavere code is kept on the topic.
- `curriculum.questions`: `school_id` becomes nullable (null = platform question).
  Add `source_code varchar(64)`, unique where not null. The importer keys
  questions by it (for example `html1:Q01`), so a re-run updates instead of
  duplicating. School-created questions have no `source_code`.
- `academic.courses`: add `track varchar(16)` (nullable) with a check that it is
  one of `ریاضی`, `تجربی`, `انسانی`, and add `topic_id bigint` referencing the
  course's tree node. The existing unique key `(grade_level, name)` is kept.
- `academic.enrollments`: add `track varchar(16)` (nullable, same check),
  set per academic year by P1.
- New `curriculum.question_reports`: `id`, `question_id`, `reporter_user_id`,
  `school_id`, `report_type` (wrong answer, typo, unclear text, other),
  `comment`, `status` (open, reviewed, fixed, closed), `resolution_note`,
  `created_at`, `updated_at`.

### 2. Permissions and visibility

- **Platform admin** (the `PLATFORM_ADMIN_EMAILS` mechanism): creates and edits
  topics, courses and platform questions; works the platform report queue.
- **School `CURRICULUM_MANAGE`** (school admins and teachers): creates, edits and
  deletes only the school's own questions.
- **School `CURRICULUM_VIEW`**: reads platform topics and platform questions, plus
  the school's own questions. Another school's questions are invisible.
- **`QUESTION_REPORT`** (new): granted to students, teachers and school admins (P6).
- **Parents**: no access.
- **Reports**: a school sees its own reports and their status (D9). A report on a
  school's own question goes to that school's queue (P6).

### 3. Course selection and loading

1. The teacher picks a grade (1–12). The list comes from `academic.courses` for
   that grade (D7). Courses outside their validity range for the selected academic
   year are hidden, as in the existing picker (`AcademicFacadeImpl.java:384`).
2. The teacher picks a course. The client loads only that course's tree, through
   a new subtree endpoint. The endpoint uses a recursive query on `parent_id`, so
   it reads that course's nodes only.
3. For a plan or exam assigned to a **specific student**, the grade and track come
   from that student's enrollment for the academic year that contains the date
   (P5). A student without an enrollment row cannot be assigned (P2). The course
   list is the common courses plus the courses of the student's track (D6, P3).
   Example: for a grade-12 mathematics student, «فارسی» (common) appears, and
   «فیزیک ۳ (ریاضی)» appears, but «فیزیک ۳ (تجربی)» does not.
4. Bank browsing has no student, so the teacher chooses both grade and course.
5. The three pages that fetch the whole tree (`curriculum.js:174`,
   `exams.js:139`, `study-plans.js:231`) switch to the subtree endpoint. The
   whole-tree endpoint stays for platform-admin tools.

### 4. Question reports

- A «گزارش مشکل» action sits beside each question wherever it is shown: the bank
  for teachers and admins, and the exam view for students (P6).
- The form asks for a report type and a comment.
- A report on a platform question goes to the platform queue. The admin marks it
  reviewed, then fixed (after editing the question) or closed. A report on a
  school's own question goes to that school.
- The reporting school sees each status (D9). Nothing changes the question except
  its owner (D10).
- Reports are stored in `curriculum`, not in a new top-level module. A student
  reports through the exam module, which calls the curriculum facade. Creating a
  new top-level module would need the `new-module` skill (`CLAUDE.md`).

### 5. Code changes for nullable topic owners (Phase 0)

Making `school_id` nullable changes these code paths, which the proposal must
cover before the migration runs:

- `TopicEntity` mapping (`TopicEntity.java:28`): not nullable → nullable.
- `CurriculumFacadeImpl.requireTopicInSchool` (`:159`): accept topics with a null
  school (platform topics) for every school; reject other schools' topics.
- `CurriculumFacadeImpl.listQuestions` (`:108–111`) and the subtree filter
  (`:128`): select platform questions (`school_id IS NULL`) together with the
  caller's own questions, and walk platform topics.
- `createQuestion` (`:68`): a school may attach its own question to a platform
  topic.
- `listTopics` and `getTopicTree`: return platform topics for every school.
- `StudyPlanFacadeImpl` topic checks (`:93`, `:133`) and the exam topic check
  (`ExamFacadeImpl`): accept platform topics.
- Integration tests for each path, following the existing `*FlowIntegrationTest`
  style.

### 6. Import

- **Converter** (one-off, offline): reads the sources and writes a JSON bundle.
  It applies only the reviewed corrections, strips HTML to plain text, and refuses
  to write while any referenced code is missing from the tree and is not covered
  by a correction. It gives every question a stable `source_code`.
- **Platform importer**: one run by the platform admin, with the mandatory dry
  run. It writes through platform endpoints, which must be built, because the
  existing topic, question and course endpoints are school-scoped. Every write is
  an upsert by its key:
  - topics by `source_code`;
  - questions by `source_code`;
  - courses by `(grade_level, name)`.

  A re-run therefore creates nothing new.
- **Per-school plans** (Phase 4, P4): each school imports or enters its own weekly
  plans, using the school's own week-start day (O5). The bundle's `weekCourses`
  is used only by that per-school run.

```json
{
  "format": 3,
  "source": "moshaver-tree-v2",
  "courses": [
    { "gradeLevel": 12, "name": "فیزیک ۳ (ریاضی)", "track": "ریاضی", "topicCode": "30119" },
    { "gradeLevel": 12, "name": "عربی", "track": null, "topicCode": "25762" }
  ],
  "topics": [
    { "code": "30122", "parentCode": "30119", "name": "قانون سوم نیوتون", "sortOrder": 2 }
  ],
  "questions": [
    { "sourceCode": "html1:Q01", "topicCode": "24145", "text": "...",
      "choices": ["...", "...", "...", "..."], "correctChoice": 1,
      "answer": null, "difficulty": null }
  ],
  "weekCourses": []
}
```

Track-specific course names include the track, as in the tree, so the
`(grade_level, name)` key stays unique. A common course has `track: null`. Track
values are the Persian strings from O12.

**Alternative considered:** one atomic `POST` of the whole bundle from the
platform admin. It would give an all-or-nothing import, but it needs a large
request and its own error handling. Revisit it if the one-by-one import proves
slow or fragile.

## Phases

0. **Platform foundation.** The migration (§1), the code changes in §5, the
   track fields and their writers (P1), the enrollment rule (P2), the validity
   filter, the new permissions (§2) and the subtree endpoint. Nothing is imported
   yet.
1. **Tree and course catalog.** Import the tree (D2) and the courses (D7). The
   grade → course flow works end to end.
2. **Questions.** The 10 chemistry questions after the corrections above, with the
   generic stem (O1), TeX rendered in the browser (O2) and the teacher ruling
   (D11). School-added questions become available.
3. **Reports.** The report button, the queues and the status flow (D8–D10, P6).
   Can run alongside phases 1–2 once phase 0 is done.
4. **Per-school weekly plans.** Each school's plans, using its own week start (O5),
   O3, O4, O6–O9 and its own weights (O10, O11). Needs phase 1 and the school's
   own weights.

## Acceptance Criteria

- A platform admin's dry run creates nothing, and the topic and question counts
  are unchanged afterwards.
- A real run creates exactly the tree (5,656 topics) with the source parent links
  and sibling order. Counts match the converter's report.
- Running the import again creates nothing: every write is an upsert by its key.
- A school admin or teacher is refused (403) on any platform topic, course or
  platform-question write.
- A teacher sees platform questions and the school's own questions, and no other
  school's questions. Platform topics appear for every school.
- The subtree endpoint returns only the chosen course's nodes, and applies the
  course validity range for the selected academic year.
- For a student with a track, the course list contains the common courses and that
  track's courses, and no other track's courses. A student with no track sees only
  common courses (P3).
- A student with no enrollment row for the academic year cannot be assigned a plan
  or exam, and the error is explicit (P2).
- A report is visible to the reporting school (with its status) and to the owner
  of the question, and to no other school. Creating a report leaves the question
  unchanged. Status moves only open → reviewed → fixed or closed.
- The converter refuses a bundle while `301222` or `22254` is uncorrected, and
  names them.
- Imported questions keep their correct choice, have no HTML tags, no `nan` and no
  stray `C40`.
- The load time of the three pages is measured with the largest course, and the
  result is recorded in this document before phase 1 closes.

## Tests That Will Prove It

- Converter: a stdlib `unittest` with small fixtures for each failure the review
  found: a UTF-16 tree fragment; a sheet with an unknown code and an «ازمون» row;
  a question with HTML, `nan` and `C40`; a TeX formula with a literal `\$`.
- Null-owner paths (§5): a platform topic is found by a school admin; a school's
  question on a platform topic is created; a school cannot read another school's
  question.
- Platform endpoints: permission tests (platform admin allowed; school admin and
  teacher refused; anonymous refused), following the existing `*FlowIntegrationTest`
  style.
- Subtree endpoint: returns only the course's nodes and respects the validity range.
- Track filter: a common course is visible to every track; a track-specific course
  only to its track; a student with no track sees only common courses.
- Enrollment: assignment fails without an enrollment row; the academic year is the
  one that contains the week start (P5).
- Reports: create, list, the platform queue, the school queue for its own
  questions, status transitions, and each school seeing only its own reports.
- Migrations: tested on a database that already holds school-owned rows (O13).
- Importer: run against a local instance, once with `--dry-run`, once for real, and
  once more to prove idempotency. Record the counts in the PR.
- `ModularityTests` after the changes, because the reports sit inside `curriculum`
  and the study-plan and exam validations change.

## Compatibility And Migration

- `MOSHAVERE_PORT_SPEC.md` §2 and `openspec/specs/current-system.md` describe
  per-school curriculum content. This proposal supersedes that rule for topics,
  courses and questions (D3, D4). Those documents are updated in the same change
  that implements it, not earlier.
- New migrations only; applied migrations are not edited.
- The paid modules (`CURRICULUM`, `STUDY_PLANNER`, `EXAM`) still cannot be granted
  over HTTP, so the importer and any test school need them granted in SQL.
- Study plans, week courses, exams and student data stay per school. Existing plan
  and exam behaviour is unchanged except for the topic checks, which must accept
  platform topics (§5).
- The academic year switches on 1 Mordad. The week of 1 Tir 1406 belongs to
  1405–1406 and the week of 3 Mordad 1406 belongs to 1406–1407 (P5). The yearly
  plan's range (1 Mehr – 31 Khordad, [`yearly-plan.md`](yearly-plan.md)) is not
  used by study plans, so the summer weeks are not a conflict.
- Undoing a mistaken platform import needs manual SQL. The mandatory dry run and a
  staging environment are the protection.

## Proposed defaults awaiting confirmation

- **P1** The school admin sets each student's track on the enrollment for the year.
- **P2** A student without an enrollment row cannot be assigned a plan or exam.
- **P3** A student with no track sees only common courses.
- **P4** Weekly plans and week courses are created per school, not by the platform run.
- **P5** A week's enrollment and track come from the academic year that contains the week's start date.
- **P6** Students, teachers and admins may report; reports on platform questions go to the platform queue, and reports on a school's own questions go to that school.
