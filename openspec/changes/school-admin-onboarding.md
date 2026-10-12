# School-Admin Onboarding (registration, school form, setup checklist)

Status: proposed 2026-10-12. Package 1 (registration and school-form copy) is
implemented 2026-10-12 and its current behavior is in
[`specs/current-system.md`](../specs/current-system.md); package 2 (dashboard
setup checklist) is not implemented.

## Verified Baseline

Walked in a browser on 2026-10-12 (desktop and mobile) and read in code.

- `/register?next=/schools/new` is titled «ثبت‌نام در رشدِاندیشه»; it does not say
  the account is the first of two steps toward creating a school.
  Evidence: [`register.jte`](../../src/main/jte/register.jte).
- `/schools/new` always shows `timezone` (`Asia/Tehran`) and `locale` (`fa-IR`)
  as free-text fields next to the week start. Only the week start says it
  cannot be changed later.
  Evidence: [`school-new.jte`](../../src/main/jte/school-new.jte),
  [`school-new.js`](../../src/main/resources/static/js/school-new.js).
- School `level` and `slug` have no edit endpoint (`SchoolFacadeImpl` only sets
  them at creation), so both are permanent; a school created without a slug has
  no branded entry address, and the dashboard says so only afterwards.
- After creating a school the admin lands on the generic dashboard titled
  «عضویت‌های من»: first shortcut is «دفتر نمرات» (no classes or students exist),
  empty tuition/attendance cards, a school-entry box that is a dead end when
  there is no slug, a phone box, and no indication of what to set up first.
  Evidence: [`dashboard.jte`](../../src/main/jte/dashboard.jte).

## Proposed Behavior

### Package 1 - registration and school form (templates and static JS only)

- When the approved post-authentication destination is `/schools/new`, the
  registration page is titled «ساخت حساب مدیر مدرسه» and says it is step 1 of 2,
  with the next step being school registration. The default registration page
  is unchanged.
- The school form shows name, level and entry address up front. `timezone`,
  `locale` and week start move into a collapsed «تنظیمات پیشرفته» section with
  the same defaults and the same submitted values.
- Level and entry address both state that they cannot be changed after the
  school is created; leaving the address empty says the school will have no
  branded entry address.
- No API, migration or permission change.

### Package 2 - setup checklist on the admin dashboard

- A «راه‌اندازی مدرسه» card at the top of the dashboard, shown to an active
  `SCHOOL_ADMIN` of a school while any step is incomplete, with a progress count.
- Steps are produced in one place (a page-model assembler) as
  `{key, label, hint, href, done}`, so later steps (full school profile) or a
  wizard can be added without touching the template. Initial candidates:
  share the entry address, invite a teacher, create a class, add a student.
  The exact steps and their "done" signals are fixed against the existing
  facades during implementation; no repository access across modules.
- While the checklist is incomplete the empty-state cards and the phone box
  move below it. When every step is done the card disappears; an optional
  «پنهان کردن» is remembered only in that browser's `localStorage`.
- A school without a slug shows an explanatory state instead of a dead end.

## Out of Scope

- A per-school branded first page with its own theme, and the full school
  profile it needs (logo, colors, details). Today `/s/{slug}` shows only the
  school name and two buttons, and no theme or profile data exists. This needs
  a data model, a migration and an editor, and is its own proposal.
- A multi-step wizard (the checklist is designed so it can follow).
- Onboarding for teachers, students, parents and users with no role.

## Acceptance Criteria

- Package 1: `/register?next=/schools/new` shows the step-1 copy and the
  default registration page does not; `/schools/new` still posts the same
  request body; advanced settings are collapsed by default and still submit
  their values; level and slug show the permanence note.
- Package 2: a freshly created school shows the checklist with no completed
  steps for its admin and not for other roles; completing a step marks it;
  completing all hides the card.

## Tests

- Extend `LoginDashboardFlowIntegrationTest` / `RegistrationFlowIntegrationTest`
  for the page copy and the unchanged form contract.
- A flow test for the checklist (package 2).
- Manual browser check on desktop and mobile widths.
