# Roshdeandishe — Design System

**Status: DRAFT (2026-07-13), pending sign-off.** This document replaces an
earlier six-file draft (`DESIGN_SYSTEM.md`, `COMPONENT_LIBRARY.md`,
`SCREEN_LIBRARY.md`, `UX_RULES.md`, `CONTENT_GUIDE.md`, `CLAUDE_DESIGN.md`)
that was never adopted — §11 records what was dropped from it and why, so
those decisions are explicit, not silent. Items marked **[SIGN-OFF]** are
proposals in this draft, not resolved decisions, until confirmed.

Subordinate to OpenSpec current-behavior docs for architecture, domain, and
access control, and to `UX_ROADMAP.md` §2 for binding shared UX patterns
(layout, navbar, tenant selection, code placement, error/success display, role
labels). This file adds the visual layer those documents don't cover: tokens,
typography, color, spacing, motion, and the component inventory. If this file
conflicts with implementation, report the conflict before changing code or
docs.

**Platform scope:** this file governs the **JTE web UI only** — the
server-rendered management pages (`UX_ROADMAP.md`) and the student web
channel (`MOBILE_ROADMAP.md` §4, added 2026-07-11), both of which share the
JTE + Alpine + Bulma stack. The **native Android student app (Kotlin +
Jetpack Compose, `MOBILE_ROADMAP.md` §2/§4) is out of scope**: it inherits
the design intent (§1) and the signed-off color values (§4) but gets its own
Material-based system when its UI is actually designed — do not translate
Bulma classes or these component rules into Compose.

Token *values* live in code, not here: the `:root` block at the top of
`src/main/resources/static/css/app.css`. This document defines the scale and
the rules; the CSS file is the single source of truth for values. If they
ever disagree, the CSS file wins and this file gets corrected.

---

## 1. Design intent

School software for admins, teachers, students, and parents. It should feel
calm, professional, and trustworthy — never flashy, never enterprise-ugly.
Every screen should reduce cognitive load: one primary action per page,
progressive disclosure, no surprises, always-visible system status, feedback
for every action. Persian-first, RTL-first.

This intent is a tiebreaker, not a rule: when two compliant options exist,
pick the calmer one.

---

## 2. Tokens

Defined as CSS custom properties in `app.css` (see that file's header
comment). The scales — do not invent values outside them:

- **Spacing:** 4, 8, 12, 16, 24, 32, 48, 64 px (`--space-1` … `--space-8`).
  Prefer Bulma's own spacing helpers/component padding where they apply;
  the scale governs any *custom* CSS.
- **Radius:** 8 / 12 / 16 px (`--radius-sm/md/lg`), bridged onto
  `--bulma-radius-small` / `--bulma-radius` / `--bulma-radius-large` so
  Bulma components pick them up with no per-component overrides.
- **Shadows:** two levels only (`--shadow-sm`, `--shadow-md`). No heavy
  shadows; elevation is not this UI's hierarchy mechanism — spacing and
  typography are.
- **Motion:** `--motion-fast: 120ms`, `--motion-base: 180ms`, ease-out only,
  no bounce. `prefers-reduced-motion` is honored globally in `app.css`.

**Bulma version dependency — verified (2026-07-15):** the bridge requires
the Bulma WebJar to be **1.x** (runtime CSS variables); `pom.xml` pins
**1.0.4**, so the `--bulma-*` overrides are live. If the WebJar is ever
downgraded to 0.9.x the bridge block becomes inert and must be replaced
with plain CSS rule overrides (a separate, explicit decision, since 0.9.x
has no supported zero-build override path).

---

## 3. Typography

No custom type scale — the allowed hierarchy is a fixed subset of Bulma's
existing classes, which makes "never invent typography" checkable in review:

| Role       | Class                    | Use                                    |
|------------|--------------------------|----------------------------------------|
| Page title | `title is-4`             | one per page, in `PageHeader` position |
| Section    | `title is-5`             | section headings within a page         |
| Subsection | `title is-6`             | card headings, rarely                  |
| Body       | (default)                | everything else                        |
| Small      | `is-size-7`              | secondary metadata                     |
| Caption    | `is-size-7 has-text-grey`| hints, timestamps                      |

Anything outside this table is a design-system change, not a page decision.

**Persian font [SIGN-OFF / open]:** currently the Bulma default (system)
stack, which renders Persian acceptably. Adopting a dedicated face (e.g.
Vazirmatn) requires a WebJar or self-hosted files — a new dependency
decision. Verify a maintained WebJar exists before deciding; do not add a
CDN `<link>` (violates the zero-build/WebJar rule, CLAUDE.md §2).

---

## 4. Color

Roles, not palettes: **primary** (the single accent), **neutral** greys
(Bulma scheme), and Bulma's stock **success / warning / danger / info** for
semantics — unchanged from Bulma defaults on purpose; they already carry the
right meanings and retinting them is a decision without a need.

Rules:

- **One accent color per screen.** Primary appears on the primary action and
  active navigation state; nowhere else. Semantic colors appear only with
  semantic meaning (danger = destructive/error, never decoration).
- Destructive actions are always `is-danger` and always confirmed (this
  restates the UX_ROADMAP/§5 confirmation rule for completeness; that file
  owns it).

**Proposed primary [SIGN-OFF]:** desaturated ink-blue,
`hsl(210, 40%, 38%)` (≈ `#3A5F84`), applied via Bulma 1.x's
`--bulma-primary-{h,s,l}` and matching `--bulma-link-{h,s,l}` so buttons and
links agree. Chosen for the calm/trustworthy intent; deliberately not a
saturated SaaS blue. Swap by editing three lines in `app.css`.

---

## 5. Components

The component library is **the set of `.jte` templates that exist**, not an
aspirational list. Current inventory:

- `layout/main.jte` — the only owner of `<head>`; `title`, `content`,
  optional `scripts`, and optional `styles` (rules in OpenSpec /
  UX_ROADMAP §2).
- `layout/navbar.jte` — school/role switcher, fed by `NavbarModelAssembler`.
- Page templates (`dashboard.jte`, `people.jte`, `classes.jte`,
  `students.jte`, `finance.jte`, …) composed from Bulma primitives:
  `box`/`card`, `table`, `notification`, `tag`, `modal`, `pagination`,
  `breadcrumb`, `tabs`, form controls.

Rules:

- **Compose from Bulma primitives inline first.** Extract a shared `.jte`
  partial on the third real duplicate (the project's rule-of-three, same as
  `csrfToken()`), and record it here when extracted. Expected early
  candidates once pages repeat them: page header (title + primary action),
  empty state, confirmation modal.
- **Modals:** Bulma `modal` driven by Alpine state. Maximum one open at a
  time — a flow needing a modal inside a modal is a flow that should be a
  page. Never browser `alert()`/`confirm()`.
- **Feedback:** the resolved notification pattern (UX_ROADMAP §2) — one
  notice area per page section, `is-danger`/`is-success`/plain. There is no
  Toast component; do not add one.
- **Empty states teach:** an empty list states what belongs there and shows
  the action that creates the first item — never a bare "no data."
- **Loading:** operations expected to exceed ~300ms show a Bulma `is-loading`
  state on the triggering control (button/section); >2s shows a progress
  message. No skeleton component exists yet — build one only when a real
  page needs it, then record it here.

---

## 6. Interaction rules (additions to UX_ROADMAP §2)

UX_ROADMAP §2 remains binding and is not repeated here. Additions:

- One primary action per page, placed at **inline-end** of the page header;
  secondary actions are `is-light`/plain buttons or a dropdown.
- Search/filter controls sit at **inline-start of the table toolbar**
  (start of reading order in RTL). All positional rules in this file use
  logical directions (inline-start/inline-end), never left/right.
- Tables: client-side pagination after 25 rows; tables over 25 rows also get
  a text filter. (Sorting persistence across visits is a non-goal — §12.)
- Validation errors are never hidden: shown inline at the field
  (`is-danger` input + `help is-danger`) *and* summarized in the section's
  notice area if submission fails.
- Long forms (>~8 fields) are split into steps on one page (Alpine-driven),
  not a multi-page wizard — no Wizard component exists.

---

## 7. Accessibility & RTL

- WCAG AA contrast; visible keyboard focus (never `outline: none` without a
  replacement); every interactive element reachable and operable by keyboard.
- Touch targets ≥ 44px on interactive controls — enforced by the `app.css`
  block, not by per-page diligence.
- Icon-only buttons (when icons exist at all — §12) require `aria-label`.
- RTL is the default document direction (layout sets `dir="rtl"` once);
  Latin data (emails, tokens, IDs) gets `dir="ltr"` on its own element
  (UX_ROADMAP §2 owns this rule). Custom CSS uses **logical properties
  only** (`margin-inline-end`, `padding-inline-start`, `inset-inline-end`)
  — a `margin-left` in review is a defect.
- `prefers-reduced-motion` disables non-essential animation globally.

---

## 8. Voice & content

- Persian, friendly-professional, short. No technical jargon in UI text; no
  raw codes (RoleLabels rule, UX_ROADMAP §2).
- Buttons are verbs naming the outcome: ذخیره / ایجاد / بایگانی / تأیید —
  never a generic "Submit"-equivalent (ارسال only when the action literally
  is sending something).
- An action keeps its name through the flow: the button that says ایجاد
  produces a success notice that says ایجاد شد.
- Error messages state what went wrong **and** what to do next. Errors don't
  apologize and are never vague.
- Empty states are invitations to act, not apologies.

**Dates, numbers, digits (decided 2026-07-13) [SIGN-OFF]:**

- **Jalali for every human-facing date** in the web UI. Numeric form
  `۱۴۰۵/۰۴/۲۲` in tables; long form `۲۲ تیر ۱۴۰۵` in headers and running
  text. The wire and database are **ISO-8601 Gregorian only**
  (`Instant`/`LocalDate` per BEST_PRACTICES) — Jalali never crosses the
  wire and is never stored; conversion is client-side via
  `static/js/jalali.js` (the `/expenses` precedent). The server has no
  Jalali library; a server-side Jalali-boundary need (e.g. a scheduled
  Jalali-month job) would be a new explicit decision, not an import.
- **The week starts Saturday (شنبه); the weekend is Friday.** Any server-
  side "week" concept (e.g. `studyplan`'s week-start, MOSHAVERE_PORT_SPEC
  §6) anchors to `DayOfWeek.SATURDAY` — plain date arithmetic, no calendar
  library.
- **Persian digits (۰–۹) for all displayed numbers** — dates, counts,
  amounts — applied at render time by a single client utility
  (`toPersianDigits()`); underlying data, DOM state, and anything
  sorted/compared stays ASCII. The Latin-data rule (UX_ROADMAP §2) is
  unchanged and includes **phone numbers**: emails, tokens, IDs, and
  phones stay Latin digits with their `dir="ltr"` island.
- **Every input normalizes Persian and Arabic-Indic digits (۰–۹, ٠–٩) to
  ASCII before validation and send** — a parent typing ۰۹۱۲… into a phone
  field must not fail validation. Shared helper; rule-of-three governs
  extraction.
- Persian-digit dates need no `dir` island (they render correctly in RTL
  flow); only Latin-digit technical strings do, per the existing rule.

---

## 9. Page-design process

Before building any new page, invoke the `design-page` skill (parallel to
the `new-module` standing instruction in CLAUDE.md): it forces, in order —
user goal and role(s), information hierarchy, the single primary action,
which existing templates/partials are used, mobile behavior, and
empty/loading/error states — *before* any JTE is written. Output of that
pass is a short spec in the PR/commit description, not a separate document.

---

## 10. Screen reference

`dashboard.jte` + `DashboardPageController` (and its UX_ROADMAP treatment)
serve as the reference implementation for page structure until a richer
exemplar is designated. New pages should look like siblings of it.

---

## 11. Resolutions vs. the dropped six-file draft (for sign-off)

Recorded so nothing was dropped silently:

1. **No standalone design authority.** `CLAUDE_DESIGN.md`'s persona framing
   ("you are the lead Product Designer… always follow DESIGN_SYSTEM.md")
   created a second source of truth. Replaced by this file's explicit
   subordination + the `design-page` skill (§9).
2. **No aspirational component list.** ~30 named components (AppLayout,
   DataGrid, Drawer, Toast, Wizard, Timeline, …) did not exist and several
   contradicted resolved patterns (Toast vs. the notification-area rule).
   Replaced by the real-inventory + rule-of-three model (§5).
3. **No Lucide / no icon pack.** New dependency with no current need; the UI
   uses no icons today. Revisit when a concrete page needs one — then as an
   explicit WebJar decision, one pack only.
4. **"Never generate CRUD pages" dropped.** It contradicted UX_ROADMAP's
   entire buildout. The defensible core survives as §9: pages are designed
   task-first via the skill, not scaffolded table-with-edit-buttons.
5. **"Forms autosave drafts" dropped** (server-side draft persistence for
   every form — real infrastructure, violates BEST_PRACTICES §3). Build
   draft-saving for a specific long form when one demonstrably needs it.
6. **"Tables always remember sorting" dropped** (per-table client state
   machinery as a blanket rule). Same reasoning.
7. **"Search always top-right / primary action right-aligned" corrected**
   to logical directions for RTL (§6).
8. **"Never open a modal if a drawer works" dropped** — there is no Drawer;
   the rule becomes "prefer a page over stacked modals" (§5).
9. **Salvaged intact:** the philosophy (§1), token scales (§2), one-accent
   rule (§4), no `alert()`/`confirm()`, destructive = danger + confirm,
   inline validation, 300ms/2s loading thresholds, empty-states-teach,
   reduced motion, ≥44px targets, and the content voice rules (§8) — the
   "never Submit" rule aligned with the existing Persian-first conventions.

---

## 12. Non-goals

- Icon pack (until a concrete need — §11.3).
- Toast/Drawer/Wizard/DataGrid components.
- Form draft autosave; table sort persistence.
- Custom CSS framework, npm/webpack/vite pipeline, CDN assets (CLAUDE.md §2).
- Dark mode — not requested; a real decision when it is (Bulma 1.x makes it
  cheap, which is not a reason to build it).

## 13. Open questions

1. ~~Bulma WebJar version — 1.x confirmed?~~ **Resolved 2026-07-15:**
   `pom.xml` pins Bulma 1.0.4; the token bridge (§2) is live.
2. Persian font adoption (Vazirmatn or stay on system stack) — §3.
3. Primary color value sign-off — §4.
4. Skeleton loading component — deferred until a page needs it (§5).
5. **Currency display.** ~~Dates and digits~~ — resolved 2026-07-13 into §8.
   What remains: Rial vs. Toman for tuition/invoice/expense amounts (users
   think in Toman; confirm what unit the stored amounts actually are before
   choosing a display convention), thousands-separator style (٬), and
   whether the unit is written out (تومان) or implied. Resolve before the
   next finance-facing page; record in §8.
