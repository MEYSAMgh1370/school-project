# AGENTS.md

These instructions apply to future Codex sessions in this repository.

## Documentation Discipline

- Read [`openspec/project.md`](openspec/project.md) first, then the relevant current specification, change proposal, decision record, or legacy source document before modifying code.
- Inspect the implementation and tests to confirm current behavior. Source code and tests are stronger evidence than existing Markdown.
- Report conflicts between documentation and code instead of silently choosing one.
- Never assume AI-generated Markdown is correct.
- Create or update a change proposal before substantial behavioral changes.
- Keep current specifications separate from future ideas, roadmaps, and historical notes.
- Update the relevant specification or decision record when behavior intentionally changes.
- Do not change documentation merely to justify a bug or accidental behavior.
- Run the relevant tests after implementation and report what was run.
- Keep documentation concise, evidence-based, and linked to implementation when practical.

## Safety Boundaries

- Do not expose secrets from configuration files.
- Do not delete historical or unclear documentation unless the user explicitly approves the move or removal.
- Do not invent business rules to fill gaps in the docs. Mark gaps as unresolved questions.
- Do not modify application behavior as part of a documentation-only task.
