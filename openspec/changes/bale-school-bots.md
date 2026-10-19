# Per-school Bale bots (بات اختصاصی مدرسه)

Status: design approved by the product owner on 2026-10-16. PR 1 implemented (data model, encryption, per-bot tokens, inbound routing and scope, membership check, school bot page and API, outbound routing, `401` handling; current behavior is in `specs/current-system.md`). The platform administrator test link, planned for PR 2, shipped in PR 1. PR 2 still to do: business API with fallback, migration messages, platform status page.

## Goal

A school can register its own Bale bot, so parents, students and staff talk to a bot that carries the school's name and picture. When the school turns on Bale's business API, the cost and send limits of its notifications fall on the school's own Bale business account. Registering a bot is optional. A school without one keeps using the shared bot.

This change covers per-school bots only. **Broadcast messages** (school-wide announcements) are a separate change that builds on this one. The **shared bot's future role** (problem reports and similar) is also a separate design.

## Current behavior (evidence)

- There is one bot. The token is `roshdeandishe.bale.bot-token` (`BALE_BOT_TOKEN`) and there is one webhook secret, `roshdeandishe.bale.webhook-secret` (`BALE_WEBHOOK_SECRET`). See `BaleClient` and `BaleWebhookController`.
- `bale.bale_links` holds `user_id` and `chat_id`, and each is unique on its own. So a user has at most one chat, with the one bot.
- Webhook handling (`BaleWebhookService`):
  - de-duplication by `update_id` (`RecentUpdateIds`);
  - per-chat ordering by `chat.id` (`ChatSerialExecutor`);
  - one shared pool (core 2, max 4, queue 100).
- Bot answers are scoped by the user's active memberships. `BotContext` holds memberships only, and `SubjectResolver` resolves targets from them.
- Notifications go through `NotificationFacade.sendMessageToUser(userId, text)` and `deliverMessageToUser(userId, text)`, which carry no school. The callers are `AbsenceRecordedEventListener`, `InvoiceIssuedEventListener` and `EventReminderJob`. Each of them belongs to one school.
- Bale facts used here come from the Bale Bot API document (docs.bale.ai, last updated 15 Mehr 1405):
  - `setWebhook` takes only `url`, and only on ports 443 or 88.
  - A private chat id equals the user id, so it is the same for every bot.
  - The business API is `https://tapi.bale.ai/business/bot<token>/<METHOD>`. It supports `sendMessage` and a few file methods, works for private chats only, and charges each message to the bot owner's business account.
  - Deep links have the form `ble.ir/<bot_username>?start=<parameter>`.
  - `getMe`, `getWebhookInfo` and `deleteWebhook` exist.

## Decisions (product owner, 2026-10-16)

1. **Goals:** branding, plus separate cost and send limits.
2. **Optional:** a per-school bot is optional, and the shared bot stays.
3. **School without a bot:** it is served by the shared bot exactly as today, until the shared bot's future role is designed.
4. **School with an ACTIVE bot:** it is served **only** by its own bot. The shared bot neither shows that school's data nor sends its notifications.
5. **Who registers:** the school admin, on a school settings page. The app validates the token, creates the webhook secret and calls `setWebhook` itself.
6. **Who may link to a school bot:** only active members of that school, in any role, plus platform administrators (see 8). Anyone else is told «شما عضو این مدرسه نیستید» and nothing is linked.
7. **Data scope:** a school bot shows only that school's data. A parent with children in two schools sees each child in that school's bot, or in the shared bot when that school has no bot of its own.
8. **Platform administrators** get two things:
   - a status panel for all school bots;
   - the right to link to any school bot for a live test. They see no school data, because they have no membership there.
9. **Migration:** when a school registers a bot, the shared bot sends one message, with a deep link to the new bot, to its linked users who are active members of that school. Linking in the new bot uses the normal contact flow. There is no token shortcut.
10. **Business API:** a per-bot switch, off by default. It applies to notifications only, and answers always use the normal API. If a business send fails permanently, the message is sent once through the normal API and the failure is shown to the school admin.
11. **Free for all schools:** this is not a toggleable or paid module.
12. **Per-bot queue share:** one school's traffic cannot fill the shared webhook queue.

## Proposed behavior

### Data model (`bale` schema, `notification` module)

New table `bale.school_bots`:

| Column | Rule |
|---|---|
| `id` | identity |
| `school_id` | not null, unique: at most one bot per school |
| `bot_user_id` | not null, unique: from `getMe`, so a bot cannot serve two schools |
| `bot_username` | not null: from `getMe` |
| `token_ciphertext` | not null: see Security |
| `token_key_version` | not null: which encryption key wrote it |
| `webhook_secret_hash` | not null, unique: SHA-256 of the secret, never the secret itself |
| `business_enabled` | not null, default `false` |
| `status` | `ACTIVE` or `DISABLED` |
| `last_update_at` | nullable: last update received, written at most once a minute |
| `last_error`, `last_error_at` | nullable: shown to the school admin and the platform admin |
| `created_at`, `created_by` | |
| `updated_at`, `updated_by` | |

Changes to `bale.bale_links`:
- New nullable `bot_id`, a foreign key to `school_bots` with `on delete cascade`. `null` means the shared bot, so every existing row stays a shared-bot link.
- Drop the single-column unique constraints on `chat_id` and `user_id`. Replace them with:
  - unique `(bot_id, chat_id)` and unique `(bot_id, user_id)` where `bot_id is not null`;
  - unique `(chat_id)` and unique `(user_id)` where `bot_id is null`.
- A user may therefore hold one shared-bot link and one link per school bot.

Rules:
- **Registering a bot** does not touch shared-bot links.
- **Deleting a bot** cascades its links away, and the school falls back to the shared bot. Users who kept their shared-bot link need no new contact share.
- **Replacing a bot's token with a different bot** (another `bot_user_id`) deletes the old bot's links and sends the migration message again (see Outbound path).
- **Replacing a token with a new token for the same bot** keeps the links.
- **A `DISABLED` bot is treated everywhere as "the school has no bot"** (Decision 4 applies to ACTIVE bots only). This covers the inbound scope, the outbound routing and the webhook lookup.

### Inbound path

1. **Bot identity.** `POST /api/bale/webhook/{secret}` resolves a bot identity before parsing:
   - If the secret equals `BALE_WEBHOOK_SECRET` (constant-time comparison, as today), the update is for the **shared bot**.
   - Otherwise the app takes the SHA-256 of the secret and looks it up in `school_bots` among `ACTIVE` rows. A match gives the **school bot** (bot id, school id).
   - Otherwise the answer is `404`, the same as today. The response does not reveal whether such a bot exists.
2. **The identity travels with the update** into the async handler, and every reply uses that bot's token.
3. **Queue keys:**
   - de-duplication by `(bot, update_id)`;
   - per-chat ordering by `(bot, chat.id)`.
4. **Per-bot queue share:** at most 40 of the 100 queued updates may belong to one school bot. The shared bot has no separate share and may use the whole queue of 100, so its behaviour is the same as before school bots existed. Past a limit, the update is dropped with a log line and its id is not kept as seen. This is the same rule as for a full queue.
5. **Scope filter.** Applied to memberships before anything else, when `BotContext` is built:
   - **school bot:** keep only memberships of that school;
   - **shared bot:** drop memberships of schools that have an `ACTIVE` bot.

   The menu, the answers and the callback re-validation all use the filtered list. No answer class changes.

   The school bot setting of `openspec/changes/school-settings.md` (on/off and allowed roles, page `/settings`) is applied after this filter, on the memberships it leaves (see "Interplay with per-school bots" there).
6. **Linking in a school bot.** The existing checks stay, in this order: private chat, the contact is the sender's own, the phone matches a user. A new last check follows: the user is an active member of the bot's school, or a platform administrator. If not, the user gets `BotTexts.NOT_A_MEMBER` («شما عضو این مدرسه نیستید.») and `bale_links` is untouched. A linked platform administrator sees the welcome and help only, because of the scope filter.
7. **Last update time.** `last_update_at` is written at most once a minute per bot.

### Outbound path

1. **Notifications carry the school.** The facade gains `sendMessageToUser(userId, schoolId, text)` and `deliverMessageToUser(userId, schoolId, text)`, and the three callers pass their school. The school-less methods stay and use the shared bot.
2. **Routing:**
   - **The school has an `ACTIVE` bot:** use the user's link with that bot. Without such a link the result is `NOT_LINKED`; there is no fallback to the shared bot (Decision 4).
   - **The school has no bot, or a `DISABLED` one:** use the shared-bot link, as today.
3. **Business API.** When the bot has `business_enabled`, notifications are posted to `/business/bot<token>/sendMessage`. If that fails with a permanent error (4xx other than 429):
   - the message is sent once through `/bot<token>/sendMessage`;
   - `last_error` and `last_error_at` are set, and the school admin sees them on the bot page.

   Answers to user messages never use the business API.
4. **Revoked token.** A `401` from Bale for a school bot sets it `DISABLED` with `last_error`. The school is then served by the shared bot until the admin enters a working token.
5. **Migration messages.** These are background jobs, rate-limited, and sent at most once per (user, bot):
   - **On registration** (and on replacing with a different bot): the shared bot sends `BotTexts.SCHOOL_BOT_AVAILABLE` with the school name and `ble.ir/<bot_username>?start=school` to its linked users who are active members of the school.
   - **On deletion:** the school bot, while its token still works, sends `BotTexts.SCHOOL_BOT_REMOVED` pointing to the shared bot.

### Pages

Both pages follow `.agents/skills/design-page/SKILL.md` at implementation time.

**«بات بله‌ی مدرسه»**
- Access: `@RequiresPermission("BALE_BOT_MANAGE")`, a new permission granted to `SCHOOL_ADMIN` by default. There is no `@RequiresModule`, because the feature is free (Decision 11).
- The page controller lives in `web`; the API lives at the `notification` module root controller.
- States:
  - **No bot:** three steps (create the bot with @botfather, copy the token, paste it here), a password-type token field, and the primary action «ثبت بات».
  - **Registration errors**, shown inline:
    - invalid token;
    - bot already registered by another school;
    - Bale unreachable;
    - the encryption key is not configured («امکان ثبت بات موقتاً فعال نیست؛ با پشتیبانی تماس بگیرید»).
  - **Active:**
    - `@username` and the `ble.ir/<username>` link;
    - count of linked members;
    - last update time;
    - the business-API switch, with the last business error if any;
    - secondary actions «تعویض توکن» and «حذف بات». Deletion has a confirmation that states its consequences.
  - **Disabled:** a warning («توکن بات نامعتبر شده است؛ تا اصلاح آن، اولیا از بات کلی سرویس می‌گیرند») and the token field.
- The token is never shown again, not even in part.
- Token validation is limited to 10 attempts per school per hour.

**«بات‌های مدارس»** (platform)
- Access: `@RequiresPermission("PLATFORM_BALE_BOT_MANAGE")`, next to the platform holidays page.
- A table with school, `@username`, status, last update and last error.
- Per row:
  - «بررسی», which calls `getWebhookInfo` on click, never on page load;
  - «ثبت دوباره‌ی وب‌هوک».
- No token and no school data appear.

### Security

- **Token encryption.** Tokens are encrypted with AES-256-GCM, using a random 96-bit nonce per encryption. The key comes from the new environment variable `BALE_TOKEN_ENCRYPTION_KEY` (Base64, 256 bits), and `token_key_version` allows a later re-encryption with a new key. Without the key, registering is refused and nothing is stored in plain text. The rest of the app runs unchanged.
- **Token handling.** A token is decrypted only to make a Bale call. It never appears in logs, API responses or pages. Failed calls log only the failure kind (already the case after PR #156).
- **Webhook secret:** 256 random bits, stored as SHA-256 only.
- **Deleting a bot:** call `deleteWebhook` (best effort), then delete the row. Later updates get `404` either way.
- **Memberships** are read per update, so an ended membership stops data at the next message.
- **Audit:** registration, token replacement, deletion and switch changes are logged with the acting user and time.

## Acceptance criteria

1. A school admin registers a valid token. `getMe` and `setWebhook` are called, a `school_bots` row is created with an encrypted token and a hashed secret, and the page shows `@username`.
2. Registration is refused, with nothing stored, for:
   - an invalid token;
   - a bot already registered by another school;
   - a missing encryption key.
3. An update posted with a school bot's secret is answered with that bot's token. An unknown secret, or a `DISABLED` bot's secret, gets `404`.
4. A user who is a member of schools A (with a bot) and B (without one) sees:
   - in A's bot: only A's data;
   - in the shared bot: only B's data.
5. A non-member who shares their own contact in a school bot gets «شما عضو این مدرسه نیستید.» and no link. A platform administrator is linked and sees no school data.
6. Notification routing:
   - School A's absence, invoice and reminder notifications go only through A's bot. A user not linked to A's bot gets `NOT_LINKED`.
   - School B's notifications go through the shared bot.
7. With `business_enabled`, notifications use the business path. A permanent business failure is retried once on the normal path and recorded in `last_error`.
8. A `401` sets the bot `DISABLED`, and the shared bot serves that school again.
9. Deleting the bot removes its links, and the school's users are served by the shared bot without a new contact share.
10. The migration message is sent once per user per bot, only to active members of that school who are linked to the shared bot.
11. One school bot cannot hold more than 40 queued updates; the shared bot can queue up to the global 100, and shared traffic does not lower a school bot's 40 while the global 100 is not reached.
12. Neither the token nor the webhook secret appears in any log, response or page.

## Tests

- **Unit:**
  - token encryption round trip and tamper detection;
  - bot identity resolution (shared, school, unknown, disabled);
  - membership scope filter;
  - outbound routing, including the business fallback and `401` handling;
  - per-bot queue share.
- **Integration (Testcontainers, CI):**
  - the multi-school scenario of criteria 4–6, 8 and 9;
  - Bale simulated by a local HTTP server, as in `BaleFlowIntegrationTest`.
- **Regression:** the existing shared-bot tests pass unchanged, so a school without a bot sees no difference.

## Delivery stages

1. **PR 1, infrastructure and school bots:**
   - data model and migration;
   - encryption;
   - per-bot token in `BaleClient`;
   - inbound bot identity, queue keys and per-bot share;
   - scope filter and the membership check on link;
   - the school bot page and API;
   - outbound routing (normal API only);
   - `401` handling.
2. **PR 2, completion:**
   - business API with fallback;
   - migration messages;
   - platform status page;
   - platform administrator test link (shipped in PR 1).

## Compatibility

- Existing `bale_links` rows become shared-bot links (`bot_id is null`), and their uniqueness is unchanged.
- With no school bots registered, behavior is identical to today.
- `BALE_TOKEN_ENCRYPTION_KEY` is optional at startup, and only registration needs it.
- The facade's school-less notification methods stay.

## Documentation deliverables

- `openspec/specs/current-system.md`: school bots, routing, scope, pages.
- `DEPLOYMENT.md`: `BALE_TOKEN_ENCRYPTION_KEY`, how to generate it, and that it must never change without re-encryption.
- `openspec/changes/bale-smart-bot.md`: a cross-reference to this change.

## Unresolved questions

1. **Shared bot's future role** (problem reports and similar): separate design. Decision 3 holds until then.
2. **Broadcast messages:** separate change, built on this one. Open points are who may send, to which groups, through which bot and API, and with what limits.
3. **Join reminders:** whether to remind parents who never joined their school's bot, periodically or never. Not in scope, decided later by the PO.
4. **Encryption key rotation:** a procedure for re-encrypting with a new key version. The column exists, but no tool is in scope.
5. **Per-bot LLM daily limits:** per-role limits come with the LLM phase of `bale-smart-bot.md`. Whether they are counted per bot or per user is open.
