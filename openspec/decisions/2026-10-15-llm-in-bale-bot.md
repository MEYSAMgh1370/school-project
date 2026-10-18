# ADR: An LLM Routes Free Text In The Bale Bot

Date: 2026-10-15 (provider and timeout decisions added 2026-10-18)

Status: accepted and implemented (stage 3 of [`changes/bale-smart-bot.md`](../changes/bale-smart-bot.md)). It supersedes `CLAUDE_LEGACY.md` §10 «What NOT To Do» ("Do not build AI features") **for the Bale bot's free-text router only**; the rule stays as written for every other use.

## Context

`CLAUDE_LEGACY.md` §10 forbade AI features, and the `BaleUpdateHandler` class comment cited it. That file is historical context, not current authority (`CLAUDE.md`). The product owner chose to let linked users ask the bot questions in free Persian text, with buttons staying the primary and always-working path.

## Decision

An LLM may be used in the Bale bot for one job: **mapping a user's free text to one of the bot's existing intents (and, for a parent with several children, one of this request's child labels)**. Nothing else.

### Privacy rules

1. **Router only.** The model chooses an option from closed lists. Every reply is built by the server from templates.
2. **No database result reaches the model.** No id, school name, phone number, grade, amount or any other stored data is sent; the model receives the pseudonymised text, the names of the intents allowed for this user's roles with a fixed Persian hint each, and the child labels of this request.
3. **Pseudonymisation.** Before the call, the user's own name, their wards' names and their schools' names are replaced by labels («[خودم]», «[فرزند_۱]», …) and digit runs of five or more by «[عدد]». The label-to-id map lives only for the request. A label the model returns that was not offered is rejected.
4. **No user text in logs.** Logs carry only intent, outcome, model name and latency. The request and response bodies and the API key are never logged.
5. Text from an unlinked chat is never sent. Text longer than `bot.llm.max-text-length` (500) is not sent. The call is outside any database transaction.
6. The switch `BOT_LLM_ENABLED` defaults to `false`; off, unavailable, over the daily limit or not understood all lead to the existing menu.

### Provider (product owner, 2026-10-18)

- Provider: **Liara** (AI service; the same platform the application is deployed on, paid in rial).
- **Primary model: Jev** (`typesafe/jev-1.13`), a decision model that answers `choice` questions with a confidence.
- **Fallback model: `qwen/qwen3-32b`**, an OpenAI-compatible chat model.
- Base URL and key are shared by both models (`BOT_LLM_BASE_URL`, `BOT_LLM_API_KEY`).

### Decisions made in the implementation plan

D1 to D5 were proposed in the implementation plan and approved by the product owner on 2026-10-18 (D2 was also chosen by the product owner). D6 is a plan decision.

- **D1. When the fallback runs.** Only when the primary *fails*: timeout, connection error, non-2xx, or a reply that cannot be parsed. A primary answer with low confidence is a real answer and goes to the menu without trying the fallback.
- **D2. Time budget (chosen by the product owner, 2026-10-18).** Primary 3 s, fallback 5 s, so the total stays within the spec's 8 s. Jev returns a choice without generating text and is fast; Qwen generates token by token and needs more time.
- **D3. Service-wide daily cap.** `bot.llm.daily-cap-total` exists with default `0` (no cap). The spec leaves the value open.
- **D4. Jev path.** TypeSafe's own API is `POST /v1/systemone`. How Liara exposes Jev was unknown, so the path relative to `BOT_LLM_BASE_URL` is configurable (`bot.llm.decision-path`, `BOT_LLM_DECISION_PATH`, default `/systemone`). `scripts/bot-llm-eval.py` confirms it before the switch is turned on.
- **D5. Qwen3 thinking mode.** The fallback adapter appends ` /no_think` to the user message, removes any `<think>…</think>` block and parses the first `{…}` of the reply. The OpenAI-compatible API gives no probability, so the adapter reports confidence `1.0` whenever the reply parses.
- **D6. Not understood.** The existing «متوجه نشدم. لطفاً از دکمه‌های پایین انتخاب کنید.» with the menu is the answer to a failed, invalid or low-confidence classification.

## Known limits

- **Pseudonymisation is partial.** Only the user's own name, their wards' names and their schools' names are replaced, plus digit runs of five or more. Other names a user types (a teacher, a classmate) and shorter numbers reach the provider unchanged.
- **Worker occupancy.** The model call runs on the webhook worker pool (at most 4 threads). One message can hold a worker for up to about 8 s (3 s primary plus 5 s fallback), so heavy free-text traffic slows button replies. Revisit with real load.
- **Counters are in memory, per instance.** The daily per-role limits and the service-wide cap reset on restart and are not shared between instances.
- **Provider terms are not settled.** Whether Liara's data-processing terms are acceptable for pseudonymised text from parents and students remains an open question in the change proposal.

## Consequences

- `BOT_LLM_ENABLED` stays `false` until `scripts/bot-llm-eval.py` has been run against both models with the evaluation set (`scripts/bot-llm-eval-set.tsv`, 100 rows); its accuracy per confidence threshold is what sets `bot.llm.min-confidence` (0.7 is still an assumption).
- Any new use of an LLM elsewhere in the product needs its own decision record; this one does not extend to it.
- The privacy rules above bind later stages: phase 2 (actions) must not send database results to the model either.
