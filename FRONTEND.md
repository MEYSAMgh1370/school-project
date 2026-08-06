# Roshdeandishe — Frontend Onboarding Guide

**Who this is for:** a frontend developer joining this project. It explains what the
product does, how the frontend is built, the rules every page follows, and the full
API surface you'll be calling.

**What you do *not* need to know:** Java. Almost all frontend work here happens in two
kinds of files — `.jte` templates (HTML with a small template syntax) and plain
`.js` files. The one Java file per page is a 10-line stub you can copy.

Other docs in this repo, for reference:

| File | What it is |
|---|---|
| `openspec/project.md` | Curated documentation map and evidence order. Start here when deciding what is current. |
| `openspec/specs/current-system.md` | Current product behavior and known limitations, checked against code. |
| `CLAUDE_LEGACY.md` | Legacy detailed architecture/domain notebook. Useful context, but verify against code and OpenSpec. |
| `UX_ROADMAP.md` | §2 is the binding list of shared UI conventions; §3 is the page inventory with build history. |
| `DESIGN.md` | The design system: tokens, typography, color, RTL, Persian dates/digits. |
| `README.md` | How to run it locally. |
| `openspec/conventions.md` | Shared engineering discipline, backend conventions, and test commands. |

---

## 1. What the product is

**Roshdeandishe (رشدِاندیشه) is a multi-tenant SaaS school management system for
Iranian schools.** The UI is **Persian and RTL**. Code comments in this repo are
mostly Persian too; docs are English.

"Multi-tenant" here means: one running app serves many schools. A **school** is the
tenant. A person has **one account** and can hold roles at several schools at once
(teacher at School A, parent at School B) — the same login, switched in the navbar.

There are four user-facing roles plus a finance role:

| Role | Code | What they do in the UI |
|---|---|---|
| School admin | `SCHOOL_ADMIN` | Everything: members, roles, classes, students, grades, finance, expenses, curriculum, study plans, exams |
| Teacher | `TEACHER` | Classes, students, grades, question bank, study plans, exams |
| Finance admin | `FINANCE_ADMIN` | Tuition fees, invoices, payments, school expenses |
| Student | `STUDENT` | Their own grades, invoices, study plan, exams |
| Parent | `PARENT` | Their linked children's grades and invoices |

Schools are created **self-service** — anyone can register an account, create a
school at `/schools/new`, and becomes its `SCHOOL_ADMIN` immediately.

### Feature areas ("modules")

The product is sold as subscribable feature areas. A school gets **ACADEMIC** and
**FINANCE** free at creation; **CURRICULUM**, **STUDY_PLANNER** and **EXAM** are paid
plans that aren't granted automatically (no billing exists yet, so in practice
they're granted by a developer).

This matters to you because **a page can be blocked for two different reasons**, and
they must show different messages — see §7.

### What already exists

Every page listed in §5 is built. The management UI is essentially complete for
`SCHOOL_ADMIN`; student/parent self-service pages exist too. There's also a native
Android student app (`android/`, Kotlin/Compose) that talks to the same backend via
JWT — out of scope for this guide.

---

## 2. The stack, and the one rule that shapes everything

```
JTE templates  +  Alpine.js  +  Bulma  +  WebJars
(server-rendered)  (sprinkles)  (CSS)    (deps via Maven)
```

- **JTE** — precompiled, type-checked Java templates. Think "HTML with `@param`,
  `@if`, `@for`". Server-rendered.
- **Alpine.js 3.15** — all client interactivity. No React/Vue/Svelte.
- **Bulma 1.0.4** — the CSS framework. No Tailwind, no custom framework.
- **WebJars** — Alpine and Bulma arrive as Maven dependencies and are served from
  `/webjars/...`. Not a CDN, not npm.

> **There is no npm, no webpack, no vite, no `package.json`, no build step for the
> frontend.** This is a deliberate, load-bearing decision (`CLAUDE_LEGACY.md` §2). Don't
> introduce one, and don't add a CDN `<script>` tag — a new dependency has to come in
> as a WebJar, which is an explicit decision to raise with the team.

Why this way: the backend is a session-cookie Spring app; an SPA would have forced a
token-auth rework for no product benefit. Server-rendered pages + Alpine keeps the
whole thing one deployable artifact.

---

## 3. Running it locally

```bash
# 1. Postgres — an empty database is enough; Flyway creates the schemas
createdb roshdeandishe_iam        # defaults: localhost:5432, postgres/postgres

# 2. Run
./mvnw spring-boot:run            # http://localhost:8080
```

Log in with the seeded demo admin: **`admin@roshdeandishe.test` / `password`**
(dev only — not seeded in production). There's a demo school with all five modules
enabled, so every page is reachable out of the box.

Swagger UI (live API reference, with try-it-out): **http://localhost:8080/swagger-ui.html**

### ⚠️ The single biggest gotcha: templates and static files are compiled in

`gg.jte.use-precompiled-templates: true`, and Spring Boot serves `static/` from
`target/classes`, not from `src/`. **Editing a `.jte`, `.js` or `.css` file and
refreshing the browser does nothing** — you have to rebuild and restart:

```bash
# Ctrl-C, then
./mvnw spring-boot:run
```

Two known symptoms of a stale build:
- Your JS/CSS edit "isn't applying" — it's serving the old copy from `target/classes`.
- A **500 "Circular view path"** on a page — the app is running against a build that
  predates that template's compiled class. Rebuild.

If this slows you down, adding `spring-boot-devtools` locally is a reasonable ask —
raise it rather than working around it.

---

## 4. Repo map — the files you'll actually touch

```
src/main/jte/                      ← page templates (HTML)
├── layout/
│   ├── main.jte                   ← the ONLY <head>. Every page wraps this.
│   └── navbar.jte                 ← shared school/role switcher + all page links
├── landing.jte  login.jte  register.jte  activate.jte  school-entry.jte
├── dashboard.jte  school-new.jte
├── people.jte  roles.jte  classes.jte  students.jte  grades.jte
├── curriculum.jte  study-plans.jte  exams.jte
├── finance.jte  expenses.jte
└── my-grades.jte  my-invoices.jte  my-exams.jte  my-study-plan.jte  exam-take.jte

src/main/resources/static/
├── css/
│   ├── app.css                    ← design tokens (:root) + a11y floor. Global.
│   └── my-study-plan.css          ← the one page-specific stylesheet so far
└── js/
    ├── navbar.js                  ← school/role switcher logic (loaded by every page)
    ├── jalali.js                  ← Jalali ↔ Gregorian date helper (no dependencies)
    └── <page>.js                  ← one Alpine.data component per page

src/main/java/com/example/roshdeandishe/web/
└── <Page>PageController.java      ← ~10-line Java stub per page (see §6)
```

Everything else under `src/main/java/` is backend, except the pure auth/identity
page controllers in `iam.shared` (`login`, `register`, `activate`). The `web`
package owns cross-module product page composition, and its controllers are
deliberately dumb — they render a shell, and the page fetches its own data over
REST.

---

## 5. Page inventory

Every page below is real and reachable. "Visible for" = which selected role shows the
navbar link (visibility only — the backend enforces access independently).

### Public (no login)

| URL | Template | What it is |
|---|---|---|
| `/` | `landing.jte` | Marketing landing; redirects to `/dashboard` if already logged in |
| `/login` | `login.jte` | Login |
| `/register` | `register.jte` | Self-service sign-up; auto-logs-in and goes to `/dashboard` |
| `/activate?token=…` | `activate.jte` | Set a first password from an emailed invite token |
| `/s/{slug}` | `school-entry.jte` | A school's branded public entry page |
| `/exam-take?token=…` | `exam-take.jte` | The exam-taking screen. No navbar, own token auth |

### Authenticated

| URL | Template / JS | Visible for | What it does |
|---|---|---|---|
| `/dashboard` | `dashboard.jte` | everyone | Memberships, school entry URL, empty-state CTA |
| `/schools/new` | `school-new.js` | (linked, not in navbar) | Create a school; you become its admin |
| `/people` | `people.js` | `SCHOOL_ADMIN` | Members: add, toggle active, issue/resend invite |
| `/roles` | `roles.js` | `SCHOOL_ADMIN` | List roles + permissions; create custom roles |
| `/classes` | `classes.js` | admin, teacher | Create/list classes |
| `/students` | `students.js` | admin, teacher | Enroll students; per-row guardian management |
| `/grades` | `grades.js` | admin, teacher | Gradebook: class + year + term → editable roster |
| `/curriculum` | `curriculum.js` | admin, teacher | Topic tree + multiple-choice question bank |
| `/study-plans` | `study-plans.js` | admin, teacher | Study plans, weekly schedules, auto-scheduling |
| `/exams` | `exams.js` | admin, teacher | Author collective exams, assign students, results |
| `/finance` | `finance.js` | admin, finance | Tuition fee, invoices, payments, backfill |
| `/expenses` | `expenses.js` | admin, finance | Expense categories/vendors/expenses, receipts, Jalali reports |
| `/my-grades` | `my-grades.js` | student, parent | Own / children's grades |
| `/my-invoices` | `my-invoices.js` | student, parent | Own / children's invoices |
| `/my-exams` | `my-exams.js` | student | Own exams; start an attempt |
| `/my-study-plan` | `my-study-plan.js` | student | Own study schedule; mark intervals done |

---

## 6. Anatomy of a page — build one end to end

Three files. Here's `/grades`, verbatim in shape.

### (a) The Java stub — `web/GradesPageController.java`

```java
@Controller
public class GradesPageController {

    private final NavbarModelAssembler navbarModelAssembler;

    public GradesPageController(NavbarModelAssembler navbarModelAssembler) {
        this.navbarModelAssembler = navbarModelAssembler;
    }

    @GetMapping("/grades")
    public String gradesPage(Model model) {
        model.addAttribute("navbar", navbarModelAssembler.forCurrentUser());
        return "grades";       // → src/main/jte/grades.jte
    }
}
```

That's the whole thing. It renders a shell — **it fetches no data**. Copy it, change
two strings. (If your page's URL should load without an `X-School-ID` header — all of
them should — add its path to `EXCLUDED_PATHS` in
`platform/TenantResolverFilter.java`; the pattern is obvious from the existing list.)

### (b) The template — `src/main/jte/grades.jte`

```jte
@import com.example.roshdeandishe.web.NavbarModel

@param NavbarModel navbar

@template.layout.main(
    title = "نمرات",
    scripts = java.util.List.of("/js/navbar.js", "/js/jalali.js", "/js/grades.js"),
    content = @`
@template.layout.navbar(navbar = navbar)

<section class="section" x-data="gradesPage()">
    <div class="container">
        <h1 class="title is-4">نمرات</h1>

        <div class="notification is-warning" x-show="!schoolId">
            برای مدیریت نمرات، یک مدرسه از سوییچر بالا انتخاب کنید.
        </div>

        <template x-if="schoolId">
            <div>
                <div class="notification is-danger" x-show="error" x-text="error"></div>
                <div class="notification is-success" x-show="notice" x-text="notice"></div>
                ...
            </div>
        </template>
    </div>
</section>
`)
```

JTE syntax you need: `@import`, `@param`, `@if`/`@endif`, `@for`/`@endfor`,
`${expression}` for interpolation, `@template.x.y(...)` to call another template, and
`@`...`` ` `` for a content block. That's about all this codebase uses.

Rules baked into that snippet:
- **`layout/main.jte` owns `<head>`.** Never write your own. Page-specific JS goes in
  the `scripts` list, page-specific CSS in a `styles` list. `scripts` entries are
  emitted *before* Alpine's own tag on purpose — both are `defer`, they run in
  document order, and your `Alpine.data(...)` registration must exist before
  `alpine:init` fires.
- **Every authenticated page renders `layout/navbar.jte`.**
- **Add your page's link to `navbar.jte`'s `navbar-start`.** No orphan URLs.

### (c) The behavior — `static/js/grades.js`

```js
document.addEventListener('alpine:init', () => {
    Alpine.data('gradesPage', () => ({
        schoolId: null,
        error: '', notice: '', blocked: false,

        init() {
            const saved = parseInt(localStorage.getItem('selectedSchoolId') || '', 10);
            this.schoolId = Number.isInteger(saved) ? saved : null;

            // the navbar dispatches this when the user switches school
            window.addEventListener('school-changed', (e) => {
                this.schoolId = e.detail.id;
                this.reset();
                this.loadClasses();
            });

            if (this.schoolId) this.loadClasses();
        },

        csrfToken() {
            const m = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
            return m ? decodeURIComponent(m[1]) : '';
        },

        async loadClasses() {
            const res = await fetch(`/api/admin/schools/${this.schoolId}/classes`, {
                headers: { 'X-School-ID': this.schoolId }
            });
            if (!res.ok) { /* classifyError — see §7 */ }
            this.classes = await res.json();
        },

        async saveGrade(row) {
            const res = await fetch(`/api/admin/schools/${this.schoolId}/grades`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'X-School-ID': this.schoolId,
                    'X-XSRF-TOKEN': this.csrfToken()
                },
                body: JSON.stringify({ /* ... */ })
            });
            if (!res.ok) { /* ... */ return; }
            this.notice = `نمره‌ی ${row.name} با موفقیت ثبت شد.`;
        }
    }));
});
```

**Code-placement rule** (explicit team decision): Bulma classes and short Alpine
expressions stay inline in the template. Anything with a `fetch`, a regex, or more
than ~10 lines of JS moves into a `static/js/` file as an `Alpine.data` component.
Custom CSS is *always* an external file — never an inline `<style>`.

---

## 7. The six rules every page follows

These are non-negotiable and reviewed. All six are visible in the snippet above.

### 1. Send `X-School-ID` on every `/api/admin/**` call

The server has **no** idea which school you're looking at. There is no server-side
"current school". The active school lives only in `localStorage.selectedSchoolId`,
written by the navbar, and every API call must carry it as a header. Calls to
`/api/me/**` do *not* need it (they're about the user, not a school).

### 2. Send `X-XSRF-TOKEN` on every mutating call

CSRF is on. The server sets a readable `XSRF-TOKEN` cookie; you echo it back as the
`X-XSRF-TOKEN` header on POST/PUT/PATCH/DELETE. The 3-line `csrfToken()` helper is
deliberately **duplicated** in each component rather than extracted — copy it.

### 3. Listen for `school-changed`

`navbar.js` dispatches `window.dispatchEvent(new CustomEvent('school-changed', {detail:{id}}))`
when the user picks a different school. Your component must reset its state and refetch.

### 4. Handle errors as `ProblemDetail`, and tell the two 403s apart

Every backend error is RFC 7807 JSON: `{type, title, status, detail}`. Show
`body.detail || body.title || <fallback>` in a `notification is-danger`. Wrap the
parse in try/catch — a few failures (e.g. a missing tenant header) return plain text.

**The important part:** two completely different situations both return **403**, and
they need different messages:

| `type` ends with | Means | Message to show |
|---|---|---|
| `module-not-entitled` | The *school* hasn't subscribed to this feature area | "این مدرسه ماژول … را فعال ندارد. برای فعال‌سازی با پشتیبانی تماس بگیرید." |
| `permission-denied` | The *user's role* lacks the permission | "نقش انتخاب‌شده‌ی شما اجازه‌ی … را ندارد." |

The established helper, copy it from `grades.js` / `finance.js` / `expenses.js`:

```js
async classifyError(response) {
    try {
        const body = await response.json();
        const type = body.type || '';
        if (type.endsWith('module-not-entitled')) return { blocking: true, message: '…' };
        if (type.endsWith('permission-denied'))   return { blocking: true, message: '…' };
        return { blocking: false, message: body.detail || body.title || 'خطایی رخ داد.' };
    } catch (e) {
        return { blocking: false, message: 'خطایی رخ داد. (' + response.status + ')' };
    }
}
```

`blocking: true` means hide the page body entirely rather than render a form that
can't work.

### 5. Confirm every mutation visibly

A successful POST/PUT/PATCH sets a Persian success notice (`notification is-success`).
A silently-updated list is not enough feedback. Errors and successes share **one
notice area per page section**, at the top. There is no toast component — don't add one.
Never use `alert()` or `confirm()`; destructive actions use a Bulma `modal`.

### 6. Never show raw codes, and never gate access client-side

- Role codes (`SCHOOL_ADMIN`, …) never reach the screen. Server-rendered templates use
  `web.RoleLabels.persian(code)`; client-rendered data uses the matching map in your JS
  (see `people.js`). Keep the two in sync.
- **The role dropdown is display-only.** It can decide which links/UI you show. It
  *never* decides what's permitted — the backend evaluates all of the user's active
  roles regardless of what's selected. Don't build "acting as role" logic.

---

## 8. Auth, sessions, and the two headers

- Web auth is a **session cookie** (`HttpSession`). `POST /api/auth/login` with
  `{email, password}` JSON, then navigate. `POST /api/auth/logout` to end it.
- Not-logged-in HTML pages redirect to `/login`; not-logged-in `/api/**` calls get
  **403** (so your `fetch` can detect it instead of receiving a login page).
- There's also OTP login (`/api/auth/otp/request`, `/api/auth/otp/verify`) — phone
  number as a *second* login path for accounts that have one registered.
- Bearer-JWT auth (`POST /api/auth/token`) exists **only for the Android app**. Ignore
  it for web work.

**Public paths** (no login): `/`, `/login`, `/register`, `/activate`, `/s/**`,
`/exam-take`, `/api/auth/**`, `/css/**`, `/js/**`, `/webjars/**`, Swagger.

---

## 9. API reference

Base path for everything school-scoped: `/api/admin/schools/{schoolId}`, and it
**requires the `X-School-ID` header to match `{schoolId}`**. The "Permission" column
is what the user's role must have; "Module" is what the school must be subscribed to.

### Me — `/api/me` (no `X-School-ID`, no permission checks)

| Method | Path | Returns |
|---|---|---|
| GET | `/api/me` | The logged-in user's profile |
| GET | `/api/me/memberships` | Every school + role they hold |
| PUT | `/api/me/phone` | Set phone number (used for Bale bot linking + OTP) |
| GET | `/api/me/enrollment?schoolId=` | "Which class am I in" (student) |
| GET | `/api/me/schools/{id}/grades` | Own + linked children's grades |
| GET | `/api/me/schools/{id}/invoices` | Own + linked children's invoices |
| GET | `/api/me/schools/{id}/exams` | Own exams |
| POST | `/api/me/schools/{id}/exams/{examId}/attempts` | Start an attempt → exam token |
| GET | `/api/me/schools/{id}/exams/{examId}/result` | Own result |
| GET/PATCH | `/api/me/schools/{id}/study-intervals` | Own study schedule |

These are the endpoints behind `/my-grades`, `/my-invoices`, `/my-exams`,
`/my-study-plan`. They're scoped server-side to the caller — you never send a
`studentId`.

### Identity & roles — no module required

| Method | Path | Permission |
|---|---|---|
| POST/GET | `…/memberships` | `MEMBERSHIP_MANAGE` |
| PATCH | `…/memberships/{id}/active` | `MEMBERSHIP_MANAGE` |
| POST | `…/memberships/{id}/invite-token` | `MEMBERSHIP_MANAGE` |
| GET | `…/memberships/names?userIds=1,2,3` | `MEMBER_NAMES_VIEW` |
| GET | `…/permissions` | `ROLE_MANAGE` |
| GET/POST | `…/roles` | `ROLE_MANAGE` |
| POST/GET | `/api/admin/schools` , `/api/admin/schools/{id}` | (create is self-service) |

> `…/memberships/names` exists because a teacher or finance admin needs to *display*
> a student's name without gaining the full member list (emails, roles, activation
> status). The pattern in `students.js`/`finance.js`: try the full `…/memberships`
> list first, and fall back to `…/names?userIds=…` when it 403s.

### Academic — module `ACADEMIC`

| Method | Path | Permission |
|---|---|---|
| POST | `…/classes` | `CLASS_MANAGE` (admin only) |
| GET | `…/classes` | `CLASS_VIEW` (admin + teacher) |
| POST | `…/students` | `STUDENT_ENROLL` (admin + teacher) |
| GET | `…/students`, `…/students/{id}` | `STUDENT_VIEW` (admin, teacher, finance) |
| POST/GET | `…/students/{id}/guardians` | `GUARDIAN_MANAGE` (admin only) |
| POST/GET | `…/grades` | `GRADES_EDIT` — **both**, deliberately |

> Why grades are `GRADES_EDIT` even for reading: `GRADES_VIEW` is held by students and
> parents, and `GET …/grades` returns a *whole class's* grades. Gating the read on
> `VIEW` would let a student see their classmates' grades by calling the API directly.

### Finance — module `FINANCE`

| Method | Path | Permission |
|---|---|---|
| PUT / GET | `…/tuition-fee` | `FINANCE_MANAGE` / `FINANCE_VIEW` |
| GET | `…/invoices` | `FINANCE_VIEW` |
| POST/GET | `…/invoices/{id}/payments` | `FINANCE_MANAGE` / `FINANCE_VIEW` |
| POST | `…/invoices/backfill` | `FINANCE_MANAGE` |
| GET/POST/PUT | `…/expense-categories`, `…/vendors` | `EXPENSE_VIEW` / `EXPENSE_MANAGE` |
| POST | `…/expense-categories/seed-defaults` | `EXPENSE_MANAGE` |
| GET/POST/PUT | `…/expenses`, `…/expenses/{id}` | `EXPENSE_VIEW` / `EXPENSE_MANAGE` |
| POST | `…/expenses/{id}/approve` | `EXPENSE_APPROVE` (admin only) |
| POST | `…/expenses/{id}/pay`, `…/cancel` | `EXPENSE_MANAGE` |
| POST/GET/DELETE | `…/expenses/{id}/attachments[/{aid}][/content]` | `EXPENSE_MANAGE` / `EXPENSE_VIEW` |
| GET | `…/expenses/summary`, `…/finance-summary` | `EXPENSE_VIEW` |

Notes for the UI:
- Invoice **status** (`PAID`/`PARTIAL`/`UNPAID`/`OVERDUE`) is derived by the server at
  read time — display it, never compute it.
- Expense **status** (`DRAFT`→`APPROVED`→`PAID`, or `CANCELLED`) is a real state
  machine. Fields are only editable while `DRAFT`. There is no delete — only cancel.
- Attachments are multipart upload, 5MB cap, Persian filenames supported.
- `GET …/invoices` scopes itself: a caller whose only role is `PARENT` sees only their
  own children's invoices.

### Curriculum — module `CURRICULUM`

| Method | Path | Permission |
|---|---|---|
| POST | `…/topics` | `CURRICULUM_MANAGE` |
| GET | `…/topics`, `…/topics/tree` | `CURRICULUM_VIEW` |
| POST | `…/questions` | `CURRICULUM_MANAGE` |
| GET | `…/questions?topicId=&difficulty=` | `CURRICULUM_VIEW` |

The topic filter is **subtree-inclusive** — picking a parent topic returns its
descendants' questions too. `/curriculum` renders the tree as a flattened,
depth-indented list computed client-side from `…/topics/tree`.

### Study plans — module `STUDY_PLANNER`

| Method | Path | Permission |
|---|---|---|
| POST/GET | `…/study-plans` | `STUDYPLAN_MANAGE` / `STUDYPLAN_VIEW` |
| POST/GET | `…/study-plans/{id}/assignments` | same |
| POST/GET | `…/study-plans/{id}/week-courses` | same |
| GET/PUT | `…/students/{id}/schedule[/default|/current]` | same |
| POST/GET/PATCH/DELETE | `…/students/{id}/study-intervals[/{id}]` | same |
| POST | `…/students/{id}/study-intervals/suggest` `/clear` `/copy` | `STUDYPLAN_MANAGE` |

`weekStart` must always be a **Saturday** — the week starts Saturday in this product.

### Exams — module `EXAM`

| Method | Path | Permission |
|---|---|---|
| POST/GET | `…/exams`, `…/exams/{id}` | `EXAM_MANAGE` / `EXAM_VIEW` |
| POST | `…/exams/{id}/assign` | `EXAM_MANAGE` |
| GET | `…/exams/{id}/students`, `…/exams/{id}/sections` | `EXAM_VIEW` |
| POST | `…/exams/{id}/sections`, `…/sections/auto` | `EXAM_MANAGE` |
| POST | `…/exams/weekly` | `EXAM_MANAGE` |
| GET | `…/exams/{id}/results[/{studentId}]` | `EXAM_VIEW` |

**Exam-taking** is separate and unauthenticated by session — `/api/exam-take/exam`,
`/api/exam-take/sections/{id}/questions`, `/api/exam-take/submit`, all authorized by a
short-lived exam token issued when the student starts an attempt. That's why
`/exam-take` is its own navbar-less page.

---

## 10. Design system — the short version

Full detail in `DESIGN.md`. The essentials:

**Intent:** calm, professional, trustworthy. Persian-first, RTL-first. One primary
action per page. Reduce cognitive load.

**Tokens** live in the `:root` block of `static/css/app.css` — that file is the source
of truth for values; `DESIGN.md` is the source of truth for rules.
- Spacing: `--space-1`…`--space-8` = 4/8/12/16/24/32/48/64px
- Radius: `--radius-sm/md/lg` = 8/12/16px, bridged onto Bulma's own variables
- Shadows: two levels only. Elevation is not the hierarchy mechanism — spacing is.
- Motion: 120ms / 180ms, ease-out, no bounce. `prefers-reduced-motion` honored globally.
- Primary color: desaturated ink-blue `hsl(210 40% 38%)`, set via Bulma 1.x's
  `--bulma-primary-{h,s,l}`. Changing the accent is a three-line edit.

**Typography** — no custom scale. The allowed set is exactly:
`title is-4` (page title, one per page) · `title is-5` (section) · `title is-6`
(card) · default (body) · `is-size-7` (small) · `is-size-7 has-text-grey` (caption).
Anything else is a design-system change, not a page decision.

**Color** — one accent per screen, on the primary action and active nav only.
Bulma's stock success/warning/danger/info keep their semantics; danger means
destructive or error, never decoration.

**RTL** — `dir="rtl"` is set once in the layout. Latin data (emails, IDs, tokens,
phone numbers) gets its own `dir="ltr"` element so it doesn't visually reverse. Custom
CSS uses **logical properties only** — `margin-inline-end`, not `margin-left`. A
`margin-left` in review is a defect.

**Dates & numbers** (this one bites people):
- **Jalali for every human-facing date.** `۱۴۰۵/۰۴/۲۲` in tables, `۲۲ تیر ۱۴۰۵` in
  headers. The wire and the database are **ISO-8601 Gregorian only** — Jalali never
  crosses the wire. Conversion is client-side via `static/js/jalali.js` (see
  `expenses.js` for the working precedent).
- **Persian digits (۰–۹) for displayed numbers.** Underlying data and anything
  sorted or compared stays ASCII.
- **Every input normalizes Persian/Arabic-Indic digits to ASCII before validating and
  sending** — a parent typing ۰۹۱۲… must not fail validation. See `normalizeDigits()`
  in `grades.js`.
- Week starts **Saturday**; the weekend is Friday.

**Accessibility floor** — enforced globally in `app.css`, not by per-page diligence:
≥44px touch targets, visible `:focus-visible` outline (never removed without a
replacement), reduced-motion honored. Icon-only buttons need `aria-label` — though
there are no icons in this UI at all yet, and adding an icon pack is an explicit
decision, not a page-level one.

**Components** — the component library is *the set of `.jte` templates that exist*,
not an aspirational list. Compose from Bulma primitives inline; extract a shared
partial on the **third** real duplicate, and record it in `DESIGN.md` when you do.
Explicit non-goals: toast, drawer, wizard, data-grid, form draft autosave, table sort
persistence, dark mode, icon packs.

**Before building a *new* page**, the team runs the shared
[`design-page`](.agents/skills/design-page/SKILL.md) pass: user goal and role,
information hierarchy, the single primary action, which existing templates get
reused, mobile behavior, and empty/loading/error states — *before* any JTE is
written. Ask about this when you pick up your first new page.

---

## 11. Gotchas, collected

1. **Rebuild after editing any `.jte`/`.js`/`.css`.** Templates are precompiled and
   static files are served from `target/classes`. See §3.
2. **`x-cloak`** — put it on anything hidden by `x-show` at load, or it flashes open
   before Alpine initializes. The CSS rule is already in `app.css`.
3. **Script order matters.** Your `scripts` entries render *before* Alpine's tag;
   don't reorder them, or `Alpine.data` won't be registered when `alpine:init` fires.
4. **`localStorage` keys are `selectedSchoolId` and `selectedRole`.** Nothing else.
   The navbar writes its resolved default on init (not just on click), so a
   single-school admin who never opened the dropdown still has a value.
5. **A page can render fine and every API call still 403.** Page access and API access
   are enforced separately — navbar visibility is cosmetic.
6. **Cross-module data needs client-side composition.** The backend modules can't see
   each other's data, so e.g. an invoice row only carries an opaque `studentId`. The
   established pattern is to compose two REST calls in JS (`studentId` → `userId` via
   the students endpoint, `userId` → name via the memberships endpoint) — *not* to ask
   for a new backend endpoint. Both lookups are best-effort: if the role lacks
   permission, fall back to showing `#id`, don't break the page.
7. **`PLATFORM_ADMIN` must never appear in any role picker.** It has no working
   permission path; granting it silently does nothing.
8. **Don't scope or narrow an already-shipped endpoint as a side effect** of unrelated
   work. That's a deliberate discipline in this repo.

---

## 12. Suggested first tasks

To get oriented, in order:

1. Run it, log in as the demo admin, and click through every page in §5 while
   switching roles in the navbar dropdown. Note what feels rough — fresh eyes are the
   point of bringing you in.
2. Read `grades.jte` + `grades.js` side by side. It's the most recent page and the
   cleanest example of every convention in §7.
3. Open Swagger UI and try a couple of endpoints — it's the live API truth.
4. Pick something small and visible: an empty state that just says "no data" instead
   of teaching what belongs there, a table over 25 rows without a filter, a missing
   loading state on a slow action. All three are documented conventions being violated
   somewhere.

Questions about *why* something is the way it is: start with OpenSpec, then use
`CLAUDE_LEGACY.md` and `HISTORY.md` for older decision context. Verify any current-state
claim against code before relying on it.
