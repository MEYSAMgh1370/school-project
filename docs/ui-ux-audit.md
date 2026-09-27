# UI/UX Audit

Date: 2026-08-07

Scope reviewed: OpenSpec docs, design docs, JTE templates, page JavaScript, shared CSS, page controllers, access-control annotations, and available frontend/integration tests. Source and tests are treated as stronger evidence than Markdown when they differ.

Evidence base: `openspec/project.md` was read first, followed by `openspec/architecture.md`, `openspec/specs/current-system.md`, conventions, proposed work, `DESIGN.md`, `FRONTEND.md`, `UX_ROADMAP.md`, `MOBILE_ROADMAP.md`, `MOSHAVERE_PORT_SPEC.md`, relevant labelled-legacy sections of `CLAUDE_LEGACY.md`, all current `docs/` content, implementation, and tests. No top-level `design/` or `ui/` directory exists. Browser evidence came from the running application at desktop/mobile viewports; source/tests overrode contradictory Markdown.

## 1. Executive Summary

Roshdeandishe is a real, usable Persian-first school administration product rather than a static prototype. It is a server-rendered Java/JTE application with Alpine.js and `fetch` for page behavior, Bulma 1.0.4 for components, and two custom CSS files. It covers identity, schools, membership and roles, classes, enrollment and guardians, grades, tuition/invoices/payments, expenses, curriculum, study plans, exams, and student/parent self-service. Attendance and a complete user-facing messaging product do not exist.

The strongest foundations are tenant-aware backend authorization, a shared SSR layout, consistent CSRF and `X-School-ID` usage, useful empty states on several pages, and a modest dependency footprint. The product is held back by page-by-page interaction patterns, role navigation that is not permission-driven, unscaled tables, inconsistent financial/date formatting, incomplete error recovery, and dense mobile workflows.

Two critical defects and several high-risk defects were verified, not inferred:

- Public `/activate` interpolated the query token into an Alpine expression. Browser HTML entity decoding made a crafted token executable; a harmless probe set a property on `<html>`. This was a reflected script-injection vulnerability and is fixed in this audit.
- Teachers can manage grades but cannot read the full membership directory. The grade page used that forbidden directory to resolve student names, causing every roster row to become `نامشخص` and creating a material risk of recording a grade against the wrong learner. The UI now uses the existing least-privilege `MEMBER_NAMES_VIEW` endpoint, in bounded batches.
- Exam answers could be lost when users navigated while a save was in flight. The fix adds per-question pending state, optimistic rollback, `keepalive`, and announced save status without freezing the entire timed attempt.
- Persian/Arabic phone digits were stored verbatim while OTP lookup used a different representation. Server-side canonicalization and uniqueness checks now use ASCII digits, with a registration-to-OTP regression test.

Important stack correction: no HTMX, `hx-*` attribute, Bump CSS source, or Bump dependency exists. Current authoritative evidence is `layout/main.jte`, `pom.xml`, `FRONTEND.md`, and repository-wide searches. Recommendations therefore retain JTE + Alpine/fetch + Bulma and do not introduce a large JavaScript framework.

Rendered review was performed against the running application at 1440px and 390px for landing, login, dashboard, people, finance, expenses, and study-plan surfaces. It confirmed a desktop navbar that clips controls even at 1440px, horizontally scrolled tables that hide action context on mobile, English/Gregorian browser date controls inside Persian workflows, and dense study-plan controls with weak hierarchy.

Changes made in this audit include the critical fixes above; static activation/registration components; authentication live regions and autocomplete; skip links/main landmarks; modal focus trap/open/return behavior; unique dynamic labels and IDs; action-column names; and ordinary-control semantics for visual tab groups that do not implement the ARIA Tabs keyboard contract. The working tree already contained unrelated/uncommitted JTE and CSS improvements before this audit (navbar button semantics, header scopes, expense labels, logical CSS utilities). Those were reviewed but are not claimed as audit-authored changes; see `docs/ui-ux-implementation-report.md`.

Current score after the safe fixes: usability 5/10, visual consistency 6/10, accessibility 4/10, responsiveness 4/10, journey completeness 5/10, implementation quality 6/10, and document-to-code alignment 4/10. The fixes remove immediate security and data-integrity risks but do not solve navigation IA, table scalability, absent modules, or the design-document contradictions.

## 2. Product And Role Map

### Main Roles

| Role | Current UI tasks | Notes |
| --- | --- | --- |
| School administrator (`SCHOOL_ADMIN`) | Create/manage members, roles, classes, students, guardians, grades, finance, expenses, curriculum, study plans, exams | Broadest role; school creator becomes this role immediately (`openspec/specs/current-system.md:19`). |
| Teacher (`TEACHER`) | Classes, students, grades, curriculum, study plans, exams | Some visible page controls may still fail server-side if the teacher lacks a specific permission; backend remains authoritative. |
| Finance staff (`FINANCE_ADMIN`) | Tuition fee, invoices, payments, expenses | Does not approve expenses per UX docs, but implementation shows shared finance/expenses pages with server-enforced permissions. |
| Student (`STUDENT`) | Own grades, own invoices, own study plan, own exams, tokenized exam taking | Student pages use `/api/me` or exam token paths. |
| Parent or guardian (`PARENT`) | Linked children's grades and invoices | No current parent messaging or attendance workflow found. |
| System/platform administrator | Future/unclear | `PLATFORM_ADMIN` exists only as incomplete future infrastructure (`openspec/specs/current-system.md:17`). |

### Main Modules

| Module | Current UI surface | Current entitlement state |
| --- | --- | --- |
| IAM | Login, register, activation, members, roles, navbar school/role switcher | Core. |
| School | School creation and public school entry page | Self-service creation; creator becomes admin. |
| Academic | Classes, students, guardians, grades | Free entitlement on school creation. |
| Finance | Tuition, invoices, payments, expenses, attachments, summaries | Free entitlement on school creation. |
| Curriculum | Topic tree and question bank | Paid/catalog module, not auto-granted. |
| Study planner | Plan, assignments, weekly schedule, student study view | Paid/catalog module, not auto-granted. |
| Exam | Authoring, assignment, results, student exams, exam taking | Paid/catalog module, not auto-granted. |
| Bale/messaging | Webhook/backend integration only | No full user-facing messaging UI found. |
| Attendance | Not found | Prompt journey exists, but no attendance module/page was found. |

### Important Pages And Navigation Paths

Public:

- `/` -> landing (`src/main/jte/landing.jte`), redirects authenticated users to dashboard.
- `/login` -> password/OTP login (`src/main/jte/login.jte`).
- `/register` -> self-service account registration (`src/main/jte/register.jte`).
- `/activate?token=...` -> invite activation (`src/main/jte/activate.jte`).
- `/s/{slug}` -> public school entry (`src/main/jte/school-entry.jte`).
- `/exam-take?token=...` -> tokenized exam attempt (`src/main/jte/exam-take.jte`).

Authenticated navigation is centralized in `layout/navbar.jte` (`src/main/jte/layout/navbar.jte:31` to `src/main/jte/layout/navbar.jte:46`). Page route controllers are thin shells under `src/main/java/com/example/roshdeandishe/web/`; auth page controllers live in `iam/shared`.

Important dependencies:

- Almost every admin page depends on `localStorage.selectedSchoolId` and `X-School-ID` (`UX_ROADMAP.md:29` to `UX_ROADMAP.md:37`; `src/main/resources/static/js/navbar.js:14`).
- Navbar role selection controls visibility only; server permission checks decide actual access (`UX_ROADMAP.md:67` to `UX_ROADMAP.md:70`).
- Cross-module display names are composed client-side. Example: finance invoices receive `studentId`, then `finance.js` composes student/member data for names (`src/main/resources/static/js/finance.js:72` to `src/main/resources/static/js/finance.js:84`).
- Paid module pages must distinguish `module-not-entitled` from `permission-denied`; finance, curriculum, study plans, roles, exams, and expenses implement variants of this pattern.

### Frontend And Endpoint Inventory

| Surface | Templates / client behavior | Page controller | Primary data endpoints |
| --- | --- | --- | --- |
| Public identity | `landing.jte`, `login.jte`, `register.jte`, `activate.jte`; `login.js`, `register.js`, `activate.js` | IAM shared login/register/activation controllers | `/api/auth/login`, `/register`, `/activate`, `/otp/request`, `/otp/verify`; `/api/me/phone` |
| School context | `dashboard.jte`, `school-new.jte`, `school-entry.jte`, shared `layout/navbar.jte`; `navbar.js`, `dashboard.js` | `DashboardPageController`, `SchoolRegistrationPageController`, `SchoolEntryPageController` | `/api/me`, `/api/me/memberships`, `/api/admin/schools`, `/api/admin/schools/{id}` |
| Members and roles | `people.jte`, `roles.jte`; page JS | `PeoplePageController`, `RolesPageController` | `/memberships`, `/memberships/names`, `/invite-token`, `/roles`, `/permissions` |
| Academic | `classes.jte`, `students.jte`, `grades.jte`; page JS + shared `member-names.js` | classes/students/grades page controllers | `/classes`, `/students`, `/students/{id}/guardians`, `/grades` |
| Finance | `finance.jte`, `expenses.jte`, `my-invoices.jte` | finance/expenses/self-service page controllers | `/tuition-fee`, `/invoices`, `/payments`, `/expenses`, `/attachments`, `/expense-summary`, `/finance-summary`, `/api/me/.../invoices` |
| Curriculum | `curriculum.jte`; `curriculum.js` | `CurriculumPageController` | `/topics`, `/topics/tree`, `/questions` |
| Study planning | `study-plans.jte`, `my-study-plan.jte`; page JS, `jalali.js`, `modal-focus.js`, page CSS | admin/student study-plan page controllers | `/study-plans`, `/assignments`, `/week-courses`, `/schedule`, `/study-intervals`, `/suggest`, `/clear`, `/copy` |
| Exams | `exams.jte`, `my-exams.jte`, `exam-take.jte`; page JS | exam admin/self-service/token page controllers | `/exams`, `/sections`, `/assign`, `/results`, `/api/me/.../exams`, `/api/exam-take/*` |

The reusable layer is thin: `layout/main.jte` owns the document head, `layout/navbar.jte` owns authenticated navigation, `app.css` owns tokens/accessibility/logical utilities, `my-study-plan.css` owns the calendar layout, and four browser helpers own navbar state, Jalali conversion, member-name batching, and modal focus. There are no reusable JTE form, table, alert, empty-state, pagination, or dialog fragments yet.

State coverage is uneven. Most pages have initial, loading, empty, success, generic error, permission/module-blocked, and submitting states. Few distinguish partial-data failures; network failures rarely offer retry; destructive confirmation is inconsistent; and only the study interval delete flow has a purpose-built confirmation state. Authentication and the patched exam attempt announce async results, while many management-page notifications remain visual-only.

### Missing Or Unclear Requirements

- No attendance, absence notification, or parent absence workflow was found, despite being called out in the requested journey list.
- No billing/upgrade UI or public REST flow for paid entitlements exists (`openspec/specs/current-system.md:143` to `openspec/specs/current-system.md:154`).
- No full messaging UI exists; Bale appears backend/webhook-oriented.
- Parent/student display identity is incomplete on self-service grades and invoices; pages show `دانش‌آموز #id` in several places (`src/main/jte/my-invoices.jte:39`, `src/main/jte/my-grades.jte:38`).
- Table scalability requirements are documented, but no shared search/pagination/sorting contract exists beyond the generic "after 25 rows" rule (`DESIGN.md:166`).
- Currency/number formatting requirements are partially documented, but not consistently implemented.

## 3. User-Journey Review

The table traces tasks end to end. “Decision/input” covers user decisions and required information; “observed issue” combines confusing moments, unnecessary steps, missing feedback/navigation, and accessibility concerns.

| Journey | Role, goal, start | Steps and decision/input | Successful outcome | Failure and recovery paths | Observed issue | Recommended flow |
| --- | --- | --- | --- | --- | --- | --- |
| Password sign-in | Any user; enter product; `/login` or `/s/{slug}` | Choose password mode; email + password; submit | Authenticated session and `/dashboard` | Invalid credentials stay on page; network/server error is announced; no password recovery path | School entry does not carry school context into login; no forgot-password journey | Preserve optional school context, add password recovery, focus the error or invalid field |
| OTP sign-in | Existing user with registered phone; `/login` | Choose OTP; phone; request; enter code; verify; decision to change/re-request | Session and dashboard | Unknown phone, wrong/expired code, delivery unavailable; user can return to phone step | SMS provider is not implemented for production; rate-limit/resend timing is unclear | Show masked destination, resend countdown, rate-limit state, delivery fallback and support path |
| Registration and school setup | New founder; `/register` | Name, email, password; automatic login; dashboard empty state; `/schools/new`; school name/level/locale/timezone/slug | School created, creator becomes active `SCHOOL_ADMIN`, context available | Duplicate email and validation preserve fields; slug conflicts/server failure surface generic detail | Registration-to-school path spans pages but lacks progress/onboarding framing | Use a two-step onboarding checklist and explain effects of level/timezone/slug before save |
| Invite activation | Invited member; emailed `/activate?token=` | Confirm/edit token; enter and confirm new password | Account activated; explicit login CTA | Invalid/expired token and network error stay on page | No invite identity/school summary or resend path; prior implementation executed crafted tokens and is fixed | Show school/role/token expiry from a safe preview endpoint and provide admin-contact recovery |
| Phone registration to OTP | Authenticated user; dashboard phone box | Enter phone, including Persian/Arabic digits; save; later use OTP | Canonical ASCII number stored; equivalent Unicode variants share uniqueness and OTP lookup | Invalid/duplicate number is announced and input remains; deployment migration stops for ambiguous legacy ownership | No country-code/format rule; semantic legacy collisions require operator resolution because ownership cannot be guessed safely | Define canonical phone format, masked display, verification-on-change and an operational collision-resolution runbook |
| Switch school/role | Multi-school/multi-role user; shared navbar | Open school menu; choose school; optional display role; page scripts reload | New school context persists and page data reloads | Stale/unauthorized school produces page notice or 403 | Role selection only changes visible links, not authorization; custom roles lose navigation; desktop navbar clips | Drive nav from server-returned capabilities, label role switch as a view filter, add current-page state and compact overflow menu |
| Create class and enroll | Admin/teacher; `/classes` then `/students` | School selected; class name/grade/section/teacher; then student identity and class | Class exists; student enrolled; roster shows real names | Permission/validation/network errors preserve form; teacher directory may be restricted | No “enroll next” CTA; read-only vs manage state unclear; cross-page context lost | After create, offer “add students to this class”; retain class filter; show capability-aware read-only state |
| Manage student and guardian | Admin/teacher; `/students` | Search/list absent; create student; optional invite; expand guardian panel; select/link parent | Student and guardian relationship visible | Sub-load failures can appear empty; invite/guardian permission failures are inconsistent | Large list has no search/pagination; expanded panels hide context on mobile; readonly invite fields need full labelling | Add toolbar/pagination, standard row details, labelled copy control and explicit per-panel errors |
| Record grades | Teacher/admin; `/grades` | Class, academic year, term; load; search roster; enter descriptive/numeric grade and optional comment; save each row | Correct named student receives grade and recorded time updates | Field validation shows row error; server error preserves row | Prior teacher path rendered every learner as `نامشخص` (fixed); no bulk save; current date formatting inconsistent | Keep least-privilege name batching, add roster-load hard failure, unsaved-change state and optional bulk save/review |
| Attendance / absence notification | Teacher/admin/parent; expected from prompt | No page, controller, API or current specification | Not possible | No recovery because capability is absent | Product requirement is missing, including states, audit, notification consent and correction flow | New proposal: take attendance by class/date, confirm bulk changes, notify by policy, expose parent acknowledgement and correction audit |
| Tuition, invoice and payment | Admin/finance; `/finance` | Set tuition; backfill; expand invoice; enter amount/method/note; submit full/partial payment | Payment history and derived invoice status update | ProblemDetail shown; fields preserved; duplicate/ambiguous backfill recovery weak | Raw money/date formatting, duplicate dynamic IDs fixed, no receipt/reversal, backfill not confirmed | Show grouped amount/unit/remaining, confirm backfill scope, generate receipt, specify correction/reversal flow |
| View overdue invoices | Admin/finance via `/finance`; family via `/my-invoices` | Scan status tags and due data | Overdue state visible | No dedicated empty/error/retry per overdue filter because filter absent | No overdue filter/sort/summary; families may see child IDs and no next action | Default overdue-first view, total due, child name, due date, payment/support instructions |
| Expense and report | Admin/finance; `/expenses` | Maintain category/vendor; create expense; filter; approve/pay/cancel; upload/download/delete attachment; choose Jalali period/report | Expense lifecycle and summary visible | Generic failures; attachment/report sub-failures can be silent/ambiguous | Cancel/delete immediate, raw dates/money, browser date picker is Gregorian/English, no history pagination | Confirmation dialog, shared Jalali display/input strategy, currency formatting, report retry/export and audit timeline |
| Study plan | Admin/teacher; `/study-plans`; student `/my-study-plan` | Create weighted plan; assign; set weekly topics/schedule; suggest/edit/copy/clear; student logs completion/tests | Schedule and progress update | Module/permission errors; sub-load failures often collapse to empty; save state preserved variably | Extremely dense page, immediate clear-week action, token-rule violations in CSS; modal focus fixed in audit | Split overview/configuration/schedule, confirm clear, expose per-subload errors, retain accessible focus utility |
| Exam authoring and taking | Admin/teacher `/exams`; student `/my-exams` and token URL | Create timed exam; sections/questions; assign; start attempt; switch sections and answer; return/results | Each answer persisted and results visible | Expired/forbidden/network errors; failed answer rolls back; per-question pending no longer locks attempt | Weekly regeneration consequences not confirmed; topic/student IDs leak; no explicit final-submit/review model | Confirm destructive regeneration, show named topics/students, add review/unanswered summary and define completion semantics |
| Messaging | School roles/families; expected from prompt | Bale webhook/backend exists; no web journey | Not possible as a web task | Delivery/error/read state not exposed | Scope (announcement, chat, automated absence notices) is undefined | Specify actors, consent/privacy, delivery state, read receipts, retry/fallback and retention before UI work |
| Permission/module failure | Any protected role; deep link or action | Backend evaluates all active roles plus module entitlement | Authorized action succeeds | Some pages separate 403 reasons and offer contact-support text | Navbar is role-code driven, selected role is misleading, custom role can have permission but no entry link; recovery CTA inconsistent | Return capability/navigation model server-side; standard blocked state with reason, allowed role and recovery |
| Validation/server/network recovery | Any form | Enter data; browser/client validation; fetch; ProblemDetail | Preserve data and show success | Most data survives; retry is manual; partial loads may look empty | Error placement and live announcement remain inconsistent; page-wide loading flags affect unrelated actions | Shared async state/error summary, field mapping, retry control, per-action lock and duplicate request idempotency where material |

### Signing In

Role: all users.

Current flow: `/login` offers password and OTP visual modes. Password and OTP flows submit through `login.js`. Registration auto-logins and redirects to `/dashboard`; activation now offers an explicit login action after success.

Good: clear first-screen form, disabled submit during invalid/submitting states, visible errors.

Problems: the patched auth pages announce errors and no longer embed fetch logic, but focus does not consistently move to the invalid field/error summary. There is no password-reset journey, OTP resend countdown, production SMS delivery, or school-context continuation.

Improved flow: after failure, focus the first invalid field or linked error summary; add password recovery and complete OTP delivery/resend/rate-limit states.

### Selecting Or Switching Schools

Role: all authenticated users with memberships.

Current flow: navbar reads and writes `localStorage.selectedSchoolId` and emits `school-changed`; pages reload data on the event. The shared navbar exposes page links by selected role (`src/main/jte/layout/navbar.jte:31` to `src/main/jte/layout/navbar.jte:46`).

Good: central shared pattern, visible selector, server authorization remains authoritative.

Problems: there is no explicit active-page state, no breadcrumb, and no recovery explanation for stale localStorage beyond "select a school" notices. Role switcher is display-only, which is correct technically but can confuse users when a visible action still fails server-side.

Improved flow: add active nav state and a standardized school-required empty state; when permission fails, explain both current selected role and recovery options.

### Creating And Managing Students

Role: admin/teacher.

Current flow: `/students` loads classes, active student/parent memberships, and student records; enrollment form creates students, then row panels manage guardians.

Good: journey can be completed from one page; no backend contract changes needed for name composition.

Problems: invite link fields are readonly inputs without explicit associated labels, table actions expand per-row panels that can become visually dense, no pagination/search, and permission recovery differs from pages with two-layer 403 handling.

Improved flow: add table toolbar search, standardized row detail pattern, labelled readonly invite fields, and consistent permission-block component.

### Creating Classes And Enrolling Students

Role: admin/teacher.

Current flow: `/classes` creates and lists classes; `/students` enrolls students into classes. Class roster expansion shows students.

Good: class creation precedes enrollment naturally; navbar exposes both pages for admin/teacher.

Problems: no explicit "next step" from newly created class to student enrollment; no bulk enrollment; no clear read-only state when teacher can view but not manage.

Improved flow: after class creation, show action to enroll students in that class; when manage permissions fail, show the page in read-only mode with explanation instead of letting every mutation fail independently.

### Recording Attendance And Notifying Parents

Role: teacher/admin/parent.

Current status: cannot verify as implemented. No attendance page, route, controller, static JS, or OpenSpec current behavior was found.

Recommendation: treat this as a new module or change proposal. Define role ownership, attendance states, absence thresholds, notification channel, audit log, parent message wording, retry/failure behavior, and opt-out/consent model before implementation.

### Creating Invoices And Recording Payments

Role: school admin/finance staff.

Current flow: `/finance` sets tuition, backfills invoices, lists invoices, expands payment history, and records full or partial payments (`src/main/jte/finance.jte:23` to `src/main/jte/finance.jte:150`).

Good: main payment workflow exists; payment form is colocated with invoice context; server-side errors are shown.

Problems: amounts render as raw numbers without grouping or Persian digits (`src/main/jte/finance.jte:73` to `src/main/jte/finance.jte:80`), dates use `toLocaleDateString('fa-IR')` while being wrapped with `dir="ltr"` (`src/main/jte/finance.jte:79`), no explicit duplicate-submission prevention per invoice, and no confirmation for invoice backfill.

Improved flow: format currency consistently; explain invoice status and remaining amount before payment; disable only the active row's payment button during submission; confirm backfill with count estimate where possible.

### Viewing Overdue Invoices

Role: admin/finance/student/parent.

Current flow: invoice rows show derived status tags including overdue (`src/main/resources/static/js/finance.js:87` to `src/main/resources/static/js/finance.js:104`; `src/main/resources/static/js/my-invoices.js` mirrors labels/classes).

Good: overdue is visually marked with `is-danger`.

Problems: no overdue-only filter, no sorting by due date, no summary total, and self-service pages show raw student IDs rather than names.

Improved flow: add overdue filter, default sorting by due date/overdue, and explain what the payer should do next.

### Entering Expenses And Viewing Financial Reports

Role: admin/finance staff.

Current flow: `/expenses` manages categories/vendors, records expenses, filters expense list, expands attachments, and loads Jalali-period summaries (`src/main/jte/expenses.jte:23` to `src/main/jte/expenses.jte:327`).

Good: broad end-to-end workflow exists on one page; attachment upload/download path handles the required `X-School-ID` header (`src/main/resources/static/js/expenses.js:315`).

Problems: cancel expense and delete attachment are destructive but immediate (`src/main/jte/expenses.jte:219` to `src/main/jte/expenses.jte:221`, `src/main/resources/static/js/expenses.js:222`, `src/main/resources/static/js/expenses.js:341`); category/vendor tables have no headers; amounts/dates render raw; no pagination/search for expense history.

Improved flow: use reusable confirmation modal for cancel/delete; format money/dates; add report empty/error state and export requirements.

### Creating Study Plans

Role: admin/teacher.

Current flow: `/study-plans` creates plans, assigns students, configures weekly topics/default schedules, auto-suggests and clears weekly intervals, manually creates intervals, and confirms interval deletion inside a modal.

Good: powerful single-page workflow; interval deletion has an in-modal confirmation (`src/main/jte/study-plans.jte:449` to `src/main/jte/study-plans.jte:468`).

Problems: clearing an entire week is immediate despite being destructive. Dialog semantics, focus trap/return, and distinct day/range control names were fixed during the audit; the dense page still lacks a simpler task hierarchy and explicit partial-load failures.

Improved flow: confirm clear-week, split dense plan editing into clear sections, and show independent loading/error/retry states for assignment, week-course, schedule, and interval data.

### Sending Messages

Role: admin/teacher/parent/student.

Current status: no complete messaging UI found. Bale integration exists at backend/webhook level, but user-facing messaging journeys are absent from current web pages.

Recommendation: define whether messaging is announcement-only, two-way chat, parent notifications, or system-generated alerts. Define read receipts, delivery failures, channel fallbacks, audit history, and privacy boundaries.

### Permission Errors

Role: all protected roles.

Current flow: backend returns ProblemDetail; some pages classify `module-not-entitled` and `permission-denied` distinctly (`src/main/resources/static/js/finance.js:123` to `src/main/resources/static/js/finance.js:137`, `src/main/resources/static/js/expenses.js:93` to `src/main/resources/static/js/expenses.js:107`).

Good: technical foundation is sound. `@RequiresModule`, `@RequiresPermission`, and `TenantContext.requireMatches` appear across protected controllers, e.g. expenses (`src/main/java/com/example/roshdeandishe/finance/ExpenseController.java:40`).

Problems: classification is duplicated per page and not universal; error notifications are not announced to assistive tech; no consistent recovery CTA.

Improved flow: add a shared page-level error component/helper with `role="alert"`, focus handling, classification, and recovery text.

### Validation And Server Errors

Role: all.

Current flow: HTML `required`/`type` attributes and JS disabled states handle most client validation; server errors show top notifications.

Good: state is often preserved after failed fetch because forms are Alpine data and only reset on success.

Problems: field-level validation is inconsistent outside grades; ProblemDetail details usually show at section top only; network errors lack retry buttons; duplicate submissions use coarse page-level flags.

Improved flow: standardize inline field errors, retry affordance, and per-action loading/disabled state.

## 4. UI/UX Document Review

| Weakness | Why it matters | Recommendation | Improved requirement / acceptance criterion |
| --- | --- | --- | --- |
| `DESIGN.md` is still draft/pending sign-off while code already uses proposed tokens (`DESIGN.md:3`, `src/main/resources/static/css/app.css:43`). | Engineers cannot tell whether color/type/token values are final or provisional. | Sign off or mark exact open decisions. | "Primary color, radius, spacing, and motion tokens are accepted for web UI as of DATE; any change requires a decision record." |
| The stack in user/request context mentions HTMX/Bump, while current docs say Alpine/Bulma (`FRONTEND.md:67`). | Future contributors may implement divergent interaction patterns. | Explicitly state "no HTMX currently" in current docs or add a change proposal before adopting it. | "All dynamic web UI uses Alpine/fetch unless an accepted proposal introduces HTMX." |
| Search/filter/pagination is specified only generically ("after 25 rows") (`DESIGN.md:166`). | Tables will continue diverging by page and large schools will suffer first. | Define table toolbar, pagination, empty, loading, numeric, and action-column standards. | "Any table with potential >25 records provides text filter, page size, total count, and keyboard-reachable pagination." |
| Destructive confirmation is documented (`DESIGN.md:109`, `UX_ROADMAP.md:56`) but no reusable modal acceptance criteria exists. | Pages implement destructive actions inconsistently and some execute immediately. | Define a shared confirmation modal/component before expanding actions. | "Every destructive action opens an accessible confirmation dialog naming the object, consequence, cancel action, and final destructive verb." |
| Accessibility rules omit focus restoration after Alpine view swaps and async failures. | Screen-reader and keyboard users may not learn that a section changed. | Add focus/announcement requirements to feedback pattern. | "After failed async submit, focus moves to the error summary; after modal close, focus returns to the opener." |
| Empty-state guidance exists (`DESIGN.md:148`) but not all pages provide action-oriented empty states. | Users may not know the next step after loading an empty module. | Add role-specific empty-state text and allowed CTAs per page. | "Empty state names the missing entity, explains the prerequisite, and shows the create/import action when permitted." |
| Persian date/digit requirements are clear but implementation coverage is incomplete (`DESIGN.md:205` to `DESIGN.md:228`). | Financial and academic data can look unprofessional and be hard to scan. | Create shared JS formatters for Jalali dates, Persian digits, and currency. | "Every displayed amount uses grouped Persian digits and unit; every human-facing date uses Jalali display." |
| Form requirements do not define required indicators, helper text, or per-field server errors consistently. | Validation may become top-notification-only and hard to recover from. | Define form row patterns and server-field-error mapping. | "Required fields are visibly marked; invalid field has `aria-invalid`, inline message, and summary link." |
| Role separation is documented by navbar visibility but not by page-level capability states. | A teacher/finance user may see controls that predictably fail. | Specify read-only/disabled states when a user can view but not manage. | "If page can be viewed but a mutation is not permitted, mutation controls are hidden or disabled with an explanation." |
| Security-sensitive interactions lack complete UX criteria. | Financial backfills, role creation, invite links, and account activation need stronger affordances. | Add confirmation, audit visibility, and sensitive-data copy rules. | "Invite links show expiry/status; role changes summarize granted permissions before save; bulk financial actions confirm scope." |
| Responsive behavior is high-level and not page-specific. | Dense tables and study grids degrade on mobile without a planned alternative. | Define mobile layouts for management tables and calendar grids. | "At <=768px, each high-density table has horizontal scroll plus preserved action visibility, or a tested card/list alternative." |
| Parent/student journeys are thinner than admin journeys. | The product may work technically but feel opaque to families. | Define names, next actions, payment instructions, and exam/result states. | "A parent sees child names, status explanation, due/overdue action, and support path without internal IDs." |
| `my-study-plan.css` says it uses only shared tokens but contains extensive raw pixels, `rem`, hex colors, and a fixed 900px grid. | The stylesheet contradicts both its own comment and `DESIGN.md`, so reviewers cannot enforce the promised token/RTL discipline. | Inventory required raw layout constants separately from visual tokens; replace physical/color values incrementally. | "Workflow CSS may use documented data-geometry constants; all spacing, color, radius and logical positioning use tokens. CI/review rejects undocumented raw visual values." |
| The 44px target rule explicitly exempts `.is-small`, while the document presents 44px as a global accessibility floor. | Dense tables and the 34px study picker remain difficult for touch users, exactly where mistakes are costly. | Define when compact controls are permitted and provide a touch-mode/mobile alternative. | "Every control at <=768px has a 44x44 CSS-pixel target; compact desktop table controls require an equivalent row action menu on touch layouts." |
| The typography table allows only admin-sized headings, while landing and school-entry pages intentionally use hero `is-1`/`is-2`. | A rule that excludes legitimate public surfaces will either be ignored or flatten marketing hierarchy. | Split public/marketing typography from authenticated task UI. | "Authenticated task pages use one `is-4` H1; public hero pages may use `is-1`/`is-2` under a separate responsive scale." |
| The role policy simultaneously says permission controls should never be hidden and suggests role-driven visibility/disabled states. | Teams can implement opposite behavior and both claim compliance; custom roles expose the conflict. | Decide capability-based visibility at navigation, page and action levels. | "Navigation is derived from effective permissions across active roles. Forbidden mutations are omitted only when absence is understandable; otherwise show read-only control plus reason. The display-role selector never changes authorization." |
| Date requirements mandate Jalali display but do not define browser-native date input behavior. | Native `date`/`datetime-local` controls render Gregorian/English in observed browsers, so code can meet markup rules while violating the Persian experience. | Specify storage, display, input, parsing, timezone and fallback separately. | "API values remain ISO; visible dates are Jalali/Persian; date entry uses a tested Persian control or an explicitly labelled Gregorian technical input with Jalali preview." |
| Currency is described inconsistently as تومان while no authoritative unit/rounding policy is identified. | A mistaken unit can produce a 10x financial error and formatted UI can make the error more convincing. | Product owner must name the persisted unit, entry/display unit, conversion and rounding. | "Every money DTO documents its unit; every field and table repeats that unit; conversion is centralized and tested with boundary examples." |
| Error-state requirements do not define partial dependency failure. | Pages compose several endpoints and often convert one failed subrequest into `[]`, making an outage look like valid empty data. | Define required vs optional subloads and fail-safe rendering. | "A required identity/financial dependency failure blocks the affected action with retry; optional data shows an explicit partial-data warning and never masquerades as an empty result." |

## 5. Implementation Review

### Frontend Architecture

- SSR entry points are JTE templates under `src/main/jte`.
- `layout/main.jte` owns head assets, global `lang="fa"` and `dir="rtl"` (`src/main/jte/layout/main.jte:16`).
- Authenticated pages render the shared navbar (`src/main/jte/layout/navbar.jte`).
- Each dynamic page usually loads `/js/navbar.js` plus one page-level Alpine component under `src/main/resources/static/js`.
- Shared CSS tokens and accessibility defaults live in `src/main/resources/static/css/app.css`; the only page-specific stylesheet found is `my-study-plan.css`.
- Backend page controllers are thin route renderers; REST controllers enforce access and module boundaries.
- No HTMX lifecycle, swap, history, indicator, focus-restoration, or progressive-enhancement behavior exists to review. All partial interaction is Alpine state plus JSON `fetch`; browser back/forward therefore navigates full pages and does not restore expanded/filter state.
- Shared browser helpers now cover navbar context, Jalali conversion, bounded member-name composition, and modal focus. Reusable JTE components are still limited to layout and navbar.

### Layout And Visual Hierarchy

Strengths:

- Page titles generally use `title is-4`; sections use `title is-6`, matching `DESIGN.md` typography rules.
- Bulma `section`, `container`, `box`, `notification`, and `table-container` create a familiar, low-JS admin UI.
- Business workflows are generally colocated with their data.

Problems:

- Many pages have multiple competing primary actions in boxes rather than one page-level primary action.
- Dense pages such as expenses, study plans, and exams use many boxes and inline expansion panels; scanning hierarchy is weak for first-time users.
- Some "box inside workflow" areas act like sections rather than cards, conflicting with the design intent to use simple page sections.
- No breadcrumbs or consistent page exit points beyond navbar.
- Browser rendering at 1440px showed the one-line desktop navbar clipping the school/person controls. This is not merely a small-screen issue; the navigation has exceeded its information capacity.

### Navigation

Strengths:

- No obvious orphan management pages: navbar links cover dashboard, people, roles, classes, curriculum, study plans, exams, student self-service, students, grades, finance, expenses, invoices, and grades (`src/main/jte/layout/navbar.jte:31` to `src/main/jte/layout/navbar.jte:46`).
- Navbar state is shared and listens to school changes.

Problems:

- No active link state, so users cannot easily confirm current page location.
- Role-specific visibility can hide a page link even if the backend would permit access through another role.
- No role/page permission preview; users learn about permission issues only after loading or submitting.
- Custom roles are especially broken: link visibility is hard-coded to five system role codes and `RoleLabels` receives only a code, so an authorized custom role can show a raw code and no route to its permitted page.
- The selected-role menu is display-only while backend authorization evaluates all active roles. The UI does not explain that distinction.

### Forms

Strengths:

- Most forms have visible labels and browser validation.
- Many mutating submit buttons show `is-loading`.
- Alpine state usually preserves input values when a server error occurs.

Problems:

- Grade search/rows, repeated study-plan ranges, dashboard phone, exam authoring controls, and dynamic payment IDs were fixed in this pass. Remaining gaps include some readonly invite/link controls and controls that rely on placeholder text rather than persistent helper text.
- Required-field indicators are inconsistent.
- Top-level notifications are not tied to invalid fields and lack live-region semantics.
- Phone canonicalization now occurs client- and server-side. Persian/Arabic digit normalization remains local rather than shared for several finance/expense numeric entry paths.
- Duplicate submission flags are often page-wide (`submitting`) rather than action-specific, which can disable unrelated actions or fail to communicate row-specific progress.

### Tables

Strengths:

- Major tables use real `table`, `thead`, `tbody`, `th`, and `td`.
- Bulma `table-container` provides horizontal scrolling for overflow.
- `scope="col"` is now present on table headers.

Problems:

- Several small utility tables are body-only with no headers, e.g. expense categories/vendors (`src/main/jte/expenses.jte:30` to `src/main/jte/expenses.jte:46`, `src/main/jte/expenses.jte:67` to `src/main/jte/expenses.jte:83`) and attachment lists (`src/main/jte/expenses.jte:239` to `src/main/jte/expenses.jte:256`).
- Numeric columns do not have a shared alignment/formatting class. Amounts and counts are rendered as raw data in many places.
- No shared table component, toolbar, pagination, sorting, sticky header, empty state, or bulk-action pattern.
- Long text overflow is left to default table behavior; no wrapping/truncation standard.
- Major action columns now have visually hidden names. Body-only category/vendor/attachment utility tables still need headers or more appropriate list markup.
- Rendered 390px pages keep the document width contained, but management tables expose only their leading columns until horizontal scroll; action context is effectively hidden and there is no scroll cue or sticky action column.

Likely root causes: page-by-page markup, missing shared JTE components, no common formatter utilities, no table acceptance criteria beyond a high-level rule, and reliance on Bulma primitives without app-level table conventions.

### Buttons And Actions

Strengths:

- Primary actions commonly use `button is-link`.
- Destructive actions usually use `is-danger`/`is-light`.
- Navbar state triggers are buttons. Visual tab groups keep ordinary link semantics because they do not implement the ARIA Tabs roving-focus/arrow-key contract; they should eventually become URL navigation or fully specified buttons/tabs.

Problems:

- Destructive actions are not consistently confirmed.
- Button wording is generally Persian verbs, but button hierarchy is inconsistent on dense pages.
- Icon usage is almost absent; this is acceptable for the current stack, but action density would benefit from consistent text/icon conventions if icons are introduced later.

### Alpine / Fetch Behavior

No HTMX behavior exists to inspect. Current async behavior is Alpine + fetch:

- Most pages call REST endpoints with `X-School-ID`.
- Mutating calls include `X-XSRF-TOKEN`.
- Error parsing generally follows ProblemDetail, but helper implementations are duplicated and not always classifying authorization failures.
- Browser history/back behavior is normal page navigation, not stateful async navigation.
- Modal focus entry, trapping, step transition, Escape close and opener restoration are implemented for both study-plan dialogs. General async replacement/error focus remains inconsistent.
- Loading indicators are plain text or button `is-loading`; there is no standardized long-running operation state.
- Exam answer saving now tracks pending state per question, permits the rest of the timed attempt to continue, rolls back failed optimistic choices, uses `keepalive`, and announces pending/saved/error status.
- Member identity composition now sends at most 100 IDs per request and fails visibly through the page load path instead of silently treating an HTTP failure as an empty name list.

### Notifications, Modals, Empty States

Strengths:

- Most pages provide danger/success notifications at top of the page section.
- Many pages have empty states, especially list pages.
- Study interval deletion includes a two-step modal confirmation.

Problems:

- Authentication, dashboard and exam-attempt notices now use appropriate alert/status semantics; many management-page notifications still lack live regions and error focus.
- Both existing modals now have dialog naming, Persian close names, focus entry/trap/return, and confirmation-step focus. The focus utility has static/flow coverage but no automated browser accessibility test.
- Empty states do not consistently include creation actions or prerequisite explanation.

## 6. Accessibility Review

WCAG 2.2 AA risk areas:

- Landmarks/bypass: every top-level template now exposes one `main` target and the shared layout provides a keyboard-visible skip link.
- Semantics: unsupported ARIA Tabs roles were removed. Visual mode switchers remain ordinary links; this avoids a false keyboard contract but is not a final component design.
- Headings: generally consistent, but dense nested workflows can skip conceptual levels due to many `title is-6` boxes.
- Form labels: expense controls and the high-risk generated controls are labelled; some readonly copy fields and placeholder-led compact controls remain incomplete.
- Error announcements: improved for auth, dashboard, and exam taking; most page-level management errors still lack live regions, `aria-describedby`, and focus movement.
- Keyboard: navbar triggers are operable; both modals trap and restore focus; page navigation has no active state; horizontally scrolled tables make row actions difficult to discover.
- Contrast: likely acceptable through Bulma defaults and token bridge, but not measured with rendered screenshots.
- Accessible names: modal close buttons, major action headers, grade rows, repeated ranges, and exam authoring inputs were corrected. A systematic accessible-name scan is still required.
- Dialogs: current dialogs meet the basic name/modal/keyboard/focus behavior, but the page background is not programmatically `inert`; the focus trap is the operative containment mechanism.
- Touch targets: global CSS enforces >=44px for non-small controls; dense table `is-small` controls are exempted (`src/main/resources/static/css/app.css:61` to `src/main/resources/static/css/app.css:68`).
- Reduced motion: globally handled in CSS (`src/main/resources/static/css/app.css:76` to `src/main/resources/static/css/app.css:86`).
- Language/direction: document-level `lang="fa" dir="rtl"` is correct; some human-facing Jalali/Persian dates are incorrectly wrapped in LTR islands.
- Status/timeout: the timed exam has a visible countdown and per-question save feedback; there is no user-controlled time extension, warning threshold, or documented accommodation policy.

Accessibility priority: finish management-page live/error handling, define touch-safe table alternatives, add field-error linkage, test keyboard/screen-reader behavior in a real browser, and document timed-assessment accommodations.

## 7. Responsive And RTL Review

Strengths:

- Document direction is RTL by default.
- Bulma containers/columns/table containers provide baseline responsiveness.
- Custom CSS now has more logical utility classes and fewer physical spacing helpers.
- Navbar mobile dropdown behavior has explicit CSS support (`src/main/resources/static/css/app.css:108` to `src/main/resources/static/css/app.css:116`).
- Browser checks found no document-level horizontal overflow at 390px on finance, expenses, or study plans; wide content is contained by internal scrollers.

Problems:

- Management tables rely mainly on horizontal scroll; no mobile-specific column reduction or card alternative exists.
- The shared navbar contains many links, which can become long on mobile.
- At 1440px the desktop navbar already clips inline-end school/person controls; increasing the mobile breakpoint would only defer the same IA problem.
- Study plan grids and charts use custom CSS and absolute positioning; the 390px screenshot shows contained overflow, but real touch/zoom behavior still needs device verification.
- At 390px, people/finance/expense tables require horizontal scrolling before operations are visible; headers and row identity disappear while users reach actions because neither edge is sticky.
- Native date fields rendered as English `mm/dd/yyyy`/Gregorian controls in the reviewed browser, contradicting the surrounding Persian/Jalali experience.
- Some dynamic inline styles remain necessary for schedule/grid positioning (`src/main/jte/study-plans.jte:361`, `src/main/jte/study-plans.jte:374`; `src/main/jte/my-study-plan.jte:85`, `src/main/jte/my-study-plan.jte:98`), but should be constrained and tested.
- Date/number `dir` handling is inconsistent: Persian formatted dates should not be forced into LTR unless they contain Latin technical strings.
- The fixed 900px study grid and several 10.5px/34px controls in `my-study-plan.css` do not meet the stated touch/typography floor even though overflow prevents page breakage.

## 8. Document-To-Code Traceability

| Area | Document requirement | Current implementation | Status | Evidence | Recommended action |
| --- | --- | --- | --- | --- | --- |
| Activation-token safety | Security-sensitive values must not become executable code | Token is HTML-escaped inert `data-token`; static component reads `dataset` | Implemented correctly | `activate.jte`, `activate.js`, `RegistrationFlowIntegrationTest` | Keep regression probe; apply same data/code separation to future server values. |
| Grade roster identity | Teacher can record grades for named students without full membership access | Shared helper uses least-privilege `/memberships/names` in batches of 100 | Implemented correctly | `grades.js`, `member-names.js`, `MembershipController` | Add browser/integration coverage for a teacher with a large roster. |
| Exam answer durability | Choice is saved immediately after selection | Per-question pending state, rollback on failure, announced status and `keepalive`; other questions remain usable | Implementation appears better than document | `exam-take.js`, `exam-take.jte` | Specify review/final-completion semantics and an explicit offline policy. |
| Phone digits/uniqueness | Persian/Arabic digit input is normalized before submission | Client/server canonicalize; V3 backfills legacy values and rejects ambiguous equivalent owners | Implemented correctly | `dashboard.js`, `PhoneNumberCanonicalizer`, `V3__canonicalize_iam_phone_numbers.sql`, migration/OTP tests | Decide punctuation/country-code rules and document collision remediation. |
| Head ownership | Shared layout owns `<head>` | Implemented in `layout/main.jte` | Implemented correctly | `src/main/jte/layout/main.jte:15` to `src/main/jte/layout/main.jte:29`; `UX_ROADMAP.md:19` | Keep. |
| RTL document | Persian UI with `lang="fa"` and `dir="rtl"` | Root html sets both | Implemented correctly | `src/main/jte/layout/main.jte:16`; `DESIGN.md:176` | Keep; audit per-element `dir`. |
| Actual stack | JTE + Alpine + Bulma | Implemented | Implemented correctly | `FRONTEND.md:67`; `src/main/jte/layout/main.jte:21`, `src/main/jte/layout/main.jte:29` | Keep current stack. |
| Prompt stack | HTMX/Bump CSS | No `hx-*`, no HTMX/Bump dependency or source usage | Document unclear | repository search returned no hits | Clarify request scope; do not add either implicitly. |
| Shared navbar | Authenticated pages use shared navbar and links | Implemented | Implemented correctly | `src/main/jte/layout/navbar.jte:16`; `src/main/jte/layout/navbar.jte:31` to `src/main/jte/layout/navbar.jte:46` | Add active state. |
| Desktop navigation fit | Navigation remains usable at desktop/tablet sizes | Link row clips school/person controls in a 1440px render | Partially implemented | rendered dashboard/management pages; `layout/navbar.jte` | Replace flat link row with permission-aware grouped/overflow navigation. |
| Tenant selection | Client-side `localStorage.selectedSchoolId`; API sends `X-School-ID` | Implemented page-by-page | Partially implemented | `UX_ROADMAP.md:29`; `src/main/resources/static/js/navbar.js:14`; page scripts | Extract shared fetch helper later. |
| Role labels/navigation | Raw role codes never reach UI; role visibility guides users | System roles mostly Persian; custom role code can render raw and has no matching nav links | Partially implemented | `RoleLabels`, `NavbarModel`, `layout/navbar.jte` | Send display name and effective capabilities from server; generate nav from permissions. |
| Destructive confirmation | Destructive actions always confirmed | Some destructive actions execute immediately | Missing | `DESIGN.md:109`; `src/main/resources/static/js/people.js:127`; `src/main/resources/static/js/expenses.js:222`; `src/main/resources/static/js/study-plans.js:640` | Add reusable confirmation modal. |
| Success feedback | Mutations show visible success notice | Generally implemented | Partially implemented | `UX_ROADMAP.md:56`; `src/main/resources/static/js/people.js:117`; `src/main/resources/static/js/finance.js:236` | Add live regions and focus. |
| ProblemDetail errors | Parse ProblemDetail detail/title | Many pages implement local parsers | Partially implemented | `UX_ROADMAP.md:51`; `src/main/resources/static/js/finance.js:123`; `src/main/resources/static/js/people.js:63` | Extract shared helper or documented duplicate pattern. |
| Two 403 types | Module-not-entitled and permission-denied shown differently | Implemented on paid/finance-like pages, not universal | Partially implemented | `UX_ROADMAP.md:140`; `src/main/resources/static/js/finance.js:127` to `src/main/resources/static/js/finance.js:132` | Standardize blocked-state component. |
| Table semantics | Proper table/thead/tbody/th scope | Header scopes added; some body-only tables remain | Partially implemented | `src/main/jte/finance.jte:56` to `src/main/jte/finance.jte:91`; `src/main/jte/expenses.jte:30` | Add shared table/definition-list patterns. |
| Table scaling | Filter and pagination after 25 rows | Mostly absent; grades has class/year/filter search only | Missing | `DESIGN.md:166`; `src/main/jte/grades.jte:72` | Implement shared table toolbar and pagination. |
| Date display | Jalali for every human-facing date | Inconsistent; some ISO/raw and `toLocaleDateString` use | Partially implemented | `DESIGN.md:207`; `src/main/jte/expenses.jte:206`; `src/main/jte/finance.jte:79` | Create shared formatter. |
| Persian digits | Persian digits for displayed numbers; normalize inputs | Phone and selected academic inputs normalize; money/count/date display remains inconsistent | Partially implemented | `DESIGN.md:219` to `DESIGN.md:228`; `dashboard.js`; finance/expenses raw amounts | Centralize display/input formatters after currency/date decisions. |
| Code placement | Fetch/regex/>10 JS in static files | Page behavior, including register/activate, is external static JS | Implemented correctly | `UX_ROADMAP.md:38`; `register.js`; `activate.js` | Keep templates declarative. |
| Visual mode groups | Tab-like widgets should be keyboard accessible | False ARIA Tabs roles removed; controls remain ordinary links | Implemented differently | `login.jte`, `exams.jte`, `study-plans.jte`, `my-study-plan.jte` | Specify whether these are navigation, disclosure, or true tabs before creating a shared component. |
| Modals | Bulma modal driven by Alpine, no browser confirm | Existing modals are named, modal, Escape/Tab operable, and restore opener focus | Implemented correctly | `study-plans.jte/js`, `my-study-plan.jte/js`, `modal-focus.js` | Add real-browser accessibility regression; use same utility for future dialogs. |
| Empty states | Empty states teach and show first action | Some useful, some passive | Partially implemented | `DESIGN.md:148`; `src/main/jte/expenses.jte:231`; `src/main/jte/my-invoices.jte:55` | Add reusable EmptyState component. |
| Paid upgrades | Paid modules blocked without entitlement; no billing UI | UI says contact support, but there is no actionable support/upgrade route | Missing | `openspec/specs/current-system.md:143`; `src/main/resources/static/js/finance.js:127` | Define upgrade workflow before UI. |
| Attendance | Important journey in prompt | No module/page/controller found | Missing | no `attendance` templates/controllers found | Create proposal/spec. |
| Messaging | Important journey in prompt | Bale backend exists; no full UI | Missing | `bale` package, no messaging page | Define messaging product requirements. |
| Visual token compliance | Custom CSS uses documented tokens/logical properties | `app.css` mostly follows; `my-study-plan.css` contains raw visual/geometry values and physical assumptions | Implemented differently | `DESIGN.md`; `my-study-plan.css` | Separate documented geometry constants from visual tokens and remove physical/color literals. |
| Contrast measurement | WCAG 2.2 AA contrast | Bulma/token values reviewed in source, but computed-state contrast was not measured | Cannot verify | no automated contrast report; only rendered visual inspection | Run axe/contrast checks for default, hover, focus, disabled and semantic states. |

## 9. Prioritized Findings

### Critical

| ID | Severity | User role affected | Page / journey | Problem | Evidence | User impact | Root cause | Recommended solution | Relevant files | Complexity |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| UX-001 | Critical — fixed | Public/invited user | Account activation | Query-token content was evaluated as Alpine code after browser entity decoding, enabling reflected script execution. | Harmless browser probe added `data-probe` to `<html>`; prior `activate.jte` embedded `${model.token()}` in `x-data`. | Account/session compromise, malicious redirects or actions on a public page. | Untrusted data and executable Alpine expression shared one HTML attribute. | Completed: inert `data-token`, static component, regression assertion that token never enters code. | `activate.jte`, `activate.js`, `RegistrationFlowIntegrationTest` | Small |
| UX-002 | Critical — fixed | Teacher, students | Gradebook/class roster | A teacher was forbidden from the full membership endpoint, so every grade row could show the same `نامشخص`. | `TEACHER` has `GRADE_MANAGE` and `MEMBER_NAMES_VIEW` but not `MEMBERSHIP_MANAGE`; prior `grades.js` converted the 403 to `[]`. | A teacher could associate a real grade with the wrong learner—an academic-record integrity risk. | Cross-module identity composition used an over-privileged endpoint and treated failure as empty data. | Completed: shared least-privilege name helper, 100-ID batches, fallback on classes/grades/study/exams, fail-visible HTTP errors. | `member-names.js`, `grades.js`, `classes.js`, `students.js`, `finance.js`, `study-plans.js`, `exams.js` | Medium |

### High

| ID | Severity | Role affected | Page / journey | Problem | Evidence | User impact | Root cause | Recommended solution | Files | Complexity |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| UX-003 | High | All authenticated roles | Global navigation | Desktop navigation clips school/person controls at 1440px; mobile becomes a very long flat menu. | Rendered dashboard/people/finance at 1440px; flat link set in `layout/navbar.jte`. | Users lose access to school switch, role context or logout and cannot identify their current page. | A growing module list is forced into one Bulma navbar row with no overflow/grouping model. | Redesign navigation into permission-aware groups/overflow, keep school/person controls fixed, add active state and test 1024/1280/1440. | `layout/navbar.jte`, `navbar.js`, `app.css`, `NavbarModel` | Large |
| UX-004 | High | Custom-role members/admins | Navigation/authorization | Custom roles can have valid permissions but no matching navigation, and their raw role code can appear. | Nav predicates compare only five system role codes; `RoleLabels.persian` falls back to code. | Authorized users face a dead end or expose internal identifiers; admins cannot trust custom-role setup end to end. | Navigation is role-code-driven while backend authorization is permission-driven; navbar model lacks display name/capabilities. | Return effective capabilities and role display names server-side; render navigation from capabilities, not role names. | `layout/navbar.jte`, `NavbarModel`, `RoleLabels`, IAM role APIs | Large |
| UX-005 | High | Admin, finance, teacher | People, expenses, study plans, exams | Several consequential/destructive actions execute immediately. | Membership active toggle; expense cancel; attachment delete; clear week; weekly exam regeneration can replace an unattempted exam. | Accidental loss, cancellation or access removal with no preview of scope. | No reusable confirmation contract/component despite a documentation rule. | Build one accessible confirm dialog and require object, consequence, cancel and precise final verb; add server idempotency/audit where needed. | `people.js`, `expenses.js/jte`, `study-plans.js/jte`, `exams.js/jte` | Medium |
| UX-006 | High | Finance/admin/families | Invoices, payments, expenses, reports | Money, dates and numbers use mixed raw ASCII/ISO, browser Gregorian inputs and inconsistent direction. | Rendered expense date showed `mm/dd/yyyy`; raw amounts/dates across finance templates; unit policy unresolved. | A user can misread amount/unit/due date or enter a date in the wrong calendar—material financial risk. | No authoritative money unit/date-input contract or shared formatter. | Decide persisted/display unit and Jalali input policy, then centralize formatting/parsing and test boundaries/timezones. | `finance.jte/js`, `expenses.jte/js`, `my-invoices.jte/js`, `jalali.js`, DTO specs | Large |
| UX-007 | High | Admin/teacher/finance | Large lists and mobile row actions | Core tables have no shared search/sort/pagination and hide action context behind horizontal scroll. | `DESIGN.md` threshold >25; rendered 390px people/finance/expenses; no pagination controls/endpoints. | Large schools become slow or impossible to operate; mobile users act without visible row identity. | Page-specific tables fetch full collections and rely on `table-container`. | Add server-backed pagination/filter/sort and total count; sticky identity/action columns or mobile row cards; explicit scroll cue. | People/students/classes/finance/expenses JTE, JS, APIs | Large |
| UX-008 | High | Keyboard/screen-reader users | Async forms and partial loads | Many management notifications are visual-only; required dependency failures can look like valid empty data. | Page-local `error`/`notice`; several subloads assign `[]` on failure; no shared focus/error summary. | Users may not know an action failed or may make decisions from incomplete data. | Duplicated fetch/error logic lacks required-vs-optional dependency semantics. | Shared alert/status/error-summary and fetch classification; block identity/financial actions on required subload failure; add retry. | Most management JTE/JS | Medium |
| UX-009 | High — fixed | Student | Timed exam attempt | One unbounded request locked every question, section and exit; earlier behavior also allowed navigation to abort an answer save. | Prior `savingQuestionId` page mutex and no timeout; review finding reproduced from source. | A stalled network request could consume the remaining exam time or a fast navigation could lose an answer. | Save state was global and navigation durability was unspecified. | Completed: per-question pending map, captured section, rollback, `keepalive`, announced status; remaining product task is explicit completion/offline policy. | `exam-take.js`, `exam-take.jte` | Small |
| UX-010 | High — fixed | Keyboard/screen-reader users | Study-plan dialogs | Declaring `aria-modal` without focus entry/trap/return let users traverse background and lose the opener. | Prior open/close only toggled Alpine state; both dialogs shared the flaw. | Dialog became disorienting and contradicted its accessibility API. | Bulma visual modal lacked a behavior layer. | Completed: shared focus utility, opener capture/restore, initial/step focus, Tab trap, Escape, Persian names. | `modal-focus.js`, both study-plan templates/scripts | Medium |
| UX-011 | High — fixed | Any OTP user | Dashboard phone to OTP | Unicode digit variants were stored distinctly from lookup and uniqueness, including pre-existing rows. | Prior raw `setPhoneNumber`; login client normalized only on OTP request. | Visually identical numbers could bypass uniqueness or become unreachable after canonical reads. | Canonicalization was trusted to one client and no data migration existed. | Completed: client/server canonicalization, V3 legacy backfill, collision guard/check constraint, Persian-flow/duplicate/migration tests. | `PhoneNumberCanonicalizer`, `IamFacadeImpl`, `V3__canonicalize_iam_phone_numbers.sql`, dashboard/OTP/migration tests | Medium |
| UX-012 | High | Teacher/admin/parent | Attendance and absence notification | The requested high-frequency school workflow does not exist in current spec or code. | No page, route, controller, module or API; only catalog/future mentions. | Teachers cannot record attendance or notify families; product journey is incomplete. | Missing product definition rather than a UI implementation defect. | Create an OpenSpec proposal with status model, corrections, audit, bulk entry, notification policy/consent/delivery and parent recovery. | New module/spec/page work | Large |

### Medium

| ID | Severity | Role affected | Page / journey | Problem | Evidence | User impact | Root cause | Recommended solution | Files | Complexity |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| UX-013 | Medium | Parent/student/admin/teacher | Messaging | Bale backend exists without a complete compose/inbox/delivery/read/recovery UI or product scope. | Bale packages/webhook; no messaging template/page. | Users cannot complete “send message” and cannot understand delivery failure/privacy. | Backend integration preceded user-facing requirements. | Define announcement vs chat vs automated notification, permissions, consent, retention, delivery/read/retry states. | OpenSpec + Bale/web modules | Large |
| UX-014 | Medium | Parent/student | My grades/invoices/exams/study | Self-service pages expose `دانش‌آموز #id` or `سرفصل #id`. | `my-invoices`, `my-grades`, exam/study label fallbacks. | Families cannot confidently map a record to a child or subject. | Self-service DTOs omit safe display labels and client composition is incomplete. | Include child/topic display names in caller-scoped DTOs; never expose a broad directory to solve it. | `/api/me` controllers/DTOs and self-service JS/JTE | Medium |
| UX-015 | Medium | Admin/teacher | Paid-module blocked states | “Contact support” is shown without a support or upgrade route. | Current-system limitation and page `module-not-entitled` messages. | Users reach a dead end and cannot evaluate price/scope/owner. | Billing/platform administration is explicitly incomplete. | Define entitlement owner and a real support/upgrade CTA; keep broad architecture as recommendation until product approval. | OpenSpec, blocked-state UI | Large |
| UX-016 | Medium | All/developers | Study calendar visual system | Page CSS violates token-only/logical/touch rules and fixes grid width at 900px. | `my-study-plan.css` raw colors/sizes/physical geometry versus its header comment and `DESIGN.md`. | Mobile readability/touch suffers and future visual changes fragment. | Geometry, spacing and theme values are not separated; design document is internally inconsistent. | Document geometry constants; tokenise visual values; create a mobile agenda/list alternative. | `my-study-plan.css`, `DESIGN.md` | Medium |
| UX-017 | Medium | All authenticated roles | Orientation/deep links | No active nav state, breadcrumb or preserved return-to-task context. | Shared navbar has no current route parameter; expanded/filter state is client-only. | Users lose location and cross-page workflow context, especially classes→students and invoices→payments. | Page shells do not send route/task context to shared navigation. | Add current route/capabilities to navbar; add contextual “back to …” only for multi-page task dependencies. | Page controllers, `NavbarModel`, navbar/template | Medium |
| UX-018 | Medium | Screen-reader users | Utility tables/copy fields/errors | Expense utility tables are body-only; some readonly copy controls and field errors lack complete association. | Expense category/vendor/attachment markup; invite-link controls; page-level errors. | Content is announced without column/purpose, and recovery requires exploration. | Dense utility markup bypasses component semantics. | Use definition/list or real header tables; labelled copy group; `aria-invalid`/`aria-describedby` plus summary links. | Expenses/people/students templates and JS | Small |
| UX-019 | Medium | New/existing users | Authentication recovery | No password reset; OTP delivery/resend/rate-limit UX is incomplete. | Login routes/templates and current SMS driver. | Locked-out users need technical/admin help and may repeatedly request codes. | Identity MVP stops at password and OTP happy paths. | Specify reset proof, resend delay, rate limits, masked phone, fallback and audit; implement before production OTP launch. | IAM spec/controllers/templates | Large |
| UX-020 | Medium | Admin/teacher/finance | Dense management pages | Expenses, study plans and exams present many framed forms/actions with competing primary hierarchy. | Rendered desktop/mobile pages and heavy nested `.box` use. | Higher learning cost, accidental action selection, poor scanability. | Features were appended to single pages without a task-level information architecture. | Separate overview from creation/configuration/details; one primary action per task section; progressive disclosure with persisted state. | `expenses.jte`, `study-plans.jte`, `exams.jte` | Large |
| UX-021 | Medium | Keyboard/screen-reader users | Visual tabs/mode switchers | False ARIA Tabs roles are fixed, but `href="#"` links still act as state buttons with no URL/history. | Login, exams, study-plan visual tab groups. | Semantics are no longer misleading, but back/forward/deep linking cannot address views and link behavior is artificial. | Component purpose is undocumented (navigation vs disclosure vs tabs). | Decide per group: real URLs for navigable views, buttons for mode switchers, or a tested full Tabs component. | Affected templates/JS, design system | Medium |

### Low

| ID | Severity | Role affected | Page / journey | Problem | Evidence | User impact | Root cause | Recommended solution | Files | Complexity |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| UX-022 | Low | All | Visual consistency | Logical utilities reduced repeated inline alignment/width rules, but data geometry and visual CSS are not cleanly separated. | `app.css`; study-grid dynamic styles | Ongoing maintenance and review ambiguity. | Page-by-page growth without a component/token lint boundary. | Keep only data-driven geometry inline; use documented logical utilities/tokens for visual styling. | CSS/JTE | Small |
| UX-023 | Low | All | Loading polish | Loading states are mostly text or button spinners. | Repeated `در حال بارگذاری...` strings | Understandable but weak for long or multi-part loads. | No loading/status component or latency thresholds. | Standard role=status component; show retry/detail after threshold rather than skeletons by default. | JTE/JS | Small |
| UX-024 | Low | All | Visual hierarchy | Card/box styling is overused for structural page sections. | Dense finance/expenses/study/exam templates | Pages feel heavier and more fragmented than necessary. | Bulma boxes became the default container. | Reserve boxes for repeated/framed objects; use headings, whitespace and dividers for major sections. | JTE/CSS | Medium |

## 10. Recommended Lightweight Design System

Keep the existing stack: JTE + Alpine/fetch + Bulma + custom CSS. Do not migrate to React/Vue/Svelte or add npm/build tooling to solve CSS consistency. Bulma 1.0.4 should remain the base component layer because templates already rely on it and no technical requirement exceeds SSR/Alpine capability. Keep `app.css` for tokens, accessibility floors and logical utilities; reduce `my-study-plan.css` to documented geometry plus tokens. Do not add Bump CSS alongside Bulma and do not introduce HTMX piecemeal; either would require an accepted proposal with migration/ownership boundaries.

### Tokens

- Spacing: keep `--space-1` to `--space-8` as 4/8/12/16/24/32/48/64 px.
- Radius: use 8px for cards/buttons, 12px for default Bulma radius, 16px only for large containers if already inherited.
- Shadows: keep two levels; do not use shadow for primary hierarchy.
- Motion: keep 120/180ms and global reduced-motion override.
- Separate visual tokens from data-geometry constants such as calendar start hour, hour height and minimum grid width; both must be named and documented.

### Typography

- One `title is-4` per page.
- `title is-5` for major page sections when needed.
- `title is-6` only inside compact boxes/cards.
- Use body text for explanatory copy; avoid hero-scale text inside admin workflows.

### Content Width

- Default pages: Bulma `.container`.
- Narrow auth/setup forms: `.app-narrow-container` or a dedicated auth width utility.
- Dense tables: full container width with `table-container`; define horizontal-scroll and mobile alternative.

### Colors

- Keep primary ink-blue token bridge in `app.css`. *(Point-in-time
  recommendation; superseded 2026-09-27 — the primary is Persian turquoise and
  the semantics are re-tinted. `DESIGN.md` §4 is authoritative. The rest of this
  file is left as the dated audit it is.)*
- Use Bulma semantic classes only for semantic state: success, warning, danger, info.
- Destructive actions: `is-danger`; final confirmation button not `is-light`.

### Buttons

- Primary: `.button.is-link`, one per section/page where possible.
- Secondary: `.button` or `.button.is-light`.
- Destructive: `.button.is-danger` for final action, `.is-danger.is-light` only for trigger.
- Links navigate; buttons mutate state or toggle UI.
- Loading: row/action-specific `is-loading`; disable duplicate submission.
- Mode switcher: use ordinary buttons with `aria-pressed` when there is no panel keyboard model; use real links for addressable views; use ARIA Tabs only with roving focus, arrow keys, labelled panels and documented history behavior.

### Forms

- Every input/select/textarea has visible label or computed `aria-label`.
- Required fields get consistent visible required marker.
- Invalid fields use `aria-invalid`, `.is-danger`, inline `.help.is-danger`, and section summary.
- Normalize Persian/Arabic digits before validation/send for all numeric/date-like text inputs.
- Canonicalize identity/uniqueness values again on the server. Client normalization is feedback, never a security/data-integrity boundary.
- Money fields must name the unit adjacent to both label and rendered value. Date fields must state Gregorian/Jalali input behavior and show a Jalali preview when native input remains Gregorian.

### Tables

- Shared table header/action-header partials.
- `th scope="col"` on all headers.
- Visually hidden "عملیات" header for action columns.
- Numeric/currency cells use `.app-num` and shared formatter.
- Long text wraps or truncates with title/expand affordance.
- Potentially large tables get toolbar: search, filters, page size, pagination, total count.
- Empty state belongs beside the table, not inside `tbody`.
- At touch widths use a tested card/row-details alternative or sticky identity/action columns; do not rely on invisible horizontal scroll alone.

### Badges And Alerts

- Badges/tags map to domain status with one label function per domain.
- Alerts/notifications:
  - danger: `role="alert"`, focus on critical submit failure;
  - success/info: `aria-live="polite"`;
  - empty state: plain notification with next action.

### Cards, Dialogs, Empty States

- Use cards/boxes for repeated items, modals, and framed tools; avoid nested cards.
- Dialog partial requirements: `role="dialog"`, `aria-modal="true"`, labelled title, Escape close, initial focus, Tab containment, focus on confirmation-step changes, and return to opener/fallback. Reuse `modal-focus.js` until a JTE dialog component owns the markup.
- Empty state partial: title, reason, primary recovery action if permitted, optional secondary link.

### Loading Indicators

- Button `is-loading` for local mutation.
- Section loading status with `role="status"` and `aria-live="polite"`.
- Retry button for network/server load failure.

### Icons

- Do not introduce an icon library yet. If later added as WebJar, use text+icon for dense row actions and `aria-label` for icon-only controls.

### Breakpoints

- Use Bulma breakpoints as default.
- Explicitly test: 390px mobile, 768px tablet, 1024px desktop, 1440px wide.
- Tables: horizontal scroll baseline; card/list alternative for parent/student self-service where readability matters more than density.

### RTL

- Keep document-level RTL.
- Use logical CSS properties only.
- LTR islands only for technical Latin strings: email, token, slug, raw IDs, phone. Persian Jalali dates and Persian digits should remain RTL.

### Reusable JTE Components / Fragments

Recommended extraction order:

1. `components/notice.jte`: danger/success/info/empty with live-region parameters.
2. `components/page-header.jte`: title, subtitle, optional primary action slot.
3. `components/empty-state.jte`: title, body, optional action.
4. `components/confirm-dialog.jte`: title, message, object label, cancel, confirm.
5. `components/table-action-header.jte`: visually hidden action column header.
6. `components/async-state.jte`: loading, empty, partial, alert, success and retry states with correct live-region behavior.
7. `components/mode-switcher.jte`: explicit `button`, link or full-tab variant; never ambiguous anchor-only markup.
8. `app-formatters.js`: Persian digits, approved Jalali/date strategy and approved currency unit; keep `member-names.js` and `modal-focus.js` narrow rather than creating a generic global utility bag.

## 11. Suggested Implementation Roadmap

### Immediate Fixes

1. Redesign the overflowing navbar enough to guarantee school/person/logout access at 1024–1440px; add active state.
2. Route navigation through effective capabilities so custom roles can reach authorized pages and show their display names.
3. Add reusable confirmation behavior to membership deactivate, expense cancel/attachment delete, clear week and weekly exam regeneration.
4. Decide and document money unit plus Jalali/native-date entry; then format finance/expense views consistently.
5. Convert required subload failures into explicit blocked/partial states with retry, starting with identities in grades/classes/exams/study plans.

### Next Sprint

1. Extract PageHeader, AsyncState, EmptyState, ConfirmDialog and table action-header JTE fragments.
2. Implement server-backed table toolbar/pagination/search on people, students, finance and expenses, with a mobile row-action strategy.
3. Improve parent/student DTOs and pages to show child/topic names and clear payment/exam next steps.
4. Define capability-aware read-only UI states for users who can view but cannot mutate.
5. Add automated browser keyboard/accessibility checks for skip link, auth errors, both modals, grade roster names and exam saves.

### Later Improvements

1. Write an attendance/absence-notification change proposal and design the full journey.
2. Define messaging/Bale user-facing workflows.
3. Define billing/upgrade UX for paid modules and platform/admin ownership.
4. Add browser-based visual/a11y regression checks for major pages at mobile/tablet/desktop widths.
5. Review Android/native UI separately; it is explicitly out of scope for the JTE design system.

## 12. Remaining Uncertainties

- Whether finance staff should see approve controls disabled/hidden or always rely on backend permission failure.
- Exact billing/upgrade path for paid modules.
- Whether attendance and messaging are intended for this web UI, Android, Bale, or all channels.
- Currency unit, rounding, and grouping standards beyond "Toman" text already present.
- Whether parent/student self-service DTOs should include child names server-side or continue client-side composition.
- Whether HTMX is a future desired stack change or only a prompt assumption.
- Canonical phone format beyond Unicode digit conversion/trim, and the operator-approved ownership process when V3 detects two legacy accounts with equivalent numbers.
- Whether visual mode groups should be URL-addressable navigation, button switchers, or true ARIA Tabs.
- Computed contrast, screen-reader output and real touch-device behavior were not measured by an automated accessibility lab. Source review and desktop/mobile browser rendering were completed; exact checks are recorded in the implementation report.
