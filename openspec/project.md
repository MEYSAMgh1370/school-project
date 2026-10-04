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
- [`changes/finance-monetary-unit.md`](changes/finance-monetary-unit.md) - proposed finance monetary-unit contract; no implementation has occurred.
- [`changes/post-authentication-school-registration.md`](changes/post-authentication-school-registration.md) - post-authentication school-registration handoff contract; implemented 2026-09-27, current behavior is in `specs/current-system.md`.
- [`changes/package-by-type-within-modules.md`](changes/package-by-type-within-modules.md) - by-type package layout within each module (`controller/`, `dto/`, `config/`); implemented 2026-09-28, current behavior is in `conventions.md` and `architecture.md`.
- [`changes/platform-admin-not-assignable.md`](changes/platform-admin-not-assignable.md) - `PLATFORM_ADMIN` is rejected at the membership-assignment boundary; implemented 2026-09-29, current behavior is in `specs/current-system.md`.
- [`changes/landing-page-expansion.md`](changes/landing-page-expansion.md) - public landing page expansion (slider, questions, footer) and the landing-only slider exception; implemented 2026-10-04, current behavior is in `specs/current-system.md`.
- [`decisions/2026-07-20-free-tier-entitlements.md`](decisions/2026-07-20-free-tier-entitlements.md) - school creation grants free Academic and Finance entitlements.
- [`decisions/2026-07-30-flyway-schema-ownership.md`](decisions/2026-07-30-flyway-schema-ownership.md) - Flyway owns schema creation and migrations.
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
