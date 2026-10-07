# Roshdeandishe — Design System

**Status: PROPOSED 2026-10-07 — visual direction "Paper & Ink-Teal".**
Replaces the "Paper & Turquoise" direction adopted 2026-09-27 once the
token values below are implemented in `app.css` and the contrast sweep in
§7 has been re-run against real pages. Until then, `app.css` is still the
source of truth for shipped values (same rule as before: if this file and
the CSS disagree, the CSS wins and this file gets corrected).

What this revision keeps and what it changes is recorded in §11, so nothing
from the previous direction is dropped silently.

Scope, authority and subordination are unchanged: this file governs the
**JTE web UI only** (management pages and the student web channel). It is
subordinate to the OpenSpec current-behavior docs for architecture, domain
and access control, and to `UX_ROADMAP.md` §2 for binding shared UX
patterns. The native Android student app (Kotlin + Compose) inherits the
intent (§1) and color values (§4) but gets its own Material-based system —
do not translate Bulma classes or these component rules into Compose.

The zero-build and no-CDN rules in `CLAUDE_LEGACY.md` §2 still hold for
everything in this file, including the new icon set (§5b).

---

## 1. Design intent

School software for admins, teachers, students and parents. It should feel
**calm, crisp and fast** — calm in color, crisp in contrast, fast for the
repetitive work that dominates a school day (entering 32 grades, marking
attendance, checking a schedule).

Every screen reduces cognitive load: one primary action per page, the most
frequent task given the most visual weight, progressive disclosure for rare
tasks, always-visible system status, feedback for every action, and a
recovery path for every mistake. Persian-first, RTL-first.

**Design for the moment of use, not the screenshot.** A screen is not done
until it has been checked in the states it is actually used in: keyboard
open on a phone, a list of 30+ items, a wrong tap, and a failed submit
(§9).

Tiebreaker, not a rule: when two compliant options exist, pick the one that
is calmer *and* faster for a repeat user.

---

## 2. Tokens

Defined as CSS custom properties in `app.css`. Do not invent values outside
these scales.

- **Spacing:** 4, 8, 12, 16, 24, 32, 48, 64 px (`--space-1` … `--space-8`).
  Unchanged.
- **Radius:** 10 / 14 / 20 px (`--radius-sm/md/lg`), bridged onto
  `--bulma-radius-small` / `--bulma-radius` / `--bulma-radius-large`.
  Unchanged in value; now with assigned roles:
  - `sm` 10 — buttons, inputs, chips' inner elements
  - `md` 14 — cards, list containers, list rows, notices
  - `lg` 20 — the featured card (§5a) and bottom sheets
  - Pills (filter chips, status chips) use `9999px`; that is a shape, not a
    radius step.
- **Shadows:** two levels only (`--shadow-sm`, `--shadow-md`), now tinted to
  the **ink** hue, `hsl(216 30% 15%)` at low alpha, so they agree with the
  text rather than the paper. Elevation is still not the main hierarchy
  mechanism; it is allowed in exactly two places: the featured card and the
  focused item in focus-mode entry (§5a).
- **Focus ring:** `2px solid` primary plus a `3px` outer halo in
  `primary-soft`. One definition, used for keyboard focus *and* for the
  "current item" in data entry, so the two never look different.
- **Motion:** `--motion-fast: 120ms`, `--motion-base: 180ms`, ease-out only,
  no bounce. `prefers-reduced-motion` honored globally. Unchanged.

**Bulma dependency (unchanged, verified 2026-07-15):** the bridge requires
the Bulma WebJar at **1.x** (runtime CSS variables); `pom.xml` pins 1.0.4.
A downgrade to 0.9.x makes the bridge inert and needs a separate decision.

---

## 3. Typography

The mechanism is unchanged: hierarchy is a fixed subset of Bulma classes, so
"never invent typography" stays checkable in review. One role is added
(**Data value**) because data-entry screens need it and Bulma has no class
for it.

| Role        | Class                     | Size  | Use                                        |
|-------------|---------------------------|-------|--------------------------------------------|
| Page title  | `title is-4`              | 24px  | one per page, in `PageHeader` position     |
| Section     | `title is-5`              | 20px  | section headings; the featured card's title|
| Subsection  | `title is-6`              | 16px  | card headings, list-row emphasis           |
| Body        | (default)                 | 16px  | everything else                            |
| Small       | `is-size-7`               | 13px  | secondary metadata                         |
| Caption     | `is-size-7 has-text-grey` | 13px  | hints, timestamps                          |
| Data value  | `app-value`               | 18px / 26px focused | grades, amounts, counts being entered or compared |

- **13px is the floor.** Nothing renders at 12px. (The 2026-10-07 canvas
  mockups used 12px for row numbers and captions; that was a mockup error,
  not a direction.)
- **Metrics (carried over):** body line-height 1.75, title line-height 1.3,
  `is-size-7` at 13px. The three non-token rules that make this reachable in
  Bulma 1.0.4 (`.is-size-7` and `.has-text-grey` hardcoded with
  `!important`; `--bulma-title-line-height` declared on `.title`) stay as
  they are — re-check them on any Bulma upgrade.
- **Weights:** 400 body, 500 names in lists, 700 headings and buttons, 800
  only for the page title on mobile and the focused data value.
- `app-value` is tabular: `font-variant-numeric: tabular-nums`, centered in
  its field, Persian digits at render time (§8).

**Font (unchanged):** Vazirmatn v33.003, SIL OFL 1.1, self-hosted as one
variable font at `static/fonts/Vazirmatn-Variable.woff2` (`font-weight:
100 900`, `font-display: swap`), `OFL.txt` beside it, system stack as
fallback. No WebJar exists for it; vendoring remains the only self-hosted
option. Never add a face through a CDN `<link>`.

---

## 4. Color

Roles, not palettes: **primary** (the single accent), **neutrals**, and
**success / warning / danger / info** for semantics.

**The direction in one sentence:** warm paper surfaces, cool ink text, one
deep teal accent. The warmth makes it calm; the cool ink makes it crisp.

### 4.1 Neutrals

Bulma derives surfaces from `scheme-h/-s` and text from a separate
`text-h/-s` — the previous direction already relied on that split. This one
uses it on purpose to put two hues on the page:

- **Surfaces:** `--bulma-scheme-h: 48`, `--bulma-scheme-s: 22%` (warm paper).
- **Text:** `--bulma-text-h: 216deg`, `--bulma-text-s: 26%` (cool ink).

| Token            | Value      | HSL                  | Measured contrast        |
|------------------|------------|----------------------|--------------------------|
| page             | `#F6F5F1`  | hsl(48, 22%, 95%)    | —                        |
| card             | `#FFFFFF`  | —                    | —                        |
| hairline         | `#E6E3DC`  | hsl(42, 17%, 88%)    | decorative only (1.17:1) |
| control border   | `#7A8494`  | hsl(217, 11%, 53%)   | 3.78:1 on card           |
| body ink         | `#1C2430`  | hsl(216, 26%, 15%)   | 14.3:1 page / 15.6:1 card|
| secondary ink    | `#4A5562`  | hsl(212, 14%, 34%)   | 7.0:1 page / 7.6:1 card  |
| weak ink         | `#5B6573`  | hsl(215, 12%, 40%)   | 5.4:1 page / 5.9:1 card  |

Rules:

- **Hairline is decoration; control border is identification.** A border
  that is the only thing telling the user "this is a field" must reach 3:1
  (WCAG 1.4.11) — inputs use the control border. Dividers between rows and
  card outlines may use the hairline.
- **Placeholders use weak ink** (5.9:1), never a lighter grey. Better still,
  do not put meaning in placeholders; the label carries it.
- Why the hue split does not look accidental: the previous direction warned
  that a cool shadow under a warm surface looks like a mistake. The fix here
  is consistency — *every* ink-colored thing (text, icons, shadows, control
  borders) is cool, and *every* surface is warm. Nothing mixes within its
  own category.

### 4.2 Primary

**Deep teal**, `hsl(180, 79%, 21%)` ≈ `#0B5E5E`, via `--bulma-primary-{h,s,l}`
and matching `--bulma-link-{h,s,l}`. White text on it 7.6:1; as text on
paper 6.9:1; on its own soft tint `#EEF5F4` 6.8:1.

**Where primary may appear** (revised — the previous rule allowed only the
primary action and active navigation):

1. the page's primary action — **at most one filled primary button per
   view**;
2. the active navigation item and the **selected** filter chip;
3. the focus ring / current-item indicator (§2);
4. progress indicators.

Never as decoration, never as a background for a block of content, never for
a second button. If a screen seems to need two filled primary buttons, it
has two primary actions and should be split.

**Why the accent is still not warm** (carried over — the argument still
holds): these are management screens with real destructive actions, and
danger must mean danger. A terracotta or amber primary beside an
`is-danger` button makes the most consequential distinction in the UI the
hardest one to see.

**Inline links still need a third variable.** `<a>` reads
`--bulma-link-text`, built from `--bulma-link-on-scheme-l`. Override it
**inside the light-scheme block only** (hoisting to `:root` breaks dark).
The value must be re-measured for the new hue during implementation.

### 4.3 Semantics

| Role    | Value     | HSL                  | Text on it   | As text on card |
|---------|-----------|----------------------|--------------|-----------------|
| success | `#2E6B3A` | hsl(132, 40%, 30%)   | white 6.4:1  | 6.4:1           |
| warning | `#C98A1B` | hsl(38, 76%, 45%)    | **ink** 5.3:1| use `#A06A12` (4.6:1) |
| danger  | `#B42318` | hsl(4, 76%, 40%)     | white 6.6:1  | 6.6:1 (6.0:1 on its soft tint) |
| info    | `#2B4C7E` | hsl(216, 49%, 33%)   | white 8.6:1  | 8.6:1           |

- **Warning never carries white text** (unchanged reasoning).
- **Every other accent needs `--bulma-<color>-invert-l: 100%`** — all four
  fills are dark, and Bulma's stock invert assumes a light fill
  (carried over).
- **Color is never alone.** Every error has an icon *and* a text message;
  every status chip has a word. (§7)

### 4.4 Category tints (avatars only)

Initial-letter avatars use a small fixed set of soft tints with a matching
dark letter color — blue `#E3ECF7`/`#2B4C7E`, clay `#F6E7DA`/`#8A4A1C`,
green `#E4F0E6`/`#2E6B3A`, violet `#EFE4F5`/`#643A80`. They carry **no
meaning** (assigned by a stable hash of the person's id) and must never be
read as status. Dark-scheme pairs are an open item (§13).

---

## 4a. The dark scheme

Owned, not inherited (Bulma 1.0.4 ships a `prefers-color-scheme: dark`
block, so the app renders dark for OS-dark users whether designed or not).
Hues stay; lightness and saturation change.

| Token               | Value     | Measured                   |
|---------------------|-----------|----------------------------|
| page                | `#0E1217` | —                          |
| card                | `#151A21` | 1.08:1 vs page → separate with a hairline, not luminance |
| body ink            | `#E8EBEF` | 14.6:1 on card             |
| weak ink            | `#A3ACB8` | 7.6:1 on card              |
| primary as text/link| `#3FA3A0` | 5.8:1 on card              |
| danger as text      | `#F26B5E` | 5.9:1 on card              |

- **Primary as a fill does not move:** white on `#0B5E5E` stays 7.6:1. As
  *text* it would be 2.3:1 on a dark card, which is why text and links use
  the lifted `-on-scheme` value.
- **The rule of thumb is carried over unchanged** because it is correct:
  accent on neutral → `-on-scheme`; accent on its own `-soft` → `-bold`;
  accent as a fill behind text → `-invert` for that text. A bare
  `var(--bulma-primary)` is right only as a fill.
- `is-light` becomes a raised surface in dark; `.tag.is-light` needs
  `--bulma-light-light-invert-l` defined (Bulma never defines it) —
  carried over.
- **Per-page sweeps remain mandatory.** Every previous sweep found failures
  the one before could not. Assume unswept pages hold more.
- **All 32 page templates swept 2026-10-07, both schemes** (Chromium, Bulma
  1.0.4, 1280px; each page rendered from its real `.jte`, hidden states —
  modals, `x-if` blocks, empty bindings — revealed before measuring). The
  sweep checks every visible text node (4.5:1, 3:1 for large text),
  placeholders, and `.input/.textarea/.select` borders (3:1). Before the fixes
  it found **1 failure in light and 11 distinct in dark** (1.07–3.9:1); after,
  **0 in both**. Root causes, all in `app.css`: (1) Bulma builds
  `.button/.tag.is-<colour>.is-light` from `--bulma-light-l` and
  `.notification.is-*.is-light` from a hardcoded 90% fill, neither of which
  follows the scheme — in dark they are now a 24% tint of the hue with 92%
  text; (2) `.button.is-link.is-light` text read Bulma's own link scale (3.4:1)
  — `--bulma-link-light-invert-l` is now 10%; (3) a field inside a tinted
  notification has no 3:1 border against the tint — it takes weak ink there;
  (4) `my-grades.css` used the link *fill* colour as text (2.38:1) — now
  `--bulma-link-text`; (5) select/dropdown arrows read the fill colour too
  (about 2.4:1 on the dark card) — `--bulma-arrow-color` is `link-text` in dark.
  **What the sweep does not measure:** icons and chart/progress fills,
  hover/pressed states, gradients or images behind text, text that only
  exists once real data loads, and layout. The arrows were found by looking at
  a screenshot, not by the script — it is a floor, not a clearance.
- **Measured 2026-10-07 in a real browser (Chromium, Bulma 1.0.4, mobile
  viewport)** on the dashboard and the grade-entry page, both schemes. Light:
  body 14.2, caption 5.45, primary button 7.35, danger tag 6.57, control border
  3.78. Dark: body 15.5, caption 8.5, danger tag 4.62. It found two failures
  the hex arithmetic could not, both fixed in `app.css`: `.button.is-light`
  turned **olive** at 4.39:1 (the light-scheme hue 48 carried into dark; dark
  now uses hue 215 → 5.65:1), and the input border was 1.8:1 because §4.1 gave
  no dark control border. **This file now sets one:** `hsl(215 12% 48%)`,
  3.9:1 on `#151A21` — a value the implementation chose; amend it here if you
  prefer another.

---

## 5. Components

The library is still **the set of `.jte` templates that exist**, plus the
patterns below that are *adopted for use when a page needs them*. Rule of
three is unchanged: extract a shared partial on the third real duplicate,
and count both server-side (`isEmpty()`) and client-side
(`x-show="…length === 0"`) forms when counting.

**Existing inventory (unchanged):** `layout/main.jte` (only owner of
`<head>`), `layout/navbar.jte` (school/role switcher, user menu, grouped
sidebar fed by `NavCatalog`), `layout/page-header.jte`,
`layout/school-scope.jte`, `layout/empty-state.jte` (§5c), and the page
templates composed from Bulma primitives.

Rules carried over unchanged: modals are Bulma `modal` driven by Alpine,
one open at a time, never `alert()`/`confirm()`; destructive actions are
`is-danger` and confirmed; loading over ~300ms shows `is-loading` on the
trigger, over 2s a progress message; no skeleton until a page needs one;
the landing slider is landing-page-only.

## 5a. Adopted patterns (from the 2026-10-07 canvas)

Each is adopted as a pattern, not pre-built as a component. Build it the
first time a page needs it, then record the template here.

**Featured shortcut.** On a dashboard, the single most frequent destination
gets one full-width card (`radius-lg`, `shadow-sm`, section-size title).
The next two get half-width cards. Everything else is a **list row**
(below). The grouping must come from `NavCatalog` groups, so the dashboard
and the sidebar always agree.

**Built 2026-10-07 on the dashboard** (`dashboard.jte`, `dashboard.css`). The
featured card, half-width cards and list rows are `NavCatalog.Tier` values
(`FEATURED`, `CARD`, `ROW`) on each link; tier is fixed per link, not per role,
because the selected role changes client-side and the layout must stay correct
for any subset. Current assignment — **a product choice to confirm**:
`/grades` featured; `/timetable` and `/exams` cards; everything else a row,
grouped under its `NavCatalog` group label. Deviations from the canvas, each
following this file over the mockup: the featured card is a plain card with a
shadow, **not** primary-filled (§4.2 forbids primary as a content background);
icon tiles are neutral, not tinted (§4.4 tints are for avatars only); the
greeting is not built (copy decision). Dashboard shortcut anchors carry
`data-shortcut`; tests count them by that attribute.

**Built 2026-10-07 on grade entry** (`grades.jte`, `grades.js`, `grades.css`;
client-side only — each grade is still one `POST`, no bulk endpoint):
list rows with a word-status chip, filter chips (shown over 25 students or
when review has items), focus mode, the keyboard-top bar, «ثبت همه», and
review mode with the likely-value suggestion (`175` → «۱۷٫۵»; only the
unambiguous three-digit case guesses). Numeric input is a text field with
`inputmode="decimal"` that normalizes Persian/Arabic digits and `٫ ، / ,`.
A `beforeunload` guard warns about unsaved grades. **Not built, on purpose:**
the «غایب»-style grade value (a grade has no such domain value — that would
be a business rule) and draft autosave (§13.5 is still undecided). In its place
the keyboard bar has «رد کردن» (next student, grade left empty), and two
**display-only** hints that never block grading: «غایب/تأخیر امروز» from
today's attendance roll (attendance is a free module, every school has it; a
teacher who may not read that class's roll simply sees no tag) and, when the
second term is being entered, «نوبت اول: …» from the same class/year/course.
Blocking a student because of absence is deliberately not done: a grade is per
term, not per day, and a block would need its own written rule and a server
check (see `openspec/changes/attendance.md`). The keyboard bar follows
`visualViewport`; it is **untested on a real phone**.

**Students page (2026-10-07), the first management list to adopt the row
pattern.** The table became `.app-student-row` rows (avatar, name and email,
class tag, guardian count, «مدیریت والدین» as a plain `is-light` button) with a
text search over name, email and class. The guardian panel is unchanged. Search
is always shown, not only over 25 rows, because it costs nothing on a short list.
The sweep found no contrast failure, but a hue check did: the `is-light` family's
**text** inherited the warm light-scheme hue and read olive-brown
(rgb(65,61,42)). The light scheme now sets that text to the cool body ink;
fill stays warm paper. Contrast-only sweeps miss this class of problem, so
hue is checked by eye on a screenshot as well.

**People, classes and finance (2026-10-07)** now use the same row pattern.
People: search over name, email and role; each row shows the status in words
(فعال / غیرفعال / فعال‌سازی شده / در انتظار فعال‌سازی) and the two actions as
plain `is-light` buttons. Classes: rows with year, grade, section and teacher;
the roster stays a panel below. Finance: invoice rows with status in words, a
search over student name, and the payments panels unchanged. Money amounts
still come from `money.js`, which currently prints Latin commas (`12,000,000`);
DESIGN.md §8 asks for `٬` with Persian digits, so that is a pending fix in
`money.js`, not in these rows.

**Exams page (2026-10-07).** The list of assessments is rows (name, time range,
duration, a «مدیریت» button that opens the panel below). The management panels
(sections, question picker, attempts, results) are unchanged. Known gap, not
fixed here: the exam time range shows ISO dates (`2026-10-20 08:00`), which
§8 does not allow for the `fa` locale. **Resolved 2026-10-07 for the exam row:**
`Calendar.formatDateTimeRange` shows the Jalali date, a 24-hour clock and Persian
digits, with the date once when the range is one day (`۱۴۰۵/۷/۲۸ ۰۸:۰۰ تا ۱۰:۰۰`)
and both dates otherwise. Used on `/exams` and `/my-exams` (the student's list).
The date comes from the existing `Calendar.formatDate`,
which prints **without** zero padding (`۱۴۰۵/۷/۲۸`), while §8 specifies padded
numeric dates (`۱۴۰۵/۰۴/۲۲`). That mismatch predates this change and affects
every numeric date; fixing it belongs in `formatDate`, not in exams. Other pages that format times by hand
(`my-study-plan.js`, `study-plans.js`) are study-interval times, not exams, and
still format by hand — a separate decision.

**Expenses (2026-10-07).** The expense list is rows with a search over category
and vendor; each row shows the amount, the status in words and its actions. The
category and vendor setup panels, the summary and the attachment tables are
unchanged. **Roles** (`roles.jte`) stays a table on purpose: it is a permission
matrix (roles × permissions), which a row list would hide. **Attendance** already
uses its own row list (`app-att-list`) and was left as it is.

**Curriculum (2026-10-07).** The question list is rows with a search over the
question text; each row shows the correct choice (Persian digits) and the
difficulty as a word. The topic picker was already a button list and is
unchanged, and so are the difficulty filter and the add-question form.

**Search, shared (2026-10-07).** Every list search goes through
`static/js/list-search.js` (`ListSearch.matches`). It treats Arabic ي/ك as
Persian ی/ک, Persian and Arabic digits as ASCII, and ignores ZWNJ and diacritics,
on both sides of the comparison. Pages that use it: students, people, finance,
expenses, curriculum, grades, promotion. Finance payment panels follow the
visible invoices and close when a search hides their invoice.

**Grade entry, unsaved scores (2026-10-07).** Changing class, year, course or
term while typed grades are unsaved is undone, with a notice in the page's
notice area (no browser dialog, per §5). The table keeps what it was loaded
for; clearing the unsaved values frees the selection. Level labels come from
`static/js/grade-labels.js`, shared by the teacher and student grade pages.

**Review follow-ups (2026-10-07).** Finance: a payment in flight blocks a second
submit; list and payment-panel responses are checked against the latest request
(a slow earlier year cannot overwrite a newer list); a school switch clears the
old school's data. Grades: the roster is for the selected year (`?academicYear=`):
the class's current students plus those enrolled in it that year — the same rule
that accepts a grade, so nobody listed is ungradeable, and a student who left the
class is not listed. Digits: one helper, `static/js/digits.js` (`Digits.toAscii`),
used by money, grades and list search; `page-scripts.test.js` checks each page loads
the helpers it uses.

**List row.** 56px minimum height, label at inline-start, optional value
(e.g. the current academic year) and a `‹` glyph (`.app-dir-glyph`) at
inline-end, hairline divider. For destinations used rarely.

**Filter chips as navigation.** For lists over 25 items, a row of pill
buttons with counts — e.g. «همه (۳۲)»، «ثبت‌نشده (۱۴)»، «غایب (۲)». Selected
chip is primary-filled with `aria-pressed="true"`. Chips answer "where
should I go next"; a scrollbar only answers "where am I". Tables over 25
rows still also get a text filter (§6).

**Focus-mode data entry.** For entering one value per item across a list
(grades, attendance):
- the current item is enlarged (avatar 48px, name at Subsection size,
  value at 26px `app-value`) with the focus ring and `shadow-md`;
- neighbors shrink and the previous, completed item dims;
- a **keyboard accessory bar** sits directly above the on-screen keyboard
  with «قبلی», the most common alternative value (e.g. «غایب»), and a
  primary «بعدی: <next name>» — the next item is named on the button;
- useful context is shown beside the field (e.g. «نمره قبلی: ۱۷») so typos
  are caught by recognition, not memory;
- inputs use `inputmode="decimal"` and normalize Persian/Arabic-Indic digits
  to ASCII before validation (§8).

**Review mode after a failed submit.** The submit button is never disabled
to signal invalid data. On press with problems:
- a summary notice at the top (`role="alert"`) states what is wrong and what
  to do, in counts;
- the list switches to a filter «نیاز به اصلاح (n)», grouped by problem type
  (invalid vs missing);
- each invalid field shows an inline error that suggests the likely intended
  value when one is obvious («شاید منظورتان ۱۷٫۵ بوده؟»);
- missing items offer the most likely fix inline (e.g. «غایب بود»);
- the primary button becomes «اصلاح موارد (۱ از ۴)» and steps through them.

**Status chips.** Small pills with a word, never color alone: «فعال»،
«غایب». A status chip that can be changed is a real `<button>`.

**Invite / share block.** Primary action is «اشتراک‌گذاری» (Web Share API,
opening the phone's share sheet); «کپی نشانی» is the secondary, `is-light`
with a hairline border. The URL is shown in a `dir="ltr"` island, truncated
with an ellipsis.

**Undo bar (narrow exception).** One transient bar at the bottom, shown for
~5 seconds after a single reversible action (marking absent, archiving a
row), containing only a short description and «بازگردانی». It is **not** a
toast system: success and error messages stay in the notice area (§6); the
undo bar never stacks, never auto-shows for anything else, and never
replaces a confirmation for destructive actions. Respect
`prefers-reduced-motion` for its entrance.

**Draft autosave (narrow exception).** Allowed for **grade entry only**,
because losing 30 typed grades is a demonstrated risk. Shown as a quiet
status line («پیش‌نویس خودکار ذخیره شد») with a check icon. Storage approach
is an open item (§13). Every other form still saves explicitly.

## 5b. Icons

**Adopted: a small vendored inline-SVG set** — this reverses §11.3 of the
previous direction, which rejected icons because no page asked for them.
The redesigned dashboard and data-entry screens now do: icons speed up
scanning of shortcut cards and carry meaning in error and status lines
alongside text.

- One partial, `layout/icon.jte`, rendering inline `<svg>` by name. **No icon
  pack dependency, no WebJar, no CDN** — the paths are vendored in the
  partial, like the empty-state art.
- Style: 24×24 grid, 2px stroke, round caps and joins, `stroke="currentColor"`,
  no fills. Rendered at 16, 20 or 24px.
- Start with only what pages use (menu, back, search, check, alert, share,
  calendar, people, class, exam, finance, clock, chevron). Every addition is
  a line in this section.
- **Built 2026-10-07** (`layout/icon.jte`, used by the dashboard): chevron,
  calendar, clock, people, class, exam, finance, check, **grades** (a document;
  added to the starting list — the featured «دفتر نمرات» card needs it), share.
  menu, back, search and alert are not built yet; add them with their first
  page. `NavSharedSourceRenderTest` fails if a `NavCatalog` link names an icon
  the partial does not draw.
- **An icon never stands alone except** back, menu and close, which require
  `aria-label`. Everywhere else it accompanies a text label and is
  `aria-hidden="true"`.
- Directional icons (back, next, chevrons) mirror under `dir="ltr"` via
  `.app-dir-glyph`.
- Icons take ink colors (`currentColor`), never primary, except inside a
  primary-filled control.

## 5c. Empty states

`layout/empty-state.jte` (params `show`, `message`, `art`, `tone`) and its
five two-color inline SVGs are kept as they are. The art color stays
`--bulma-primary-on-scheme` (measured fix carried over) and
`EmptyStatePartialRenderTest` still guards the `&&` escaping. The art is
drawn in the same stroke style as §5b so the two read as one family.

Still open (carried over): most empty-state copy only states absence;
every one should name the action that creates the first item (§13).

---

## 6. Interaction rules (additions to UX_ROADMAP §2)

UX_ROADMAP §2 remains binding. Additions:

- One primary action per page. On desktop, at **inline-end** of the page
  header; **on mobile, in a bottom action bar** when the page is a long
  list or form (thumb reach), otherwise in the header.
- Search/filter controls at **inline-start** of the toolbar. All positional
  rules use logical directions.
- Lists and tables over 25 items: client-side pagination or filter chips
  (§5a), plus a text filter.
- **Never disable a submit button to signal invalid input.** Let it be
  pressed and go to review mode (§5a). Disabling is only for "an operation
  is in progress".
- Validation errors are shown inline at the field (`is-danger` input +
  `help is-danger` + icon) **and** summarized in the section's notice area
  when submission fails.
- **Every hidden gesture has a visible equivalent.** Long-press and swipe
  may exist as shortcuts; the same action must always be reachable by a
  visible control.
- Every reversible action offers a way back — visible toggle, undo bar, or
  edit — chosen in that order.
- Long forms (>~8 fields) split into steps on one page, not a wizard.

---

## 7. Accessibility & RTL

Carried over unchanged unless noted:

- WCAG AA contrast, measured not eyeballed — redo the sweep on any color
  token change, across both schemes and real pages.
- **New:** control borders that identify a field reach 3:1 (§4.1);
  placeholder text reaches 4.5:1.
- **New:** color is never the only signal — errors carry an icon and words,
  status chips carry words.
- Visible keyboard focus, using the single focus-ring token (§2).
- Semantic text helpers take the `-on-scheme` suffix. Plain
  `has-text-<colour>` has no correct use as a text color (it is a fill
  lightness).
- `.has-text-grey` and `.has-text-grey-dark` remain remapped onto
  `--bulma-text-weak` / `--bulma-text` (Bulma hardcodes them); `-darker`,
  `-light`, `-lighter` need the same on first use.
- Touch targets ≥ 44px, enforced in `app.css`.
- Real `<button>`, `<a href>`, `<input>` + `<label>`; tapping a row's label
  focuses its input.
- RTL is the default direction; Latin data (emails, tokens, IDs, phones,
  URLs) gets `dir="ltr"` on its own element. Custom CSS uses logical
  properties only — `margin-left` in review is a defect.
- `prefers-reduced-motion` disables non-essential animation globally.

---

## 8. Voice & content

Carried over unchanged — it was right:

- Persian, friendly-professional, short. No jargon, no raw codes.
- Buttons are verbs naming the outcome (ذخیره / ایجاد / بایگانی / تأیید),
  never a generic "Submit"; the success notice reuses the verb (ایجاد → ایجاد
  شد).
- Error messages state what went wrong **and** what to do next; never
  apologize, never vague. **New:** when the likely intended value is
  obvious, suggest it.
- Empty states are invitations to act.
- A primary «بعدی» names where it goes («بعدی: امیر کاظمی»).

**Dates, numbers, digits (unchanged):** Jalali for every human-facing date
in the `fa` locale (`۱۴۰۵/۰۴/۲۲` in tables, `۲۲ تیر ۱۴۰۵` in running text),
ISO-8601 on the wire and in storage, conversion via `static/js/calendar.js`
(`Calendar`), page locale chooses the calendar, the week start belongs to
the school (`SchoolFacade.WeekStart`), Persian digits at render time via
`Calendar.digits()` / `Money.formatAmount()`, phones and other Latin data in
`dir="ltr"` islands, finance inputs normalize Persian/Arabic-Indic digits to
ASCII. The financial unit remains unresolved (§13).

---

## 9. Page-design process

Before building any page, apply the
[`design-page`](.agents/skills/design-page/SKILL.md) skill: user goal and
role, information hierarchy, the single primary action, existing
templates/partials, mobile behavior, empty/loading/error states.

**Added: the four moments check.** Before a page is considered designed,
answer in the spec:

1. **Long list** — what happens with 30+ items? How does the user find the
   next thing to do?
2. **Keyboard open** — on a phone with the keyboard up, can the user still
   see what they are editing and move to the next item?
3. **Wrong tap** — how is the most likely mistake undone?
4. **Failed submit** — what does the user see, and how do they get to each
   problem?

Output is still a short spec in the PR/commit description.

---

## 10. Screen reference

Until implemented, the reference designs are the 2026-10-07 canvas
artboards: grade entry (list), grade entry (keyboard open / focus mode),
grade entry (review after failed submit), and the principal dashboard.
Once built, `dashboard.jte` and the grade-entry page become the reference
implementations; new pages should look like their siblings.

---

## 11. Changes from "Paper & Turquoise" (decision record)

**Kept:** design intent's core (calm, one primary action, status, feedback),
spacing and radius scales, motion, two shadow levels, Vazirmatn
self-hosted, the Bulma-class typography mechanism and Persian metrics, the
"accent is not warm" argument, semantic invert rules, owned dark scheme and
mandatory per-page sweeps, the `-on-scheme / -bold / -invert` rule of thumb,
the grey-helper remaps, real-inventory + rule-of-three, empty-state partial,
modal and loading rules, all voice and date/digit conventions, the
`design-page` process, all non-goals except icons.

**Changed:**

1. **Palette.** Warm-paper surfaces kept, but text moves from warm brown
   ink (`#4E4236`) to cool ink (`#1C2430`, 14.3:1 vs 9.7:1) for crisper
   reading, and the accent moves from turquoise `#1F7571` to a deeper teal
   `#0B5E5E` (white text 7.6:1 vs 5.5:1).
2. **Accent rule widened** from "primary action and active nav" to four
   named uses (§4.2), still with one filled primary button per view.
3. **Icons adopted** as a vendored inline-SVG set (§5b), reversing
   §11.3 / §12 of the previous direction. Reason: the redesigned dashboard
   and data-entry screens are concrete pages that need them.
4. **Featured card and shortcut hierarchy** replace the equal-weight
   «دسترسی سریع» grid.
5. **Undo bar** adopted as a narrow exception to "no Toast" (§5a). The
   general Toast remains a non-goal.
6. **Draft autosave** adopted for grade entry only (§5a). Blanket autosave
   remains dropped.
7. **New patterns:** filter chips as navigation, focus-mode data entry,
   review mode, status chips, share block, the `app-value` type role.
8. **New rules:** never disable submit for validation, every hidden
   gesture has a visible equivalent, control borders 3:1, placeholders
   4.5:1, the four-moments check.

---

## 12. Non-goals

- A general Toast system (only the narrow undo bar, §5a).
- Drawer / Wizard / DataGrid components.
- An icon **pack** or icon-font dependency (the vendored set in §5b is not
  one).
- Carousels on authenticated pages.
- Blanket form autosave; table sort persistence.
- Custom CSS framework, npm/webpack/vite pipeline, CDN assets.

---

## 13. Open questions

1. **Implementation and sweep.** Move the §4 / §4a values into `app.css`,
   then measure computed styles across the component set and real pages in
   both schemes. This file stays PROPOSED until that sweep passes.
2. **Inline link lightness** (`--bulma-link-on-scheme-l`) for the new hue —
   measure, do not carry over 29%.
3. **Icon set inventory** — confirm the starting list in §5b against the
   pages actually being built; nothing goes in speculatively.
4. **Avatar tints in dark** — define dark-scheme pairs for §4.4 and measure
   the letter contrast.
5. **Grade-entry autosave storage** — client-side (IndexedDB, consistent
   with the PWA strategy) or server-side draft; decide before building.
6. **Undo bar mechanics** — how the reversal is implemented server-side
   (soft state vs compensating request) per action.
7. **Currency contract** (carried over) — persisted, entry and display unit
   (Rial/Toman), conversion and rounding. Neutral grouped output via
   `money.js` until then.
8. **Empty-state copy** (carried over) — every empty state names the action
   that creates the first item.
9. **Per-page dark verification** — text, placeholder and field-border
   contrast swept on all 32 templates (§4a); still open: non-text marks,
   hover/pressed states, and pages with real data loaded.
