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
- **Assignable roles.** Any UI offering a role picker lists exactly
  `STUDENT`, `TEACHER`, `PARENT`, `SCHOOL_ADMIN`, `FINANCE_ADMIN` with Persian
  labels. `PLATFORM_ADMIN` never appears — it has no working permission-check
  path (CLAUDE.md §7 item 1) and granting it would silently do nothing
  enforceable.

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
   `academic.ClassController` (`CLASS_MANAGE`, SCHOOL_ADMIN only per the
   admin/day-to-day split in CLAUDE.md §4). `academic` has no visibility into
   `iam` names (module boundary — `teacherId` is an opaque `Long`), so
   `classes.js` resolves teacher names client-side from the same
   `iam.MembershipController` list `/people` already uses, filtered to active
   `TEACHER` memberships — no backend change, no new `web`→`academic` Java
   dependency (the page composes two existing REST endpoints in JS, not two
   Facades in the controller).
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
5. **Finance** — `/finance` — set/view tuition fee, list invoices. Backed by
   `finance.FinanceController` (`FINANCE_MANAGE`/`FINANCE_VIEW`). First page
   that must render `ModuleNotEntitled` (403, module not subscribed) distinctly
   from `PermissionDenied` (403, role lacks the permission) — CLAUDE.md §5's two
   layers need two different user-facing messages here, not one generic 403.
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

### FINANCE_ADMIN

Same `/finance` page as SCHOOL_ADMIN — visibility is driven by the selected
role in the navbar, enforcement stays entirely on the backend permission check.

### TEACHER

`/students` (has `STUDENT_ENROLL`). A read-only class roster view is a
plausible next step but not scheduled — build when asked.

### PARENT / STUDENT

The existing read-only `/dashboard` only, for now. Grades views are blocked:
`GRADES_VIEW`/`GRADES_EDIT` permissions exist in the seed data but **no grades
feature or data model exists yet** — do not build UI ahead of the backend.

### Explicitly out of scope

`PLATFORM_ADMIN` UI, entitlement-toggling/school-suspension UI (no REST
endpoint exists for either — CLAUDE.md §7 item 1, §10), custom-role management,
any B2C module UI (AI Tutor, Exam Prep, etc. — CLAUDE.md §11 Q7). All gated
behind explicit backend work that hasn't happened.

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
