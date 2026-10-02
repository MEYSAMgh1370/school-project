# Dashboard: "My Children" For Parents

Status: proposed and implemented 2026-10-02. The current-state statement lives in
[`specs/current-system.md`](../specs/current-system.md) under Web UI.

## Verified Baseline

A user with the `PARENT` role saw only the memberships table, the phone box and
the shortcut cards on `/dashboard`; nothing showed which children were linked to
them. The data already existed: `AcademicFacade.listWards(guardianUserId,
schoolId)` (used by `MyInvoicesController` and the Bale "فرزندان من" command),
`IamFacade.findUser`, `SchoolFacade.findSchool`,
`AcademicFacade.findMyEnrollment(userId, schoolId)` and
`FinanceFacade.listInvoicesForStudents(schoolId, studentIds)`.

## Proposed Contract

- `/dashboard` renders a «فرزندان من» section for a user who is an active
  `PARENT` in at least one school, between «دسترسی سریع» and the memberships
  table. Order: shortcuts → children → memberships → school-entry box (admins) →
  phone box.
- It is shown only while the selected role is `PARENT`
  (`$store.nav.selectedRole`, same display-only rule as the shortcut cards).
- One card per linked child (all active `PARENT` schools): name, school name,
  class and section (each omitted when unknown), and a finance state.
- **Finance state** is derived only from the child's invoice statuses, with no
  amounts (the monetary unit is unresolved, see `finance-monetary-unit.md`), by
  precedence: any `OVERDUE` → "N فاکتور دیرکرد" (danger); else any `UNPAID` or
  `PARTIAL` → "N فاکتور در انتظار پرداخت" (warning); else all `PAID` → "همه‌ی
  فاکتورها تسویه‌شده" (success); no invoices → "فاکتوری صادر نشده". Counts are
  rendered ASCII and converted with `Jalali.toPersianDigits` (DESIGN.md §8).
- A parent with no linked child sees: «هنوز دانش‌آموزی به حساب شما لینک نشده است.
  مدیر مدرسه می‌تواند از صفحه‌ی «دانش‌آموزان» فرزندتان را به حساب شما متصل کند.»
- Each card has two links, «نمره‌ها» → `/my-grades?student=<studentId>&school=<schoolId>`
  and «فاکتورها» → `/my-invoices?student=<studentId>&school=<schoolId>`. Only ids
  go in the URL, never the child's name (children's data must not leak into
  history or logs). The destination's navbar accepts `?school=` as the selected
  school (only if the user is a member; otherwise the saved/first school), so the
  link works without a click side effect: open-in-new-tab and middle-click load
  the child's school too. `?student=` / `?school=` must be entirely digits
  (`100junk` is ignored). Buttons keep the normal 44px size.
- **`/my-grades` has a student picker.** A control labelled «انتخاب دانش‌آموز»
  shows the selected student's name; opening it reveals a list of the names, each
  with a grade count ("بدون نمره" when none), and choosing one closes the list and
  shows only that student's grades in the table below (no student column).
  The list holds one option per linked child of the selected school (also a child
  with no grades; the table then says «نمره‌ای برای این دانش‌آموز ثبت نشده است»),
  plus «نمره‌های من» for the user's own grades (a student's own grades are not a
  ward); options are sorted by name, the user's own first. Following a dashboard
  card (`?student=<id>`) pre-selects that child; with no or an unknown/invalid
  `student` the first option is selected. With a single student the picker is
  hidden and only the name is shown. The toggle is a real button
  (`aria-expanded`, `aria-controls`, label via `aria-labelledby`), the selected
  option has `aria-current`; the list closes on selection, Escape or a click
  outside, and selecting or Escape returns focus to the button.
- **Names come from the server, not the URL.** `MyGradesPageController` adds
  `WardsAssembler.wardRefsForCurrentUser()` (names only, no finance call); the page
  emits them as escaped `data-*` attributes outside the `school-scope` template
  (its content lives in `<template x-if="schoolId">` and is not in the DOM when
  the script initialises) and `my-grades.js` reads them.
- **`/my-invoices` keeps the `?student=` filter:** it shows only that child's
  rows, with a notice «فقط فاکتورهای دانش‌آموز #id نمایش داده می‌شود» and a «نمایش
  همه» link. A non-numeric `student` is ignored (all rows); an id that is not one
  of the user's wards yields the empty state, because the API never returns it.
- Data is assembled by `web.model.WardsAssembler` and scoped like
  `MyInvoicesController`: only children linked to the signed-in user, no
  `TenantContext`. A new web → academic/finance dependency goes through public
  facades only.

## Compatibility And Security

No REST, permission, schema or configuration change. The section exposes only
data the signed-in guardian can already read through `/my-invoices` and the Bale
bot. Shown and hidden by selected role is display-only; the data is rendered for
any active `PARENT` membership.

## Unresolved Questions

- The finance state is a count-based summary chosen in this change; whether
  parents also want amounts waits on the monetary-unit decision.
- `/my-invoices` still labels rows «دانش‌آموز #id» and its filter notice shows the
  same id; its API returns no student name. The same server-side names could be
  added there (as `/my-grades` now does) or the page could get the same picker;
  that is a separate decision.
- The picker and the filter are display-only: the APIs still return every linked
  ward for the school, and authorization is unchanged.
- A parent who is also admin or teacher sees the section only after switching the
  role to `PARENT`.

## Acceptance Criteria

- A parent sees one card per linked child with the fields above; a parent with no
  linked child sees the invitation text; a non-parent sees no section.
- The finance state follows the precedence above and counts only that child's
  invoices.
- Section order and the `x-data` root hold (Alpine does not initialise without it).
- Each card's links carry only the student id and its school; following «نمره‌ها»
  opens `/my-grades` for that school with that child selected, and following
  «فاکتورها» opens `/my-invoices` filtered to that child (with «نمایش همه»).

## Tests

- `WardsAssemblerTest` (Mockito, no Docker) — active-parent only, linked wards
  only, invoice counts per ward, no finance call without wards, fallbacks.
- `WardRowTest` — finance state precedence and optional class/section.
- `DashboardWardsRenderTest` (JTE render, no Docker) — cards, four finance
  states, empty text, absence for a non-parent, role gating and `x-data` root,
  section order, `jalali.js` loaded, per-child links with only the id in the URL.
- `MyPagesPerStudentRenderTest` (JTE render, no Docker) — the grades picker's
  accessible toggle, list and options, close on Escape/outside click with focus
  return, hidden for a single student, only the selected student's grades and no
  leftover filter or accordion, ward names as escaped attributes placed before the
  `school-scope` template, the invoices filter and «نمایش همه». The behavior (open
  from a card, list of names, choosing, a child with no grades, the user's own
  grades, unknown ids, outside click, Escape, keyboard, focus return) was verified
  in a browser against the compiled templates; the project has no JS test setup.
- `WardsAssemblerTest` also covers `wardRefsForCurrentUser` (names only, for
  every school with an active membership of any role - matching
  `MyGradesController`, which returns wards independent of role; no finance call).
- Existing `*FlowIntegrationTest` assertions on the dashboard run in CI.
