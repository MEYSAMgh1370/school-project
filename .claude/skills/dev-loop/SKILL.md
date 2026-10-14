---
name: dev-loop
description: Orchestrated development loop that plans work, delegates implementation to cheaper Sonnet subagents, then reviews and integrates results in the main (Opus) session. Use this skill whenever the user asks to implement a feature, build a module, fix a bug that spans multiple files, refactor across the codebase, or explicitly says "dev-loop". Do NOT use for trivial single-file edits or quick questions — handle those inline.
---

# Dev Loop — Orchestrated Delegation Workflow

You are the **orchestrator**. You run on the expensive, high-judgment model (Opus). Your job is planning, prompt-writing, reviewing, and integrating — **not** bulk exploration or bulk code writing. All heavy token consumption (grep, file reading, implementation) must happen inside Sonnet subagents whose context dies with them.

## Model policy (non-negotiable)

- Main session: `opus` (set in `.claude/settings.json`; do not override).
- All subagents spawned by this workflow: `model: sonnet` (already set in their frontmatter — never pass `model: inherit`, and never omit the field, since `inherit` is its default; either would silently run them on Opus and destroy the cost advantage).
- `code-explorer` may be spawned with a **per-invocation `model: haiku`** for narrow factual lookups you can cheaply verify ("which file defines `X`", "list every class annotated `@Y`"). Leave it on its Sonnet default for open-ended mapping ("how does entitlement checking flow end to end"). The split is **per question, not per agent**: Haiku for retrieval you will check, Sonnet for judgment you will trust without re-deriving.
- Never route `task-implementer`/`ui-implementer` to Haiku. They must judge whether their own tests actually passed, and a false DONE costs a full review cycle plus a corrective respawn — more than the saving.
- Do not set `CLAUDE_CODE_SUBAGENT_MODEL`. It sits *above* both the per-invocation parameter and frontmatter in Claude Code's resolution order, so setting it silently flattens every choice above.
- If a subagent's output is repeatedly wrong on a genuinely hard reasoning task, do that one task yourself in the main session instead of retrying a third time.

## Phase 1 — Understand & Plan (orchestrator, Opus)

1. Restate the goal in 1–3 sentences. If acceptance criteria are ambiguous, ask the user **before** spawning anything.
2. If you lack codebase context, spawn `code-explorer` (read-only) to map the relevant modules. Do not grep the codebase yourself beyond trivial lookups — that is exactly the context pollution this workflow exists to prevent.
3. Break the work into tasks. Each task must be:
   - **Self-contained**: completable without conversation history (subagents are one-shot; there is no back-and-forth).
   - **Independently verifiable**: has explicit acceptance criteria (see "Default verification" below).
   - **Scoped to files**: lists the exact files/paths it may touch.
   - **Module-aligned**: task boundaries follow Spring Modulith module boundaries wherever possible. A task never reaches into another module's internals — public API or application events only.
4. Route tasks to the right worker: JTE templates, Alpine.js components, or any user-facing page → `ui-implementer`; everything else → `task-implementer`.
5. Skill composition:
   - A "new module" task → apply the shared `.agents/skills/new-module/SKILL.md` workflow first (package layout, error-handling stack, facade, entitlement registration), then delegate the implementation via this loop.
   - A new page, or a substantial restructure of an existing one → apply the shared `.agents/skills/design-page/SKILL.md` workflow first. It settles goal/hierarchy/primary action/states before any JTE is written.
   - Any UI task's delegation prompt must reference OpenSpec/current templates plus `DESIGN.md` and the `:root` token block in `src/main/resources/static/css/app.css` for visual rules and token values.
6. Mark which tasks are independent → those get spawned **in parallel**. Dependent tasks run in sequence, each receiving the relevant results of its predecessors inside its prompt.
7. Present the task plan to the user briefly before executing (skip only if the user said to proceed autonomously).

## Default verification per task type

Include the matching command(s) as the acceptance criteria in every delegation prompt:

- Any module change: `./mvnw test -Dtest=<RelevantFlowIntegrationTest>`. This project is **one Maven artifact, not a reactor** (`openspec/conventions.md` and `CLAUDE_LEGACY.md` §2), so there is no `-pl <module>`. Integration tests live flat in `com.example.roshdeandishe`, named `*FlowIntegrationTest`, and need Docker — they run against a real Postgres via Testcontainers.
- Full suite, exactly as CI runs it: `./mvnw clean verify`
- Structural / cross-module change: `./mvnw test -Dtest=ModularityTests` (`ApplicationModules.verify()`)
- Any schema change (new table, new/changed column, new module schema): **Flyway owns the schema** (`openspec/conventions.md` and `CLAUDE_LEGACY.md` §12) and `ddl-auto` is `validate`, so an entity change without a migration fails startup. Add a new `src/main/resources/db/migration/V<n>__....sql` — never edit an applied one, `V1` included. A new module's schema goes in `spring.flyway.schemas` in `application.yml` (there is no `init-schemas.sql` any more; Testcontainers starts empty and Flyway builds everything). Seed *data* is still Java seed runners, not migrations — make every seed addition per-code idempotent rather than guarded by a row-count check (that trap has bitten this repo three times).
- UI change: the JTE template compiles — JTE is precompiled, so `./mvnw clean verify` catches template errors — and the affected page's `*FlowIntegrationTest` passes.

"It compiles" is never sufficient grounds for reporting success.

## Phase 2 — Delegate (Sonnet subagents)

For each task, write a delegation prompt containing:

- **Goal**: one paragraph, concrete.
- **Context**: everything the agent needs — relevant findings from `code-explorer`, OpenSpec/current-code evidence, project conventions that apply, interfaces/signatures of neighboring code. Remember: the subagent has **zero** knowledge of this conversation.
- **Scope**: exact files to modify or create. State explicitly: "Do not touch anything outside this list. Do not access other Modulith modules' internals — public API or events only."
- **Acceptance criteria**: the exact command(s) from "Default verification" above and what output counts as success.
- **Report format**: "Return: files changed, summary of each change, verification output, and anything you were unsure about."

Rules:
- Never delegate two overlapping file scopes in parallel (merge conflicts inside one working tree).
- Prefer several small tasks over one giant task — small tasks fail cheaply and re-run cheaply.
- Exploration questions that arise mid-flow go to `code-explorer`, not to your own context.

## Phase 3 — Review (orchestrator, Opus)

This phase compensates for the workflow's structural weakness: you could not correct the subagent mid-flight, so you must review after the fact.

1. Read the diff of each completed task (`git diff` scoped to the task's files — read the diff, not the whole files).
2. Check against the generic criteria: acceptance criteria met, OpenSpec/current-code conventions followed, YAGNI (delete speculative additions), surgical-change discipline (flag unrelated edits).
3. Check the **project-specific review checklist**:
   - Display uses Persian digits and Jalali calendar; anything on the wire (APIs, DB, logs) is ISO-8601 only.
   - RTL-first: layout and Bulma classes are correct for RTL; no accidental LTR-only styling.
   - Entity → facade-record/DTO mapping is hand-written in the facade impl or controller, matching neighbouring code. This project has no MapStruct; a change must not introduce one.
   - No change loosens the security config: no newly exposed actuator endpoints, no weakened CSRF/session settings.
   - Week starts on Saturday anywhere calendars/schedules are involved.
4. Run the full verification the subagent claimed passed. Trust but verify.
5. Small fixes (< ~10 lines): fix them yourself. Larger problems: spawn a **new** subagent with a corrective prompt that includes the review findings verbatim. Do not "continue" the old agent — it is gone.

## Phase 4 — Deliver

- Summarize for the user: what was built, files changed, how it was verified, any open questions or follow-ups deferred (YAGNI list).
- Suggest a commit message. Do not commit unless the user has authorized it.

## Phase 5 — Finish (only after the user says to merge)

When the user asks to merge ("merge it", "ادغام کن"), finish the whole job with one command instead of hand-running git:

```bash
scripts/finish-pr.sh <PR-number>          # add --dry-run first if unsure
```

It merges only when the PR is mergeable and every check passed, then fast-forwards the main checkout, removes the PR's worktree and local branch (refusing if there are uncommitted changes or commits that are not in `origin/<base>`), deletes the remote branch, and runs `mvn -o compile` in the main checkout so new Flyway migrations and precompiled JTE templates are in its build (otherwise a stale build gives "Circular view path" 500s).

- Run the full review (Phase 3) before the user merges; the script is not a review.
- The repository deletes merged head branches itself (`delete_branch_on_merge`); the script covers the local side and the case where that setting is off.
- If `gh pr merge` is denied by the permission mode, stop and tell the user: merge in GitHub, or add a permission rule for `gh pr merge`; then run `scripts/finish-pr.sh <PR> --no-merge` for the cleanup. Do not look for another way to merge.
- Removing the worktree this session runs in leaves the session without a working directory, so make it the last action.
- After a merge that adds Flyway migrations, tell the user the migrations run on first start of every database (staging first) and have only been exercised on Testcontainers unless the verification says otherwise.
