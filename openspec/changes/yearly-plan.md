# Yearly plan (برنامه‌ی سالانه)

Status: proposed 2026-10-06; **phases A and B implemented 2026-10-06** (year date range, the per-school extend-only range `V16`, yearly-plan API, `YEARLY_PLAN_MANAGE`, the `/yearly-plan` page, platform-holiday storage `V15` and its effect on `isClosedDay`); current behavior is in `specs/current-system.md`. **Phase C (the platform-admin path: `V17`, the env-var provisioner, the platform permission branch, `/api/platform/**`, `/platform/holidays`) implemented 2026-10-06 on its own branch/PR**; current behavior is in `specs/current-system.md`.

## Current behavior (evidence)

- `school.calendar_entries` (`V12__school_calendar.sql`) already allows `kind IN ('CLOSED','EVENT')`; `EVENT` is "reserved for the yearly plan" (`SchoolFacade.CalendarKind`, `attendance.md` §School calendar).
- `SchoolFacade.addCalendarEntry/listCalendar/removeCalendarEntry` accept any kind and apply no date-range check; only `isClosedDay` filters on `CLOSED`.
- `CalendarController` gates admin writes by `CALENDAR_MANAGE`; `GET /api/me/schools/{id}/calendar` is open to any active member and returns all kinds.
- `/calendar` (`calendar.jte`, `school-calendar.js`) is month-based, admin-only, and filters to `CLOSED`.
- `AcademicYearInfo` is `(id, code, startYear)`; no dates are exposed. `school.academic_years` is global (not per school). Years are created by `AcademicYearProvisioner`; the *switch* to a new year is the configured `start-month/start-day` (default 1 Mordad).
- Permission seeding is idempotent per code on every startup (`IamSeedDataRunner`): a new permission needs no migration. Roles are not (`seedRoles` returns early when roles exist).
- There is **no platform-admin actor**: `PLATFORM_ADMIN` is a seeded role with no grants, rejected at the membership boundary (`IamFacadeImpl` ~290), and `PermissionDecisionService` denies every non-school context. Memberships require a `schoolId`. `TenantResolverFilter` requires `X-School-ID` outside an allowlist, and the header is not bound to the URL id. There is no first-privileged-user provisioning in prod (the demo admin is seeded only under `!prod`). `SchoolFacade.listSchools()` is documented "for platform admin panel" and is unused.

## Decisions (product owner, 2026-10-06)

1. Content: dated events on the calendar (`EVENT`), single day each. No ranges, no terms.
2. Visibility: every active member of the school reads the plan; admins edit.
3. Per-school edit permission: a new `YEARLY_PLAN_MANAGE`, granted to `SCHOOL_ADMIN` by default.
4. Scope: the page shows one academic year at a time, defaulting to the school's current year. Events keep plain dates; no year foreign key.
5. **Year range: from 1 Mehr of `startYear` to the end of Khordad (31 Khordad) of `startYear + 1`.**
6. **Platform-wide holidays**: a platform administrator adds holidays per academic year; they appear in every school's plan and **close the day** for attendance in every school (`isClosedDay` true; schools cannot override).
7. **A minimal platform-admin path is built now** (not deferred, not seed-only).

8. **Provisioning** (answers question 1): `PLATFORM_ADMIN_EMAILS` (environment variable) lists emails; at startup a listed email matching an existing, activated user is added to `iam.platform_admins`. Unknown emails are ignored; no register/invite/seed path creates one.
9. **Delivery** (answers question 3): Phases A and B ship first; Phase C ships as a separate PR reviewed for security on its own. Until C lands, platform holidays can be loaded only by the facade/tests.

10. **Per-school range** (2026-10-06): a school may change its own plan range **per academic year**. The default 1 Mehr - 31 Khordad is the minimum: a school can only **extend** it (start on or before the default start, end on or after the default end), never narrow it, so no existing event can fall outside. Anyone holding `YEARLY_PLAN_MANAGE` may set it. Setting the default dates again clears the override. Event validation and the plan listing use the school's effective range; platform holidays stay validated against the default range. The extension is capped at **one Jalali month on each side** (product owner, 2026-10-06): the start may not be before 1 Shahrivar and the end not after 31 Tir of the following year, which also keeps consecutive years' ranges from overlapping.

## Phases

### Phase A - per-school yearly plan

- Year range per decision 5, exposed as ISO `startDate`/`endDate` on the academic-year info and responses. Needs a server-side Jalali-to-Gregorian conversion (`SolarCalendar` has only the reverse), tested against known dates, including Esfand 29/30 and a leap year.
- Consequence to note: the year *switch* (1 Mordad) and the plan range (1 Mehr - 31 Khordad) differ, so 1 Tir - 31 Shahrivar-ish dates fall in no plan range (a summer window); events there are rejected. Confirm this is intended.
- New `YearlyPlanController` in `school`:
  - `GET /api/me/schools/{id}/yearly-plan?academicYearId=` (active member; defaults to the current year; `EVENT` entries within the year's range, plus platform holidays from Phase B, marked by `source`).
  - `POST /api/admin/schools/{id}/yearly-plan`, `DELETE .../{entryId}`: `@RequiresPermission("YEARLY_PLAN_MANAGE")`, `TenantContext.requireMatches`; kind fixed to `EVENT`; a date outside the selected year is `InvalidCalendarEntry`; delete refuses non-`EVENT`.
  - `CALENDAR_MANAGE` endpoints unchanged (they can still create `EVENT`; reported, not altered).
- Page `/yearly-plan` (JTE + Alpine + Bulma): events grouped by Jalali month, year selector, teaching empty state, primary action «افزودن رویداد» at the header's inline-end, add modal with Jalali input, delete confirm modal. Write controls stay visible and 403 into the shared notice (UX_ROADMAP §3). One `NavCatalog` link for all roles plus an admin dashboard shortcut; `NavCatalogTest` and the `school` group's `visibleWhen` change accordingly. Add the path to the `TenantResolverFilter` allowlist. The `design-page` Step 1-7 spec goes in the PR description.

### Phase B - platform holidays (read side and effect)

- New table `school.platform_holidays (id, academic_year_id references school.academic_years, day, title ≤ 128, created_at)` via a new Flyway migration; `day` must lie in the year's range.
- `SchoolFacade` gains platform-holiday add/remove/list methods for the platform path (Phase C) and merges them into the yearly-plan read.
- `isClosedDay(school, day)` is true when the school has a `CLOSED` entry **or** a platform holiday covers `day`. This changes attendance behavior: rolls cannot be taken on platform holidays; existing recorded rolls are untouched. Covered by an attendance test.

### Phase C - minimal platform-admin path (security-sensitive)

Status: implemented 2026-10-06. Design:

- **Model**: `iam.platform_admins (user_id primary key references iam.users, created_at)` (migration `V17`). It is **not** a school membership and **not** the reserved `PLATFORM_ADMIN` role: that role code, its `RoleNotFound` guard at the membership boundary and its absence from every role listing stay exactly as they are, so no legacy `PLATFORM_ADMIN` membership row can ever grant anything. (Whether such rows exist in a deployed DB is therefore irrelevant to this path.)
- **Provisioning (out of band only)**: `roshdeandishe.platform-admin.emails` (environment variable `PLATFORM_ADMIN_EMAILS`, comma separated, default empty). A startup runner makes `platform_admins` equal to the listed emails that match an **existing, ACTIVE, activated** user (non-null password hash). It never creates a user, never runs from register/invite/OTP/seed code and never logs an email. The variable is the single source of truth: an email removed from it (or a user later suspended) loses the row at the next start. An unset or empty variable therefore means no platform admins. **Operator procedure and risk** (recorded in `DEPLOYMENT.md`): self-registration is open and unverified, so the operator must register their own account first and only then list its email; listing an email nobody has registered yet lets whoever registers it first become platform admin.
- **Permission path**: `PermissionDecisionService` gains a `PlatformContext` branch that grants a fixed platform permission set, initially only `PLATFORM_HOLIDAY_MANAGE`, to a user in `platform_admins`, and denies everything else. The branch never applies to `SchoolContext`: platform-admin status never satisfies a school-scoped permission, and no school role ever holds a platform permission (the `PLATFORM_HOLIDAY_MANAGE` catalog row is granted to no role). `IamFacade.isPlatformAdmin(userId)` exposes the state for the navbar.
- **Tenant handling**: `/api/platform/**` and `/platform/**` are outside the `X-School-ID` requirement and a school header sent to them never turns the request into a school context for a school permission.
- **API** (in `school`, `@RequiresPermission("PLATFORM_HOLIDAY_MANAGE")`): `GET /api/platform/academic-years` (all years with default dates), `GET /api/platform/academic-years/{yearId}/holidays`, `POST /api/platform/academic-years/{yearId}/holidays` `{day, title}`, `DELETE /api/platform/holidays/{id}`; the facade methods from phase B do the validation (day within the year's default range).
- **Page** `/platform/holidays` («تعطیلی‌های سراسری»): year selector, list, add/confirm-delete modals, same conventions as `/yearly-plan`; reachable by a user with zero school memberships through a platform link the navbar shows only when `isPlatformAdmin` is true (display only; the API is the enforcement).

## Acceptance criteria

- A member lists the current year's plan; a non-member gets 4xx; another school's events are never visible or deletable.
- A `YEARLY_PLAN_MANAGE` holder adds/removes events; a student gets `403 permission-denied`. Out-of-range day, blank or >128-char title, null day: `400 invalid-calendar-entry`.
- Events never close a day and never appear on the closed-days page.
- A platform holiday appears in every school's plan and makes `isClosedDay` true in every school.
- A platform admin adds and removes holidays; every other user, including a `SCHOOL_ADMIN`, gets `403`; a platform admin holding no school role gets nothing on school-scoped endpoints; a school-scoped permission is never satisfied by platform admin status, nor a platform permission by a school role.
- No endpoint, seed or registration path can create a platform admin except the chosen provisioning mechanism, and none runs under the demo seed.
- Dates display in Jalali with Persian digits; the wire is ISO only; the week start follows the school setting.

## Tests

`SolarCalendar` round-trip; range validation facade test (`SchoolCalendarFacadeTest` style); `YearlyPlanFlowIntegrationTest`; platform-holiday flow test including attendance closure and school isolation; a platform-admin authorization flow test with the negative cases above; `NavCatalogTest`, `NavPermissionsFlowIntegrationTest`, `page-scripts.test.js`, page render tests; `ModularityTests`.

## Documentation to update on implementation

`specs/current-system.md` (calendar paragraph, pages, and the "no platform-admin actor" statement), `platform-admin-not-assignable.md` (a platform-scoped path now exists), `attendance.md` (the "future yearly plan" lines), `project.md` index entry.

## Unresolved questions

1. ~~How is the first platform admin provisioned in production~~ **Resolved:** env var of emails, applied at startup with the safeguards in Phase C; originally: how is the first platform admin provisioned in production (for example an environment variable listing emails, applied at startup to an existing activated user; or a one-time CLI/runner)? Self-registration is open and unverified, so a bootstrap rule that trusts an email string alone is a privilege-escalation risk.
2. ~~Do existing databases contain `PLATFORM_ADMIN` membership rows?~~ **Moot:** the new path does not read memberships or the reserved role. Originally: do existing databases contain `PLATFORM_ADMIN` membership rows? Not verified; a check or cleanup may be needed.
3. ~~Should Phase C ship in the same PR as A and B~~ **Resolved:** separate PR (branch `claude/platform-admin-holidays`). Originally: should Phase C ship in the same PR as A and B, or as a separate PR reviewed for security on its own?
4. Should `CALENDAR_MANAGE` stop accepting `EVENT`, so each kind has one owner?
5. ~~Confirm the summer-window consequence in Phase A.~~ **Resolved 2026-10-06:** the product owner kept 1 Mehr - 31 Khordad although pre-start activities begin on 1 Mordad. The new year is current from 1 Mordad, so its plan can be written then, but an event dated before 1 Mehr is rejected (`400 invalid-calendar-entry`).
6. National holidays are not pre-loaded; the platform admin enters them.
7. ~~Should an extended range be capped~~ **Resolved:** one Jalali month on each side (decision 10). Originally: should an extended range be capped (for example so that two years' ranges cannot overlap, or at a maximum length)? The product owner gave only the extend-only rule.
