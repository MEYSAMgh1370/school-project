# CLAUDE.md

These instructions apply to Claude sessions in this repository.

## Start Here

- Read [`openspec/project.md`](openspec/project.md) first.
- Use [`openspec/conventions.md`](openspec/conventions.md) plus the relevant current specification, change proposal, decision record, or legacy source document before modifying code.
- Inspect implementation and tests before relying on Markdown. Source code and tests are stronger evidence than existing docs.
- Report documentation/code conflicts instead of silently choosing one.
- Treat [`CLAUDE_LEGACY.md`](CLAUDE_LEGACY.md) as historical architecture and product context, not unchecked current authority.

## Shared Skills

- Before creating a new top-level module under `com.example.roshdeandishe`, apply [`new-module`](.agents/skills/new-module/SKILL.md).
- Before creating or substantially restructuring a JTE page, page controller, or page flow, apply [`design-page`](.agents/skills/design-page/SKILL.md).

## Claude-Specific Workflow

- For multi-file features, new modules, or cross-cutting refactors, use the Claude-specific [`dev-loop`](.claude/skills/dev-loop/SKILL.md) skill.
- The dev-loop skill depends on Claude Code agents, subagent model routing, and `.claude/settings.json`; do not treat it as a shared workflow.

## Safety

- Do not expose secrets from configuration files.
- Do not invent business rules to fill documentation gaps. Mark gaps as unresolved questions.
- Do not modify application behavior as part of a documentation-only task.
