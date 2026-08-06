---
name: design-page
description: >
  Mandatory workflow before creating or substantially restructuring any
  Roshdeandishe web page. Use this skill whenever the task involves a new
  .jte page template, a new page controller in the web module, or reshaping
  an existing page's layout/flow — including phrasings that never say
  "page," like "add a screen for X," "build the teacher view," "let admins
  manage Y," or "make a UI for Z." Forces the design decisions (goal,
  hierarchy, primary action, components, states, RTL/mobile) to be made
  explicitly and in order BEFORE any JTE or JS is written, the same way the
  new-module skill gates module creation. Also governs which styling stack
  is allowed: for in-repo pages, never React/Tailwind/shadcn and never
  theme-factory themes — see "Boundaries with other skills."
---

# design-page — Roshdeandishe page design workflow

Pages built without this pass tend to drift: invented spacing, a second
accent color, a Toast that doesn't exist, `margin-left` in an RTL app.
This skill exists so every page is designed task-first and lands looking
like a sibling of the existing pages.

**Precedence:** OpenSpec current-behavior docs + implementation/tests >
`UX_ROADMAP.md` §2 > `DESIGN.md` > legacy `CLAUDE_LEGACY.md` context > this
skill's convenience summaries. If anything below contradicts current code or
OpenSpec, report the conflict and fix the stale documentation.

**If the page belongs to a module that doesn't exist yet, STOP and apply
`.agents/skills/new-module/SKILL.md` first.** Come back here for the page
after the module pass resolves codes and boundaries.

---

## Step 0 — Read before designing

1. `DESIGN.md` (tokens §2, typography table §3, color rules §4, component
   inventory §5, interaction additions §6).
2. `UX_ROADMAP.md` §2 — the binding shared patterns (layout/navbar wiring,
   tenant selection, CSRF, error/success display, role labels, code
   placement).
3. The `:root` token block at the top of
   `src/main/resources/static/css/app.css` — the only source of token
   values.
4. One existing page as reference: `dashboard.jte` +
   `DashboardPageController` (DESIGN.md §10), or the closest sibling to
   what's being built (e.g. `students.jte` for another management table).

## Step 1 — Frame the task (write these down; they go in the spec)

- **User goal:** one sentence, from the user's side of the screen — what a
  person accomplishes here, not what the system does.
- **Role(s) and access:** which roles use this page; the exact
  `@RequiresModule` / `@RequiresPermission` codes its API calls need (both,
  in order — never one). If no existing permission code fits, that's a
  domain decision to raise, not a code to invent here.
- **Owning surface:** cross-module product page controllers live in the `web`
  module; pure auth/identity pages may live in `iam.shared`. The data API
  lives at the owning domain module's root controller. Name both.
- **Navigation:** where the navbar link goes and for which *selected*
  (display-only) roles it's visible — every management page is reachable
  from `navbar-start`, no orphan URLs (UX_ROADMAP §2). Link visibility may
  follow the selected role; enforcement stays server-side only. Exception:
  one-time bootstrapping pages (the `/schools/new` precedent) link from
  their entry-point flow instead of the navbar — say which case this is.

## Step 2 — Hierarchy and the primary action

- State the information hierarchy top-down: what a user must see first,
  second, third. If everything is equally important, the framing in Step 1
  is wrong — go back.
- Name the **single primary action** as a Persian verb (DESIGN.md §8) and
  place it at **inline-end of the page header**. Everything else is
  secondary (plain/`is-light` buttons or a dropdown).
- A page with two primary actions is two pages (or one page + a modal).

## Step 3 — Workflow shape

- Prefer a page over a modal; prefer a modal over stacked modals (max one
  open — a modal-in-a-modal flow is a page).
- Tables: text filter + client-side pagination when they can exceed 25
  rows; search/filter at inline-start of the table toolbar.
- Forms over ~8 fields become Alpine-driven steps on one page — there is no
  Wizard component and no multi-page wizard pattern.
- **Never hide or disable UI by permission** (resolved convention,
  UX_ROADMAP §3): a control the current role can't use stays visible and
  403s into the shared permission-denied notice. The only permission-shaped
  visibility in this app is the navbar link's selected-role rule (Step 1).
  Do not add client-side role-gating of forms or buttons.

## Step 4 — Component plan (compose, don't invent)

- List the templates/partials used: always `layout/main.jte` (+ `scripts`
  param for any page JS — never a duplicated `<head>`), `layout/navbar.jte`
  via `NavbarModelAssembler`, then Bulma primitives (`box`, `table`,
  `notification`, `modal`, `tag`, `pagination`, form controls).
- Typography only from the DESIGN.md §3 table. Spacing: Bulma helpers, or
  `--space-*` tokens in external CSS — never a raw px value.
- If a fragment is being written for the **third** time across pages,
  extract a shared `.jte` partial now and record it in DESIGN.md §5. First
  and second time: keep it inline.
- A component that doesn't exist (Toast, Drawer, DataGrid, icon…) is a
  design-system change: flag it as a separate decision, do not build it
  inside the page task.

## Step 5 — States (all four, decided before coding)

- **Empty:** teaches — says what belongs here and shows the action that
  creates the first item. Never a bare "no data."
- **Loading:** `is-loading` on the triggering control for >300ms
  operations; a progress message for >2s.
- **Error:** `ProblemDetail` parse → `notification is-danger` in the page
  section's single notice area (try/catch — some failures are plain text).
  **Decide whether this page needs the two-kinds-of-403 split**: pages
  behind an entitled module distinguish `ModuleNotEntitled` from
  `PermissionDenied` via the `ProblemDetail.type` suffix and block the page
  body with distinct Persian messages — reuse the `classifyError` pattern
  from `finance.js`/`expenses.js` verbatim, don't reinvent it.
- **Validation:** inline at the field (`is-danger` + `help is-danger`) AND
  summarized in the notice area on failed submit. Never hidden.
- Every successful mutation shows a Persian `is-success` notice whose
  wording echoes the button's verb (ایجاد → ایجاد شد).

## Step 6 — RTL and mobile

- The layout already sets `dir="rtl"`; Latin data (emails, IDs, tokens,
  phone numbers) gets `dir="ltr"` on its own element.
- **Dates and numbers per DESIGN.md §8:** Jalali + Persian digits for
  display (via `jalali.js` / `toPersianDigits()` at render time), ISO
  Gregorian + ASCII on the wire and in state; every input normalizes
  Persian/Arabic-Indic digits to ASCII before validation. Weeks start
  Saturday.
- Any custom CSS uses logical properties only (`margin-inline-end`, not
  `margin-left`). Positional words in the spec are inline-start/inline-end.
- Touch targets ≥44px come from `app.css` globally — don't undo them with
  `is-small` on touch-first controls.
- State how the page degrades on a narrow viewport (which columns a table
  drops, whether the toolbar wraps) — one sentence is enough, but it must
  exist.

## Step 7 — Data contract

- List the API endpoints called, and confirm each page follows the tenant
  pattern: read `selectedSchoolId` from localStorage, send `X-School-ID` on
  every call, `X-XSRF-TOKEN` on every mutation, reload on the navbar's
  `school-changed` event.
- The pattern above is the **management-page** contract. Self-service pages
  (`/api/me/**` — e.g. a future `/my-invoices`, or student web-channel
  pages) may not fit it verbatim (own-data endpoints, exam-take token
  instead of the admin header pattern) — decide the deviation explicitly in
  the spec rather than cargo-culting the admin wiring.
- JS placement per the 2026-07-05 rule: anything with `fetch`, a regex, or
  >~10 lines moves to `static/js/<page>.js` as an `Alpine.data` component
  registered on `alpine:init`; small expressions stay inline.

## Step 8 — Only now, write the code

JTE template + page controller + (if needed) the `Alpine.data` file.
Follow the plan exactly; a deviation discovered while coding goes back
through the relevant step, it doesn't get improvised.

## Step 9 — Self-check before presenting

**This checklist is design-only and does NOT replace UX_ROADMAP §5's
per-slice definition of done** — run that too before calling the slice
finished (navbar link, integration test, `ModularityTests`, mobile-viewport
check, and relevant OpenSpec/current-doc update when behavior changes).
Design additions:

- [ ] Typography classes ⊆ DESIGN.md §3 table; no raw px spacing.
- [ ] Exactly one accent on screen; danger only on destructive/error.
- [ ] One primary action, inline-end of the page header.
- [ ] No physical direction properties (`grep -n 'left\|right'` any new CSS).
- [ ] Displayed dates are Jalali, displayed numbers Persian-digit; inputs
      normalize digits to ASCII; nothing Jalali on the wire (DESIGN.md §8).
- [ ] All four states from Step 5 exist and are reachable.
- [ ] `<head>` untouched; page assets go through the layout's `scripts`.
- [ ] No `alert()` / `confirm()`; destructive actions confirm via modal.
- [ ] Both access-check annotations on every protected endpoint touched.
- [ ] The Step 1–7 answers are pasted into the PR/commit description as the
      page's design spec (DESIGN.md §9) — that spec is the deliverable
      alongside the code.

---

## Boundaries with other skills

- **`new-module`** wins first: no page for a module that has not been
  through `.agents/skills/new-module/SKILL.md`.
- Out-of-repo artifact builders may use different stacks for throwaway
  explorations, demos, or promotional pages. Their React/Tailwind/shadcn
  components must not appear in Roshdeandishe. For prototyping a
  Roshdeandishe page, prefer a plain single-file HTML artifact using Bulma
  classes with the `app.css` token block pasted in.
- Theme-generation tooling must not restyle Roshdeandishe pages or
  prototypes. The design system's tokens are the only product theme, and
  the one-accent rule excludes multi-color palettes.
