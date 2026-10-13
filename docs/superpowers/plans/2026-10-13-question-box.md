# Question Box Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A new `questionbox` module where teachers keep multiple-choice and essay questions (typed or image-only, with images), compose saved sheets from sections of those and curriculum-bank questions, and print them from the browser under a chosen header.

**Architecture:** New Spring Modulith module `questionbox` (schema `questionbox`, migration `V26`) following the `attendance` layout. It reads bank questions only through `CurriculumFacade` and the creator's active roles only through `IamFacade`. `curriculum` and `exam` are untouched. Images live in Postgres `bytea` like `finance.ExpenseAttachmentContentEntity`. Pages are thin JTE shells (`web/controller`) over REST, printing is an `@media print` page.

**Tech Stack:** Java/Spring Boot, Spring Modulith, JPA + Flyway, JTE, Alpine.js, Bulma, KaTeX (`/js/math.js`), Testcontainers integration tests.

**Spec:** [`openspec/changes/question-box.md`](../../../openspec/changes/question-box.md)

## Global Constraints

- Package layout per `.agents/skills/new-module/SKILL.md` and `openspec/conventions.md` (by-type: `controller/`, `dto/request/`, `dto/response/`, `internal/`, `shared/`). Apply `new-module` before Task 1 and `design-page` before Task 6.
- Module code `QUESTION_BOX` (SCHOOL scope), permission `QUESTION_BOX_MANAGE`; every endpoint has both `@RequiresModule` and `@RequiresPermission`, and `TenantContext.requireMatches(schoolId)`.
- Error handling: sealed `QuestionBoxError`, `QuestionBoxException`, `@Component("questionBoxExceptionHandler")` advice using `ProblemDetailFactory`.
- Flyway owns the schema: add `questionbox` to `spring.flyway.schemas`, new `V26__question_box.sql`, never edit applied migrations.
- Edit/delete of a box question: creator, or `SCHOOL_ADMIN`, or anyone if the creator has no active `TEACHER` role in the school (orphaned). Delete is soft (`deleted_at`).
- A question is `MULTIPLE_CHOICE` or `ESSAY`; `content_mode` is `TEXT` or `IMAGE_ONLY`. Choices and `correct_choice` exist only for multiple-choice TEXT questions.
- `sheet_items` reference exactly one of `box_question_id` / `bank_question_id`. Bank questions are read-only here.
- Header `mode` is `LAYOUT` (2-3 built-in layouts) or `IMAGE`.
- UI strings are Persian, matching neighbouring pages. Print reference browser: Chrome.
- Not built: online exams from these questions, essay grading, AI, server-side PDF, cross-school sharing, essay in `curriculum.questions`.
- **Assumptions awaiting product-owner confirmation** (record in the spec's open questions when built): image limit 5 MB per file (same as expenses), types `image/png`, `image/jpeg`, `image/webp`, at most 6 images per question; the module is granted to new schools free like `ATTENDANCE`, with a startup backfill.

## Review Focus

- Image-only question with no image uploaded: rejected (400), never saved empty.
- Essay question submitted with choices or `correctChoice`: rejected rather than silently dropped.
- Question of another school requested by id (box or sheet item): 404, not 403 and never its content.
- Soft-deleted question inside a saved sheet: sheet still lists and prints it; box list and builder picker no longer offer it.
- Bank question later removed/unavailable when a sheet is opened: item renders a "سؤال در دسترس نیست" placeholder instead of a 500.
- Uploading a non-image or oversize file (or SVG/HTML renamed to `.png`): rejected by content type and size; image endpoint serves with the stored content type plus `X-Content-Type-Options: nosniff`.
- Orphan rule: a creator whose membership was deactivated stops being "the creator" for authorization on the next request (no cache).
- Reordering sections/items with duplicate or missing positions normalises to a contiguous order.

---

## File Structure

`src/main/java/com/example/roshdeandishe/questionbox/` (all under it):
- `QuestionBoxFacade.java` (+ `package-info.java`): public interface and records.
- `controller/QuestionBoxController.java`, `controller/QuestionSheetController.java`, `controller/QuestionHeaderController.java`, `controller/QuestionImageController.java`.
- `dto/request/*`, `dto/response/*`.
- `internal/`: `BoxQuestionEntity`, `QuestionImageEntity`, `HeaderEntity`, `SheetEntity`, `SheetSectionEntity`, `SheetItemEntity` + repositories; `QuestionBoxFacadeImpl` (questions, images, authorization), `SheetService` (sheets, sections, items, resolved view), `HeaderService`.
- `shared/`: `QuestionBoxError`, `QuestionBoxException`, `QuestionBoxExceptionHandler`, `QuestionBoxProblemResolver`.

Other files: `src/main/resources/db/migration/V26__question_box.sql`; `application.yml` (flyway schemas); `iam/internal/IamSeedDataRunner.java` (module, permission, grants, demo entitlement, free-tier backfill); `curriculum/CurriculumFacade.java` (one read method); `web/controller/QuestionBoxPageController.java`, `web/model/NavCatalog.java`; JTE `question-box.jte`, `question-sheets.jte`, `question-sheet-print.jte`; `static/js/question-box.js`, `question-sheets.js`; tests under `src/test/java/com/example/roshdeandishe/`.

---

### Task 1: Module skeleton, schema, permission, errors

**Files:**
- Create: `questionbox/package-info.java`, `QuestionBoxFacade.java` (empty-method interface for now), `shared/*` (four classes above), `V26__question_box.sql`
- Modify: `application.yml` (`spring.flyway.schemas` add `questionbox`), `IamSeedDataRunner.java`
- Test: `QuestionBoxModuleBootTest.java` (new)

**Interfaces:**
- Produces: `QuestionBoxError` cases `QuestionNotFound`, `SheetNotFound`, `HeaderNotFound`, `ImageNotFound`, `InvalidQuestion(String msg)`, `InvalidImage(String msg)`, `InvalidSheet(String msg)`, `NotAllowed`; `QuestionBoxException(QuestionBoxError)`; module `QUESTION_BOX`, permission `QUESTION_BOX_MANAGE` granted to `TEACHER` and `SCHOOL_ADMIN`.

- [ ] **Step 1: Write the failing test** `QuestionBoxModuleBootTest`: `@SpringBootTest` with `TestcontainersConfiguration`; assert via `JdbcTemplate` that tables `questionbox.questions`, `question_images`, `headers`, `sheets`, `sheet_sections`, `sheet_items` exist, and that `iam` has module `QUESTION_BOX` and permission `QUESTION_BOX_MANAGE` granted to roles `TEACHER` and `SCHOOL_ADMIN`.
- [ ] **Step 2: Run** `./mvnw -q test -Dtest=QuestionBoxModuleBootTest` — Expected: FAIL (tables missing).
- [ ] **Step 3: Write `V26__question_box.sql`** with the six tables from the spec's Data section. Constraints: `type in ('MULTIPLE_CHOICE','ESSAY')`, `content_mode in ('TEXT','IMAGE_ONLY')`, `role in ('STEM','CHOICE_1'..'CHOICE_4','ANSWER')`, header `mode in ('LAYOUT','IMAGE')`, a check that exactly one of `box_question_id`/`bank_question_id` is non-null on `sheet_items`; `bank_question_id` has no foreign key (cross-module), `school_id`/`created_by_user_id` are plain ids; indexes on `(school_id, deleted_at)` and `(sheet_id, position)`. Image bytes in a `bytea` column of `question_images` and `headers.image` (nullable).
- [ ] **Step 4: Add `questionbox` to flyway schemas; seed module and permission** in `IamSeedDataRunner` (idempotent per item like neighbours) plus the demo-school entitlement list entry and the free-tier backfill/new-school grant mirroring `ATTENDANCE`.
- [ ] **Step 5: Create skeleton** (`package-info` with `@ApplicationModule(displayName="Question Box")`, sealed error, exception, handler with the explicit bean name, resolver mapping each case to a status: not-found cases 404, invalid cases 400, `NotAllowed` 403).
- [ ] **Step 6: Run** `QuestionBoxModuleBootTest` and `ModularityTests` — Expected: PASS.
- [ ] **Step 7: Commit** `feat(questionbox): module skeleton, schema and permission`.

### Task 2: Box questions (create, list, edit, soft delete, authorization)

**Files:** Create `internal/BoxQuestionEntity`, `BoxQuestionRepository`, `QuestionBoxFacadeImpl`, DTOs, `controller/QuestionBoxController`; Modify `QuestionBoxFacade`; Test `QuestionBoxQuestionsFlowIntegrationTest`.

**Interfaces:**
- Produces on `QuestionBoxFacade`:
  - `QuestionInfo createQuestion(Long schoolId, Long actorUserId, NewQuestion q)`
  - `QuestionInfo updateQuestion(Long schoolId, Long actorUserId, Long questionId, NewQuestion q)`
  - `void deleteQuestion(Long schoolId, Long actorUserId, Long questionId)` (soft)
  - `QuestionInfo getQuestion(Long schoolId, Long questionId)`; `List<QuestionInfo> listQuestions(Long schoolId, QuestionListFilter f)` where `QuestionListFilter(String search, QuestionType type, Long topicId)`.
  - records `NewQuestion(QuestionType type, ContentMode contentMode, Long topicId, String text, List<String> choices, Integer correctChoice, String answer, Integer suggestedScore)`, `QuestionInfo(Long id, Long schoolId, Long createdByUserId, boolean editable, ... same fields, List<ImageRef> images)`, enums `QuestionType`, `ContentMode`.
- REST under `/api/admin/schools/{schoolId}/question-box/questions` (`GET`, `POST`, `GET/PUT/DELETE /{id}`).
- Consumes: `IamFacade.findActiveRoleCodes(UserId, Long)`, `IamFacade.currentUserId()`.

- [ ] **Step 1: Write failing tests** in `QuestionBoxQuestionsFlowIntegrationTest` (each test builds its own school and users like `AttendanceFlowIntegrationTest`):
  - `teacherCreatesMultipleChoiceAndEssay` (201, essay has no choices);
  - `essayWithChoicesIsRejected` (400); `multipleChoiceNeedsFourChoicesAndCorrectChoiceOneToFour` (400); `textModeNeedsText` (400);
  - `otherTeacherSeesButCannotEdit` (list contains it with `editable=false`, `PUT` → 403);
  - `adminCanEditAny`; `orphanedQuestionEditableByAnyTeacher` (deactivate creator's membership via `IamFacade.setMembershipActive`, other teacher `PUT` → 200);
  - `otherSchoolQuestionIs404`; `deleteIsSoft` (gone from list, row has `deleted_at`); `studentGets403`.
- [ ] **Step 2: Run** `./mvnw -q test -Dtest=QuestionBoxQuestionsFlowIntegrationTest` — Expected: FAIL.
- [ ] **Step 3: Implement** the entity, repository, facade methods and controller. Authorization helper `canEdit(schoolId, actorUserId, entity)` in `QuestionBoxFacadeImpl`: true if creator, or actor has `SCHOOL_ADMIN` in `findActiveRoleCodes`, or the creator's `findActiveRoleCodes` lacks `TEACHER`. Validation per the Global Constraints, mapped to `InvalidQuestion` with a Persian message.
- [ ] **Step 4: Run** the test class — Expected: PASS.
- [ ] **Step 5: Commit** `feat(questionbox): teacher questions with soft delete and orphan editing`.

### Task 3: Question images

**Files:** Create `internal/QuestionImageEntity`, `QuestionImageRepository`, `controller/QuestionImageController`; Modify `QuestionBoxFacade`, `QuestionBoxFacadeImpl`; Test `QuestionBoxImagesFlowIntegrationTest`.

**Interfaces:**
- Produces: `ImageRef addImage(Long schoolId, Long actorUserId, Long questionId, ImageRole role, String contentType, byte[] content)`; `void removeImage(Long schoolId, Long actorUserId, Long questionId, Long imageId)`; `ImageContent getImage(Long schoolId, Long imageId)` returning `ImageContent(String contentType, byte[] content)`; records `ImageRef(Long id, ImageRole role)`, enum `ImageRole { STEM, CHOICE_1, CHOICE_2, CHOICE_3, CHOICE_4, ANSWER }`.
- REST: `POST .../questions/{id}/images` (multipart `file`, `role`), `DELETE .../questions/{id}/images/{imageId}`, `GET /api/admin/schools/{schoolId}/question-box/images/{imageId}` returning bytes with the stored content type and `X-Content-Type-Options: nosniff`.
- Rule: an `IMAGE_ONLY` question must have at least one `STEM` image to be valid; creating one without it is done in two steps, so validity is checked on read via `QuestionInfo.complete` (false until a stem image exists) and enforced when the question is added to a sheet.

- [ ] **Step 1: Write failing tests**: `uploadPngAndFetchBytesBack`; `rejectsTextFileRenamedToPng` (magic-byte check, 400); `rejectsOversize` (> 5 MB, 400); `rejectsSeventhImage` (400); `onlyEditorMayUpload` (403); `otherSchoolImage404`; `removeImage`; `imageOnlyQuestionWithoutStemIsNotComplete`.
- [ ] **Step 2: Run** — Expected: FAIL.
- [ ] **Step 3: Implement** with limits as constants in the facade impl; sniff magic bytes for PNG/JPEG/WEBP instead of trusting the declared type.
- [ ] **Step 4: Run** — Expected: PASS.
- [ ] **Step 5: Commit** `feat(questionbox): question images stored as bytea`.

### Task 4: Headers

**Files:** Create `internal/HeaderEntity`, `HeaderRepository`, `HeaderService`, `controller/QuestionHeaderController`, DTOs; Modify `QuestionBoxFacade`; Test `QuestionBoxHeadersFlowIntegrationTest`.

**Interfaces:**
- Produces: `HeaderInfo createHeader(Long schoolId, Long actorUserId, NewHeader h)`, `updateHeader(...)`, `deleteHeader(...)`, `listHeaders(Long schoolId)`, `HeaderImage getHeaderImage(Long schoolId, Long headerId)`, `setHeaderImage(Long schoolId, Long actorUserId, Long headerId, String contentType, byte[] content)`; `NewHeader(String name, HeaderMode mode, HeaderLayout layout, boolean showSchoolName, boolean showLogo, Set<HeaderField> fields)`; enums `HeaderMode {LAYOUT, IMAGE}`, `HeaderLayout {CLASSIC, COMPACT, BOXED}`, `HeaderField {TITLE, SUBJECT, CLASS, TEACHER, DATE, DURATION, STUDENT_NAME, SCORE_BOX}`.
- REST under `.../question-box/headers`; image at `.../headers/{id}/image` (`PUT` multipart, `GET`). Edit/delete uses the same `canEdit` rule as questions (creator, admin, orphan).

- [ ] **Step 1: Write failing tests**: `createLayoutHeaderWithFields`; `imageModeRequiresImageBeforeUse` (`complete=false` until uploaded); `layoutModeRejectsImage`; `fieldsPersistAsSet`; `editRulesMatchQuestions` (other teacher 403, admin 200, orphan 200); `otherSchoolHeader404`.
- [ ] **Step 2: Run** — Expected: FAIL.
- [ ] **Step 3: Implement**; reuse the image validation from Task 3 via a shared package-private `ImageValidator` in `internal/` (extract it in this task and make Task 3 call it).
- [ ] **Step 4: Run** — Expected: PASS, and Task 3 tests still PASS.
- [ ] **Step 5: Commit** `feat(questionbox): reusable sheet headers`.

### Task 5: Sheets, sections, items, resolved view

**Files:** Modify `curriculum/CurriculumFacade.java` (+ impl): add `List<QuestionInfo> findQuestionsVisibleToSchool(Long schoolId, Collection<Long> ids)` returning only questions visible to that school (platform or own). Create `internal/SheetEntity`, `SheetSectionEntity`, `SheetItemEntity`, repositories, `SheetService`, `controller/QuestionSheetController`, DTOs; Test `QuestionBoxSheetsFlowIntegrationTest`, `CurriculumVisibleQuestionsTest` (small).

**Interfaces:**
- Consumes: `CurriculumFacade.findQuestionsVisibleToSchool`, Task 2-4 facade methods.
- Produces: `SheetInfo createSheet(Long schoolId, Long actorUserId, SheetDraft d)`, `SheetInfo saveSheet(Long schoolId, Long actorUserId, Long sheetId, SheetDraft d)` (replaces sections/items wholesale and normalises positions), `deleteSheet`, `listSheets(Long schoolId)`, `ResolvedSheet resolveSheet(Long schoolId, Long sheetId)`.
  - `SheetDraft(String title, Long headerId, Map<HeaderField,String> headerValues, boolean showEssayAnswerSpace, boolean includeAnswerKey, List<SectionDraft> sections)`; `SectionDraft(String title, String instruction, List<ItemRef> items)`; `ItemRef(ItemSource source, Long questionId)` with `ItemSource {BOX, BANK}`.
  - `ResolvedSheet` = header info + sections of `ResolvedItem(int number, ItemSource source, boolean available, QuestionType type, ContentMode mode, String text, List<String> choices, Integer correctChoice, String answer, List<ImageRef> images)`; a missing/unavailable item has `available=false`. Numbering is continuous across sections.
- REST under `.../question-box/sheets` (`GET` list, `POST`, `GET/PUT/DELETE /{id}`, `GET /{id}/resolved`). Sheet edit/delete uses the same `canEdit` rule.

- [ ] **Step 1: Write failing tests**: `saveSheetWithTwoSectionsMixingBoxAndBank`; `resolvedNumbersContinueAcrossSections`; `boxQuestionOfOtherSchoolRejected` (400 `InvalidSheet`); `incompleteImageOnlyQuestionRejected`; `bankQuestionNotVisibleToSchoolRejected`; `softDeletedBoxQuestionStillResolvesInSavedSheet`; `deletedBoxQuestionNotAddableToSheet`; `unavailableBankQuestionResolvesAsPlaceholder`; `positionsNormalised`; `otherSchoolSheet404`; `unusableHeaderRejected` (image-mode header without image).
- [ ] **Step 2: Run** — Expected: FAIL.
- [ ] **Step 3: Implement** `SheetService` (validate all item ids in one batch per source, then persist). Resolution loads box questions including soft-deleted ones and bank questions in one batch each.
- [ ] **Step 4: Run** the new classes and `CurriculumFlowIntegrationTest` — Expected: PASS.
- [ ] **Step 5: Commit** `feat(questionbox): saved sheets with sections and resolved view`.

### Task 6: Pages (box, sheet builder, print)

**Files:** Create `web/controller/QuestionBoxPageController.java`, `question-box.jte`, `question-sheets.jte`, `question-sheet-print.jte`, `static/js/question-box.js`, `static/js/question-sheets.js`, `static/css/` print rules in the existing app stylesheet; Modify `web/model/NavCatalog.java`; Test `QuestionBoxPagesRenderTest`, `QuestionBoxPagesFlowIntegrationTest`.

**Interfaces:**
- Pages: `GET /question-box`, `GET /question-sheets`, `GET /question-sheets/{id}/print`. Controller only adds `navbar` (and `sheetId` for print); data comes from the Task 2-5 REST endpoints. Nav link (group «آموزش») for `TEACHER` and `ADMIN` with `.withPermissions("QUESTION_BOX_MANAGE")`.
- Print page: `@media print` hides the navbar and controls; header by mode (LAYOUT → the chosen layout/fields, IMAGE → full-width image); sections with title/instruction; multiple-choice choices as ۴ lines; essay answer space (blank lines block) when `showEssayAnswerSpace`; answer key on a separate page when `includeAnswerKey`; KaTeX rendered through `/js/math.js`; images inline; digits via `/js/digits.js`.

- [ ] **Step 1: Apply the `design-page` skill**, then write failing tests: `QuestionBoxPagesRenderTest` (the three templates render with a minimal model, include the KaTeX assets, print template contains the `@media print` hook class and a section title), `QuestionBoxPagesFlowIntegrationTest` (teacher gets 200 on the pages, student is redirected/403, sidebar shows the link for teacher and not for student).
- [ ] **Step 2: Run** — Expected: FAIL.
- [ ] **Step 3: Implement** controller, templates, scripts, print CSS and nav entry following `curriculum.jte`/`curriculum.js` patterns (file upload with `FormData`, list search via `list-search.js`).
- [ ] **Step 4: Run** the two test classes plus `ModularityTests` — Expected: PASS.
- [ ] **Step 5: Verify in the browser** with `preview_start`: create a multiple-choice, an essay and an image-only question, build a two-section sheet with a header, open the print page and use Chrome print preview. Expected: no navbar, formulas rendered, images and answer space present.
- [ ] **Step 6: Commit** `feat(questionbox): pages for the box, sheet builder and print`.

### Task 7: Documentation

**Files:** Modify `openspec/changes/question-box.md` (status line: implemented; note the image-limit assumptions), `openspec/specs/current-system.md` (current behavior), `openspec/project.md` (index entry), `openspec/architecture.md` (module list).

- [ ] **Step 1: Update the four documents** to match what was built; keep still-unconfirmed assumptions as open questions.
- [ ] **Step 2: Run** `./mvnw -q test` — Expected: all PASS.
- [ ] **Step 3: Commit** `docs: record the question box`.

---

## Self-review

- **Spec coverage:** decisions 1-11 map to Tasks 2-6 (sources: 5; text/image-only: 2-3; browser print: 6; school-wide visibility and edit rules: 2; saved sheets: 5; sections: 5; headers: 4; bank questions unchanged: 5 reads only; layouts: 4). Out-of-scope items are not planned.
- **Type consistency:** `NewQuestion`, `QuestionInfo`, `ImageRef`, `ImageRole`, `HeaderField`, `ItemSource`, `canEdit` are defined once (Tasks 2-5) and reused by name.
- **Gaps:** the two spec open questions (image limits, per-question score on print) are carried as assumptions/Not-built, not decided silently.
