# Roshdeandishe — UX Roadmap

This document governs the management-UI buildout that CLAUDE.md §7 item 6 unblocked
(CSRF + the school/role switcher are both resolved). It does not redefine backend
architecture, domain model, or access control — CLAUDE.md remains the source of
truth for those, and wins on any conflict with this file.

## 1. Purpose & scope

Four pages exist today (`login`, `register`, `activate`, `dashboard`), all
read-only or auth-only. Everything *mutating* — membership, class, enrollment,
finance management — is still missing. This roadmap lists what to build, in what
order, and the UX conventions every new page must follow so the frontend stays
coherent as more pages are added by different people/sessions over time.

## 2. Shared UX patterns (binding for every page)

- **Layout.** Every page wraps `layout/main.jte` (`title`, `content` params).
  The `<head>` (WebJar stylesheets/scripts) is owned exclusively by the layout —
  a page needing a page-specific static asset uses the layout's `scripts` param
  (`List<String>` of script URLs), never a duplicated `<head>`.
- **Navbar.** Every authenticated management page renders the shared
  `layout/navbar.jte` template, fed by `web.NavbarModelAssembler`
  (`forCurrentUser()` for pages that need nothing else from `iam`, or
  `assemble(profile, memberships)` for pages that already fetched those for
  their own content — see `DashboardPageController`). Do not re-implement the
  school/role composition logic per page.
- **Tenant selection is client-side only.** `localStorage.selectedSchoolId` /
  `selectedRole` are the only source of "which school/role is active" (§11 Q2
  in CLAUDE.md) — the server never knows this. Every management page:
  - reads `selectedSchoolId` from `localStorage` on load;
  - sends `X-School-ID` on every `/api/admin/**` call;
  - sends `X-XSRF-TOKEN` (read from the `XSRF-TOKEN` cookie) on every mutating
    call (POST/PUT/PATCH/DELETE);
  - listens for the navbar's `school-changed` window event (dispatched by
    `static/js/navbar.js`) and reloads its data when it fires.
- **Code placement (2026-07-05 rule, from CLAUDE.md §2).** Bulma classes and
  small Alpine expressions stay inline in the template. Anything with `fetch`,
  a regex, or more than ~10 lines of JS moves to a static file under
  `src/main/resources/static/js/` as an `Alpine.data` component, registered on
  `alpine:init`. Custom CSS is always an external file, never inline `<style>`
  (no page has needed one yet). `csrfToken()` is a 3-line helper duplicated per
  component rather than extracted — extract only on the third real duplicate
  past what already exists, per the project's rule-of-three.
- **Language/direction.** UI text is Persian, page `dir="rtl"` (set once in the
  layout). Latin data — emails, tokens, IDs — gets `dir="ltr"` on its own
  element so it doesn't visually reverse.
- **Styling.** Bulma only, no custom CSS framework/pipeline, no npm/webpack build.
- **Errors.** Parse the response as `ProblemDetail` JSON and show
  `body.detail || body.title || <fallback text>` in a `notification is-danger`
  Bulma box. Wrap the parse in try/catch — some failures (e.g. the tenant
  filter's missing-header 400) are plain-text, not JSON. Successes use
  `notification is-success`; empty states use a plain `notification`.
- **Every mutation confirms.** A successful POST/PUT/PATCH sets a visible
  success notice (Persian, `notification is-success`) — a silently-updated
  list is not enough feedback. Errors and successes share one notice area at
  the top of the page section.
- **Role labels.** Raw role codes (`SCHOOL_ADMIN`, …) never reach the UI.
  Server-rendered templates use `web.RoleLabels.persian(code)`; client-rendered
  pages (API data) use the matching map in their `Alpine.data` component (see
  `people.js`). Keep the two in sync when roles change.
- **Navigation.** Every management page is reachable from the shared navbar's
  `navbar-start` links — never ship an orphan URL. Link *visibility* may follow
  the selected (display-only) role; access is still enforced server-side only.
- **Role dropdown is display-only.** It may drive which UI elements are shown,
  but never what the backend permits — permission checks always evaluate all of
  the user's active roles server-side (CLAUDE.md §11 Q2). Never build
  per-request "acting as role" logic off the back of it.
- **Assignable roles.** Any UI offering a role picker lists the five system
  roles — `STUDENT`, `TEACHER`, `PARENT`, `SCHOOL_ADMIN`, `FINANCE_ADMIN` —
  with Persian labels, **plus** (since 2026-07-13) any custom roles the
  school itself has created via `/roles` (CLAUDE.md §4's Role subsection).
  `PLATFORM_ADMIN` never appears — it has no working permission-check path
  (CLAUDE.md §7 item 1) and granting it would silently do nothing
  enforceable; it's also filtered out of `GET .../roles` server-side, not
  just hidden client-side.

## 3. Page inventory per persona

### SCHOOL_ADMIN (build order)

1. **People** — `/people` (`web.PeoplePageController`) — **built.**
   List/add members, toggle active, issue/resend invite tokens. Backed by
   `iam.MembershipController`.
2. **Navigation + funnel fixes** — **built.** No new backend; fixes the gaps
   that made slice 1 undiscoverable:
   - Page links in the shared navbar (`navbar-start`): Dashboard always;
     People only while the *selected* role is `SCHOOL_ADMIN` (first real
     consumer of the display-only role dropdown — visibility only, backend
     still enforces). New pages MUST add their link here; no orphan URLs.
   - Proper Bulma `navbar-menu` + burger so the navbar works on mobile
     (previous markup skipped `navbar-menu` entirely).
   - The navbar persists its *resolved default* school/role to localStorage
     on init — before this, a single-school admin who never clicked the
     dropdown got "select a school" on `/people` despite the navbar visibly
     showing their school as selected.
   - Persian role labels everywhere via `web.RoleLabels` (server-rendered
     navbar + dashboard) mirrored by the map in `people.js` (client-rendered
     API data) — raw codes like `SCHOOL_ADMIN` never reach the UI.
   - Success notices on every mutation in `/people` (previously only the
     invite action confirmed; add/toggle succeeded silently).
3. **Classes** — `/classes` (`web.ClassesPageController`) — **built.**
   Create/list classes (name, optional section, optional teacher). Backed by
   `academic.ClassController` (create: `CLASS_MANAGE`, SCHOOL_ADMIN only per
   the admin/day-to-day split in CLAUDE.md §4; list: `CLASS_VIEW`,
   SCHOOL_ADMIN + TEACHER — split out 2026-07-07 after enrollment revealed
   TEACHER couldn't populate the class picker on `/students` without read
   access to the class list, see CLAUDE.md §4). `academic` has no visibility
   into `iam` names (module boundary — `teacherId` is an opaque `Long`), so
   `classes.js` resolves teacher names client-side from the same
   `iam.MembershipController` list `/people` already uses, filtered to active
   `TEACHER` memberships — no backend change, no new `web`→`academic` Java
   dependency (the page composes two existing REST endpoints in JS, not two
   Facades in the controller). Navbar link widened 2026-07-07 to also show
   for `TEACHER` (now that `CLASS_VIEW` lets them load the page usefully) —
   read-only in practice, since `CLASS_MANAGE` stays `SCHOOL_ADMIN`-only and
   the create form falls back to the same generic permission-denied
   notification every other page already shows; no client-side role-gating
   of the form itself, consistent with how the rest of the app never hides
   UI by permission, only by navbar-link visibility.
4. **Enrollment** — `/students` (`web.StudentsPageController`) — **built.**
   Enroll students into classes, listed with a class picker. Backed by
   `academic.StudentController` (`STUDENT_ENROLL`, SCHOOL_ADMIN + TEACHER) —
   required adding `AcademicFacade.listStudents(schoolId)` /
   `StudentRepository.findBySchoolId` and a `GET` on `StudentController`
   (previously enroll-only + get-by-id, no roster listing existed). Same
   composition pattern as Classes: `academic` has no visibility into `iam`
   names, so `students.js` resolves student name/email client-side from
   `iam.MembershipController`, filtered to active `STUDENT` memberships; class
   names are resolved from the existing `academic.ClassController` list
   (fetched client-side, same as the class picker). Navbar link added,
   visible while the selected role is `SCHOOL_ADMIN` or `TEACHER` (first link
   shown to a non-admin role, since `STUDENT_ENROLL` is granted to both).
   **Deepened 2026-07-17**: a per-row "مدیریت والدین" panel (guardian
   management, `GUARDIAN_MANAGE`/SCHOOL_ADMIN-only) — see CLAUDE.md §7 Done,
   "Bale integration gap cleanup". Zero backend change; closes the "curl-only"
   gap `MOBILE_ROADMAP.md` had flagged since the Bale Phase 1 build.
5. **Finance** — `/finance` (`web.FinancePageController`) — **built, deepened
   2026-07-12.** Set/view the school's tuition fee and list its invoices,
   entirely client-side against the existing `finance.FinanceController`
   (`FINANCE_MANAGE`/`FINANCE_VIEW`). First page that renders
   `ModuleNotEntitled` (403, school hasn't subscribed to FINANCE) distinctly
   from `PermissionDenied` (403, role lacks the permission) — `finance.js`
   inspects the `ProblemDetail.type` URI suffix (`module-not-entitled` vs
   `permission-denied`, set by `IamProblemResolver`) and shows two different
   Persian messages, blocking the whole page body in both cases rather than
   rendering a broken form. Invoice rows only carry an opaque `studentId`
   (finance has no visibility into student names, same module-boundary reason
   as teacher/student names elsewhere), so `finance.js` resolves names the
   same way `students.js` does: `academic.StudentController` (id → userId)
   composed with `iam.MembershipController` (userId → name), both
   best-effort — if the current role lacks `STUDENT_ENROLL`, invoices just
   show `#id` instead of breaking. Navbar link visible while the selected
   role is `SCHOOL_ADMIN` or `FINANCE_ADMIN` (§3's FINANCE_ADMIN persona
   reuses this same page). **Deepened 2026-07-12 (CLAUDE.md §7 Done,
   "Finance deepening, phase 1"):** each invoice row now shows a derived
   status tag (PAID/PARTIAL/UNPAID/OVERDUE) with an expandable payment-history
   panel and a record-payment form, plus a backfill button for students
   enrolled before a fee was ever set — no new page, same `/finance`.
8. **Expenses** — `/expenses` (`web.ExpensesPageController`) — **built
   2026-07-12**, from a user-supplied Persian design document (CLAUDE.md §7
   Done, "Finance deepening, phase 2"). Manage expense categories/vendors,
   record/approve/pay/cancel expenses, attach receipts, and view a
   Jalali-period expense report — entirely client-side against the new
   `finance.ExpenseController` (`EXPENSE_VIEW`/`EXPENSE_MANAGE`/
   `EXPENSE_APPROVE`). Same `classifyError`/two-layer-403 pattern as
   `finance.js`, reused verbatim. First page in this codebase to handle file
   upload (receipt attachments) and the first to use a Jalali calendar
   (`static/js/jalali.js`, client-side only — the report-period picker
   converts a Persian month/quarter/year to a Gregorian date range before
   calling the server). Navbar link visible for the same roles as `/finance`
   (`SCHOOL_ADMIN`/`FINANCE_ADMIN`) — see CLAUDE.md §4's `Expense` subsection
   for the full domain model and lifecycle.
9. **Roles & permissions** — `/roles` (`web.RolesPageController`) — **built
   2026-07-13**, resolving CLAUDE.md §11 Q3. Lists every role visible to the
   school (five system roles, read-only, plus any custom roles already
   created) with its permission set, and a form to create a new custom role
   from a checklist of the existing fixed permission catalog — entirely
   client-side against the new `iam.RoleController` (`ROLE_MANAGE`,
   `SCHOOL_ADMIN`-only). `/people`'s role-assignment dropdown was extended to
   fetch the same role list, so a freshly-created custom role is immediately
   assignable there too — see CLAUDE.md §4's Role subsection for the full
   model and the three explicit scoping decisions. Navbar link visible for
   `SCHOOL_ADMIN` only, same as `/people`.
6. **School registration** — `/schools/new` — **built.** First shipped (PR #16)
   as an inline form on the dashboard; moved to this dedicated page 2026-07-06
   because real people don't casually "create a school" from their own
   dashboard — the form was showing up for every logged-in user, including
   invited students/parents with no reason to see it. Linked from the new
   public landing page (`GET /`, item 7) and from the dashboard's
   empty-membership state, not from the navbar (it's a one-time bootstrapping
   action, not ongoing management). `createSchoolForm` now lives in
   `/js/school-new.js`. Self-service creation itself is unchanged (CLAUDE.md
   §1) — this was a presentation fix, not a new gate.
7. **Public landing page** — `GET /` — **built** alongside item 6.
   `web.LandingPageController`: login/register CTAs plus the "ثبت مدرسه" link
   to `/schools/new`, for anonymous visitors. An already-authenticated visitor
   is redirected straight to `/dashboard`.
10. **School entry page** — `GET /s/{slug}` — **built 2026-07-19** (Tier 1 of
   the per-school-domain decision, CLAUDE.md §11 Q9). Public branded entry
   page per school: school name + ورود CTA (or "ادامه به داشبورد" for an
   already-authenticated visitor — deliberately no redirect, the CTA click
   writes `localStorage.selectedSchoolId` so post-login the switcher lands on
   that school), invite-activation link, and a teaching 404 state for
   unknown/suspended slugs. Server-rendered (`web.SchoolEntryPageController`
   + `school-entry.jte`), no navbar (anonymous trust model, same as `/`), no
   fetch — an explicit deviation from the management-page contract. Not in
   the navbar: entry-point-flow exception (same as `/` and `/schools/new`) —
   the school distributes its own URL, and the admin picks the slug on
   `/schools/new` (new optional field with live URL preview). See CLAUDE.md
   §7 Done ("School entry page") for the slug's DNS-label validation and
   what Tier 2/3 defer.

### FINANCE_ADMIN

Same `/finance` and `/expenses` pages as SCHOOL_ADMIN — visibility is driven by
the selected role in the navbar, enforcement stays entirely on the backend
permission check. One real difference: `FINANCE_ADMIN` does **not** hold
`EXPENSE_APPROVE` (segregation of duties, CLAUDE.md §4) — the approve button
on `/expenses` 403s for this role even though the rest of the page works.

### TEACHER

`/students` (has `STUDENT_ENROLL`). **Read-only class roster view — built
2026-07-07**, on `/classes`: a "مشاهده دانش‌آموزان" toggle per class row
(plain Persian, not a transliteration of "roster") opens a dismissible
panel listing that class's enrolled students, reusing the same
`academic.StudentController` `GET .../students` endpoint `/students` already
calls (`STUDENT_ENROLL`, no backend change). See CLAUDE.md §7 for the
fallback-name bug found and fixed while building it.

### PARENT / STUDENT

The existing read-only `/dashboard` only, plus `/my-invoices` and
`/my-study-plan` (below), for now. **Grades backend is built (CLAUDE.md §11
Q8, resolved 2026-07-18) but there is still no `/grades`/`/my-grades` UI** —
`academic.GradeController` (admin gradebook) and `academic.MyGradesController`
(`GET /api/me/schools/{schoolId}/grades`, the established "my" pattern — own
grades + linked wards' grades) exist and are tested
(`GradeManagementFlowIntegrationTest`), same posture `curriculum`/`studyplan`
shipped with at their own new-module time: backend + REST first, UI as a
separate slice through the `design-page` skill. When built, `/my-grades`
should follow the same shape as `/my-invoices` below (STUDENT: own; PARENT:
linked wards'), and an admin/teacher gradebook page (`/grades`) should follow
`/finance`'s pattern (`GRADES_EDIT`-gated — deliberately not `GRADES_VIEW`,
see CLAUDE.md §4's Grade subsection for why). A PARENT's own self-service data
(which children are theirs, their tuition status) is available today through
the Bale bot, and — since the 2026-07-12 payments deepening — also via
`GET /api/me/schools/{schoolId}/invoices` (`finance.MyInvoicesController`,
CLAUDE.md §4's `Invoice` subsection). **A dedicated page now exists:
`/my-invoices`** (`web.MyInvoicesPageController`, built 2026-07-13 on explicit
request) — read-only, for both STUDENT (own invoices) and PARENT (linked
wards' invoices), no new REST behind it. Navbar link visible while the
selected role is `STUDENT` or `PARENT`. See CLAUDE.md §7 Done ("Finance
deepening, phase 3"). Neither this nor the Bale bot is a substitute for a
real `/grades`/`/my-grades` web page.

**A study-plan self-view now also exists: `/my-study-plan`**
(`web.MyStudyPlanPageController`, built 2026-07-17 from a Claude Design
handoff bundle) — read-only weekly calendar of the student's own
auto-scheduled study sessions (RTL, Jalali dates, week/month navigation) plus
a client-computed statistics tab; a student can mark a session done and log
a test count (the only per-session fields the real `StudyIntervalEntity`
carries — see CLAUDE.md §7 Done, "Student study-plan self-view", for the
explicit decision not to extend that schema to match the design mockup's
richer invented fields). Backed by the new
`studyplan.MyStudyIntervalController`, no new REST behind `/my-exams`'s
established "my" pattern. Navbar link visible while the selected role is
`STUDENT` only (a study plan is assigned per-student, not per-guardian —
unlike `/my-invoices`, `PARENT` has no linked-wards view here).

**Also as of 2026-07-13:** the admin `GET .../invoices` endpoint (used by
`/finance`, item 5 above) is no longer unscoped for `PARENT` callers — a
`PARENT` with no `SCHOOL_ADMIN`/`FINANCE_ADMIN` role at the school now sees
only their own linked children's invoices there too, closing the gap this
section previously left open (a parent could otherwise see the whole
school's tuition data via `/finance` even without `/my-invoices` existing).
`SCHOOL_ADMIN`/`FINANCE_ADMIN` visibility on `/finance` is unchanged.

### Explicitly out of scope

`PLATFORM_ADMIN` UI, entitlement-toggling/school-suspension UI (no REST
endpoint exists for either — CLAUDE.md §7 item 1, §10), any B2C module UI
(AI Tutor, Exam Prep, etc. — CLAUDE.md §11 Q7). All gated behind explicit
backend work that hasn't happened. (Custom-role management shipped
2026-07-13 — see item 9 above and CLAUDE.md §11 Q3 — and is no longer on
this list; editing an *existing* system role's permissions through the UI
remains out of scope, a deliberate part of that same decision.)

## 4. Slicing rationale

**People first.** It's the tenant's bootstrapping loop — a school with zero
members has nothing else to manage — and building it exercises every shared
pattern above exactly once (navbar reuse, client-side tenant header, CSRF,
the static-JS code-placement rule, ProblemDetail rendering). Every later page
in the SCHOOL_ADMIN order depends on people existing (classes need a teacher
to (optionally) assign, enrollment needs students, finance needs students to
invoice). One page = one slice; each slice ships as: the page + whatever
minimal facade/DTO enrichment it needs + one integration test covering the
page shell and the REST flow behind it.

## 5. Per-slice definition of done

- [ ] Page renders behind login (redirects to `/login` when unauthenticated).
- [ ] Shared navbar rendered via `NavbarModelAssembler` + `layout/navbar.jte`,
      **and the page has a navbar link** (no orphan URLs).
- [ ] All mutating fetches send both `X-School-ID` and `X-XSRF-TOKEN`.
- [ ] ProblemDetail errors rendered via the shared pattern (§2), tolerant of
      non-JSON error bodies; **every successful mutation shows a success notice**.
- [ ] Role codes rendered through `RoleLabels`/the client map — never raw.
- [ ] New interactive widgets are keyboard-usable (Escape/light-dismiss where
      applicable) and carry correct ARIA state (`aria-expanded`, …).
- [ ] Checked at a mobile viewport (~375px) — navbar burger and page layout hold.
- [ ] At least one integration test (`@SpringBootTest` + `MockMvc`, following
      `LoginDashboardFlowIntegrationTest`'s pattern) covering the page shell and
      the REST flow it drives.
- [ ] `mvn test -Dtest=ModularityTests` still green (module boundaries intact).
- [ ] CLAUDE.md updated (module structure §3, "Done" list §7) if the slice adds
      a new controller, facade method, or resolves an open item.

## 6. Critical user-journey review (2026-07-19)

A persona-by-persona walkthrough of every journey against what is actually
built (pages, navbar links, permissions, seed data), ranked by how badly each
gap hurts. Re-run this review after each gap below is closed and update this
section — it is the standing prioritization for the next slices.

### 6.1 CRITICAL — a real school's onboarding funnel dead-ends at creation

Journey: visitor lands on `/` → registers → creates a school at `/schools/new`
→ becomes `SCHOOL_ADMIN` → **cannot use the product**. A freshly created
school has zero entitlements and there is *no path whatsoever* — no REST, no
UI, no billing — to ever grant one (`IamFacade.setSchoolEntitlement` is
facade-only by explicit decision, CLAUDE.md §7 item 1). Only the seeded demo
school (id 1) gets `ACADEMIC`/`FINANCE`/`CURRICULUM`/`STUDY_PLANNER`/`EXAM`.
A real founder can use `/people` and `/roles` (not module-gated) but every
other navbar link — `/classes`, `/students`, `/finance`, `/expenses`,
`/curriculum`, `/study-plans`, `/exams` — 403s `module-not-entitled`. The
core product journey currently works **only for the demo school**.

This is not a UI slice — it needs an explicit product decision first (trial
entitlement bundle at creation vs. ops-script stopgap vs. wait for billing) —
see CLAUDE.md §11 Q10. Do not bolt an entitlement-granting endpoint onto
unrelated work (§10 discipline); decide it on purpose.

### 6.2 HIGH — two finished backends are unreachable by any real user

1. **Grades has no UI at all.** A teacher cannot enter term grades and a
   student/parent cannot see them — backend + REST + tests done 2026-07-18
   (CLAUDE.md §11 Q8), journey completeness 0%. Next slices: `/grades`
   (teacher/admin gradebook, `GRADES_EDIT`-gated, `/finance`-style) and
   `/my-grades` (`/my-invoices`-shaped: STUDENT own, PARENT linked wards) —
   both through the `design-page` skill, as §3 above already specifies.
2. **Phone number has no UI, stranding Bale + OTP entirely.**
   `PUT /api/me/phone` is called from no page or JS, and `/login` has no OTP
   form — so no real user can ever link the Bale bot or use OTP login through
   the UI; both features are curl-only today. Smallest unlock: a phone-number
   field on `/dashboard` (or a future profile surface) calling the existing
   endpoint; an OTP tab on `/login` is a separate follow-up slice.

### 6.3 MEDIUM — activation-funnel friction on the highest-volume flow

Enrolling a student on `/students` creates a passwordless account, but the
invite step lives on a different page (`/people` → find membership → issue
invite) with nothing pointing there, and `/people` doesn't show which members
have never activated (no password yet). For a school onboarding dozens of
students/parents this is the main drop-off point. Candidate fixes (one
slice): an "activated / awaiting invite" indicator on `/people` rows (needs a
small `MembershipInfo` enrichment, e.g. a `hasPassword` flag), and an invite
shortcut in `/students`' enrollment success path. Also note: SMTP defaults to
localhost:1025, so until deployment config exists the *real* journey is
`emailSent=false` + manual link handover.

### 6.4 LOW — degraded-but-working day-to-day paths

- **TEACHER sees "نامشخص" for student names on `/students`** — name
  resolution is gated by `MEMBERSHIP_MANAGE`, which TEACHER lacks. Known
  (CLAUDE.md §7 Done, "TEACHER persona"); the real fix is a narrower
  member-name read endpoint — a permission-model decision, still parked.
- **FINANCE_ADMIN sees `#id` instead of student names on `/finance`** (lacks
  `STUDENT_ENROLL`) — same class of gap, same parked fix.
- **School entry URL is write-only**: the slug can only be set at creation
  (no school-settings surface — deliberate, CLAUDE.md §11 Q9) and the
  dashboard never displays the `/s/{slug}` URL afterward. Showing the
  school's own entry URL on `/dashboard` for `SCHOOL_ADMIN` is a small,
  decision-free display slice; *editing* the slug stays gated on the
  school-settings permission decision.

### 6.5 Verified solid end-to-end

Login/register/activate; school/role switcher incl. zero memberships; the
full SCHOOL_ADMIN management loop *for an entitled school*; parent invoice
scoping (`/my-invoices` + scoped admin endpoint); student exam-taking
(`/my-exams` → `/exam-take`); `/my-study-plan`.

### 6.6 Recommended order of attack

1. Decide §6.1 (entitlement path — everything else is moot without it).
2. Grades UI (`/grades` + `/my-grades`).
3. Phone-number field (unlocks Bale + OTP), then an OTP login tab.
4. Invite-status visibility on the enrollment funnel (§6.3).
5. Dashboard entry-URL display (§6.4) opportunistically.
