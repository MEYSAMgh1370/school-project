# Navigation Sidebar And Dashboard Shortcuts

Status: proposed and implemented 2026-10-02. The sidebar landed in #85 and the
dashboard shortcuts in #86; #85 shipped without this proposal, so it is
recorded here retroactively. The current-state statement lives in
[`specs/current-system.md`](../specs/current-system.md) under Web UI.

## Verified Baseline

Before this change, every authenticated page rendered `layout/navbar.jte`, whose
`navbar-start` held one flat link per management page.

- For a `SCHOOL_ADMIN` that was eleven links plus the brand, school switcher and
  user menu in one row; at 1440px the user name was clipped at the edge.
- Link visibility followed the selected role, which is client-side
  (`localStorage`, `static/js/navbar.js`) and display-only; the backend checks
  all active roles.
- The dashboard (`dashboard.jte`) showed the phone-number box, the school-entry
  box (admins), then the memberships table, with no links to other pages.

## Proposed Contract

**One source for page links.** `web.model.NavCatalog` is a static catalog of
groups and links (`href`, `label`, `description`, `roles`, `shortcutRoles`).
`layout/navbar.jte` and `dashboard.jte` both render from it, so a new page's link
is added once. `shortcutRoles` must be a subset of `roles`.

**Sidebar.**

- Groups: مدرسه, آموزش, نمرات, مالی, plus an always-visible داشبورد link. A
  group is shown when the selected role sees at least one of its links.
- Desktop (≥1024px): fixed, full-height, at inline-start (right in RTL); the top
  bar keeps only the school/role switcher and user menu.
- Groups are collapsible (`aria-expanded`/`aria-controls`); open state persists
  in `localStorage` keyed by group id, and the group containing the current page
  is always open.
- Below 1024px: sticky top bar with a burger; the sidebar is a drawer from
  inline-start over a backdrop (closes via backdrop, close button or Escape,
  locks page scroll while open). The school/role switcher and logout move into
  the drawer.

**Dashboard.**

- A «دسترسی سریع» section of link cards, gated by the selected role. The role
  reaches the page through `Alpine.store('nav').selectedRole`, which
  `navbar.js` mirrors from the switcher; the section needs its own `x-data` root.
- Content order: shortcuts → memberships → school-entry box (admins) → phone box.
- Cards are three per row on desktop and two per row on tablet and mobile.

**Shortcut cards per selected role.**

| Role | Cards |
|---|---|
| `SCHOOL_ADMIN` | اعضای مدرسه, دانش‌آموزان, کلاس‌ها, آزمون‌ها, دفتر نمرات, امور مالی |
| `TEACHER` | دانش‌آموزان, کلاس‌ها, آزمون‌ها, دفتر نمرات |
| `STUDENT` | آزمون‌های من, برنامه‌ی مطالعه‌ی من, نمره‌های من, فاکتورهای من |
| `PARENT` | نمره‌های من, فاکتورهای من |
| `FINANCE_ADMIN` | امور مالی, هزینه‌ها |

The sidebar links per role are unchanged from the previous navbar except that
the grades link is now «دفتر نمرات» for admins and teachers, and «دانش‌آموزان»
moved from the grades group to «مدرسه» (also visible to `TEACHER`).

## Compatibility And Security

No REST payload, permission, tenant, schema or session change. Links and cards
are navigation affordances only; every page's API still enforces access
server-side. Accordion state saved under the previous index keys is ignored once.

## Unresolved Questions

- The one-line card descriptions and the role-to-card mapping are UI copy and
  curation chosen in this change, not product-approved; they live in
  `NavCatalog` and can change without a contract change.
- The `PARENT` and `FINANCE_ADMIN` dashboards were covered by catalog tests but
  not checked in a browser; real-device mobile and keyboard-only use were not
  checked either.

## Acceptance Criteria

- Every sidebar link and every dashboard card comes from `NavCatalog`; every
  card link also appears in the sidebar.
- For each role, only the links and cards listed for it are visible, and
  switching the selected role updates the cards without a reload.
- The dashboard section order and the mobile two-column cards hold.

## Tests

- `NavCatalogTest` — unique hrefs and group ids, labels/descriptions present,
  `shortcutRoles ⊆ roles`, every role has a shortcut, expression output.
- `NavSharedSourceRenderTest` — renders `navbar.jte` and `dashboard.jte`
  without Spring or Docker: all catalog links in the sidebar, every card link in
  the sidebar, role-gating expressions, the `x-data` root, section order, mobile
  columns.
- Existing `*FlowIntegrationTest` assertions on `href="/…"` keep passing in CI.

## Update 2026-10-03

Sidebar links and group headers are now also shown to a custom role that holds the page's
management permission (`NavCatalog.Link.permissions`); dashboard shortcut cards stay by
built-in role. See [`self-service-gate-nav-permissions-bad-requests.md`](self-service-gate-nav-permissions-bad-requests.md).

Shortcut cards are now also shown to a custom role holding all of a card's link permissions
(system roles unchanged), and custom role titles replace raw codes in the role switcher. See
[`custom-role-ui-and-timetable-gate.md`](custom-role-ui-and-timetable-gate.md).
