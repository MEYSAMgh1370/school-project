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
- Each card has two links, «نمره‌ها» → `/my-grades?student=<studentId>` and
  «فاکتورها» → `/my-invoices?student=<studentId>`. Only the student id goes in
  the URL, never the child's name (children's data must not leak into history or
  logs). Before navigating, the link stores the child's school as the selected
  school (`Alpine.store('nav').rememberSchool`), so the destination loads that
  school's data. Buttons keep the normal 44px size.
- `/my-grades` and `/my-invoices` read `?student=` and show only that child's
  rows (client-side, over the list the API already returns for the school), with a
  notice «فقط نمره‌های/فاکتورهای دانش‌آموز #id نمایش داده می‌شود» and a «نمایش همه»
  link back to the unfiltered page. A non-numeric `student` is ignored (all rows);
  an id that is not one of the user's wards yields the empty state, because the
  API never returns it.
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
- The filtered pages still label rows «دانش‌آموز #id» (as before) and the notice
  shows the same id, because the grades and invoices APIs return no student name.
  Showing names needs an API change and is a separate decision.
- The filter is display-only: the APIs still return every linked ward for the
  school, and authorization is unchanged.
- A parent who is also admin or teacher sees the section only after switching the
  role to `PARENT`.

## Acceptance Criteria

- A parent sees one card per linked child with the fields above; a parent with no
  linked child sees the invitation text; a non-parent sees no section.
- The finance state follows the precedence above and counts only that child's
  invoices.
- Section order and the `x-data` root hold (Alpine does not initialise without it).
- Each card's links carry only the student id and its school; following one opens
  the page for that school filtered to that child, and «نمایش همه» restores all.

## Tests

- `WardsAssemblerTest` (Mockito, no Docker) — active-parent only, linked wards
  only, invoice counts per ward, no finance call without wards, fallbacks.
- `WardRowTest` — finance state precedence and optional class/section.
- `DashboardWardsRenderTest` (JTE render, no Docker) — cards, four finance
  states, empty text, absence for a non-parent, role gating and `x-data` root,
  section order, `jalali.js` loaded, per-child links with only the id in the URL.
- `MyPagesStudentFilterRenderTest` (JTE render, no Docker) — both pages iterate
  and empty-state on the filtered list and offer «نمایش همه». The filtering
  itself was verified in a browser (the project has no JS test setup).
- Existing `*FlowIntegrationTest` assertions on the dashboard run in CI.
