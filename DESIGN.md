# Roshdeandishe — Design System

**Status: current web design guidance, adopted 2026-08-16; visual direction
replaced 2026-09-27.** This document replaces an
earlier six-file draft (`DESIGN_SYSTEM.md`, `COMPONENT_LIBRARY.md`,
`SCREEN_LIBRARY.md`, `UX_RULES.md`, `CONTENT_GUIDE.md`, `CLAUDE_DESIGN.md`)
that was never adopted — §11 records what was dropped from it and why, so
those decisions are explicit, not silent. Font loading, neutral
number presentation, and the need-driven loading-component policy were
confirmed on 2026-08-16. The canonical financial unit remains unresolved.

**Visual direction "Paper & Turquoise", adopted 2026-09-27.** Supersedes the
ink-blue primary, the cool Bulma neutral scheme, and the system-font decision
signed off on 2026-08-16. Two moves define it: warm the neutral scheme
(hue 221 → 28) and move the single accent to Persian turquoise.

Adopted in full, not in part:

- **Tokens** — warm scheme, turquoise primary, semantics re-tinted, radius
  10/14/20, warm shadows, type metrics (§2, §3, §4).
- **Vazirmatn v33.003**, self-hosted as a vendored static variable font — no
  WebJar for it exists (§3, §13.2).
- **Three shared partials** — `layout/page-header.jte`,
  `layout/school-scope.jte`, `layout/empty-state.jte`, adopted across 14
  pages (§5, §5a).
- **Empty-state illustration** — five inline SVGs behind one partial, 15
  sites (§5a). An icon pack was re-examined and rejected (§11.3).
- **The dark scheme is owned**, no longer inherited undesigned from Bulma
  (§4a, §12).

Touches `app.css`, 15 `.jte` templates, three new partials, the vendored font,
and one test. No Java, no controllers, no `pom.xml`, no build change; the
zero-build and no-CDN rules in `CLAUDE_LEGACY.md` §2 both hold. What remains
open is listed in §13 — notably the currency contract (§13.5), per-page dark
verification, and empty-state copy (§13.9).

This supersedes two OpenSpec records that predate it:
[`openspec/specs/current-system.md`](openspec/specs/current-system.md)'s web-UI
invariant and the ink-blue/system-font entries under "Accepted Behavior" in
[`openspec/changes/web-design-followups.md`](openspec/changes/web-design-followups.md).
Both are annotated there; this file is the current authority for the visual
layer.

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
- **Radius:** 10 / 14 / 20 px (`--radius-sm/md/lg`), bridged onto
  `--bulma-radius-small` / `--bulma-radius` / `--bulma-radius-large` so
  Bulma components pick them up with no per-component overrides.
  (One step softer than the 8/12/16 of the ink-blue era — warmth reads as
  roundness.)
- **Shadows:** two levels only (`--shadow-sm`, `--shadow-md`), tinted warm
  to `hsl(28 25% 20%)`. No heavy
  shadows; elevation is not this UI's hierarchy mechanism — spacing and
  typography are. A cool-grey shadow under a warm surface is what makes a
  warm palette look accidental, which is why the tint is not optional.
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

**Metrics (revised 2026-09-27).** The mechanism above is unchanged — the
hierarchy stays a fixed subset of Bulma classes so "never invent typography"
remains checkable in review. Only the values behind those classes moved, and
only where Persian needs it:

- **Body line-height 1.75** (Bulma ships 1.5). Persian needs the extra
  leading to read calmly. Set via `--bulma-body-line-height`.
- **Title line-height 1.3** (Bulma ships 1.125, which collides Persian
  ascenders with the dot-heavy descenders ج چ ژ).
- **`is-size-7` is 13px** (Bulma ships 12px) — it carries *both* the Small
  and Caption roles, and 12px is below comfortable Persian legibility.
- `title is-4` and `is-5` already resolved to 1.5rem / 1.25rem, which is what
  the direction wants; their sizes are deliberately untouched.

**Three of these are not reachable from `:root`** — Bulma 1.0.4 hardcodes
`.is-size-7` (`font-size: 0.75rem !important`) and `.has-text-grey`
(`color: hsl(221, 14%, 48%) !important`), and declares
`--bulma-title-line-height` on `.title, .subtitle` rather than `:root`, where
an element-scoped declaration beats any root override. `app.css` therefore
carries exactly three non-token rules, grouped and commented as such. Re-check
them on any Bulma upgrade; delete any that a later version tokenizes. Verified
2026-09-27 by reading computed styles in a browser, not from Bulma's docs.

**Persian font (superseded 2026-08-16 decision; Vazirmatn adopted
2026-09-27):** the system stack was retained on 2026-08-16 because nothing
depended on it. The warm direction does — a human feel is carried more by
letterforms than by any hex value, and the system stack renders Persian with
different metrics and weights on every platform.

- **Vazirmatn v33.003**, SIL OFL 1.1, self-hosted at
  `static/fonts/Vazirmatn-Variable.woff2`.
- **Not a WebJar.** No `vazirmatn` artifact exists on Maven Central under
  `org.webjars`, `org.webjars.npm`, or any other group — verified
  2026-09-27 against the repository, not assumed. The font is therefore
  **vendored** as a static file. That still satisfies `CLAUDE_LEGACY.md` §2
  (the rule bans CDN links and build pipelines, not static assets), but it
  does mean we own updating it; there is no dependency bump to ride.
- **One variable font, not three static weights.** `Vazirmatn[wght].woff2`
  covers 100–900 in 111 KB; the 400/500/700 static trio would be 153 KB.
  Declared `font-weight: 100 900` with `font-display: swap`. Renamed to
  `Vazirmatn-Variable.woff2` because the upstream brackets need URL-escaping.
- The previous system stack is kept as the fallback list, so a font failure
  degrades to exactly what shipped before rather than to a browser default.
- `OFL.txt` sits beside the font, as the licence requires.

Never add a face through a CDN `<link>` (which violates the
zero-build/WebJar rule in `CLAUDE_LEGACY.md` §2).

---

## 4. Color

Roles, not palettes: **primary** (the single accent), **neutral** warm greys
(the Bulma scheme, re-hued), and **success / warning / danger / info** for
semantics — re-tinted 2026-09-27 into the warm family (see below).

Rules:

- **One accent color per screen.** Primary appears on the primary action and
  active navigation state; nowhere else. Semantic colors appear only with
  semantic meaning (danger = destructive/error, never decoration).
- Destructive actions are always `is-danger` and always confirmed (this
  restates the UX_ROADMAP/§5 confirmation rule for completeness; that file
  owns it).

**Neutral scheme (adopted 2026-09-27):** warm paper. `--bulma-scheme-h: 28`
with `--bulma-scheme-s: 34%` for surfaces and borders, and
`--bulma-text-h: 28deg` / `--bulma-text-s: 18%` for text. **Both pairs are
required:** Bulma derives surfaces from `scheme-h/-s` but text from a separate
`text-h/-s`, so setting only the scheme warms the page while leaving every
caption cool grey. Text runs at the lower saturation because warm greys go
olive past roughly 20% at these lightnesses.

Resolved light values: page `#F8F5F1`, card `#FEFEFD`, hairline `#E6D8CC`,
body ink `#4E4236` (9.7:1), weak ink `#7E6A58` (5.1:1).

**Primary (adopted 2026-09-27):** Persian turquoise (فیروزه),
`hsl(177, 58%, 29%)` (≈ `#1F7571`), applied via Bulma 1.x's
`--bulma-primary-{h,s,l}` and matching `--bulma-link-{h,s,l}` so buttons and
links agree. 5.4:1 on paper; 5.5:1 carrying white text. Supersedes the
desaturated ink-blue `hsl(210, 40%, 38%)` adopted 2026-08-16. Changing it is a
design-system decision, implemented by editing the corresponding values in
`app.css`.

**Why the accent is not warm.** The obvious move for a warmer feel is a
terracotta or amber primary. It is rejected for a reason specific to this app:
these are management screens with real destructive actions, and the rule above
commits to danger meaning danger and nothing else. A warm red-adjacent primary
button sitting beside an `is-danger` button makes the most consequential
distinction in the UI the hardest one to see. The warmth therefore comes from
the neutrals — which is also where it is cheapest and most pervasive — and the
accent stays unmistakably not-a-red.

**Inline links need a third variable.** `<a>` does not read `--bulma-link`; it
reads `--bulma-link-text`, built from `--bulma-link-on-scheme-l`, which ships
at 58% for the light scheme and 73% for dark. Left alone against the turquoise
hue, the light value renders a pale `#56D2CC` at 1.8:1. It is overridden to
29% **inside the light-scheme block only** — hoisting it to `:root` drops
dark-scheme links to 3.3:1. Both measured, not assumed.

**Semantics, re-tinted (adopted 2026-09-27).** This reverses the 2026-08-16
decision to leave them at Bulma's defaults. The reason is a need that did not
exist then: Bulma's stock semantics are light (l 53–70%) and cool, and they
read as foreign against warm paper.

| Role    | Value                  | Contrast on paper        |
|---------|------------------------|--------------------------|
| success | `hsl(144deg, 46%, 31%)`| 5.8:1 with white text    |
| warning | `hsl(37deg, 75%, 45%)` | 5.8:1 with **ink** text  |
| danger  | `hsl(5deg, 61%, 44%)`  | 5.9:1 with white text    |
| info    | `hsl(200deg, 50%, 36%)`| 5.9:1 with white text    |

Two consequences worth knowing:

- **Warning never carries white text.** It is the one semantic that cannot at
  any usable lightness. It keeps Bulma's dark invert, exactly as the stock
  yellow already behaved, so no template changes.
- **Every other accent needs `--bulma-<color>-invert-l: 100%`.** Bulma sizes
  `is-*` text off `<color>-invert-l`, whose stock values assume a *light*
  fill — `--bulma-primary-invert-l` resolves to 6%. All four accents here are
  dark, so without the override `.button.is-primary` ships near-black on dark
  teal at roughly 2:1.

---

## 4a. The dark scheme (adopted 2026-09-27)

The app ships a dark rendering whether or not anyone designs it (§12). This
section owns it. `app.css` carries a `@media (prefers-color-scheme: dark)`
block that replaces Bulma's stock stops. What it changes and why:

- **Saturation drops to 18%.** 34% is right for paper at 95%+ lightness and
  muddy at 13%. Hue stays 28 — the warmth is deliberately scheme-wide, which
  is why hue and saturation live in `:root` and only the stops are scoped.
- **Page sits below card.** Bulma points `body-background-color` at
  `scheme-main`, so page and card resolved to the same colour and nothing
  separated. The page is pushed to `hsl(28, 18%, 8%)` beneath a 13% card.
- **`is-light` becomes a raised surface** (`--bulma-light-l: 40%`). Bulma does
  not flip this named colour for dark, so it was firing a near-white button
  onto a near-black page.
- **Danger lifts to l 50%.** At the light-scheme 44% the `.input.is-danger`
  border measured 2.88:1 against a dark field — under the 3:1 that non-text
  state indicators want. 50% gives 3.5:1 and keeps white-on-danger at 4.8:1.
- **Primary does not move.** White-on-turquoise is 5.5:1 and the button clears
  3:1 against the page; lightening it for presence would drop the label
  below AA.

Swept 2026-09-27 across **both** the component set and the public landing page,
in both schemes: all body, caption, link, help, title, tag and six button
variants clear AA, and every button fill clears 3:1 against the page.

The landing-page sweep (added after merging master, which brought
`landing.css`) caught two dark-scheme failures that the component sweep could
not, because neither element appears outside that page:

- **`.has-text-grey-dark` measured 1.68:1** — hardcoded to a *dark* cool grey,
  so in the dark scheme it is dark-on-dark. It carries the footer sign-in link,
  i.e. a real control nobody could read. Same defect class as
  `.has-text-grey`; remapped the same way (see §7).
- **`.tag.is-light` measured 3.74:1** — a regression from `--bulma-light-l: 40%`
  above. Bulma emits `.tag.is-light.is-light` at specificity 0,3,0, which always
  wins and reads `--bulma-light-light-invert-l` — a variable **Bulma never
  defines**, so `--bulma-tag-color-l` was invalid and the tag silently inherited
  body text colour. Harmless while `is-light` was near-white; broken against a
  mid-tone fill. Fixed by defining it.

Both are why the sweep has to cover real pages and not just a component
harness: `landing.css` is entirely token-driven and inherited the warm scheme
for free, yet the page still had two unreadable elements.

**`my-study-plan.css` (2026-09-28).** Swept next, and it was worse — a
correction to an earlier claim in this file that it "already read
`var(--bulma-border)`, so it followed for free." It uses `var()` 46 times, but
**only for spacing and radius**; all **33** colour declarations were hardcoded
and none tokenized. It therefore adopted the new geometry and kept a cool
palette, and `background: #fff` put a white slab in the middle of the dark UI
with `#9aa0a7` on it at 2.4:1 — an AA failure that predates this direction.
All 33 are now tokenized. Six further dark-scheme failures surfaced only after
that, all one root cause plus one new category:

- **Accent text on an accent fill needs `*-bold`, not `*-on-scheme`.** Bulma
  defines `--bulma-soft-l`/`--bulma-bold-l` as a *swapping* pair — 90%/20% in
  light, 20%/90% in dark — so `*-soft` background with `*-bold` text is
  scheme-correct by construction. `-on-scheme` is for accent text on a
  **neutral** surface, which is a different situation and the wrong tool here.
  Four elements were failing at 1.66–4.35:1; they now measure 7.8–8.5:1.
- **Chart fills are graphical objects, not decoration.** The day and topic bars
  used the raw accent against a `border-weak` track: 1.95:1 in dark, a bar
  invisible against the thing it is measured against. Moved to
  `-on-scheme` (3.55:1 dark / 7.19:1 light). If a mark carries data, it owes
  3:1 to its track (WCAG 1.4.11).

Rule of thumb this produced, worth applying before the remaining pages:
**accent on neutral → `-on-scheme`; accent on its own `-soft` → `-bold`;
accent as a fill behind text → `-invert` for that text.** A bare
`var(--bulma-primary)` is right only as a fill.

One judgement call recorded so it is not re-litigated: `.button.is-light` in
the **light** scheme has a fill at 1:1 against the page and cannot reach 3:1
while remaining light. That is not a WCAG 1.4.11 failure — a control
identifiable by its text label is exempt, and the label measures 10.9:1. It
gets a hairline border for affordance, not for conformance.

---

## 5. Components

The component library is **the set of `.jte` templates that exist**, not an
aspirational list. Current inventory:

- `layout/main.jte` — the only owner of `<head>`; `title`, `content`,
  optional `scripts`, and optional `styles` (rules in OpenSpec /
  UX_ROADMAP §2).
- `layout/navbar.jte` — top bar (school/role switcher, user menu) plus the
  grouped page-link sidebar (`aside.app-sidebar`, inline-start, fixed ≥1024px;
  sticky-bar + drawer below), fed by `NavbarModelAssembler`. The page links come
  from `web.model.NavCatalog`, the same source as the dashboard's «دسترسی سریع»
  cards (`dashboard.jte`, `app-shortcut` in `dashboard.css`). Layout rules live
  in `app.css` §"Sidebar layout".
- `layout/page-header.jte` — `title` + optional `subtitle`; renders the
  `title is-4` / `subtitle is-6` pair from §3. Extracted 2026-09-27,
  adopted by 14 pages.
- `layout/school-scope.jte` — the shell every school-scoped page repeated:
  the "no school selected" warning, the error notice area, and the success
  notice area, wrapped in `<template x-if="schoolId">`. Params:
  `noSchoolMessage`, `content`, and `successNotice` (default true — pass
  false on read-only pages whose Alpine component defines no `notice`, or
  Alpine throws on the undefined reference). Extracted 2026-09-27, adopted
  by 14 pages.
- Page templates (`dashboard.jte`, `people.jte`, `classes.jte`,
  `students.jte`, `finance.jte`, …) composed from Bulma primitives:
  `box`/`card`, `table`, `notification`, `tag`, `modal`, `pagination`,
  `breadcrumb`, `tabs`, form controls.

Rules:

- **Compose from Bulma primitives inline first.** Extract a shared `.jte`
  partial on the third real duplicate (the project's rule-of-three, same as
  `csrfToken()`), and record it here when extracted.

**The 2026-09-27 extraction pass, and a correction.** This file previously
predicted the early candidates would be "page header (title + primary
action), empty state, confirmation modal." Counted against the templates,
**all three were wrong**:

| Predicted partial | Actual count | Verdict |
|---|---|---|
| Page header *with primary action* | **0** pages | Does not exist; no page puts an action in its header |
| Confirmation modal | **2** pages | Rule-of-three genuinely not met |
| Empty state | **30** sites / **14** pages | Massively overdue — see §5a |

What was actually duplicated — and badly overdue — was the school-scoped page
shell, repeated verbatim in **14** pages with only the warning message
differing. That is what got extracted, along with the bare title/subtitle
pair (21 pages). The lesson is the one §11.2 already states in a different
context: count the templates, do not predict from a component wishlist.

Counting note, recorded because it caused a wrong answer once: empty states in
this codebase are overwhelmingly **client-side**, written
`x-show="!loading && xs.length === 0"`. Grepping only for the server-side
`isEmpty()` finds **1** and suggests the rule of three is unmet; the real
figure is 30. Count both forms. Exclude `:disabled="…length === 0"`, which is
a form guard, not an empty state.

Extraction is verified by JTE precompilation (`mvn compile`) plus the test
suite; the three `LoginDashboardFlowIntegrationTest` failures present
afterwards are pre-existing and reproduce on a clean tree.
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

## 5a. Empty states (adopted 2026-09-27)

`layout/empty-state.jte`. Params: `show` (the Alpine condition, moved across
unchanged), `message` (a `Content`, so existing copy keeps its links and
conditional spans), `art`, and `tone`.

Adopted at **15 sites across 13 pages**. Two `.notification is-warning` blocks
are deliberately **not** converted — `students.jte` ("first create a class")
and `curriculum.jte` ("first create a topic") gate a form rather than describe
an empty collection. They are prerequisite warnings, not empty states.

**The art.** Five hand-drawn inline SVGs (`list`, `people`, `money`, `exam`,
`calendar`, plus `none`), two-colour, stroked in the accent inside a
`primary-soft` well, `aria-hidden` because the text carries every bit of the
meaning. These are **not an icon pack** and do not reopen §11.3 — they are
five shapes used in exactly one partial.

Two things worth keeping:

- The art colour is `--bulma-primary-on-scheme`, **not** `--bulma-primary`.
  `primary-soft` flips dark for the dark scheme and `primary` does not, so the
  obvious pairing rendered a 29% stroke on a 25% well — 1.66:1, an invisible
  illustration. Measured 7.4:1 light / 3.0:1 dark after the fix.
- `EmptyStatePartialRenderTest` locks the one failure mode JTE compilation
  cannot catch: the `show` parameter lands inside an HTML attribute, and the
  Alpine conditions contain `&&`. If escaping ever changes such that `&&` no
  longer decodes back, all 15 empty states silently become always-visible or
  always-hidden while still compiling.

**Still open:** most of this copy is a bare statement of absence, which §5's
"empty states teach" rule says it should not be — only 8 of the sites name a
next action. The copy was carried across **verbatim**; rewriting it means
deciding what action each state should offer, which is a product decision, not
a re-skin. Logged as §13.9.

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
  The 2026-09-27 direction was verified against this by reading computed
  styles for body text, captions, inline links, help text, and all six button
  variants in **both** colour schemes; all clear AA. Redo that sweep — do not
  eyeball it — on any change to a colour token.
- **Semantic text helpers take the `-on-scheme` suffix.** Write
  `has-text-success-on-scheme`, not `has-text-success`. The plain helper reads
  `--bulma-success-l` — the *fill* lightness — so on a dark card it measured
  2.76:1 (`students.jte`) and 2.72:1 (`exam-take.jte`). The `-on-scheme`
  variant reads `--bulma-success-on-scheme-l`, which flips per scheme: the same
  text now measures 6.8:1 dark / 8.5:1 light. Bulma ships `-on-scheme` for
  every named colour, so this needs no override — just the right class.

  **Plain `has-text-<colour>` has no correct use as a text colour.** An earlier
  draft of this section said it was "correct on a matching coloured fill" —
  that was wrong, and backwards: `has-text-success` on an `is-success` fill
  resolves to the *same* `--bulma-success-l` the background uses, i.e. the text
  disappears into it. It also contradicted §4a. The value is a fill lightness,
  not a text lightness. Per §4a, text over an accent fill takes **`-invert`**
  (Bulma applies that itself for `.button.is-*` / `.notification.is-*`; write
  `has-text-<colour>-invert` only when you are colouring text on an accent fill
  by hand), and accent text on a neutral surface takes **`-on-scheme`**.
  `has-text-white` on the `hero is-link` in `school-entry.jte` is an explicit
  invert of exactly that kind — white on the accent fill — which is why it
  measured 5.47:1 and was left alone.
- **`.has-text-grey` and `.has-text-grey-dark` are remapped in `app.css`.**
  Bulma hardcodes every `has-text-grey*` helper with `!important`, so none of
  them follow the token scheme. `grey` is a cool `hsl(221, 14%, 48%)` measuring
  4.31:1 on the warm page — below AA, where it cleared 4.69:1 on Bulma's old
  white ground. `grey-dark` is a cool *dark* `hsl(221, 14%, 29%)`, which in the
  dark scheme is dark-on-dark at 1.68:1. Since §3 mandates `has-text-grey` for
  the Caption role and Bulma 1.0.4 ships no `.has-text-weak`, they are remapped
  onto `--bulma-text-weak` and `--bulma-text`.
  Only these two are used today (33 and 1 sites). `-darker`, `-light`, and
  `-lighter` are hardcoded the same way and will need the same treatment on
  first use — check before reaching for one.
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

**Dates, numbers, digits (decided 2026-07-13; adopted 2026-08-16):**

- **Jalali for every human-facing date** in the web UI. Numeric form
  `۱۴۰۵/۰۴/۲۲` in tables; long form `۲۲ تیر ۱۴۰۵` in headers and running
  text. The wire and database are **ISO-8601 Gregorian only**
  (`Instant`/`LocalDate` per [`openspec/conventions.md`](openspec/conventions.md)) — Jalali never crosses the
  wire and is never stored; conversion is client-side via
  `static/js/jalali.js` (the `/expenses` precedent). The server has no
  Jalali library; a server-side Jalali-boundary need (e.g. a scheduled
  Jalali-month job) would be a new explicit decision, not an import. That
  decision has since been made once, for academic years:
  `school.internal.SolarCalendar` is a small port used by
  `AcademicYearProvisioner` (`openspec/changes/academic-year.md`), and it does
  store a Jalali-derived code such as `1405-1406`. Read "never stored" above
  as true for dates and times, and not for that one code.
- **Which calendar a page shows follows its locale** (2026-10-03,
  [`decisions/2026-10-03-multi-market-not-migration.md`](openspec/decisions/2026-10-03-multi-market-not-migration.md)).
  The Jalali bullet above describes the `fa` locale. `<html lang>` selects the
  calendar: `fa` is Jalali, any other language is Gregorian with names from
  `Intl`. Pages do not call `Jalali` or `toLocaleDateString('fa-IR')` for
  dates; they go through `Calendar` (`static/js/calendar.js`: `formatDate`,
  `formatRange`, `fromDate`, `toDate`, `daysInMonth`, `periodRange`,
  `monthNames`, `weekdayNames`). A directional glyph (`‹` `›`) is written in
  the RTL convention and given `.app-dir-glyph`, which mirrors it under
  `dir="ltr"`. The week still starts Saturday in every locale: that is a
  server rule, so the calendar seam does not move it.
- **The week starts Saturday (شنبه); the weekend is Friday.** Any server-
  side "week" concept (e.g. `studyplan`'s week-start, MOSHAVERE_PORT_SPEC
  §6) anchors to `DayOfWeek.SATURDAY` — plain date arithmetic, no calendar
  library.
- **Persian digits (۰–۹) for displayed numbers** — dates, counts, and
  amounts — applied at render time by the relevant shared client utility
  (`Calendar.digits()`, which follows the page locale, or `Money.formatAmount()`);
  underlying data, DOM
  state, and anything sorted/compared stays ASCII. The Latin-data rule
  (UX_ROADMAP §2) is unchanged and includes **phone numbers**: emails,
  tokens, IDs, and phones stay Latin digits with their `dir="ltr"` island.
- **The financial unit is unresolved.** `scripts/seed-demo-data.py` calls a raw
  value Rial while the earlier finance templates called the same API values
  Toman; the UI/UX audit explicitly records the missing contract. Until a
  product decision defines persisted, entry, and display units plus conversion
  and rounding, read-only amounts use neutral Persian digits and `٬` grouping
  through `static/js/money.js`, with no unit suffix and no conversion.
- **Finance amount inputs normalize Persian and Arabic-Indic digits (۰–۹,
  ٠–٩) to ASCII before browser validation and again before serialization.**
  These controls use text fields with a numeric input mode because browser
  number controls may reject localized digits before JavaScript can normalize
  them. Other numeric-input areas require their own verified adoption before
  global input-normalization compliance can be claimed.
- Persian-digit dates need no `dir` island (they render correctly in RTL
  flow); only Latin-digit technical strings do, per the existing rule.

---

## 9. Page-design process

Before building any new page, apply the shared
[`design-page`](.agents/skills/design-page/SKILL.md) skill (parallel to the
[`new-module`](.agents/skills/new-module/SKILL.md) workflow): it forces, in
order — user goal and role(s), information hierarchy, the single primary
action, which existing templates/partials are used, mobile behavior, and
empty/loading/error states — *before* any JTE is written. Output of that pass
is a short spec in the PR/commit description, not a separate document.

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
   **Re-examined 2026-09-27 and upheld.** An icon subset was proposed
   alongside the warm direction and then rejected on evidence: every place an
   icon would plausibly go is already served. The four close controls use
   Bulma's CSS-drawn `.delete` (and already carry `aria-label`); the navbar
   dropdown arrows are Bulma's CSS `::after`; and every table and form action
   is a Persian verb label, which §8 positively prefers over a glyph. Adding
   icons now would be this list's own item 2 all over again — a component set
   with no page asking for it. The five empty-state drawings (§5a) are not a
   counter-example: they are used by one partial and nothing else.
4. **"Never generate CRUD pages" dropped.** It contradicted UX_ROADMAP's
   entire buildout. The defensible core survives as §9: pages are designed
   task-first via the skill, not scaffolded table-with-edit-buttons.
5. **"Forms autosave drafts" dropped** (server-side draft persistence for
   every form — real infrastructure, violates the YAGNI discipline in
   [`openspec/conventions.md`](openspec/conventions.md)). Build
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
- Custom CSS framework, npm/webpack/vite pipeline, CDN assets (`CLAUDE_LEGACY.md` §2).
- ~~Dark mode~~ — **no longer a non-goal; adopted 2026-09-27.** The earlier
  wording ("Dark mode — not requested") was factually wrong about the shipped
  app: **Bulma 1.0.4 ships its own `@media (prefers-color-scheme: dark)`
  block** (`bulma.css` line 1810 sets `--bulma-scheme-main-l: 9%`), and
  `layout/main.jte` sets no theme class, so the app had been rendering dark
  for OS-dark visitors since the Bulma 1.x upgrade, on undesigned defaults.
  See §4a.

## 13. Open questions

1. ~~Bulma WebJar version — 1.x confirmed?~~ **Resolved 2026-07-15:**
   `pom.xml` pins Bulma 1.0.4; the token bridge (§2) is live.
2. ~~Persian font adoption~~ — **resolved 2026-09-27:** adopt Vazirmatn
   v33.003, self-hosted as a vendored static variable font (§3). Note for the
   record that the route first proposed for this — `org.webjars.npm:vazirmatn`,
   "follows the established WebJar pattern" — **does not exist**; no Vazirmatn
   WebJar is published in any group. Vendoring was the only self-hosted option.
3. ~~Primary color value sign-off~~ — **superseded 2026-09-27:** the ink-blue
   resolved on 2026-08-16 is replaced by Persian turquoise (§4).
4. ~~Skeleton loading component~~ — **resolved 2026-08-16:** do not
   standardize one speculatively; add it only with a concrete page need (§5).
5. **Currency contract — unresolved:** define the persisted, entry, and
   display unit (Rial/Toman), conversion direction, and rounding; then
   reconcile the demo seeder, DTOs, forms, and displays. Neutral grouped
   output is the safety fallback, not the final currency decision (§8).
   The 2026-09-27 direction does not touch this and does not depend on it.
6. ~~The three overdue partials~~ — **resolved 2026-09-27**, but not as
   predicted: the three named candidates did not meet the rule of three at
   all, and the real duplicate (a school-scoped page shell, 14 pages) was
   extracted instead. See the correction table in §5. Empty state (1 page)
   and confirmation modal (2 pages) remain **un-extracted on purpose** — they
   have not hit three yet.
7. ~~Empty-state illustration and an icon subset~~ — **split and resolved
   2026-09-27:** illustration adopted (§5a); the icon subset **rejected on
   evidence**, see the re-examination in §11.3.
8. **Owning the dark scheme** — adopted 2026-09-27 (§4a, corrected §12), but
   **per-page verification is incomplete and is the live risk.** Swept so far:
   the component set, the landing page, and the weekly-grid pages
   (`my-study-plan.css`). Each of those three sweeps found real failures the
   previous one could not — 2, 2, and 6 respectively — so the remaining pages
   should be assumed to hold more, not assumed clean.
   The three previously-predicted cases were measured on 2026-09-28 and are
   now closed. The predictions were accurate where they were failures and
   wrong where they were not, which is the argument for measuring rather than
   fixing from inspection:
   - `has-text-success` ×2 (`students.jte`) — predicted ~2.7:1, **measured
     2.76:1**, fixed.
   - `has-text-info` ×1 (`exam-take.jte`) — predicted ~2.7:1, **measured
     2.72:1**, fixed.
   - `has-text-white` ×2 (`school-entry.jte`) — flagged as unknown, **measured
     5.47:1 and correct**. It sits on a `hero is-link`, so white is the right
     choice; remapping it would have been damage.

   What remains is genuinely unexplored: the management pages other than
   students, the auth/registration pages, the dashboard, and the exam and
   grade flows have had no per-page sweep at all.
9. **Empty-state copy — unresolved.** 15 empty states now share one shape, but
   the words are unchanged and most just state absence; only 8 name a next
   action, which §5 says all of them should. Fixing this means choosing the
   right action per state (product judgement), not restyling.
