# Question box («باکس سؤالات»)

Status: proposed 2026-10-13; **not implemented**. Product-owner decisions are recorded below; the open questions at the end are unresolved and must not be settled silently.

## Purpose

Teachers need to build **printable question sheets** (برگه‌ی سؤال) from multiple-choice **and essay** (تشریحی) questions, with images. This is separate from the exam builder (`exam` module), which stays multiple-choice only so that scoring remains automatic. A later, AI-assisted essay exam builder may reuse the question box, but nothing for it is built now.

## Decisions (product owner, 2026-10-13)

1. **Essay questions are not part of the exam builder** (they cannot be scored automatically). They exist only in the question box.
2. **Sources:** a sheet can mix questions from the existing curriculum bank (read-only, multiple-choice) and the teacher's own questions (both types) kept in the box.
3. **Entering a teacher question:** either typed text (LaTeX formulas as the bank already renders with KaTeX) with attached images, or an **image-only question** (a photo/scan of the whole question). The teacher picks one per question.
4. **Output:** a browser print page with `@media print` CSS (Chrome is the reference browser). No server-side PDF.
5. **Visibility:** a teacher's own questions are visible and usable by **all teachers of the same school**.
6. **Editing:** only the creator and the school admin may edit or delete a question. If the creator no longer has an active teacher role in the school, the question is treated as orphaned and any holder of the question-box permission may edit it.
7. **Sheets are saved** and can be reopened, edited and reprinted.
8. **Sections:** a sheet is divided into titled sections (بخش), e.g. «بخش اول: تستی», each with an optional instruction line and its own question order.
9. **Headers:** a sheet uses a reusable header (سربرگ) chosen from the school's saved headers. A header is either a configurable built-in layout or an uploaded header image (see Design).
11. Header layouts: 2-3 built-in layouts are enough to start (product owner, 2026-10-13).
10. Bank questions that have no image (or are image-only) are accepted as they are; no change to the bank is needed.

## Design

### Module

New Spring Modulith module `questionbox` (apply the `new-module` skill; schema `questionbox`, migration `V26`, a new module code and permission, e.g. `QUESTION_BOX_MANAGE`, registered in `IamSeedDataRunner`). It reads bank questions only through `CurriculumFacade` and the creator's active roles only through `IamFacade.findActiveRoleCodes`. `curriculum` and `exam` are not changed.

### Data

- `questionbox.questions`: `school_id`, `created_by_user_id`, optional `topic_id`, `type` (`MULTIPLE_CHOICE` | `ESSAY`), `content_mode` (`TEXT` | `IMAGE_ONLY`), `text`, `choice1..4` and `correct_choice` (multiple-choice only, nullable), `answer` (worked solution), optional `suggested_score`, timestamps.
- `questionbox.question_images`: `question_id`, `role` (stem, choice 1-4, answer), `content_type`, `data bytea` (same reasoning as `ExpenseAttachmentContentEntity`), size limits to be set. Served by an authenticated endpoint scoped to the school.
- `questionbox.headers`: `school_id`, `name`, `mode` (`LAYOUT` | `IMAGE`). `LAYOUT` stores a layout choice (a small fixed set), the school name/logo toggle, and which fields to show (title, subject, class, teacher, date, duration, student name, score box). `IMAGE` stores an uploaded header image (`bytea`, same limits as question images) printed full-width. Headers are shared by the school's teachers; edit/delete follows decision 6.
- `questionbox.sheets`: `school_id`, `created_by_user_id`, `title`, `header_id`, values for the header fields (date, duration, ...), print options (show answer space for essays, include answer key).
- `questionbox.sheet_sections`: `sheet_id`, `position`, `title`, optional `instruction`.
- `questionbox.sheet_items`: `section_id`, `position`, and either `box_question_id` or `bank_question_id` (exactly one).
- Deleting a box question is a soft delete (`deleted_at`): it disappears from the box but saved sheets keep printing it unchanged.

### Access

`@RequiresModule` and `@RequiresPermission` on every endpoint, `TenantContext.requireMatches` on `{schoolId}` paths. Granted to `TEACHER` and `ADMIN`. Edit/delete rule from decision 6 is enforced in the facade, not the controller. Bank questions are never edited here.

### Pages

- `/question-box`: list with search and type filter, add/edit form with image upload.
- `/question-sheets`: saved sheets; builder to pick from the box and the bank, reorder, and open the print page.
- Print page: school header, title, essay answer space, optional answer key, print CSS.
New or restructured JTE pages must follow the `design-page` skill.

### Out of scope

Online exams from these questions, essay grading, AI generation, server-side PDF, sharing across schools, and adding essay questions to `curriculum.questions`.

## Open questions

- Image limits (max size per file, max images per question, allowed types).
- Per-question score display on the print page (the header covers student name/class and a score box).
