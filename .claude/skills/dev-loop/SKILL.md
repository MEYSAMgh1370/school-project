---
name: dev-loop
description: Orchestrated development loop that plans work, delegates implementation to cheaper Sonnet subagents, then reviews and integrates results in the main (Opus) session. Use this skill whenever the user asks to implement a feature, build a module, fix a bug that spans multiple files, refactor across the codebase, or explicitly says "dev-loop". Do NOT use for trivial single-file edits or quick questions — handle those inline.
---

# Dev Loop — Orchestrated Delegation Workflow

You are the **orchestrator**. You run on the expensive, high-judgment model (Opus). Your job is planning, prompt-writing, reviewing, and integrating — **not** bulk exploration or bulk code writing. All heavy token consumption (grep, file reading, implementation) must happen inside Sonnet subagents whose context dies with them.

## Model policy (non-negotiable)

- Main session: `opus` (set in `.claude/settings.json`; do not override).
- All subagents spawned by this workflow: `model: sonnet` (already set in their frontmatter — never pass `model: inherit`, that would silently run them on Opus and destroy the cost advantage).
- If a subagent's output is repeatedly wrong on a genuinely hard reasoning task, do that one task yourself in the main session instead of retrying a third time.

## Phase 1 — Understand & Plan (orchestrator, Opus)

1. Restate the goal in 1–3 sentences. If acceptance criteria are ambiguous, ask the user **before** spawning anything.
2. If you lack codebase context, spawn `code-explorer` (read-only) to map the relevant modules. Do not grep the codebase yourself beyond trivial lookups — that is exactly the context pollution this workflow exists to prevent.
3. Break the work into tasks. Each task must be:
   - **Self-contained**: completable without conversation history (subagents are one-shot; there is no back-and-forth).
   - **Independently verifiable**: has explicit acceptance criteria (see "Default verification" below).
   - **Scoped to files**: lists the exact files/paths it may touch.
   - **Module-aligned**: task boundaries follow Spring Modulith module boundaries wherever possible. A task never reaches into another module's internals — public API or application events only.
4. Route tasks to the right worker: JTE templates, HTMX fragments, or any user-facing page → `ui-implementer`; everything else → `task-implementer`.
5. Skill composition:
   - A "new module" task → run the `new-module` skill first (spec-first OpenAPI workflow), then delegate the implementation via this loop.
   - Any UI task's delegation prompt must reference `DESIGN.md` and `design-tokens.css` as authoritative.
6. Mark which tasks are independent → those get spawned **in parallel**. Dependent tasks run in sequence, each receiving the relevant results of its predecessors inside its prompt.
7. Present the task plan to the user briefly before executing (skip only if the user said to proceed autonomously).

## Default verification per task type

Include the matching command(s) as the acceptance criteria in every delegation prompt:

- Any module change: `./mvnw test -pl <module>`
- Structural / cross-module change: run the Modulith verification test (`ApplicationModules.verify()`)
- New Flyway migration: `./mvnw flyway:validate` + check version naming and ordering
- UI change: template compiles and the affected page's tests (if any) pass

"It compiles" is never sufficient grounds for reporting success.

## Phase 2 — Delegate (Sonnet subagents)

For each task, write a delegation prompt containing:

- **Goal**: one paragraph, concrete.
- **Context**: everything the agent needs — relevant findings from `code-explorer`, project conventions that apply, interfaces/signatures of neighboring code. Remember: the subagent has read CLAUDE.md but has **zero** knowledge of this conversation.
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
2. Check against the generic criteria: acceptance criteria met, CLAUDE.md conventions followed, YAGNI (delete speculative additions), surgical-change discipline (flag unrelated edits).
3. Check the **project-specific review checklist**:
   - Display uses Persian digits and Jalali calendar; anything on the wire (APIs, DB, logs) is ISO-8601 only.
   - RTL-first: layout and Bulma classes are correct for RTL; no accidental LTR-only styling.
   - Mapping goes through MapStruct — no hand-written mapping code.
   - No change loosens the security config: no newly exposed actuator endpoints, no weakened CSRF/session settings.
   - Week starts on Saturday anywhere calendars/schedules are involved.
4. Run the full verification the subagent claimed passed. Trust but verify.
5. Small fixes (< ~10 lines): fix them yourself. Larger problems: spawn a **new** subagent with a corrective prompt that includes the review findings verbatim. Do not "continue" the old agent — it is gone.

## Phase 4 — Deliver

- Summarize for the user: what was built, files changed, how it was verified, any open questions or follow-ups deferred (YAGNI list).
- Suggest a commit message. Do not commit unless the user has authorized it.
