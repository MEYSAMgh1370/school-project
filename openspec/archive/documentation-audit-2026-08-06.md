# Documentation Audit - 2026-08-06

This audit was created before introducing OpenSpec documentation. It records how existing Markdown mapped to implementation at the time of review. Shared skills later moved from `.claude/skills/` to `.agents/skills/`, and the legacy architecture notebook moved from `CLAUDE.md` to `CLAUDE_LEGACY.md`; rows below use current paths where practical.

## Inventory

| Path | Apparent purpose | Behavior type | Implementation support | Overlap | Outdated |
|---|---|---|---|---|---|
| `README.md` | Overview, quick start, deployment env summary | Current + onboarding | Mostly | `CLAUDE_LEGACY.md`, `DEPLOYMENT.md` | Partly |
| `CLAUDE.md` | Claude-specific entry instructions | Agent guidance | N/A | `AGENTS.md`, `.claude/skills/dev-loop/SKILL.md` | No major issue |
| `CLAUDE_LEGACY.md` | Legacy architecture/product/domain notebook and AI guidance | Current + proposed + historical mixed | Partly | Most docs | Yes |
| `BEST_PRACTICES.md` | Backend/module/development conventions | Compatibility redirect after later migration | Mostly | `openspec/conventions.md`, agent skills | Partly |
| `DESIGN.md` | Visual design tokens and UI review notes | Current design guidance + open decisions | Mostly | `FRONTEND.md`, `UX_ROADMAP.md` | Minor |
| `DEPLOYMENT.md` | Liara/Docker/Postgres production guide | Current operational guidance | Mostly | `README.md`, `CLAUDE_LEGACY.md` | Minor |
| `FRONTEND.md` | JTE/Alpine/Bulma frontend architecture and page map | Current spec + guidance | Mostly | `DESIGN.md`, `UX_ROADMAP.md`, `CLAUDE_LEGACY.md` | Partly |
| `HISTORY.md` | Chronological implementation history and roadmap appendix | Historical record | Time-stamped | `CLAUDE_LEGACY.md`, roadmap docs | Yes if read as current |
| `MOBILE_ROADMAP.md` | Native Android/mobile roadmap | Proposed change + stale current-state notes | Partly | `android/README.md`, `CLAUDE_LEGACY.md` | Yes |
| `MOSHAVERE_PORT_SPEC.md` | Porting proposal for OTP, curriculum, study plans, and exams | Historical proposal with completed work | Mostly complete | `HISTORY.md`, `CLAUDE_LEGACY.md` | Partly |
| `UX_ROADMAP.md` | UI completion roadmap and UX conventions | Historical roadmap + guidance | Partly | `FRONTEND.md`, `DESIGN.md` | Yes at top |
| `android/README.md` | Android app setup and scope | Current Android guidance | Yes | `MOBILE_ROADMAP.md` | No major issue |
| `.claude/agents/code-explorer.md` | Claude code exploration agent | Agent guidance | N/A | `AGENTS.md` | No major issue |
| `.claude/agents/task-implementer.md` | Claude implementation agent | Agent guidance | Mostly | `openspec/conventions.md` | Minor |
| `.claude/agents/ui-implementer.md` | Claude UI implementation agent | Agent guidance | Mostly | `DESIGN.md`, `FRONTEND.md` | Minor |
| `.agents/skills/design-page/SKILL.md` | Shared page-design workflow | Agent guidance | Mostly | `DESIGN.md`, `UX_ROADMAP.md` | Minor |
| `.claude/skills/dev-loop/SKILL.md` | Claude backend development loop | Agent guidance | Mostly | `openspec/conventions.md` | No major issue |
| `.agents/skills/new-module/SKILL.md` | Shared new-module scaffold workflow | Agent guidance | Partly | `openspec/conventions.md` | Yes |

Untracked or ignored Markdown files under `.claude/worktrees/` appear to be historical worktree snapshots. They overlap tracked docs and were not edited.

## Conflicts

| Document | Documented claim | Code evidence | Classification | Resolution |
|---|---|---|---|---|
| `README.md` | `CLAUDE.md` is the full authoritative architecture/domain source of truth. | The legacy notebook contains stale current-state statements. | Contradicted / unsafe | Point README to OpenSpec as the curated index. |
| `CLAUDE_LEGACY.md` | This file was once treated as the source of truth for all architecture, domain, and product decisions. | Current behavior is better evidenced by source/tests and OpenSpec curation. | Contradicted / unsafe | Add legacy-status banner and OpenSpec precedence. |
| `CLAUDE_LEGACY.md` | Frontend has ten page templates and a limited JS inventory. | `src/main/jte` and static JS contain many more pages/components. | Outdated | Replace with current summary and point to `FRONTEND.md`. |
| `CLAUDE_LEGACY.md` | `app.css` is the one global stylesheet so far. | `my-study-plan.css` exists. | Outdated | Clarify shared vs page-specific CSS. |
| `CLAUDE_LEGACY.md` | Login/register/activate all keep inline components. | Login now uses `login.js`; register/activate still inline. | Partially implemented | Correct the claim. |
| `BEST_PRACTICES.md` | Tests are thin and only two tests exist. | Many `*FlowIntegrationTest` files exist. | Contradicted | Update testing guidance. |
| `.agents/skills/new-module/SKILL.md` | Schemas must be created manually because `ddl-auto` only creates tables. | Flyway creates listed schemas; Hibernate validates. | Contradicted | Update to Flyway migration guidance. |
| `FRONTEND.md` | `web` is the only Java package rendering pages. | Auth/identity page controllers live in `iam.shared`. | Contradicted | Clarify page-controller split. |
| `MOBILE_ROADMAP.md` | Android app is not built; zero Kotlin exists. | `android/` contains a Kotlin/Compose app. | Contradicted | Mark native shell as current and keep richer work proposed. |
| `MOBILE_ROADMAP.md` | Grades and exams are deferred/unbuilt. | Grades and exams exist in backend/web with tests. | Contradicted | Distinguish current web/backend behavior from future native work. |
| `MOBILE_ROADMAP.md` | File upload does not exist anywhere. | Finance expense attachments exist. | Partially contradicted | Clarify homework-specific upload is absent. |
| `MOSHAVERE_PORT_SPEC.md` | Draft before-build proposal with unresolved open items. | Major listed port features are marked done and are present in code/tests. | Outdated/mixed | Mark as historical/completed proposal. |
| `UX_ROADMAP.md` | Only four pages exist and mutating UI is missing. | Management and student/parent pages exist. | Outdated | Mark as historical roadmap; use OpenSpec/Frontend for current inventory. |
| `HISTORY.md` | Old roadmap appendix has stale deferred items. | Later code and tests implement some deferred items. | Historical/outdated | Add warning that history is not current specification. |

## Classification

| File | Category |
|---|---|
| `README.md` | Development guidance |
| `CLAUDE.md` | Claude-specific entry instructions |
| `CLAUDE_LEGACY.md` | Historical or obsolete for current-state claims; legacy architecture notebook |
| `BEST_PRACTICES.md` | Compatibility redirect |
| `DESIGN.md` | Development guidance |
| `DEPLOYMENT.md` | Development guidance |
| `FRONTEND.md` | Development guidance + current frontend spec |
| `HISTORY.md` | Historical or obsolete |
| `MOBILE_ROADMAP.md` | Proposed change |
| `MOSHAVERE_PORT_SPEC.md` | Historical or obsolete proposal |
| `UX_ROADMAP.md` | Historical roadmap + development guidance |
| `android/README.md` | Current specification |
| `.agents/skills/**` | Shared agent workflows |
| `.codex/skills/**` | Codex-specific agent workflows |
| `.claude/**` | Claude-specific agent instructions |

## Source Evidence Sample

- `pom.xml`, `Dockerfile`, `liara.json`, and `application*.yml` confirm runtime, deployment, Flyway, and production profile behavior.
- `src/main/jte/`, `src/main/resources/static/js/`, and `src/main/resources/static/css/` confirm current frontend inventory.
- `android/` confirms the native Kotlin/Compose app exists.
- `src/test/java/com/example/roshdeandishe/*FlowIntegrationTest.java` confirms broad integration coverage.
- `SecurityConfig.java` and `TenantResolverFilter.java` confirm authentication, authorization, CSRF, and tenant-header behavior.
