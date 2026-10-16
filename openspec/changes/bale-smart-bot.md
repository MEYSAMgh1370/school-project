# Bale smart bot (بات هوشمند بله)

Status: proposed 2026-10-15, revised 2026-10-16 (classification contract with confidence, Jev as a candidate), stage 1 implemented 2026-10-16 (`studentview` module, callback queries and inline keyboards, `BotContext`, asynchronous de-duplicated webhook handling; current behavior is in `specs/current-system.md`); later stages not implemented. Designed with the product owner in a brainstorming session on 2026-10-15; every decision below marked **(PO)** was made by the product owner, items marked **assumption** were not and are listed under Unresolved questions.

## Goal

Let linked users (parents, students, teachers) use the service from the Bale bot: first by **asking about their own data** in free Persian text or through buttons (phase 1), later by **doing things** with explicit confirmation (phase 2) **(PO)**. An LLM is used only to understand free text; the bot must stay correct, private and usable when the LLM is weak, slow or switched off.

## Current behavior (evidence)

Note: written before stage 1; the synchronous handling, the missing callback support and the `web`-only student summary below were changed by stage 1 (see Status and `specs/current-system.md`).

- `notification/internal/BaleUpdateHandler` is a command bot: `/start` (contact-share request), contact sharing (links a chat to the user whose profile phone matches, stored in `BaleLinkEntity`), «راهنما», «مدرسه‌های من», «فرزندان من» (ward names and invoice count). Any other text answers «دستور نامعتبر است».
- Security model: there is no session; every query reads only the linked user's data (from `BaleLinkEntity`) or their linked wards, the same trust pattern as `/api/me` (class comment in `BaleUpdateHandler`).
- `BaleWebhookService.processRawUpdate` parses and handles the update **synchronously** inside the webhook request; `handle()` is `@Transactional`; all exceptions are swallowed so the webhook always answers 200.
- `BaleClient` supports `sendMessage` and `sendContactRequest`; no inline keyboards or callback queries are handled (`BaleUpdate` has no `callback_query`).
- The student summary (latest 3 grades of the current year, up to 3 upcoming exams, unexcused/excused/late counts) and today's attendance state are assembled in the `web` module (`web/model/StudentSummaryAssembler`, `web/model/AttendanceTodayAssembler`), which `notification` must not depend on.
- No LLM, MCP or agent-framework dependency exists in `pom.xml`.

### Documentation conflict

The `BaleUpdateHandler` class comment cites "CLAUDE.md §10: do not build AI". That rule is `CLAUDE_LEGACY.md` §10 «What NOT To Do» ("Do not build AI features"), a historical document; the current `CLAUDE.md` has no such rule. The product owner chose to add an LLM layer **(PO)**; this must be recorded as a decision record that explicitly supersedes the legacy rule (see Documentation deliverables), and the class comment updated.

## Decisions (product owner, 2026-10-15)

1. **Scope order:** personal-data questions first (phase 1, read-only), actions second (phase 2).
2. **LLM provider: not chosen yet.** Build a provider-agnostic port; send the model the minimum, with names replaced by labels.
3. **The LLM is a router only.** It maps text to an intent and arguments; replies are built by the server from templates. **No database result is ever sent to the LLM.** This also compensates for possibly weaker domestic models.
4. **Architecture: in-process, layered** (approach 1). No MCP server and no agent framework in phase 1. Each intent is a self-contained handler so it can later be exposed as an MCP tool without rework.
5. **Phase-1 intent list approved** (below); **students never see finance**.
6. **A config switch `BOT_LLM_ENABLED`**; off or unavailable means a fully button-driven bot.
7. **Daily LLM limit per role**, teacher (and the default) 30 per day.
8. **Classification contract (2026-10-16):** the model port is "text plus closed option lists → chosen option plus confidence", so a decision model such as TypeSafe AI's Jev and an ordinary LLM fit the same port; low confidence falls back to the menu.

## Approach: what needs what

| Capability | LLM | MCP | Agent framework |
|---|---|---|---|
| Buttons, exact commands, account linking, outbound notifications | no | no | no |
| Phase-1 questions from buttons | no | no | no |
| Phase-1 questions from free text | yes (router) | no | no |
| Phase 2 actions with confirmation | yes (router) | no, unless a second consumer appears | re-evaluate in the phase-2 design |
| Exposing the same tools to another client (Android, admin agent, Claude Desktop) | n/a | yes | n/a |

MCP earns its cost only with a second consumer or a separate deployment boundary; an in-process tool call is simpler and keeps `userId` inside the trust boundary.

## Proposed behavior (phase 1)

### Components

All new code lives under `notification` in a `bot` sub-package (placement to be checked against `new-module` and Modulith rules before coding). Stage 1 deferred the `bot` sub-package to stage 2: `BotContext` and `BotContextLoader` are package-private classes in `notification/internal`.

```
BaleWebhookController (existing)
 └─ BaleWebhookService → async executor, update_id de-duplication
     └─ BaleUpdateHandler (existing, slimmed)
         ├─ callback query (button) ──────────┐
         ├─ exact command / menu ─────────────┤
         └─ free text                          │
             └─ TextUnderstanding              │
                 ├─ Pseudonymizer              │
                 ├─ LlmClient (port)           │
                 └─ IntentParser + Validator   │
                             ▼                 ▼
                       IntentDispatcher ── BotContext
                             │
                       IntentHandler (one per intent; calls facades)
                             │
                       ReplyRenderer (Persian templates, buttons) → BaleClient
```

- **`BotContext`** — the linked user, active memberships, roles per school and linked wards, built once per update **from server data only**. Nothing produced by the model enters it.
- **`Pseudonymizer`** — replaces the user's and wards' names and long digit runs (phone, national id) in the text with labels (`[فرزند_۱]`, …). Matching normalises Arabic/Persian ی and ک, ZWNJ and spaces, and prefers the longest match (`علیرضا` before `علی`). The label map lives only for the request.
- **`LlmClient`** — one method, a classification: pseudonymised text plus closed option lists (the allowed intents; the ward labels) → for each list, the chosen option and a confidence in `0..1`. Adapters:
  - **OpenAI-compatible chat completions** (most domestic providers), `temperature=0`, JSON-schema or JSON mode where offered; confidence is reported as `1.0` when the reply parses, since such models give no calibrated probability.
  - **Decision model** (TypeSafe AI's Jev, candidate): one `Choice` question per list; its returned probability is the confidence.
  - Base URL, key, model and adapter type come from configuration (env).
- **`IntentParser` / `Validator`** — check the result against the closed lists; the intent must be in the list *and* allowed for the user's roles; `child` must be one of this request's labels; each confidence must reach `bot.llm.min-confidence` (**assumption:** 0.7). Any failure yields `UNKNOWN`. A constrained model can still return a valid but wrong option, so this check and the server-side ownership rules stay even with a model that cannot emit an invalid type.
- **`IntentHandler`** — one class per intent, input `BotContext` plus validated arguments, output a reply model. Reads only through public facades.
- **`ReplyRenderer`** — Persian templates, Solar dates (`SchoolFacade.formatSolarDate`), follow-up buttons.
- Summary and today's-attendance logic is moved out of `web/model` into a facade both the dashboard and the bot use, so both give the same answer.

### Phase-1 intents (all read-only) (PO)

Parent (argument `child`, resolved among the user's linked wards):

| Intent | Example | Source |
|---|---|---|
| `CHILD_ATTENDANCE_TODAY` | «امروز مدرسه بود؟» | today's-attendance logic (an untaken roll is never shown as present) |
| `CHILD_ATTENDANCE_SUMMARY` | «چند تا غیبت داره؟» | `AttendanceFacade.listMyAttendance`, current year, recorded days |
| `CHILD_RECENT_GRADES` | «نمره‌هاش چی شد؟» | `AcademicFacade.listGradesForStudents`, latest 3, no average |
| `CHILD_UPCOMING_EXAMS` | «امتحان داره؟» | `ExamFacade.listExamsForStudent` |
| `CHILD_TIMETABLE_TODAY` | «امروز چه کلاس‌هایی داره؟» | `TimetableFacade` |
| `CHILD_INVOICES` | «شهریه چقدر مونده؟» | `FinanceFacade.listInvoicesForStudents` + instalments |

Student: `MY_ATTENDANCE_TODAY`, `MY_ATTENDANCE_SUMMARY`, `MY_RECENT_GRADES`, `MY_UPCOMING_EXAMS`, `MY_TIMETABLE_TODAY` — **no finance intent (PO)**.

Teacher: `TEACHER_TODAY_SCHEDULE` (`TimetableFacade.listEntriesForTeacher`), `TEACHER_ROLL_STATUS_TODAY` (`AttendanceFacade.listRecordableClasses`).

Every linked user: `UPCOMING_EVENTS` (yearly plan / calendar), `MY_SCHOOLS`, `HELP` (existing), `UNKNOWN` (menu).

Out of phase 1: payments, recording attendance, submitting an excuse (first phase-2 candidate), admin and finance statistics, study plans, weekly exams.

### Common rules

- Roles come from active memberships; an intent that does not fit the user's role answers «در دسترس نیست», never data.
- Several schools, roles or children, with no clear target → ask with buttons.
- Every intent is reachable from the menu and from free text; the LLM is only one entry point.
- **No stored conversation state in phase 1.** Disambiguation buttons carry the intent and the target id in `callback_data`; on click the server re-checks that the target belongs to this user.

### LLM contract and privacy

- **Sent:** the pseudonymised text, the intents allowed for this user's roles, the ward labels.
- **Never sent:** ids, names, school name, phone numbers, any database result.
- Text longer than 500 characters (**assumption**) is not sent; the menu is shown.
- Output: per list, one option and a confidence, e.g. `intent = CHILD_RECENT_GRADES (0.91)`, `child = [فرزند_۱] (0.88)`; anything else in the reply is ignored.
- Prompt injection cannot leak data: the model holds none, `child` is restricted to this user's labels, and `userId` comes from `BaleLink`. The worst outcome is a different allowed intent for the same user.
- Text from an **unlinked** chat is never sent to the LLM.

### Webhook processing

- The webhook answers 200 immediately; processing runs on a bounded executor.
- Recently seen `update_id`s are kept for a short window to drop retries.
- Transactions cover database reads only, never the LLM call.
- A "typing" chat action is sent before calling the LLM.

### Errors and limits

| Situation | Behavior |
|---|---|
| LLM timeout (**assumption:** 8 s) or network error | menu + «متوجه نشدم، از گزینه‌ها انتخاب کنید» |
| Unparsable reply, unknown or disallowed intent, confidence below `bot.llm.min-confidence` | same menu, no retry |
| `BOT_LLM_ENABLED=false` (default) | free text goes straight to the menu |
| Unlinked user | current message, no LLM |
| Daily limit reached | «لطفاً از دکمه‌ها استفاده کنید»; buttons keep working |

- **Daily limit per role (PO):** `bot.llm.daily-limit.<ROLE_CODE>` with `bot.llm.daily-limit.default=30`; teacher 30 **(PO)**; `SCHOOL_ADMIN` higher (**assumption:** 100). A user's limit is the **highest** of their active roles; custom roles use `default`. Plus a service-wide daily cap (**assumption**, value open). In phase 1 the admin has only the common intents, so a higher admin limit matters from phase 2 on.
- **Logging:** intent, outcome (`OK`, `FALLBACK`, `ERROR`), latency. **User text is never logged.**

## Acceptance criteria

1. With `BOT_LLM_ENABLED=false`, every phase-1 intent is reachable through the menu and buttons and answers from the same data as the dashboard.
2. A parent sees only their linked wards; a forged `callback_data` with another student's id answers «در دسترس نیست».
3. A student never receives a finance intent or finance data, from buttons or text.
4. Text from an unlinked chat never reaches `LlmClient`.
5. The request to the LLM contains no user or ward name, no id and no phone number (asserted on the captured request).
6. An LLM timeout, error, invalid output or confidence below the threshold answers the menu within the timeout plus rendering time; the webhook itself answers 200 without waiting.
7. A repeated `update_id` is processed once.
8. A user over their daily limit gets the limit message; buttons still work; the limit is the highest of their active roles.
9. Logs contain intent, outcome and latency, and never the user's text.

## Tests

- **Unit:** `Pseudonymizer` (Persian names, ی/ک, ZWNJ, `علی`/`علیرضا`); `Validator` (role-disallowed intent, forged label, broken JSON); each `IntentHandler` with fake facades; limit resolution across several roles.
- **Security:** acceptance criteria 2–5 as dedicated tests.
- **Integration:** webhook with a stub `LlmClient` returning canned output; timeout, limit and duplicate-update scenarios.
- **Model evaluation (manual, not in CI):** about 100 realistic Persian sentences with the expected intent, and a script reporting accuracy per provider, used to choose among providers (domestic APIs and Jev); it also reports accuracy at several confidence thresholds to set `bot.llm.min-confidence`.

## Delivery stages (one deployable PR each)

1. **Groundwork:** async webhook processing and de-duplication, `BotContext`, callback-query support in `BaleUpdate`/`BaleClient`, summary and today's-attendance logic moved to a facade. No user-visible change.
2. **Full button bot:** every phase-1 intent from the menu, no LLM. Useful on its own.
3. **LLM layer:** `Pseudonymizer`, `LlmClient`, `Validator`, limits, `BOT_LLM_ENABLED` (default off), evaluation set.
4. **Phase 2 (separate design):** actions with an explicit «تأیید / لغو» step, starting with submitting an absence excuse; decide there whether conversation memory, an agent framework or MCP is needed.

## Compatibility

- Existing commands («راهنما», «مدرسه‌های من», «فرزندان من»), `/start` and contact linking keep working.
- Outbound notifications (`NotificationChannel`, absence, invoice, event reminders) are unchanged.
- No schema change is expected in stages 1–2; stage 3 may need storage for daily counters (in memory is acceptable for the single production instance, **assumption**).

## Documentation deliverables

- This proposal, kept in sync as stages land; current behavior moves to `specs/current-system.md` and `architecture.md` per stage.
- `decisions/2026-10-15-llm-in-bale-bot.md`: supersedes `CLAUDE_LEGACY.md` §10 "Do not build AI features" for the bot's free-text router, and records the privacy rules (router only, no database result to the model, pseudonymisation, no logging of user text). Written with stage 3; the `BaleUpdateHandler` class comment is updated in the same change.

## Unresolved questions

1. Which provider (domestic API, foreign API, self-hosted, or a decision model such as TypeSafe AI's Jev); to be decided with the evaluation set. For Jev specifically: Persian support is not stated in its public material (as of 2026-10-16), it is a foreign service (access and payment from Iran, data leaving the country), and it is a new release whose speed figures are the vendor's own.
2. The confidence threshold (0.7 is an assumption), set from the evaluation set.
3. Exact `SCHOOL_ADMIN` daily limit (100 is an assumption) and the service-wide daily cap.
4. LLM timeout (8 s) and maximum text length (500) are assumptions.
5. Whether daily counters may be in memory (single instance) or must be persisted.
6. Whether the provider's data-processing terms are acceptable for pseudonymised text from minors' parents and students.
7. Stage 1 assumptions, not confirmed by the product owner: the webhook handling pool (core 2, max 4, queue 100) and the de-duplication capacity (last 1000 `update_id`s) were chosen without load data; both should be revisited with real traffic.
8. Per-chat ordering: updates from one chat can be handled out of order on the 2-4 pool threads of `BaleWebhookService`; consider serializing per `chat.id` in stage 2.
