# Public Landing Page Expansion

Status: proposed 2026-10-04, implemented in the same change. The current-state
statement lives in [`specs/current-system.md`](../specs/current-system.md) under
Web UI; the slider's design-system exception is recorded in
[`DESIGN.md`](../../DESIGN.md) §5.

## Verified Baseline

`/` serves `landing.jte` to anonymous visitors and redirects authenticated ones
to `/dashboard` (`LandingPageController.java:19-22`). Before this change the page
carried a hero, a three-card feature grid, a three-step start list, a closing
CTA and a one-line footer, all static — `landing.css` existed, no page JS did.

- `LoginDashboardFlowIntegrationTest.landingPageIsPubliclyReachableWithAllFounderEntryPaths`
  pinned five strings: the feature heading, the «ثبت مدرسه» label, and the
  `/login?next=/schools/new`, `/login` and `/register?next=/schools/new` hrefs.
  Those are the founder entry paths from the 2026-07-06 product decision and
  must survive any rewrite of this page.
- `layout/main.jte` owns `<head>`; a page adds assets only through its `styles`
  and `scripts` parameters, and `scripts` entries are emitted before the Alpine
  bundle so `Alpine.data` registration precedes `alpine:init`.
- The page is not reachable from `layout/navbar.jte`, which requires
  authentication. It is the public entry point itself.

## Proposed Contract

- The page gains four sections: a four-slide slider over the product's real
  areas, a three-item facts row under the hero intro, a questions section, and
  a four-column footer. Hero, feature grid and steps are unchanged.
- **The slider is scoped to this page only.** No general carousel enters the
  design system; `DESIGN.md` §5 records the exception and forbids adopting it
  on an authenticated page.
- Its behavioral contract:
  - all four slides are server-rendered and carry no `x-cloak`, so a visitor
    whose JavaScript never runs sees all four stacked and readable, separated
    by rules that `.app-landing-slider:not(.is-live)` supplies — Alpine adds
    `is-live` on init, so the separators disappear exactly when the slider
    becomes interactive;
  - the active slide changes via `x-show`, never an inline-axis transform, so
    RTL carries no direction arithmetic;
  - autoplay is skipped entirely when
    `matchMedia('(prefers-reduced-motion: reduce)')` matches, and pauses on
    hover, on focus, and on any manual selection;
  - ArrowLeft advances and ArrowRight goes back, matching RTL reading order;
  - the region carries `aria-roledescription`, each slide is a labelled group
    («۱ از ۴» … in Persian digits), and `aria-live` is off while playing.
- **Copy may assert only what the implementation does.** No invented
  statistics, customers, testimonials, prices or contact details, and every
  link resolves to a route that exists.

## Compatibility And Security

- No API is called, so there is no `X-School-ID`, no CSRF token and no
  `@RequiresModule`/`@RequiresPermission` pair to name. Nothing here is
  school-scoped and no authorization surface changes.
- The five pinned founder-entry strings are preserved verbatim.
- The page remains anonymous-only; the `/dashboard` redirect is untouched.
- The slider degrades to readable stacked content without JavaScript, so the
  entry paths stay reachable even if Alpine fails to load.

## Acceptance Criteria

1. `GET /` returns 200 for an anonymous visitor and still contains the five
   strings the existing test pins.
2. The response contains all four slides, the carousel region, the page's
   script wiring and the footer — asserted by
   `landingPageRendersSliderAndFooterNavigation`.
3. Every link on the page resolves to an existing route or an in-page anchor
   that matches an `id` on the same page.
4. No horizontal overflow at 320–1920px in either colour scheme.
5. Typography stays inside the `DESIGN.md` §3 subset; custom CSS uses logical
   properties only; touch targets on the slider controls are at least 44px.
6. Copy about per-role self-service matches the controllers, not the navbar
   alone.

## Tests

- `LoginDashboardFlowIntegrationTest.landingPageIsPubliclyReachableWithAllFounderEntryPaths`
  — unchanged, guards criterion 1.
- `LoginDashboardFlowIntegrationTest.landingPageRendersSliderAndFooterNavigation`
  — added, guards criterion 2. It asserts server-rendered output, so it fails
  if a slide is dropped from the template, which is also what a no-JS visitor
  would lose.
- Criteria 3–5 are verified by rendering the template through `gg.jte` and
  driving the result in Chromium; they are not covered by an automated test in
  this repository today. See Unresolved Questions.

## Unresolved Questions

1. **The layout criteria are not regression-guarded.** Criteria 3–5 were
   checked by hand against a real browser render. Three defects found that way
   — a separator rule leaking past `display: none`, 8px of horizontal overflow
   from Bulma's `is-variable is-8` negative gutters, and an undersized dot
   target — were all invisible to JTE compilation and to the integration test.
   Whether this repository should take on a browser-driven test harness for the
   public page is a separate decision, not one this change makes.
2. **The mobile overflow was pre-existing.** `is-8` sets a 2rem column gap,
   which Bulma turns into `margin: -32px` on `.columns`, while `.section` gives
   24px of padding at mobile widths. It is fixed here only for `.app-landing`,
   because that is the page this change delivers. Whether authenticated pages
   using `is-variable is-8` have the same 8px overflow is unverified.
