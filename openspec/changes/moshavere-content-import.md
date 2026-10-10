# Moshavere Content Import (ورود محتوای «مشاور با من»)

Status: proposed 2026-10-09. Product-owner decisions D1–D13 recorded on
2026-10-09 (see **Decisions**). Open decisions O1–O13 still block the phases
named in **Open Decisions**. Not implemented.

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

Two answers were read from short replies and need confirming:

- **Stem (O1):** the answer «همان شکل باشد» was read as keeping the generic
  stem for all 10 questions.
- **Math notation (O2):** the answer «a or b» was read as plain Unicode
  conversion, with a renderer as a later option.

## Open Decisions

These block the phases shown. Each has a proposed default.

| ID | Question | Blocks | Proposed default |
|---|---|---|---|
| O1 | Confirm the generic stem is kept for all 10 chemistry questions. | Phase 2 | Keep it. |
| O2 | Confirm plain Unicode conversion for TeX. | Phase 2 | Convert. |
| O3 | Which Solar year do the summer sheets belong to? | Phase 4 | Tir–Shahrivar 1406. |
| O4 | Which week counts as «اول تیر»? | Phase 4 | Ask the school. |
| O5 | What date should a week course use for Monday-start schools? | Phase 4 | The school's own week-start day; the server rejects other days. |
| O6 | Where do the two codes labelled «سوم و چهارم مرداد» go? | Phase 4 | Ask the school. |
| O7 | How is the «ازمون» row handled? | Phase 4 | Skip it; it is not a topic. |
| O8 | What happens to the Physics 1 codes (28733, 28734, 28766)? | Phase 4 | Drop them until Physics 1 weights exist. |
| O9 | Which tree level do week courses use? | Phase 4 | Lesson level, matching the weights file. |
| O10 | Who supplies course weights for each plan? A plan cannot exist without them. | Phase 4 | The school enters them in the app. |
| O11 | Are sub-topic weights from `b33fbf93` needed? | Phase 4 | No; topic-level weights only. |
| O12 | Which track values exist, and which courses are track-specific? | Phase 0 | ریاضی, تجربی, انسانی. Common courses have no track. |
| O13 | Do any environments already contain school-created topics or questions? | Phase 0 | Check first. Existing school rows keep their school. |

Outside input, not a product decision: the Zn–SHE ruling (D11), the source-book
check for `22254` (D12), and the teacher review of repeated codes (D13).

## Answers Received (2026-10-10)

| ID | Answer | Status |
|---|---|---|
| O1 | Read as keeping the generic stem for all 10 questions. | To confirm |
| O2 | Read as plain Unicode conversion. The explanation was given; the choice was not confirmed. | To confirm |
| O3 | No answer. | Open |
| O4 | No answer. | Open |
| O5 | Each school's own week-start day (option a). | Decided |
| O6 | Ask the school which code belongs to which week (option c). | Waiting for the school |
| O7 | Skip the «ازمون» row (option a). | Decided |
| O8 | Drop the Physics 1 codes until their weights exist (option a). | Decided |
| O9 | Lesson level, matching the weights (option a). | Decided |
| O10 | The school enters course weights in the app (option a). | Decided |
| O11 | Topic-level weights only (option a). | Decided |
| O12 | No answer. | Open |
| O13 | No answer. | Open |

Outside input received: the source-book check for `22254` (D12) and the teacher
review of repeated codes (D13) were both answered «check first», so both stay
open until the checks are done.

## Verified Baseline

From code, not from earlier docs. Items marked **changes** are what D3–D6 change.

- `curriculum.topics` has `id`, `school_id` (not null), `parent_id`, `name`
  (`varchar(250)`) and `sort_order`. There is no source-code column
  ([`V1__baseline_schema.sql:322`](../../src/main/resources/db/migration/V1__baseline_schema.sql)).
  **Changes:** topics become global.
- Topics are per school, as `MOSHAVERE_PORT_SPEC.md` §2 records (confirmed
  2026-07-11). **Changes:** D3 supersedes §2 for topics and questions.
- `CurriculumFacadeImpl.createTopic` rejects a blank name and a parent from
  another school. It does not enforce unique sibling names
  ([`CurriculumFacadeImpl.java:32`](../../src/main/java/com/example/roshdeandishe/curriculum/internal/CurriculumFacadeImpl.java)).
- The topic and question APIs are `POST`/`GET` only, under
  `/api/admin/schools/{schoolId}/topics` and `.../questions`. There is no edit
  or delete. They require the `CURRICULUM` module, `CURRICULUM_MANAGE` to write
  and `CURRICULUM_VIEW` to read (`TopicController.java:29,39`,
  `QuestionController.java:27,37`).
- `curriculum.questions` requires `text` and `choice1`–`choice4`;
  `correct_choice` must be 1–4; `topic_id` is required. `school_id` is not null
  (`V1__baseline_schema.sql:331`, `CurriculumFacadeImpl.java:68`).
  **Changes:** `school_id` becomes nullable; null means a platform question.
- Question text is rendered with Alpine `x-text` (`curriculum.jte:104`,
  `exam-take.jte:63,71`, `exams.jte:120`). No template uses `x-html`, and no
  math renderer exists.
- The course catalog `academic.courses` (`V5`) is global, has `grade_level`
  (1–12) and `name`, is unique per grade, and is empty by design. Its only
  read endpoint needs a class: `GET …/grades/courses?classId=`
  (`GradeController.java:78`). **Changes:** it gains a track and a link to a
  topic node.
- A student's grade is in `academic.enrollments.grade_level` for each
  academic year (`V11`). There is no track field anywhere in the code, the
  migrations or the UI. `classes.name` is free text.
- A plan needs at least one course weight; `createPlan` rejects an empty list
  ([`StudyPlanFacadeImpl.java:82`](../../src/main/java/com/example/roshdeandishe/studyplan/internal/StudyPlanFacadeImpl.java)).
  There are no sub-topic weights anywhere
  ([`FillSlices.java:96`](../../src/main/java/com/example/roshdeandishe/studyplan/internal/FillSlices.java)).
- A week course is a plan, a `week_start` date, a `course_topic_id` and up to
  10 sub-topic IDs. `createWeekCourse` requires the week start to fall on the
  school's week-start day, and checks topic membership against the school's
  topics (`StudyPlanFacadeImpl.java:128`, `:529`). It does not check that
  sub-topics descend from the course topic.
- The suggester attaches a week course's sub-topics only when its
  `course_topic_id` equals a weighted course of the plan
  ([`Suggestor.java:70`](../../src/main/java/com/example/roshdeandishe/studyplan/internal/Suggestor.java)).
- The curriculum, exam and study-plan pages each fetch the whole topic tree
  (`curriculum.js:174`, `exams.js:139`, `study-plans.js:231`). The tree
  endpoint has no root or subtree parameter (`TopicController.java:67`). The
  filter `subtreeTopicIds` loads every topic of the school to find
  descendants (`CurriculumFacadeImpl.java:128`). No server cache exists.
- The application has no spreadsheet or CSV parser; `pom.xml` has no library
  for one.
- Platform-admin code exists for platform holidays (`/platform/holidays`,
  `PLATFORM_ADMIN_EMAILS`, [`yearly-plan.md`](yearly-plan.md)). Nothing yet
  manages topics, courses or questions at platform level.
- [`scripts/seed-demo-data.py`](../../scripts/seed-demo-data.py) fills a school
  through the public REST API. It records that the paid modules
  (`CURRICULUM`, `STUDY_PLANNER`, `EXAM`) cannot be granted over HTTP.

## Source Review (2026-10-09)

| Source (upload prefix) | Content | Use |
|---|---|---|
| `69df89ac` tree_expanding_view_v2 (`399ad317` is a byte-identical copy) | «درخت مشاور با من»: 5,656 topics, 23 root courses, depth 0–5, 4,206 leaves, every node labelled `name(code)` | **Import** (phase 1) |
| `a4821972` tree_expanding_view | Older tree with the same 5,656 codes, 24 roots, 104 nodes renamed or moved | Superseded by v2 (D2) |
| `f12e8f54` html1 | Chemistry 3 multiple-choice export: 10 questions, verdicts, explanations, topic paths | **Import after cleanup** (phase 2) |
| `0ab2c2a9`, `e71a6871`, `e7fde81a`, `84c4a8d4`, `85877446`, `82a3abf4` | Summer weekly plans: one Moshavere code per row under a Persian week label such as «اول تیر» | **Import after decisions** (phase 4) |
| `b33fbf93` | Physics 3 (math track) sub-topic weights within each topic | No model for it (O11) |
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
- The ten chemistry topic codes in the excluded CSV resolve to the same topics
  as the html1 questions.
- In html1, every question has exactly one choice marked «درست», and it matches
  the «پس گزینه‌ی N صحیح است» line.
- No week in a plan sheet has more than 10 codes.
- All 32 topics in the physics 3 weights file sum to 1.0.

### Corrections Required Before Import

| Source | Item | Correction | Confidence / decision |
|---|---|---|---|
| `b33fbf93` | Sub-topic `301222` | `30122` (Newton's third law; siblings 30120 and 30121) | High |
| `e7fde81a` biology | Code `22254` (not in the tree) | `26254` (Biology 2, chapter 1, lesson 2; between 26248 and 26267) | D12: check the source first |
| `e7fde81a` biology | «ازمون» row in the code column | Not a topic | O7 |
| `84c4a8d4` chemistry | «سوم و چهارم مرداد» spans two weeks | | O6 |
| `0ab2c2a9`, `e7fde81a`, `85877446` | Codes 30102, 22736, 19948, 19951, 19952 in more than one week | Confirm intended | D13 |
| Plan sheets | Trailing spaces in week labels | Trim | High |
| `f12e8f54` | 13 explanations are the literal `nan` | Store no explanation | High |
| `f12e8f54` | Two choices end with a stray `C40` | Delete | High |
| `f12e8f54` | All 10 questions share the stem «کدام یک از گزینه‌های زیر صحیح است.» | Keep the generic stem | O1 |
| `f12e8f54` | «در سلول گالوانی Zn-SHE هیدروژن نقش کاتد را ایفا می‌کند» is marked false in questions 1, 6 and 9. In a Zn–SHE cell the SHE is the cathode, so it is arguably true, which would give those questions two correct answers | Teacher ruling; key unchanged meanwhile | D11 |
| `f12e8f54` | Text contains HTML tags and TeX (`$\displaystyle SO_{3}^{2-} $`) | Convert to plain Unicode | O2 |

## Proposed Design

Plans, week courses, exams and student data stay per school. Only topics,
courses and platform questions become global.

### 1. Data model (a new migration; applied migrations are not edited)

- `curriculum.topics`: drop the requirement that `school_id` is set. Add
  `source_code varchar(32)`, unique where not null, so the Moshavere code is
  kept on the topic. Phases 2 and 4 then resolve codes without a side file.
  Existing school-owned rows, if any (O13), keep their `school_id`.
- `curriculum.questions`: `school_id` becomes nullable. Null means a platform
  question; a value means the school's own question.
- `academic.courses`: add `track varchar(16)` (nullable; values per O12) and
  `topic_id bigint` referencing the course's tree node. Because topics are
  global, the link needs no per-school mapping.
- `academic.enrollments`: add `track varchar(16)` (nullable), set for each
  academic year.
- New `curriculum.question_reports`: `id`, `question_id`, `reporter_user_id`,
  `school_id`, `report_type` (wrong answer, typo, unclear text, other),
  `comment`, `status` (open, reviewed, fixed, closed), `resolution_note`,
  `created_at`, `updated_at`.

### 2. Permissions and visibility

- **Platform admin** (the `PLATFORM_ADMIN_EMAILS` mechanism): creates and edits
  topics, courses and platform questions; works the report queue.
- **School `CURRICULUM_MANAGE`** (school admins and teachers): creates, edits
  and deletes only the school's own questions.
- **School `CURRICULUM_VIEW`**: reads platform topics and platform questions,
  plus the school's own questions. Another school's questions are invisible.
- **Students**: report questions (D8) but do not browse the bank.
- **Parents**: no access.
- **Reports**: a school sees its own reports and their status (D9). The
  platform admin sees all reports.

### 3. Course selection and loading

1. The teacher picks a grade (1–12). The list comes from `academic.courses`
   for that grade (D7).
2. The teacher picks a course. The client loads only that course's tree,
   through a new subtree endpoint. The endpoint uses a recursive query on
   `parent_id`, so it reads that course's nodes only, not the whole tree.
3. For a plan or exam assigned to a **specific student**, the grade and track
   come from that student's enrollment for the current academic year. The list
   is the common courses (no track) plus the courses with the student's track
   (D6). Example: for a grade-12 mathematics student, «فارسی» (common) appears,
   and «فیزیک ۳ (ریاضی)» appears, but «فیزیک ۳ (تجربی)» does not.
4. Bank browsing has no student, so the teacher chooses both grade and course.
5. The three pages that fetch the whole tree (`curriculum.js:174`,
   `exams.js:139`, `study-plans.js:231`) switch to the subtree endpoint.
   The whole-tree endpoint can remain for platform-admin tools.

A grade with no tracks keeps the track empty, and the filter then depends on
the grade alone.

### 4. Question reports

- A «گزارش مشکل» action sits beside each question in the bank and in any view
  where a student sees a question (D8). The student-facing view must be
  checked before implementation.
- The form asks for a report type and a comment.
- The report lands in the platform-admin queue. The admin marks it reviewed,
  then fixed (after editing the question) or closed.
- The reporting school sees each status (D9). Nothing changes the question
  except the platform admin (D10).
- Reports live in `curriculum`, not in a new top-level module. A separate
  reports section, planned for later, can link to them. Creating a new
  top-level module would need the `new-module` skill (`CLAUDE.md`).

### 5. Import

- **Converter** (one-off, offline): reads the sources and writes a JSON bundle.
  It applies only the reviewed corrections, strips HTML to plain text, and
  refuses to write while any referenced code is missing from the tree and is
  not covered by a correction. The bundle includes the catalog courses
  (`gradeLevel`, `name`, `track`, `topicCode`).
- **Platform importer**: one run by the platform admin, with the mandatory
  dry run. It writes through platform endpoints, which must be built: the
  existing topic, question and course endpoints are school-scoped. It is
  idempotent by `source_code`, so a re-run creates nothing. The alternative
  (a single atomic bundle endpoint) is recorded below.

```json
{
  "format": 2,
  "source": "moshaver-tree-v2",
  "courses": [
    { "gradeLevel": 12, "name": "فیزیک ۳", "track": "RIAZI", "topicCode": "30119" }
  ],
  "topics": [
    { "code": "30122", "parentCode": "30119", "name": "قانون سوم نیوتون", "sortOrder": 2 }
  ],
  "questions": [
    { "topicCode": "24145", "text": "...", "choices": ["...", "...", "...", "..."],
      "correctChoice": 1, "answer": null, "difficulty": null, "scope": "platform" }
  ],
  "weekCourses": []
}
```

`track` and the course names are placeholders until O12 is answered.
`weekCourses` stays empty until phase 4.

**Alternative considered:** one atomic `POST` of the whole bundle from the
platform admin. It would give an all-or-nothing import, but it needs a large
request and its own error handling. Revisit it if the one-by-one import
proves slow or fragile.

## Phases

0. **Platform foundation.** The migration (§1), platform endpoints,
   permissions (§2), the track field and the subtree endpoint. Needs O12 and
   O13. Nothing is imported yet.
1. **Tree and course selection.** Import the tree (D2) and link the courses.
   The grade → course flow works end to end. Needs phase 0.
2. **Questions.** The 10 chemistry questions after the corrections above, with
   the generic stem (O1), plain-text conversion (O2) and the teacher ruling
   (D11). School-added questions become available. Needs phases 0 and 1.
3. **Reports.** The report button, the queue and the status flow (D8–D10).
   Needs phase 0; it can run in parallel with phases 1–2.
4. **Weekly plans.** Week courses from the six sheets. Needs O3–O11, and course
   weights for each plan (O10).

## Acceptance Criteria

- A platform admin's dry run creates nothing, and the topic and question counts
  are unchanged afterwards.
- A real run creates exactly the tree (5,656 topics) with the source parent
  links and sibling order. Counts match the converter's report.
- Running the import again creates nothing.
- A school admin or teacher is refused (403) on any platform topic, course or
  platform-question write.
- A teacher sees platform questions and the school's own questions, and no
  other school's questions.
- The subtree endpoint returns only the chosen course's nodes.
- For a student with a track, the course list contains the common courses and
  that track's courses, and no other track's courses.
- A report is visible to the reporting school (with its status) and to the
  platform admin, and to no other school.
- Report status moves only open → reviewed → fixed or closed. Creating a report
  leaves the question unchanged.
- The converter refuses a bundle while `301222` or `22254` is uncorrected, and
  names them.
- Imported questions keep their correct choice, have no HTML tags, no `nan` and
  no stray `C40`.
- The load time of the three pages is measured with the largest course, and the
  result is recorded in this document before phase 1 closes.

## Tests That Will Prove It

- Converter: a stdlib `unittest` with small fixtures for each failure the
  review found: a UTF-16 tree fragment; a sheet with an unknown code and an
  «ازمون» row; a question with HTML, `nan` and `C40`.
- Platform endpoints: permission tests (platform admin allowed, school admin
  and teacher refused, anonymous refused), following the existing
  `*FlowIntegrationTest` style.
- Subtree endpoint: a test that the result contains only the course's nodes.
- Visibility: school A cannot see school B's questions; both see platform
  questions.
- Track filter: a common course is visible to all tracks; a track-specific
  course is visible only to its track.
- Reports: create, list, the platform queue, status transitions, and the
  reporting school seeing only its own reports.
- Migrations: tested on a database that already holds school-owned rows
  (O13).
- Importer: run against a local instance, once with `--dry-run`, once for real,
  and once more to prove idempotency. Record the counts in the PR.
- `ModularityTests` after the changes, because the reports sit inside
  `curriculum` and the study-plan and exam validations change.

## Compatibility And Migration

- `MOSHAVERE_PORT_SPEC.md` §2 and `openspec/specs/current-system.md` describe
  per-school curriculum content. This proposal supersedes that rule for
  topics, courses and questions (D3, D4). Those documents are updated in the
  same change that implements it, not earlier.
- New migrations only; applied migrations are not edited.
- The paid modules (`CURRICULUM`, `STUDY_PLANNER`, `EXAM`) still cannot be
  granted over HTTP, so the importer and any test school need them granted in
  SQL.
- Study plans, week courses, exams and student data stay per school, so
  existing plan and exam behaviour is unchanged except for the topic validation
  (plans must reference global topics).
- The summer weeks (Tir–Shahrivar) fall outside the yearly-plan range
  (1 Mehr – 31 Khordad, [`yearly-plan.md`](yearly-plan.md)). Study plans do not
  use that range, so this is not a conflict, but O3 must name a Solar year.
- The academic year switches on 1 Mordad. Summer weeks that straddle the switch
  need a rule when O3 is decided.
- Undoing a mistaken platform import needs manual SQL. The mandatory dry run and
  a staging environment are the protection.
