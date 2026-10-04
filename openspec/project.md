# Roshdeandishe OpenSpec

Status: current documentation index, created from a repository audit on 2026-08-06.

These OpenSpec documents are the curated entry point for current behavior, proposed work, and documentation discipline. They do not replace the source code or tests as evidence. When documentation and implementation disagree, report the conflict and inspect the code before changing either.

## Evidence Order

1. Source code, migrations, configuration, and tests describe current behavior.
2. OpenSpec `specs/` documents describe current behavior that has been checked against the repository.
3. OpenSpec `changes/` documents describe proposed work and the rationale behind it. Check each document's `Status:` line: most are unimplemented, but an implemented proposal is retained for its rationale, and its current behavior belongs to `specs/`.
4. OpenSpec `decisions/` records capture important architectural decisions and consequences.
5. Legacy Markdown files preserve useful history and domain thinking, but may contain outdated AI-assisted statements.

## Current OpenSpec Documents

- [`architecture.md`](architecture.md) - verified architecture and deployment shape.
- [`specs/current-system.md`](specs/current-system.md) - current product/module behavior and known limitations.
- [`diagrams/iam-c4.md`](diagrams/iam-c4.md) - C4-style IAM context, container, component, and flow diagrams, with PlantUML sources.
- [`conventions.md`](conventions.md) - development, testing, documentation, and frontend conventions.
- [`changes/proposed-work.md`](changes/proposed-work.md) - planned or unresolved work that is not current behavior.
- [`changes/eu-market-readiness.md`](changes/eu-market-readiness.md) - audit of the market-specific coupling in the current build (localization, calendar, currency, messaging, data protection, commercial surface) and the work items it implies; proposed 2026-10-03, **partly implemented**: W0 (`/fonts` served), W3 (`lang`/`dir`/font from the resolved locale), W2/W2b (the `Calendar` client seam and digits) and W9 (a per-school week start) are done and their current behavior is in `specs/current-system.md`; W1 (strings), W4 (currency), W6/W7 (data protection, commercial surface) and W8 (academic year) are not implemented; W5 was already done on master.
- [`changes/finance-monetary-unit.md`](changes/finance-monetary-unit.md) - proposed finance monetary-unit contract; no implementation has occurred.
- [`changes/post-authentication-school-registration.md`](changes/post-authentication-school-registration.md) - post-authentication school-registration handoff contract; implemented 2026-09-27, current behavior is in `specs/current-system.md`.
- [`changes/package-by-type-within-modules.md`](changes/package-by-type-within-modules.md) - by-type package layout within each module (`controller/`, `dto/`, `config/`); implemented 2026-09-28, current behavior is in `conventions.md` and `architecture.md`.
- [`changes/split-dto-request-response.md`](changes/split-dto-request-response.md) - each module's `dto/` split into `dto/request/` and `dto/response/`; implemented 2026-10-04, current behavior is in `conventions.md` and `architecture.md`.
- [`changes/dashboard-parent-wards.md`](changes/dashboard-parent-wards.md) - «فرزندان من» section on the parent dashboard with a count-based finance state; implemented 2026-10-02, current behavior is in `specs/current-system.md`.
- [`changes/grades-by-course.md`](changes/grades-by-course.md) - grades broken down per course (درس) from a global per-grade-level catalog; proposed 2026-10-02; backend and UI implemented, catalog read-only (phase 1).
- [`changes/academic-year.md`](changes/academic-year.md) - platform-wide academic year, the school's current year, and the year-scoped data it implies (courses, tuition, classes/enrollments, promotion); proposed 2026-10-02; **phase 0 implemented** (years created by the application from a Solar date in config, the school's current year, the `/academic-year` page), **phases 0-3 implemented** (phase 1 2026-10-03: grade-year validation, course validity range, who-sees-which-year, the current year in the navbar and dashboard; phase 2 2026-10-03: tuition and invoices per year, per-year invoice issuing, attribution of existing data; phase 3 2026-10-04: classes and enrollments per year, attribution of existing classes/students, promotion with graduation, leaving, pending placement, undo and an automatic mode, the `/promotion` page), with the product owner's 2026-10-03 answers recorded (automatic promotion at the year switch, new classes from scratch, invoices by a separate action, past years hidden from non-admins) promoted students are recorded with a pending class that the admin fills in, an automatic run can be undone, graduates are marked in the automatic run, a leaver is removed from the next year's list with data kept, identity data across schools was dropped, and one deferred question (a school may offer several levels, but the model has exactly one level per school); foundation for the class timetable.
- [`changes/class-timetable.md`](changes/class-timetable.md) - school class timetable «برنامهٔ کلاسی» (per academic year, periods defined by the school, activities not in the report card, new `TIMETABLE_MANAGE` permission, dashboard section for students); proposed and **implemented 2026-10-02** (V7 migration, `TimetableFacade`, `/timetable`, `/my-timetable`, student dashboard section); one question open (platform-default activity names, list ships empty).
- [`changes/self-service-gate-nav-permissions-bad-requests.md`](changes/self-service-gate-nav-permissions-bad-requests.md) - `/api/me/**` returns nothing to a user with no active membership in the school (made per-role by `custom-role-ui-and-timetable-gate.md`); sidebar links also appear for custom roles holding the page's management permission; Spring MVC client errors answer `4xx` instead of `500`; proposed and implemented 2026-10-03, current behavior is in `specs/current-system.md`.
- [`changes/notification-channel-abstraction.md`](changes/notification-channel-abstraction.md) - `NotificationChannel` abstraction inside the `notification` module, Bale as the first channel; implemented 2026-10-02, current behavior is in `architecture.md`.
- [`changes/rename-bale-module-to-notification.md`](changes/rename-bale-module-to-notification.md) - the `bale` module becomes `notification` (internal rename; external Bale contracts unchanged); implemented 2026-10-02, current behavior is in `architecture.md`.
- [`changes/custom-role-ui-and-timetable-gate.md`](changes/custom-role-ui-and-timetable-gate.md) - custom role titles in the role switcher, shortcut cards for custom roles by permission, and every `/api/me/**` endpoint requiring the active role that fits it (the timetable's rule); proposed and implemented 2026-10-03, current behavior is in `specs/current-system.md`.
- [`changes/exam-token-membership-check.md`](changes/exam-token-membership-check.md) - `/api/exam-take/**` also requires the token's student to still have an active `STUDENT` membership in the exam's school (otherwise `401 invalid-exam-token`); proposed and implemented 2026-10-03, current behavior is in `specs/current-system.md`.
- [`changes/navigation-sidebar-and-dashboard-shortcuts.md`](changes/navigation-sidebar-and-dashboard-shortcuts.md) - grouped sidebar and role-gated dashboard shortcuts rendered from one `NavCatalog`; implemented 2026-10-02, current behavior is in `specs/current-system.md`.
- [`changes/platform-admin-not-assignable.md`](changes/platform-admin-not-assignable.md) - `PLATFORM_ADMIN` is rejected at the membership-assignment boundary; implemented 2026-09-29, current behavior is in `specs/current-system.md`.
- [`changes/production-memory-footprint.md`](changes/production-memory-footprint.md) - production memory-footprint reduction, with the measurements behind it; Tier 1 implemented 2026-09-29, current behavior is in `architecture.md`. Tier 2 was implemented and reverted the same day; the proposal records why.
- [`decisions/2026-07-20-free-tier-entitlements.md`](decisions/2026-07-20-free-tier-entitlements.md) - school creation grants free Academic and Finance entitlements.
- [`decisions/2026-07-30-flyway-schema-ownership.md`](decisions/2026-07-30-flyway-schema-ownership.md) - Flyway owns schema creation and migrations.
- [`decisions/2026-10-03-multi-market-not-migration.md`](decisions/2026-10-03-multi-market-not-migration.md) - Iranian schools remain a segment, so market-specific coupling becomes a per-tenant configurable dimension rather than something removed.
- [`archive/documentation-audit-2026-08-06.md`](archive/documentation-audit-2026-08-06.md) - inventory, conflicts, and classification from the audit that introduced OpenSpec.

## Legacy Documentation Classification

| File | Classification | Use |
|---|---|---|
| [`../README.md`](../README.md) | Development guidance | Quick start and deployment summary. |
| [`../CLAUDE.md`](../CLAUDE.md) | Claude-specific entry instructions | Short pointer to OpenSpec, shared skills, and Claude orchestration. |
| [`../CLAUDE_LEGACY.md`](../CLAUDE_LEGACY.md) | Historical/legacy architecture notebook | Detailed context; verify against code and OpenSpec before relying on it. |
| [`../BEST_PRACTICES.md`](../BEST_PRACTICES.md) | Compatibility redirect | Points to OpenSpec conventions and agent skills. |
| [`../DESIGN.md`](../DESIGN.md) | Development guidance | JTE web design system and visual decisions. |
| [`../DEPLOYMENT.md`](../DEPLOYMENT.md) | Development guidance | Production deployment runbook. |
| [`../FRONTEND.md`](../FRONTEND.md) | Development guidance | Frontend onboarding, page inventory, and API usage notes. |
| [`../HISTORY.md`](../HISTORY.md) | Historical/obsolete for current-state claims | Chronological build log; do not use as current spec. |
| [`../MOBILE_ROADMAP.md`](../MOBILE_ROADMAP.md) | Proposed change | Mobile, parent communication, and native-app roadmap. |
| [`../MOSHAVERE_PORT_SPEC.md`](../MOSHAVERE_PORT_SPEC.md) | Historical proposal | Porting proposal for work that is largely complete; remaining gaps are future work. |
| [`../UX_ROADMAP.md`](../UX_ROADMAP.md) | Historical roadmap + development guidance | UI buildout history and shared UX conventions. |
| [`../android/README.md`](../android/README.md) | Current specification | Native Android v1 scope and local setup. |
| [`../.agents/skills/`](../.agents/skills/) | Shared agent workflows | Agent-neutral repository skills used by Codex and Claude. |
| [`../.codex/skills/`](../.codex/skills/) | Codex-specific workflows | Codex-only repository skills; not product authority. |
| [`../.claude/`](../.claude/) | Claude-specific workflows | Claude agents, settings, and orchestration; not product authority. |

Ignored or untracked Markdown snapshots under `.claude/worktrees/` are historical worktree artifacts. Do not edit or treat them as authoritative.

## Change Workflow

Create or update a document under `changes/` before making substantial behavioral changes. The proposal should state:

- current behavior, with code/test evidence;
- proposed behavior;
- acceptance criteria;
- migration or compatibility concerns;
- tests that will prove the change.

When a change is implemented, update the relevant current specification and decision record in the same change. Keep future ideas out of current specs.
