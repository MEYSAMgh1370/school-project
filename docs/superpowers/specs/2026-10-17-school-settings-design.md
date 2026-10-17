# School settings page and finance overview (`/settings`)

Status: proposed 2026-10-17, awaiting review. Nothing here is implemented.

## Intent (what the product owner asked for)

- One page where a school admin sets the school's **general, set-once settings**.
- The **school's academic year is chosen by the admin on that page**; no other page lets the user pick a year.
- Settings that are not "set once" do not belong there.
- Finance (`/finance`) stops having its own year selector. It shows every year's invoices together, with a scope filter (all / this year / previous years) and name search.
- Chosen approach: a single `school_settings` model (approach 2), not a page that only re-hosts scattered controls.

## Out of scope (decided)

- Week start, time zone and locale are **not** in settings. They stay fixed at school creation.
- Tuition amount (per year), promotion runs, calendar/events and invoice issuing keep their own pages.
- A per-school bot token / own bot. The Bale bot stays one global bot (`BALE_BOT_TOKEN`).
- Splitting `full_name` into first and last name columns (separate sub-project if wanted).

## Current state (verified in the repository)

- Current academic year per school and `auto_promotion` (`school.schools.auto_promotion`, V11), edited on `/academic-year` (`PUT .../academic-year/auto-promotion`).
- `school.schools.installments_enabled` (V28), edited on `/finance` (`PUT .../finance/settings/installments`, `FINANCE_MANAGE`).
- Reminder recipient roles in `school.reminder_roles` (V18), edited on `/yearly-plan` (`YEARLY_PLAN_MANAGE`).
- The Bale bot has no per-school setting; `BaleUpdateHandler` serves any linked user.
- `/finance` has its own year select, an "unattributed data" banner, a per-year tuition box, an installments box, and a list of invoices (one row per invoice) with search by student name and status chips.
- Users have a single `full_name` (`iam.users.full_name`); there are no separate first/last name columns.

## Design

### 1. `school.school_settings`

One row per school (primary key `school_id`). Initial columns:

| Column | Source today | Notes |
|---|---|---|
| `installments_enabled` | `schools.installments_enabled` | moved |
| `auto_promotion` | `schools.auto_promotion` | moved |
| `bot_enabled` | new | default **true**, so current behavior is unchanged |
| `bot_roles` | new | roles allowed to use the bot; empty = every role (the reminder-roles convention) |

The current academic year stays where it is stored today (it already has its own owner, `SchoolFacade`); settings only exposes it. Reminder roles stay in `school.reminder_roles` and are edited from the settings page through the existing endpoints.

Migration: create the table, copy `installments_enabled` and `auto_promotion` for every school, keep the old columns until the new code is verified (a later migration drops them). The existing endpoints keep working and read/write the new table, so older clients are not broken.

### 2. Page `/settings` («تنظیمات مدرسه»)

Sections, each with its own save button and the existing permission of the thing it edits:

1. **سال تحصیلی** (`ACADEMIC_YEAR_MANAGE`): current year, auto-promotion, and the one-time "attribute old data to a year" action moved from `/finance` and `/academic-year`.
2. **مالی** (`FINANCE_MANAGE`): installments on/off.
3. **یادآوری‌ها** (`YEARLY_PLAN_MANAGE`): reminder recipient roles.
4. **بات بله** (new `SCHOOL_SETTINGS_MANAGE`, granted to `SCHOOL_ADMIN`): on/off and allowed roles.

The page opens for a user holding at least one of these; a section without its permission is hidden. `/academic-year` redirects to `/settings`; the sidebar and dashboard shortcuts (`NavCatalog`) point to `/settings`. Follow `DESIGN.md` and the `design-page` skill (row/box patterns, RTL, empty and error states per section, `role=status` loading).

### 3. Pages that use a year

`/classes`, `/finance`, `/grades` and other year-aware pages drop their year selector and use the school's current year. Admins keep access to past years where the data requires it through the finance scope filter below.

### 4. Finance overview (`/finance`)

List rows are **students**, not invoices.

- Row: student name, current-year invoice status, **previous debt** (sum of remaining on prior-year invoices), **total remaining**.
- Scope filter: **همه** (default), **فقط امسال**, **سال‌های پیش**, with a count on each.
  - all: a student with a current-year invoice or any prior-year debt;
  - current: a student with a current-year invoice;
  - previous: a student with any prior-year invoice (paid or not), including graduated and left students (status shown); the previous-debt column counts only the unpaid remainder.
- Status chips (owing, overdue, settled) combine with the scope.
- Expanding a row lists the invoices of the scope, one per year, each with the existing payments, discounts and instalments panel (unchanged).
- Search by **first name** and **last name** (separate inputs) plus a general text box. Because only `iam.users.full_name` exists, "first name" matches the first word and "last name" the remaining words; double-barrelled names may not split exactly. The search is a list of declared fields so national id, class or guardian phone can be added later without restructuring.
- Removed from `/finance`: the year select, the "unattributed" banner and its action (moved to settings). Kept: setting the current year's tuition and "issue this year's invoices to students without one".

API: `GET /api/admin/schools/{id}/finance/students?scope=all|current|previous&firstName=&lastName=&q=&status=&page=&size=` (`FINANCE_MANAGE`), totals computed server-side, paged, resolving names in one batch per page. Existing invoice, payment, discount and instalment endpoints are unchanged.

### 5. Settings API

`GET /api/admin/schools/{id}/settings` returns all sections the caller may see; per-section `PUT` endpoints reuse existing paths where they exist and add `PUT .../settings/bot`. Invalid input answers `400 invalid-setting` (existing code). Writes are audited the way `INSTALLMENTS_SETTING_CHANGED` is today.

### 6. Bot enforcement

`BaleUpdateHandler`/`BotContext` check `bot_enabled` and `bot_roles` for the user's active role in the school before answering a data question; a disabled or excluded user gets a short Persian message. Contact linking itself is not blocked.

## Error handling

- Settings row missing for a school: created lazily with defaults.
- Changing the current year with auto-promotion on: unchanged behavior (`CurrentAcademicYearChangedEvent`, promotion after commit).
- A section save failing leaves the other sections untouched and shows the error next to that section (not in one top banner).

## Testing

- Migration copies existing values; the old endpoints still read and write the same values.
- Each section returns `403` without its permission; the page opens for any one of them.
- Finance overview: previous-debt sums, scope membership, graduated/left students with debt, paging, name search (first/last/general), combination with status chips.
- Year-aware pages follow the settings year.
- Bot: disabled and role-excluded users are refused; default behavior unchanged.
- Page render tests, as for the existing pages.

## Decisions confirmed by the product owner (2026-10-17)

1. New permission `SCHOOL_SETTINGS_MANAGE` for the bot section, granted to `SCHOOL_ADMIN`: **yes**.
2. `bot_enabled` defaults to on: **yes**.
3. Old columns `school.schools.installments_enabled` (V28) and `school.schools.auto_promotion` (V11) are dropped in a **later** migration, after the new code has run; this change only copies them. (Owner asked where they are dropped from: the `school.schools` table.)
4. `/academic-year` **redirects** to `/settings`.
5. A student whose prior-year invoices are fully paid **may be shown** under "previous years" (so the scope is "has a prior-year invoice", not only "has prior debt"); the previous-debt column still counts only the unpaid remainder.
6. Auto-promotion on year change keeps its current behavior (assumed, not objected to).

## Open questions

- `openspec/changes/` record and `specs/current-system.md` update are required with the implementation (per `CLAUDE.md`).
