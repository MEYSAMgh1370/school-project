# Yearly plan (برنامه‌ی سالانه)

Status: proposed 2026-10-06; not implemented. Product-owner answers recorded the same day (see Decisions). One assumption below needs confirmation before implementation.

## Current behavior (evidence)

- `school.calendar_entries` (`V12__school_calendar.sql`) already allows `kind IN ('CLOSED','EVENT')`; `EVENT` is "reserved for the yearly plan" (`SchoolFacade.CalendarKind`, `attendance.md` §School calendar).
- `SchoolFacade.addCalendarEntry/listCalendar/removeCalendarEntry` accept any kind and apply no date-range check; only `isClosedDay` filters on `CLOSED`.
- `CalendarController` gates admin writes by `CALENDAR_MANAGE`; `GET /api/me/schools/{id}/calendar` is open to any active member and returns all kinds.
- `/calendar` (`calendar.jte`, `school-calendar.js`) is month-based, admin-only, and filters to `CLOSED`.
- `AcademicYearInfo` is `(id, code, startYear)`; **no start/end dates are exposed**. Years are created by `AcademicYearProvisioner` from `roshdeandishe.academic-year.start-month/start-day` (default 1 Mordad).
- Permission seeding is idempotent per code on every startup (`IamSeedDataRunner`), so a new permission needs no migration or backfill.

## Decisions (product owner, 2026-10-06)

1. Content: dated events on the calendar (`EVENT`), single day each. No ranges, no terms.
2. Visibility: every active member of the school reads the plan; admins edit.
3. Permission: a new `YEARLY_PLAN_MANAGE`, granted to `SCHOOL_ADMIN` by default.
4. Scope: the page shows one academic year at a time, defaulting to the school's current year. Events keep plain dates; no year foreign key.

## Proposed behavior

- **Year date range** (assumption, unconfirmed): an academic year `startYear` runs from Jalali `startYear/startMonth/startDay` (the configured values) to the day before the same date in `startYear+1`. The school module exposes `startDate`/`endDate` (ISO) on the year info and on both academic-year responses. Needs a server-side Jalali-to-Gregorian conversion (`SolarCalendar` has only the reverse), tested against known dates, including Esfand 29/30.
- **API** (new `YearlyPlanController` in `school`):
  - `GET /api/me/schools/{id}/yearly-plan?academicYearId=` (active member; defaults to the current year; returns `EVENT` entries within the year's range).
  - `POST /api/admin/schools/{id}/yearly-plan` and `DELETE .../{entryId}`, `@RequiresPermission("YEARLY_PLAN_MANAGE")`, `TenantContext.requireMatches`. The kind is fixed to `EVENT`; a date outside the selected year is `InvalidCalendarEntry`. Delete refuses non-`EVENT` entries.
  - The existing `CALENDAR_MANAGE` endpoints are unchanged (they can still create `EVENT`; reported, not altered).
- **Page** `/yearly-plan`: a year-at-a-glance list grouped by Jalali month, year selector, empty state that teaches, add-event modal (Jalali date input), delete with confirm modal. Primary action «افزودن رویداد» at the header's inline-end. Read-only users see the same page; write controls stay visible and 403 into the shared notice (UX_ROADMAP §3). Per `design-page`, the Step 1-7 spec goes in the PR description.
- **Navigation**: one `NavCatalog` link for all roles, with a dashboard shortcut for admin; the `school` group's `visibleWhen` and `NavCatalogTest` change accordingly. Add the path to the `TenantResolverFilter` page allowlist.

## Acceptance criteria

- A member of the school lists events of the current year; a non-member gets 4xx.
- A holder of `YEARLY_PLAN_MANAGE` adds and removes events; a student gets `403 permission-denied` on the admin endpoints.
- An event outside the year's range, with a blank or >128-character title, or with a null day is rejected with `invalid-calendar-entry`.
- Events never close a day (`isClosedDay` stays false) and never appear on the closed-days page.
- Another school's events are never visible or deletable.
- Dates display in Jalali with Persian digits; the wire carries ISO only; the week starts per the school's setting.

## Tests

`SolarCalendar` round-trip unit test; `SchoolCalendarFacadeTest`-style facade test for range validation; `YearlyPlanFlowIntegrationTest` (Testcontainers) covering the criteria above; `NavCatalogTest`, `NavPermissionsFlowIntegrationTest`, `page-scripts.test.js`, a render test for the page; `ModularityTests`.

## Documentation to update on implementation

`specs/current-system.md` (calendar paragraph and pages), `attendance.md` (the "future yearly plan" lines), `project.md` index entry.

## Unresolved questions

- Confirm the year range rule above (or supply explicit start/end per year).
- Should `CALENDAR_MANAGE` stop accepting `EVENT`, so each kind has one owner?
- National holidays are not shipped (the admin enters them), as in the calendar proposal.
