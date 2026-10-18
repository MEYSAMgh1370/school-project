# School settings page and finance overview (`/settings`)

Status: proposed and implemented 2026-10-18 (migration `V33`; current behavior is in `specs/current-system.md`). The design is `docs/superpowers/specs/2026-10-17-school-settings-design.md`; the build log is under `.superpowers/sdd/2026-10-17-school-settings/`.

## Intent (what the product owner asked for)

- One page where a school admin sets the school's **general, set-once settings**.
- The **school year is chosen by the admin on that page**; no other page that follows the school's current year (`/finance`, `/classes`, `/grades`) lets the user pick a year. `/timetable`, `/yearly-plan`, `/promotion` and the platform-holidays page keep their own year select (see "Rulings and deviations").
- Finance (`/finance`) shows **every year's invoices together**, with a scope filter (all / this year / previous years) and **search by student name**.
- Bale bot settings (on/off and which roles may use it) live on the same page.
- Chosen approach: a single `school.school_settings` model, not a page that only re-hosts scattered controls.

## Decisions confirmed by the product owner (2026-10-17)

1. New permission `SCHOOL_SETTINGS_MANAGE` for the bot section, granted to `SCHOOL_ADMIN`.
2. The bot is **on by default** (`bot_enabled` default true; no `bot_roles` rows = every role).
3. The old columns `school.schools.installments_enabled` (V28) and `school.schools.auto_promotion` (V11) are **kept and dual-written** (new table first, old column mirrored); a **later** migration drops them.
4. `/academic-year` **redirects** to `/settings`.
5. «سال‌های پیش» shows **debtors only**: a student with an unpaid remainder on a prior-year invoice, graduated and left students included; a student whose prior-year invoices are all settled is not listed.
6. Week start, time zone and locale stay **out** of settings (fixed at school creation).
7. Name search is first name / last name / general text over the single `iam.users.full_name` (first = first word, last = remaining words); there are no separate name columns.
8. Auto-promotion on a year change keeps its behavior (assumed, not objected to).

## What was built

- `V33__school_settings.sql`: `school.school_settings(school_id, installments_enabled, auto_promotion, bot_enabled)` and `school.bot_roles(school_id, role_code)`; existing schools are backfilled from `school.schools`. A missing settings row is created lazily by copying `installments_enabled` and `auto_promotion` from `school.schools` (`bot_enabled` true, no `bot_roles`; `SchoolSettingsRepository.createIfMissing`).
- `SchoolFacade`: `BotSettings`, `getBotSettings`, `setBotSettings`, `isBotAllowed`; the installments and auto-promotion methods read the new table and still write the old columns.
- `GET /api/admin/schools/{id}/settings` and `PUT .../settings/bot`; page `/settings` (four sections); `/academic-year` redirects.
- `GET /api/admin/schools/{id}/finance/students` (and `.../students/{studentId}/invoices`), `FINANCE_MANAGE`; `/finance` lists students instead of invoices.
- Bot gate in `BotContextLoader`; `NavCatalog` link `/settings`; the year selectors are gone from `/classes`, `/grades` and `/finance`; the recipients modal is gone from `/yearly-plan`.

## Rulings and deviations

Each is a decision taken during the build; the cost is what happens if the ruling turns out wrong.

- **Bot-setting changes are logged at INFO, not audited.** The audit table (`finance.finance_audit`) is finance-only, so `INSTALLMENTS_SETTING_CHANGED`-style auditing was not copied. Cost if wrong: no queryable history of who switched the bot off; needs a general audit table or a new action kind.
- **«راهنما», contact linking, push notifications and event reminders are not gated by the bot setting.** Only data answers, callbacks, «مدرسه‌های من» and wards are gated. Cost if wrong: a school that switched the bot off still sees help text and still receives event reminders and pushes in Bale.
- **The bot gate sits in `BotContextLoader`, per membership role.** A membership is kept only if `isBotAllowed(school, {role})`; a user left with none gets «بات برای مدرسه‌ی شما فعال نیست.». Cost if wrong: a user with a role allowed in one school and excluded in another sees only the allowed school's data (intended), but any new bot code that reads memberships outside the loader would bypass the gate.
- **`/grades` uses the current year and shows a notice when the school has none.** The grades API requires an academic year, so the page cannot omit it. Admins lose per-page past-year views on `/classes` and `/grades`; the APIs still accept other years. Cost if wrong: an admin cannot open a past year's gradebook or class list from the UI.
- **Navigation:** the `/settings` link is visible to `ADMIN` and `FINANCE_ADMIN` (and to a custom role holding any of `ACADEMIC_YEAR_MANAGE`, `FINANCE_MANAGE`, `YEARLY_PLAN_MANAGE`, `SCHOOL_SETTINGS_MANAGE`); the dashboard shortcut is `ADMIN` only. Cost if wrong: a finance admin has no dashboard shortcut to settings, and the «مدرسه» sidebar group now appears for `FINANCE_ADMIN` with one link.
- **The `/finance` students overview does per-student IAM lookups and one-by-one lookups for LEFT/GRADUATED debtors.** `IamFacade` has no batch name lookup (one `findUser` per invoiced student, before paging, because the name filter and sort need it), and `AcademicFacade.listStudents` returns only `ACTIVE`/`PENDING`, so left and graduated students with invoices are fetched with `findStudentInfo` one at a time. The whole school's invoices are aggregated in memory. Cost if wrong: slow responses for a school with thousands of invoiced students.
- **Year selectors that stay.** `/timetable` (`ttYear`, `timetable.jte:31`), `/yearly-plan` (`yearlyPlanYear`), `/promotion` (`promotionFromYear`) and the platform-holidays page (`platformHolidaysYear`) keep their year select, because each acts on a specific year by nature. The Intent sentence "no other page lets the user pick a year" applies to the pages that follow the school's current year (`/finance`, `/classes`, `/grades`). Cost if wrong: the owner may want the timetable selector removed; a small follow-up.
- **`/finance` row expansion lists every year's invoices of the student**, not only those of the active scope (the design said "the invoices of the scope"). Payments on any invoice change the row's totals, and one view of all years avoids hiding a debt. Cost if wrong: a small client-side filter by scope.
- **Unresolved question for the product owner: the debt chips ignore the scope.** `owing`/`overdue`/`settled` are evaluated on the student's overall state across all years (`FinanceFacadeImpl.matchesDebt`, `toStudentRow`), so `scope=current` with `debt=overdue` lists a student whose only overdue invoice is from a prior year. Not decided; to be confirmed.
- **Unresolved question for the product owner: the row's current-year status tag** (`currentStatus`) comes from the most recently issued current-year invoice only (`FinanceFacadeImpl.toStudentRow`; invoices arrive newest first), not an aggregate over several current-year invoices. Not decided; to be confirmed.
- **`TenantResolverFilter` lists `/settings` as a header-exempt page path** (like the other pages), because without it `GET /settings` answers 400; the API under it still requires the tenant header.

## Behaviour changes

- **`FINANCE_ADMIN` can no longer attribute year-less tuition and invoices from the UI.** The action moved from `/finance` to the settings year section, which loads from `GET /academic-year` (`ACADEMIC_YEAR_MANAGE`), so a user holding only `FINANCE_MANAGE` does not see it. The endpoint itself, `POST .../finance/attribute-year`, is still `FINANCE_MANAGE` (`FinanceController.java:298-299`). Cost if wrong: finance admins need `ACADEMIC_YEAR_MANAGE` (or the section needs a finance-only data source) to attribute year-less finance data.

## Open follow-ups

- The role list for the reminder and bot checkboxes comes from `GET .../yearly-plan/reminder-roles` (`YEARLY_PLAN_MANAGE`), so a user holding only `SCHOOL_SETTINGS_MANAGE` sees no role boxes (the bot section reports that the list failed to load and sends the stored selection unchanged). Better: serve the role list from the settings GET, or from an endpoint gated by `SCHOOL_SETTINGS_MANAGE`.
- A later migration drops `school.schools.installments_enabled` and `school.schools.auto_promotion` once the new code has run in production.
- Add a batch name lookup to `IamFacade` (and a status-aware student listing or batch lookup in `AcademicFacade`) to remove the per-student lookups in the finance overview.
- `GET`/`PUT .../yearly-plan/reminder-roles`: the 400 body for an unavailable role does not list the available roles.
- On `/grades`, a failed fetch of the current year is silent (the page behaves as if the school had no year).
- Sorting in the finance overview is by normalised code-point order, not Persian collation.
