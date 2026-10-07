# Yearly-plan event reminders (یادآوری رویدادهای برنامه‌ی سالانه)

Status: proposed 2026-10-06; **implemented 2026-10-07** (migrations `V18` and `V19`, `EventReminderJob`, the reminder-roles and upcoming-events endpoints, the `/yearly-plan` additions and the dashboard box); current behavior is in `specs/current-system.md`. The assumptions below were built as written and are still open for confirmation. Product-owner answers recorded the same day (see Decisions); items marked **assumption** were not decided by the product owner and are listed under Unresolved questions.

## Current behavior (evidence)

- The yearly plan stores a school's events as `EVENT` rows in `school.calendar_entries` (`id, school_id, day, kind, title`); events are single-day with no time of day and have no reminder concept (`current-system.md`, yearly plan).
- `NotificationFacade.sendMessageToUser(userId, text)` is best-effort and returns `false` for a user with no linked Bale account or a failed send; `BaleChannel` is the only channel. In production without `BALE_BOT_TOKEN` the client only logs the message (`DEPLOYMENT.md`). There is no email/SMS/in-app channel and no opt-out (`attendance.md`: "No opt-out in this version").
- Existing notifications are event-driven (`AbsenceRecordedEvent`, `InvoiceIssuedEvent`) with no sent-log; the only scheduled job is `AcademicYearProvisioner` (daily `@Scheduled`, `@EnableScheduling` lives in its nested config). Production runs exactly one instance (`DEPLOYMENT.md`).
- There is no in-app inbox. The dashboard is a set of `<section x-data>` boxes (shortcuts, tuition, attendance, timetable, wards).
- `IamFacade.findMembershipsBySchool(schoolId)` returns active/inactive memberships with `roleCode`; per-school settings live as columns/tables owned by the `school` module and are changed through `YEARLY_PLAN_MANAGE`-style admin endpoints.

## Decisions (product owner, 2026-10-06)

1. **Recipients: the school admin picks the roles** that get reminders.
2. **Lead time is per event**, chosen by the admin when adding the event.
3. **Delivery: a Bale message plus a dashboard box** («رویدادهای نزدیک») so users without a linked Bale account still see what is coming.

## Proposed behavior

- **Per-event lead time**: `school.calendar_entries.remind_days_before smallint null` (migration `V18`). `null` = no reminder. Only `EVENT` rows carry it. **Assumption:** the value is whole days, `0..30` (`0` = on the day itself); anything else is `400 invalid-calendar-entry`. The add-event form gets an optional «یادآوری» choice; `POST /api/admin/schools/{id}/yearly-plan` accepts `remindDaysBefore`; every plan item reports it. Platform holidays never remind (they are not events).
- **Reminder roles per school**: new table `school.reminder_roles(school_id, role_code)` (`V18`). **No rows = every active member** of the school receives the reminder (the default until an admin chooses); with rows, only active members holding one of those role codes. An empty selection is rejected (an admin who wants no reminders simply sets no lead time on events). Role codes are the school's roles (system roles and the school's custom roles); the code is validated against the school's roles. `GET`/`PUT /api/admin/schools/{id}/yearly-plan/reminder-roles` need `YEARLY_PLAN_MANAGE`.
- **When an event is "due"**: an event is due for a school on its local date `today` when `day - remindDaysBefore <= today <= day`. Dates are the school's own (the school's `timezone`).
- **Scheduled job** (module `notification`, runs hourly): for each school whose local hour is at or after the send hour (**assumption:** 08:00, configurable with `roshdeandishe.reminders.send-hour` / `REMINDER_SEND_HOUR`), take its due events and message each recipient once. The hourly catch-up means an event added late or a restart after 08:00 still sends the same day. Each message states the event title, how soon (today / tomorrow / in N days), the Jalali date and the school name (a user may belong to several schools).
- **No duplicates**: a ledger `bale.event_reminders_sent(calendar_entry_id, user_id, sent_at)` with primary key `(calendar_entry_id, user_id)` (`V19`) is written **before** sending (`insert … on conflict do nothing`; send only when the row was inserted). **Revised 2026-10-07 after review:** a recipient is tried once per event when the outcome is final (`SENT`, `NOT_LINKED`, or `UNDELIVERABLE` such as an unconfigured bot token or a Bale 4xx), but a *temporary* failure (Bale 5xx or 429, a network error, a timeout) releases the ledger claim so the next hourly run retries while the event is due; three temporary failures in a row stop the run for that hour. This replaces the earlier assumption that every recipient is attempted once even when the send fails, which would have lost a whole day's reminders to a short Bale outage. A user is messaged once per event even if the event's window spans several days; editing is not supported (events are added or deleted), so a changed lead time means deleting and re-adding the event.
- **Dashboard box «رویدادهای نزدیک»**: `GET /api/me/schools/{id}/upcoming-events` returns the school's due events (`{id, day, title, daysLeft}`) to an active member who is a reminder recipient (same role rule as above); the dashboard section lists them (hidden when empty or when no school is selected). Calendar days only, no time of day.
- **Not in scope**: opt-out (consistent with the other notifications), an inbox/bell, email/SMS/push, per-user lead time, editing an event, reminders for platform holidays, and a delivery report to the admin (unlinked users are skipped silently).

## Acceptance criteria

- An event added with `remindDaysBefore = 3` for a date 3 days away is reminded to every active member of a school with no role selection, once, at the next hourly run at or after the send hour; running the job again sends nothing more; a restart does not resend.
- With roles `PARENT` and `STUDENT` selected, a teacher gets no message and no dashboard box; a student and a parent do.
- An event with no lead time never sends and never shows in the box; a past event never sends; `remindDaysBefore` outside `0..30` is `400 invalid-calendar-entry`; a non-admin gets `403 permission-denied` on the reminder-roles endpoints and a role code that is not one of the school's roles is `400`.
- A user without a linked Bale account sees the event in the dashboard box and gets no message; one failing send does not stop the others; the job never throws out of a run.
- School A's events and settings never reach school B's members; the job restores and clears the tenant context per school.
- Display is Jalali with Persian digits; the wire is ISO.

## Tests

Facade unit tests (lead-time bounds, due window edges, role selection rules, EVENT-only); job unit tests with fakes (send hour and school timezone, catch-up, ledger idempotency, one failure isolated, role filter, per-school isolation); controller and page render tests; JS tests for new pure logic; a flow test (Testcontainers) for the HTTP and permission behavior; `ModularityTests` (the `notification` → `school`/`iam` facade dependencies).

## Documentation to update on implementation

`specs/current-system.md` (yearly plan, notifications, dashboard), `project.md` index, `attendance.md` is unchanged; `DEPLOYMENT.md` (`REMINDER_SEND_HOUR` and the Bale token note).

## Unresolved questions

1. **Assumption — lead-time bounds** (`0..30` whole days). Confirm or give a different range or fixed choices.
2. **Assumption — send hour** 08:00 in the school's timezone, configurable globally rather than per school.
3. **Assumption — default recipients** are all active members until the admin picks roles.
4. ~~**Assumption — one attempt per recipient and event**~~ **Revised:** final outcomes are tried once, temporary Bale failures are retried (see Proposed behavior). An unlinked user is still not retried when they link Bale later.
5. Should the dashboard box also show events with no lead time (a plain «next events» list)? This proposal shows only events whose reminder window has started.
6. Bale volume and rate limits are undocumented; a large school sends one synchronous call per recipient in the job thread.
