# Moshavere Content Import (ورود محتوای «مشاور با من»)

Status: proposed 2026-10-09. Not implemented. No product-owner decisions are
recorded yet; the questions in **Open Questions** block phases 2 and 3, and
three of them (1–3) must be answered before phase 1 starts.

The `curriculum` and `studyplan` modules were ported from Moshavere in July
2026 ([`../../MOSHAVERE_PORT_SPEC.md`](../../MOSHAVERE_PORT_SPEC.md) §4, §6),
but only the logic and schema came across. The content did not: a school
starts with an empty topic tree, an empty question bank and no study plans.
A set of Moshavere content files was shared for review on 2026-10-09. This
proposal says which of them can be imported, what must be corrected first, and
how to import them without bypassing the domain rules.

The source files are not in the repository. They are referred to below by
content and by the ID prefix they were uploaded with.

## Verified Baseline

From code, not from earlier docs:

- `curriculum.topics` has `id`, `school_id` (not null), `parent_id`, `name`
  (`varchar(250)`) and `sort_order`. There is no external or source-code
  column, so a Moshavere topic code cannot be stored on a topic
  ([`V1__baseline_schema.sql:322`](../../src/main/resources/db/migration/V1__baseline_schema.sql)).
- Topics are per school. This is a recorded decision: "curriculum content
  (topics, questions, plans) is per-school, not a shared platform library"
  (`MOSHAVERE_PORT_SPEC.md` §2, confirmed 2026-07-11).
- `CurriculumFacadeImpl.createTopic` rejects a blank name and a parent from
  another school. It does not enforce unique names among siblings
  ([`CurriculumFacadeImpl.java:32`](../../src/main/java/com/example/roshdeandishe/curriculum/internal/CurriculumFacadeImpl.java)).
- The topic and question APIs are `POST`/`GET` only:
  `/api/admin/schools/{schoolId}/topics`, `.../topics/tree` and
  `.../questions`. There is no edit or delete. They need the `CURRICULUM`
  module, `CURRICULUM_MANAGE` to write and `CURRICULUM_VIEW` to read
  (`TopicController.java:29,39`, `QuestionController.java:27,37`).
- `curriculum.questions` requires `text` and `choice1`–`choice4`, and
  `correct_choice` must be 1–4. `answer` (the explanation) and `difficulty`
  are optional. `topic_id` is required (`V1__baseline_schema.sql:331`,
  `CurriculumFacadeImpl.java:68`).
- Question text and choices are rendered with Alpine `x-text`
  (`curriculum.jte:104`, `exam-take.jte:63,71`, `exams.jte:120`). HTML tags
  in the text would show literally. No math renderer exists in the templates
  or static assets.
- A plan needs at least one course weight (`topic_id`, `weight >= 0`,
  `priority >= 1`, one row per topic). The suggester gives each weighted
  course `totalTime * weight` of the week's study time. There are no
  sub-topic weights anywhere
  ([`StudyPlanFacadeImpl.java:82`](../../src/main/java/com/example/roshdeandishe/studyplan/internal/StudyPlanFacadeImpl.java),
  [`FillSlices.java:96`](../../src/main/java/com/example/roshdeandishe/studyplan/internal/FillSlices.java)).
- A week course is a plan, a `week_start` date, a `course_topic_id` and up to
  10 sub-topic IDs, unique per (plan, week start, course).
  `createWeekCourse` checks that every topic belongs to the school and that
  `week_start` falls on the school's week-start day. It does not check that
  sub-topics descend from the course topic (`StudyPlanFacadeImpl.java:128`,
  `:529`).
- The suggester attaches a week course's sub-topics only when its
  `course_topic_id` equals a weighted course of the plan
  ([`Suggestor.java:70`](../../src/main/java/com/example/roshdeandishe/studyplan/internal/Suggestor.java)).
- The week-course API is `POST`/`GET` only, under the `STUDY_PLANNER` module
  with `STUDYPLAN_MANAGE` (`WeekCourseController.java:21,31`).
- The curriculum, exams and study-plans pages fetch the whole topic tree
  (`curriculum.js:174`, `exams.js:139`, `study-plans.js:231`). The study-plans
  page flattens it into select options (`study-plans.js:242`).
- The application has no spreadsheet or CSV parser, and `pom.xml` has no
  library for one.
- [`scripts/seed-demo-data.py`](../../scripts/seed-demo-data.py) is the
  precedent for bulk content: it fills a school through the public REST API
  so nothing bypasses the domain rules. It also records that the paid
  modules (`CURRICULUM`, `STUDY_PLANNER`, `EXAM`) cannot be granted over HTTP.

## Source Review (2026-10-09)

| Source (upload prefix) | Content | Use |
|---|---|---|
| `69df89ac` tree_expanding_view_v2 (`399ad317` is a byte-identical copy) | «درخت مشاور با من»: 5,656 topics, 23 root courses, depth 0–5, 4,206 leaves, every node labelled `name(code)` | **Import** (phase 1) |
| `a4821972` tree_expanding_view | Older tree with the same 5,656 codes, 24 roots, 104 nodes renamed or moved | Superseded by v2, pending Q2 |
| `f12e8f54` html1 | Chemistry 3 multiple-choice export: 10 questions, verdicts, explanations, topic paths | **Import after cleanup** (phase 2) |
| `0ab2c2a9`, `e71a6871`, `e7fde81a`, `84c4a8d4`, `85877446`, `82a3abf4` | Summer weekly plans (physics, religion, biology, chemistry, humanities arts and sciences, literature): one Moshavere code per row under a Persian week label such as «اول تیر» | **Import after decisions** (phase 3) |
| `b33fbf93` | Physics 3 (math track) sub-topic weights within each topic | No model for it; see Q9 |
| `2becf8a4` shimis.csv | Chemistry true/false statements with corrections | Excluded: 43 of 44 rows are corrupted (legacy font encoding, not readable Persian), 15 rows have the wrong field count, and there is no true/false model |
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
- In html1, every question has exactly one choice marked «درست» and it matches
  the «پس گزینه‌ی N صحیح است» line.
- No week in a plan sheet has more than 10 codes.

### Corrections Required Before Import

| Source | Item | Correction | Confidence |
|---|---|---|---|
| `b33fbf93` | Sub-topic `301222` | `30122` (Newton's third law; its siblings are 30120 and 30121) | High |
| `e7fde81a` biology | Code `22254` (not in the tree) | `26254` (Biology 2, chapter 1, lesson 2; fits between 26248 and 26267) | Needs confirmation |
| `e7fde81a` biology | Row «ازمون» in the code column | Not a topic; see Q7 | Decision |
| `84c4a8d4` chemistry | Label «سوم و چهارم مرداد» spans two weeks | See Q7 | Decision |
| `0ab2c2a9`, `e7fde81a`, `85877446` | Codes 30102, 22736, 19948, 19951, 19952 appear in more than one week | Confirm intended | Decision |
| Plan sheets | Trailing spaces in week labels | Trim | High |
| `f12e8f54` | 13 explanations are the literal `nan` | Store no explanation | High |
| `f12e8f54` | Two choices end with a stray `C40` | Delete | High |
| `f12e8f54` | All 10 questions share the stem «کدام یک از گزینه‌های زیر صحیح است.» | See Q4 | Decision |
| `f12e8f54` | «در سلول گالوانی Zn-SHE هیدروژن نقش کاتد را ایفا می‌کند» is marked false in questions 1, 6 and 9. In a Zn–SHE cell the SHE is the cathode, so it is arguably true, which would give those questions two correct answers | See Q6 | Decision |
| `f12e8f54` | Text contains HTML tags and TeX (`$\displaystyle SO_{3}^{2-} $`) | Convert to plain text; see Q5 | Decision |

## Proposed Approach

Import through the existing REST API with an offline script, following
`scripts/seed-demo-data.py`. Phases 1–3 need no schema change, no new
endpoint and no application change. The imported content becomes ordinary
school-owned content, consistent with the per-school decision.

### Canonical bundle

The importer reads one JSON bundle and nothing else:

```json
{
  "format": 1,
  "source": "moshaver-tree-v2",
  "topics": [
    { "code": "7", "parentCode": null, "name": "فیزیک", "sortOrder": 5 },
    { "code": "30122", "parentCode": "30119", "name": "قانون سوم نیوتون", "sortOrder": 2 }
  ],
  "questions": [
    { "topicCode": "24145", "text": "...", "choices": ["...", "...", "...", "..."],
      "correctChoice": 1, "answer": null, "difficulty": null }
  ],
  "weekCourses": [
    { "planTitle": "...", "weekStart": "YYYY-MM-DD", "courseCode": "7",
      "subTopicCodes": ["30085"] }
  ]
}
```

`sortOrder` is the node's position among its siblings in the source tree. All
text in the bundle is plain text. `weekCourses` stays empty until phase 3's
decisions exist.

### Converter (one-off, offline)

A converter script reads the source files and writes the bundle. It:

- decodes the tree (UTF-16) and the question export;
- applies only the corrections listed in a reviewed corrections file;
- strips HTML to plain text;
- refuses to write the bundle if any referenced code is missing from the tree
  and not covered by a correction;
- emits a report of counts, corrections applied and rows rejected.

It needs `openpyxl` for the plan sheets, which is a run-time requirement of
the script, not of the application.

### Importer (per school)

The importer runs against one school with a `SCHOOL_ADMIN` or `TEACHER`
account:

```
python3 scripts/import-curriculum.py --url ... --email ... --password ... \
    --school-id 12 --bundle bundle.json --roots 7,8,12 [--dry-run]
```

- **Subtree selection.** It imports only the requested roots and their
  descendants. The full tree is 5,656 topics and every topic page loads all of
  them, so a school imports the courses it teaches (Q3).
- **Idempotent.** It reads the school's topics and matches existing nodes by
  (parent, name) path. That path is unique in the source. It creates only
  missing nodes, parents before children. Questions are skipped when a
  question with the same topic, text and choices already exists.
- **Dry run first.** `--dry-run` validates everything and prints what it would
  create, without writing. The topic, question and week-course APIs have no
  edit or delete, so a wrong import can only be undone with SQL.
- **Mapping output.** It writes a file of Moshavere code → school topic ID.
  Phases 2 and 3 resolve codes through it, or by the same path match if the
  file is lost.
- **Stops early** on the first `module-not-entitled` or permission error,
  before writing anything else.

### Alternative Considered: Server-Side Import

A nullable `curriculum.topics.source_code` (unique per school), a
JSON import endpoint and a facade method would give an atomic import, a
durable code mapping and self-service for schools. It costs a migration, a
new endpoint, new error cases and integration tests. It is not proposed for
now. Revisit it (phase 4) if imports become recurring, for example a new
summer plan every year, or if schools need to run them themselves.

## Phases

1. **Topic tree.** Converter, importer, and selected subtrees for one school.
   Needs Q1–Q3.
2. **Questions.** The 10 chemistry questions, after the html1 corrections.
   Needs Q4–Q6.
3. **Weekly plans.** Week courses from the six sheets. Needs Q7 and course
   weights for each plan, which no source file contains.
4. **Optional server-side import** as described above.

## Acceptance Criteria

- `--dry-run` against a school creates nothing: topic and question counts are
  unchanged afterwards.
- Importing the chosen roots creates exactly the topics of those subtrees,
  with the source parent links and sibling order. Counts per root match the
  converter's report.
- Running the same import again creates nothing.
- A school without `CURRICULUM` gets `module-not-entitled` and nothing is
  written.
- The mapping file lists every imported code exactly once.
- The converter refuses a bundle while `301222` or `22254` is uncorrected and
  names them in its report.
- Imported questions keep their correct choice, contain no HTML tags and no
  `nan`, and have no stray `C40`.
- The importer sends only the given school's `X-School-ID`.
- The load and usability of the curriculum, exams and study-plans pages are
  measured with the largest imported subtree, and the result is recorded in
  this document before phase 1 is closed.

## Tests That Will Prove It

- Converter: a stdlib `unittest` with small fixtures for each failure the
  review found:
  - a UTF-16 tree fragment;
  - a sheet with an unknown code and an «ازمون» row;
  - a question with HTML, `nan` and `C40`.
- Importer: run against a local instance on a fresh school with
  `CURRICULUM`, once with `--dry-run`, once for real, and once more to prove
  idempotency. Record the counts in the PR.
- Phases 1–3 change no Java code, so no Java tests change. Phase 4 would add
  cases to `CurriculumFlowIntegrationTest`.

## Compatibility And Migration

- No schema, API or behavior change in phases 1–3.
- The imported topics are ordinary school data. The school can add to them;
  until topic edit and delete exist, it cannot rename or remove them.
- Undoing a mistaken import needs manual SQL, hence the mandatory dry run.
  Run the first import on a staging school.
- The importer needs the paid module already granted. That cannot be done over
  HTTP, the same restriction `seed-demo-data.py` documents.
- The summer weeks (Tir–Shahrivar) fall outside the yearly-plan range
  (1 Mehr – 31 Khordad, [`yearly-plan.md`](yearly-plan.md)). Study plans do
  not use that range, so this is not a conflict, but the date rule in Q7 must
  name a Solar year.

## Open Questions

1. **Rights.** May this repository store, and schools receive, the Moshavere
   tree and questions? Moshavere is a separate project family. If not, the
   bundle stays outside the repository and the converter reads the sources
   from a path.
2. **Tree version.** Is v2 (`69df89ac`, 23 roots) canonical? The older tree
   differs in 104 nodes.
3. **Which subtrees.** Does a school import the whole tree or chosen courses,
   and who chooses?
4. **Question stems.** Will real stems be supplied, or should all 10 questions
   keep the generic sentence? `text` is required.
5. **Math notation.**
   - Option A: convert TeX to plain Unicode text (for example `SO₃²⁻`) during
     conversion.
   - Option B: add a math renderer. A CDN is not allowed, and a WebJar would
     need approval.
   - Either way, question text keeps rendering with `x-text`.
6. **Disputed verdict.** A chemistry teacher should rule on the Zn–SHE
   statement in questions 1, 6 and 9.
7. **Weekly plans.**
   - Which Solar year do the sheets belong to, and which date is «اول تیر»
     (the week containing 1 Tir, or the first full week)? Schools whose week
     starts on Monday also need a rule.
   - How should «سوم و چهارم مرداد» and the «ازمون» row be handled?
   - Which tree node is each sheet's course (for example the root «فیزیک» or
     the book «فیزیک 3 (ریاضی)»)? It must also be the plan's weighted course
     for the suggester to use the sub-topics.
   - The physics sheet mixes Physics 1 and Physics 3 codes.
   - Who supplies the course weights?
8. **Biology code.** Confirm `22254` → `26254` against the source.
9. **Sub-topic weights.** Are the physics 3 sub-topic weights (`b33fbf93`)
   needed? Nothing in the current model stores or uses them.
