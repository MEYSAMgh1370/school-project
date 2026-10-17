# Per-school Bale bots — PR 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A school admin can register the school's own Bale bot. That bot serves only that school's members, and the school's notifications go through it. Schools without a bot keep the shared bot unchanged.

**Architecture:** Everything lives in the `notification` module and the `bale` schema. A `BotRef` (shared or school bot) is resolved from the webhook secret and travels with every update. Every Bale call takes the token of that bot. Memberships are filtered by the bot's scope before menus and answers are built, so the answer classes do not change. Notifications carry their school and are routed to the school's bot when it is ACTIVE.

**Tech Stack:** Java 21, Spring Boot, Spring Modulith, Spring Data JPA, Flyway (PostgreSQL), JTE + Alpine.js + Bulma, JUnit 5, AssertJ, Mockito, Testcontainers (CI only), JDK `HttpServer` as the fake Bale API.

**Spec:** `openspec/changes/bale-school-bots.md` (PR 1 scope = "Delivery stages" item 1). Background: `openspec/changes/bale-smart-bot.md`.

## Global Constraints

- **Maven:** run every `mvn` command as `./mvnw -s /tmp/claude-0/-home-user-school-project/d33e7fdc-c4d1-5a0a-8b9b-2145c3339491/scratchpad/maven-settings.xml …`. Without that settings file, Maven Central returns 429.
- **Fast suite:**

  ```
  test -Dtest='!*IntegrationTest,!*BootTest,!Demo5ApplicationTests,!CurriculumVisibleQuestionsTest,!PhoneNumberCanonicalizationMigrationTest' -Dsurefire.failIfNoSpecifiedTests=false
  ```

  There is no Docker locally. `*IntegrationTest` classes must compile (`test-compile`) and run in CI only.
- **Module conventions** (`openspec/conventions.md`):
  - Internals in `notification/internal` are package-private.
  - Public contracts are facades in the `notification` root.
  - Controllers go in `notification/controller`, and request/response records in `notification/dto/request` and `notification/dto/response`.
  - Errors use a sealed `NotificationError` in `notification/shared` with an exception and a named handler `@Component("notificationExceptionHandler")`, the same pattern as `attendance/shared`.
- **Copy:** all bot copy goes in `BotTexts`. Persian Javadoc comments, same density as the surrounding code.
- **Secrets:**
  - A token or webhook secret never appears in a log, response or page.
  - Failed Bale calls log `BaleClient.failureKind(e)` only.
  - JSON parsing uses a standalone `com.fasterxml.jackson.databind.ObjectMapper`, never an injected one.
- **Values copied from the spec:**
  - per-bot queue share: at most **40** of **100** queued updates;
  - registration attempts: **10 per school per hour**;
  - `last_update_at` written at most **once a minute** per bot;
  - webhook secret: **256** random bits, stored as **SHA-256** only;
  - token encryption: **AES-256-GCM** with a random **96-bit** nonce, key from `BALE_TOKEN_ENCRYPTION_KEY` (Base64, 256 bits), `token_key_version` = **1**;
  - non-member reply: «شما عضو این مدرسه نیستید.»;
  - new school permission: `BALE_BOT_MANAGE`, granted to `SCHOOL_ADMIN`, with no `@RequiresModule`.
- **Out of scope (PR 2):**
  - business API;
  - migration messages;
  - platform status page;
  - `PLATFORM_BALE_BOT_MANAGE`.

  The column `business_enabled` is created now but not read.
- **Push target:** the single available branch, `ccr-f1f836a8-1gyky3`. Commit trailers:

  ```
  Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
  Claude-Session: https://claude.ai/code/session_01B7DjynHyKXoGV5xXaLRf6P
  ```

## Review Focus

1. **Same chat id in two bots.** One person links to the shared bot and to school A's bot, which gives the same `chat_id` in both. Each bot must find its own link, and a contact share in one bot must not move the other bot's link. *Test: Task 5, `sameChatIdIsLinkedSeparatelyPerBot`.*
2. **A member's membership ends after linking.** The school bot must stop showing data at the next message, without unlinking. *Test: Task 5, `endedMembershipShowsNoSchoolDataInSchoolBot`.*
3. **A school's bot becomes DISABLED while users hold links to it.** The shared bot must serve the school again, inbound and outbound, and the disabled bot's secret must get `404`. *Tests: Task 4, `disabledBotSecretIsUnknown`; Task 5, `sharedBotServesSchoolWhoseBotIsDisabled`; Task 6, `disabledSchoolBotRoutesToSharedBot`.*
4. **Re-registering the same school with a token of a different bot.** The old bot's links must be deleted, and the new secret must replace the old one, so the old webhook URL now gets `404`. *Test: Task 7, `replacingWithDifferentBotDeletesOldLinksAndSecret`.*
5. **A token pasted with spaces or a newline from BotFather.** It must be trimmed before `getMe`. An empty value after trimming is an invalid token, and no Bale call is made. *Test: Task 7, `tokenIsTrimmedAndBlankIsInvalidWithoutCallingBale`.*

---

## File map

All paths are under `src/main/java/com/example/roshdeandishe/notification/` unless they start with `src/`.

| Status | File | Responsibility |
|---|---|---|
| Create | `src/main/resources/db/migration/V32__school_bale_bots.sql` | `bale.school_bots`, `bale_links.bot_id`, new uniqueness |
| Create | `internal/SchoolBotEntity.java`, `internal/SchoolBotRepository.java`, `internal/SchoolBotStatus.java` | persistence of school bots |
| Modify | `internal/BaleLinkEntity.java`, `internal/BaleLinkRepository.java` | `botId`, bot-scoped lookups |
| Create | `internal/TokenCipher.java` | AES-GCM encryption of tokens |
| Create | `internal/WebhookSecrets.java` | secret generation and hashing |
| Create | `internal/BotRef.java` | identity of the bot an update or send belongs to |
| Create | `internal/BotTokens.java` | `BotRef` → token (shared from config, school decrypted) |
| Modify | `internal/BaleClient.java` | token per call; `getMe`, `setWebhook`, `deleteWebhook`, `deliverDetailed` |
| Modify | `internal/RecentUpdateIds.java`, `internal/ChatSerialExecutor.java` | generic keys; per-group cap |
| Create | `internal/BotResolver.java` | webhook secret → `BotRef` |
| Modify | `NotificationFacade.java`, `internal/NotificationFacadeImpl.java`, `controller/BaleWebhookController.java`, `internal/BaleWebhookService.java` | inbound with bot identity; school-aware sends |
| Modify | `internal/BaleUpdateHandler.java`, `internal/BotContextLoader.java`, `internal/BotTexts.java` | bot-threaded replies, scope filter, membership check |
| Modify | `internal/NotificationChannel.java`, `internal/BaleChannel.java` + the three callers | outbound routing |
| Create | `SchoolBotFacade.java`, `internal/SchoolBotService.java`, `internal/RegistrationAttempts.java` | register, replace, delete, find, disable, touch |
| Create | `shared/NotificationError.java`, `shared/NotificationException.java`, `shared/NotificationExceptionHandler.java` | domain errors → ProblemDetail |
| Create | `controller/SchoolBotController.java`, `dto/request/RegisterSchoolBotRequest.java`, `dto/response/SchoolBotResponse.java` | REST API |
| Modify | `src/main/java/com/example/roshdeandishe/iam/internal/IamSeedDataRunner.java` | `BALE_BOT_MANAGE` + grant |
| Create | `src/main/java/com/example/roshdeandishe/web/controller/SchoolBotPageController.java`, `src/main/jte/bale-bot.jte`, `src/main/resources/static/js/bale-bot.js` | school page |
| Modify | `src/main/java/com/example/roshdeandishe/web/model/NavCatalog.java` | link |
| Modify | `src/main/resources/application.yml`, `application-prod.yml` | `token-encryption-key`, `webhook-base-url` |
| Create | `src/test/java/com/example/roshdeandishe/SchoolBotFlowIntegrationTest.java` | end-to-end multi-school scenario |
| Modify | `openspec/specs/current-system.md`, `DEPLOYMENT.md`, `openspec/changes/bale-smart-bot.md`, `openspec/changes/bale-school-bots.md` | docs |

---

### Task 1: Persistence, token encryption, secrets

**Files:**
- Create: `src/main/resources/db/migration/V32__school_bale_bots.sql`
- Create: `internal/SchoolBotEntity.java`, `internal/SchoolBotRepository.java`, `internal/SchoolBotStatus.java`, `internal/TokenCipher.java`, `internal/WebhookSecrets.java`, `internal/BotRef.java`
- Modify: `internal/BaleLinkEntity.java:30,33` (remove `unique = true`, add `botId`), `internal/BaleLinkRepository.java`
- Modify: `src/main/resources/application.yml:161-172`, `src/main/resources/application-prod.yml:153-158`
- Test: `src/test/java/com/example/roshdeandishe/notification/internal/TokenCipherTest.java`, `WebhookSecretsTest.java`, `BotRefTest.java`

**Interfaces:**
- Produces:
  - **`record BotRef(Long botId, Long schoolId)`** (both null for the shared bot):
    - `static final BotRef SHARED`;
    - `static BotRef school(long botId, long schoolId)`;
    - `boolean isShared()`.
  - **`enum SchoolBotStatus { ACTIVE, DISABLED }`**.
  - **`SchoolBotEntity`** (Lombok `@Getter/@Setter/@NoArgsConstructor`, table `bale.school_bots`). Fields:
    - `Long id`, `Long schoolId`, `Long botUserId`, `String botUsername`;
    - `String tokenCiphertext`, `int tokenKeyVersion`, `String webhookSecretHash`;
    - `boolean businessEnabled`, `SchoolBotStatus status` (`@Enumerated(STRING)`);
    - `Instant lastUpdateAt`, `String lastError`, `Instant lastErrorAt`;
    - `Instant createdAt`, `Long createdBy`, `Instant updatedAt`, `Long updatedBy`.
  - **`SchoolBotRepository extends JpaRepository<SchoolBotEntity, Long>`**:
    - `Optional<SchoolBotEntity> findBySchoolId(Long schoolId)`;
    - `Optional<SchoolBotEntity> findByBotUserId(Long botUserId)`;
    - `Optional<SchoolBotEntity> findByWebhookSecretHashAndStatus(String hash, SchoolBotStatus status)`;
    - `Optional<SchoolBotEntity> findBySchoolIdAndStatus(Long schoolId, SchoolBotStatus status)`;
    - `@Query("select b.schoolId from SchoolBotEntity b where b.status = ACTIVE") Set<Long> findActiveSchoolIds()`.
  - **`BaleLinkEntity`**: new `Long botId` (`bot_id`, nullable) and constructor `BaleLinkEntity(Long botId, Long userId, Long chatId, Instant linkedAt)`. Remove the old three-argument constructor and fix its callers.
  - **`BaleLinkRepository`** (keep the old two methods only if still used; otherwise delete them):
    - `findByBotIdIsNullAndChatId(Long)`, `findByBotIdIsNullAndUserId(Long)`;
    - `findByBotIdAndChatId(Long, Long)`, `findByBotIdAndUserId(Long, Long)`;
    - `long countByBotId(Long)`, `void deleteByBotId(Long)`;
    - `default Optional<BaleLinkEntity> findByChat(BotRef bot, Long chatId)` and `default Optional<BaleLinkEntity> findByUser(BotRef bot, Long userId)`, which pick the `IsNull` or `botId` variant.
  - **`TokenCipher`** (`@Component`):
    - Constructor `TokenCipher(@Value("${roshdeandishe.bale.token-encryption-key}") String base64Key)`.
    - `boolean available()`, `Encrypted encrypt(String plain)`, `String decrypt(String ciphertext, int keyVersion)`, `record Encrypted(String ciphertext, int keyVersion)`.
  - **`final class WebhookSecrets`**: `static String newSecret()` (32 random bytes from `SecureRandom`, Base64-URL without padding) and `static String hash(String secret)` (SHA-256, lowercase hex).

- [ ] **Step 1: Write the failing tests**

  ```java
  // TokenCipherTest
  @Test void encryptThenDecryptRoundTrips()   // key = Base64 of 32 bytes; decrypt(encrypt("123:abc")) == "123:abc"; keyVersion == 1
  @Test void sameTokenEncryptsDifferentlyEachTime() // two encrypt() ciphertexts differ (random nonce)
  @Test void tamperedCiphertextIsRejected()   // flip one byte of the decoded ciphertext → decrypt throws IllegalStateException
  @Test void blankKeyIsUnavailableAndRefusesToEncrypt() // new TokenCipher("").available() == false; encrypt throws IllegalStateException
  @Test void keyOfWrongLengthFailsAtConstruction()       // Base64 of 16 bytes → IllegalStateException in constructor
  // WebhookSecretsTest
  @Test void newSecretHas256BitsAndIsUrlSafe() // decoded length 32; matches [A-Za-z0-9_-]+; two calls differ
  @Test void hashIsSha256Hex()                 // hash("abc") == "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
  // BotRefTest
  @Test void sharedHasNoIds()                  // BotRef.SHARED.isShared(); botId()==null; school(5,7).isShared()==false
  ```

- [ ] **Step 2: Run them and confirm they fail.** Run `./mvnw -s … test -Dtest='TokenCipherTest,WebhookSecretsTest,BotRefTest'`. Expected: compilation failure (types missing).

- [ ] **Step 3: Write migration `V32__school_bale_bots.sql`.** Start with a Persian header comment, following the repo convention.

  Create `bale.school_bots` with the columns above:
  - `id bigint generated by default as identity primary key`;
  - `school_id bigint not null unique`;
  - `bot_user_id bigint not null unique`;
  - `bot_username varchar(64) not null`;
  - `token_ciphertext text not null`;
  - `token_key_version int not null`;
  - `webhook_secret_hash char(64) not null unique`;
  - `business_enabled boolean not null default false`;
  - `status varchar(16) not null`;
  - `last_update_at timestamptz`, `last_error varchar(500)`, `last_error_at timestamptz`;
  - `created_at timestamptz not null`, `created_by bigint`, `updated_at timestamptz not null`, `updated_by bigint`.

  Then change `bale.bale_links`:

  ```sql
  alter table bale.bale_links add column bot_id bigint references bale.school_bots(id) on delete cascade;
  alter table bale.bale_links drop constraint if exists bale_links_chat_id_key;
  alter table bale.bale_links drop constraint if exists bale_links_user_id_key;
  create unique index ux_bale_links_shared_chat on bale.bale_links (chat_id) where bot_id is null;
  create unique index ux_bale_links_shared_user on bale.bale_links (user_id) where bot_id is null;
  create unique index ux_bale_links_bot_chat on bale.bale_links (bot_id, chat_id) where bot_id is not null;
  create unique index ux_bale_links_bot_user on bale.bale_links (bot_id, user_id) where bot_id is not null;
  ```

  The constraint names `bale_links_chat_id_key` and `bale_links_user_id_key` are the PostgreSQL defaults for the unnamed inline `unique` in `V1__baseline_schema.sql:497-503`. `SchoolBotFlowIntegrationTest` (Task 9) inserts two rows with the same `chat_id` and different `bot_id`, which proves the old constraint is gone.

- [ ] **Step 4: Implement the entities, repositories, `BotRef`, `TokenCipher` and `WebhookSecrets`** with the signatures above.
  - `TokenCipher` stores `Base64(nonce ‖ ciphertext+tag)` and uses `AES/GCM/NoPadding` with a 128-bit tag.
  - Blank key: `available()` is false and `encrypt`/`decrypt` throw `IllegalStateException`.
  - A non-blank key that is not 32 bytes fails in the constructor, so a misconfigured server fails at startup.

- [ ] **Step 5: Add config.**
  - In `application.yml`, under `roshdeandishe.bale`, add each key with a Persian comment:

    ```yaml
    token-encryption-key: ${BALE_TOKEN_ENCRYPTION_KEY:}
    webhook-base-url: ${APP_BASE_URL:http://localhost:8080}
    ```

  - In `application-prod.yml`, under `roshdeandishe.bale`:

    ```yaml
    token-encryption-key: ${BALE_TOKEN_ENCRYPTION_KEY:}
    webhook-base-url: ${APP_BASE_URL}
    ```

- [ ] **Step 6: Run the tests and confirm they pass.** Run the Step 2 command, then the fast suite. Expected: all green. `ModularityTests` and `ArchitectureTests` must pass.

- [ ] **Step 7: Commit** with message `feat(notification): school bot persistence, token encryption and webhook secrets`.

### Task 2: Token per call in `BaleClient`, plus bot-management calls

**Files:**
- Modify: `internal/BaleClient.java`
- Create: `internal/BotTokens.java`
- Modify callers: `internal/BaleUpdateHandler.java`, `internal/BaleChannel.java`, and any other `baleClient.` call site. Every call temporarily passes `botTokens.tokenFor(BotRef.SHARED)`, so behavior is unchanged.
- Test: `BaleClientDeliverTest`, `BaleClientInlineKeyboardTest`, `BaleClientMenuTest`, `BaleClientTokenLoggingTest`, plus new `BaleClientBotApiTest` and `BotTokensTest`.

**Interfaces:**
- Consumes: `BotRef`, `SchoolBotRepository`, `TokenCipher` (Task 1).
- Produces:
  - **`BaleClient`** constructor becomes `BaleClient(@Value("${roshdeandishe.bale.api-base-url}") String apiBaseUrl)`. The token is no longer a field. Every method takes `String token` as its first parameter:
    - `sendMessage(String token, Long chatId, String text)`;
    - `deliver(String token, Long chatId, String text)`;
    - `sendContactRequest(String token, Long chatId, String text)`;
    - `sendMenu(String token, Long chatId, String text, List<List<String>> rows)`;
    - `sendMessage(String token, Long chatId, String text, List<List<InlineButton>> inlineKeyboard)`;
    - `answerCallbackQuery(String token, String callbackQueryId)`.
  - **`record SendResult(DeliveryResult result, int httpStatus)`** (`httpStatus` = 0 when no HTTP response was received) and **`SendResult deliverDetailed(String token, Long chatId, String text)`**. `deliver` returns `deliverDetailed(...).result()`.
  - **`record BotInfo(long id, String username)`** and **`BotInfoResult getMe(String token)`**, where `sealed interface BotInfoResult` has:
    - `record Found(BotInfo bot)`;
    - `record InvalidToken()` (HTTP 401 or 404, or `ok:false`);
    - `record Unreachable()` (network error, 5xx, 429).
  - **`boolean setWebhook(String token, String url)`** (true only when HTTP 200 and `ok:true`) and **`boolean deleteWebhook(String token)`**.
  - **`BotTokens`** (`@Component`):
    - Constructor `BotTokens(@Value("${roshdeandishe.bale.bot-token}") String sharedToken, SchoolBotRepository bots, TokenCipher cipher)`.
    - `String tokenFor(BotRef bot)`: the shared token for `SHARED`, otherwise the decrypted token of `bots.findById(bot.botId())`. Returns `""` when missing; `BaleClient` already treats a blank token as "do not call".

- [ ] **Step 1: Update the existing `BaleClient*Test`s to the new signatures and add the failing tests**

  ```java
  // BaleClientBotApiTest (JDK HttpServer, like BaleClientDeliverTest)
  @Test void getMeReturnsBotIdAndUsername()   // server /botT/getMe → {"ok":true,"result":{"id":42,"is_bot":true,"first_name":"x","username":"school_bot"}} → Found(BotInfo(42,"school_bot"))
  @Test void getMeWith401IsInvalidToken()     // → InvalidToken
  @Test void getMeWith502IsUnreachable()      // → Unreachable; unreachable server → Unreachable
  @Test void setWebhookPostsTheUrl()          // request to /botT/setWebhook contains "\"url\":\"https://x/api/bale/webhook/s\""; returns true on {"ok":true,"result":true}
  @Test void setWebhookFalseWhenOkFalse()     // {"ok":false} → false
  @Test void deleteWebhookCallsTheMethod()    // path /botT/deleteWebhook hit once
  @Test void deliverDetailedReportsHttpStatus() // 401 → SendResult(UNDELIVERABLE, 401); 200 → (SENT, 200); unreachable → (TEMPORARY_FAILURE, 0)
  // BotTokensTest
  @Test void sharedRefUsesConfiguredToken()
  @Test void schoolRefDecryptsStoredToken()   // repository mock returns entity with cipher.encrypt("999:zz") → "999:zz"
  @Test void unknownSchoolBotGivesBlank()
  ```

  `BaleClientTokenLoggingTest` keeps its assertion that the token never appears in output. Extend it to `getMe`, `setWebhook` and `deleteWebhook`.

- [ ] **Step 2: Run them and confirm they fail.** Run `./mvnw -s … test -Dtest='BaleClient*Test,BotTokensTest'`. Expected: compilation failure.

- [ ] **Step 3: Implement the new `BaleClient` signatures and methods, and `BotTokens`.**
  - Parse responses with a standalone `ObjectMapper`.
  - Failures log `failureKind(e)` only.
  - Then update every caller to pass `botTokens.tokenFor(BotRef.SHARED)`. Inject `BotTokens` where needed.

- [ ] **Step 4: Run the tests and confirm they pass.** Run the Step 2 command, then the fast suite. Expected: green, with `BaleUpdateHandlerTest` and `BaleChannelTest` adapted to the new constructor and argument lists only.

- [ ] **Step 5: Commit** with message `refactor(notification): Bale calls take the bot token per call; add getMe/setWebhook/deleteWebhook`.

### Task 3: Generic queue keys and per-bot share

**Files:**
- Modify: `internal/RecentUpdateIds.java`, `internal/ChatSerialExecutor.java`
- Test: `RecentUpdateIdsTest`, `ChatSerialExecutorTest`

**Interfaces:**
- Produces:
  - **`final class RecentUpdateIds<K>`**: `RecentUpdateIds(int capacity)`, `boolean markSeen(K key)`, `void forget(K key)`.
  - **`final class ChatSerialExecutor<K>`**:
    - `ChatSerialExecutor(Executor pool, int maxPending, int maxPendingPerGroup, Function<K, ?> group)`;
    - `void execute(K key, Runnable task)`.

    A null key still runs unordered and uncounted. A task is rejected with `RejectedExecutionException` when the global `pending >= maxPending` **or** the pending count of `group.apply(key)` is `>= maxPendingPerGroup`. A null group value is a valid group, used for the shared bot.

- [ ] **Step 1: Adapt the existing tests to the generic types and add**

  ```java
  @Test void oneGroupCannotExceedItsShare()     // maxPending 100, perGroup 40, group = k -> k.bot; 40 tasks for bot A queued (blocking pool), 41st for A rejected, a task for bot B accepted
  @Test void groupCountIsReleasedWhenTasksRun() // after A's tasks drain, A can queue again
  @Test void sameChatInTwoGroupsRunsIndependently() // keys (A,5) and (B,5) are separate queues
  // RecentUpdateIdsTest
  @Test void sameUpdateIdInTwoBotsIsNotADuplicate() // markSeen(key(A,9)) and markSeen(key(B,9)) both true
  ```

  Use a test-local `record Key(Long bot, Long chat)`.

- [ ] **Step 2: Run them and confirm they fail.** Run `./mvnw -s … test -Dtest='ChatSerialExecutorTest,RecentUpdateIdsTest'`.

- [ ] **Step 3: Implement.** Keep a `Map<Object, Integer> pendingPerGroup` next to `pending`, under the same lock. Increment it on enqueue, decrement it when a task is polled and on rollback, and remove the entry at zero. Keep `BaleWebhookService` compiling by using a temporary `Long` key, `maxPendingPerGroup = 100` and `group = k -> null`. Task 4 replaces these.

- [ ] **Step 4: Run the tests and confirm they pass.** Run the Step 2 command, then the fast suite.

- [ ] **Step 5: Commit** with message `feat(notification): generic per-chat queue keys with a per-group share`.

### Task 4: Inbound bot identity

**Files:**
- Create: `internal/BotResolver.java`
- Modify: `NotificationFacade.java`, `internal/NotificationFacadeImpl.java`, `controller/BaleWebhookController.java`, `internal/BaleWebhookService.java`
- Test: `BotResolverTest`, `BaleWebhookServiceTest`, `NotificationFacadeImplTest`, and the controller's existing test if present.

**Interfaces:**
- Consumes:
  - `BotRef`, `SchoolBotRepository.findByWebhookSecretHashAndStatus`, `WebhookSecrets.hash` (Task 1);
  - `ChatSerialExecutor<K>`, `RecentUpdateIds<K>` (Task 3).
- Produces:
  - **`BotResolver`** (`@Component`):
    - Constructor `BotResolver(@Value("${roshdeandishe.bale.webhook-secret}") String sharedSecret, SchoolBotRepository bots)`.
    - `Optional<BotRef> resolve(String secret)`:
      - a blank or null secret gives empty;
      - a match with a non-blank shared secret (`MessageDigest.isEqual`) gives `SHARED`;
      - otherwise `findByWebhookSecretHashAndStatus(hash(secret), ACTIVE)` gives `BotRef.school(id, schoolId)`, or empty.
  - **`NotificationFacade`**: replace `void processIncomingUpdate(String rawJson)` with `boolean processIncomingUpdate(String secret, String rawJson)`. It returns false when the secret resolves to no bot (the controller then answers `404`). Otherwise it hands the body to the webhook service and returns true.
  - **`BaleWebhookService.processRawUpdate(BotRef bot, String rawBody)`**. Internal keys:
    - `record UpdateKey(Long botId, long updateId)` for de-duplication;
    - `record ChatKey(Long botId, Long chatId)` for ordering;
    - `group = ChatKey::botId`, with `maxPending` 100 and `maxPendingPerGroup` 40.
  - **`BaleUpdateHandler.handle(BotRef bot, BaleUpdate update)`**: signature only in this task. The body may ignore `bot` until Task 5.
  - **`BaleWebhookController`**: no `@Value` any more. It returns `404` when `!notificationFacade.processIncomingUpdate(secret, rawBody)`, otherwise `200`. Keep the existing comments about always answering fast.

- [ ] **Step 1: Write the failing tests**

  ```java
  // BotResolverTest
  @Test void sharedSecretResolvesToSharedBot()
  @Test void schoolSecretResolvesToItsBot()   // repo returns entity id 3, school 7 for hash(secret) & ACTIVE → BotRef.school(3,7)
  @Test void disabledBotSecretIsUnknown()     // repo returns empty for ACTIVE → Optional.empty()
  @Test void blankSharedSecretMatchesNothing() // sharedSecret "" and request secret "" → empty, no repository call
  // BaleWebhookServiceTest
  @Test void sameUpdateIdFromTwoBotsIsHandledTwice()
  @Test void handlerReceivesTheResolvedBot()  // verify(handler).handle(BotRef.school(3,7), update)
  @Test void oneBotIsCappedAtFortyQueuedUpdates() // blocking executor; 41st update of the same bot dropped and its id forgotten; another bot's update accepted
  // NotificationFacadeImplTest
  @Test void unknownSecretIsRejectedWithoutParsing() // returns false; webhook service never called
  ```

- [ ] **Step 2: Run them and confirm they fail.** Run `./mvnw -s … test -Dtest='BotResolverTest,BaleWebhookServiceTest,NotificationFacadeImplTest'`.

- [ ] **Step 3: Implement** with the signatures above. Remove the temporary key and the group function left by Task 3.

- [ ] **Step 4: Run the tests and confirm they pass.** Run the Step 2 command, then the fast suite. `BaleFlowIntegrationTest` must still compile; its secret `test-webhook-secret` keeps resolving to `SHARED`.

- [ ] **Step 5: Commit** with message `feat(notification): resolve the bot from the webhook secret and carry it with each update`.

### Task 5: Bot-scoped handling, scope filter, membership check on link

**Files:**
- Modify: `internal/BaleUpdateHandler.java`, `internal/BotContextLoader.java`, `internal/BotTexts.java`
- Create: `internal/SchoolBotActivity.java` (throttled `last_update_at`)
- Test: `BaleUpdateHandlerTest`, `BotContextLoaderTest`, new `SchoolBotActivityTest`

**Interfaces:**
- Consumes:
  - `BotRef`, `BaleLinkRepository.findByChat/findByUser`, `SchoolBotRepository.findActiveSchoolIds` (Task 1);
  - `BotTokens.tokenFor` (Task 2);
  - `handle(BotRef, BaleUpdate)` (Task 4);
  - `IamFacade.findMembershipsByUser(UserId)`, `IamFacade.isPlatformAdmin(UserId)`.
- Produces:
  - **`BotContextLoader.load(BotRef bot, Long chatId)`**, returning `Optional<BotContext>`. It looks up the link with `findByChat(bot, chatId)` and keeps active memberships, then filters them:
    - **school bot:** keep `m.schoolId().equals(bot.schoolId())`;
    - **shared bot:** drop `m.schoolId()` in `findActiveSchoolIds()`.

    `BotContext` keeps its components.
  - **`BotTexts.NOT_A_MEMBER = "شما عضو این مدرسه نیستید."`**
  - **Every reply in `BaleUpdateHandler`** uses `botTokens.tokenFor(bot)`. `bot` is passed through `handleCallback`, `handleContactShared`, `sendAfterCommit/sendLinkConfirmation`, `send` and `describe*`.
  - **Linking:** `handleContactShared(BotRef bot, Long chatId, BaleMessage message)` keeps its current order (own contact, then phone match). For a school bot it then requires `iamFacade.findMembershipsByUser(id)` to contain an active membership of `bot.schoolId()`, **or** `iamFacade.isPlatformAdmin(id)`. Otherwise it sends `NOT_A_MEMBER` and returns without saving. The upsert uses `findByUser(bot, userId)` and sets `botId = bot.botId()`.
  - **`SchoolBotActivity`** (`@Component`):
    - Constructor `SchoolBotActivity(SchoolBotRepository bots, Clock clock)`, plus a public constructor that uses `Clock.systemUTC()`, following the `SchoolAnswers` pattern.
    - `void touch(BotRef bot)`: for a school bot, writes `lastUpdateAt = now` at most once per 60 seconds per bot, tracked in an in-memory `ConcurrentHashMap<Long, Instant>`. The shared bot is a no-op.
    - Called from `BaleWebhookService.processRawUpdate` after a successful enqueue.

- [ ] **Step 1: Write the failing tests**

  ```java
  // BotContextLoaderTest
  @Test void schoolBotKeepsOnlyItsSchoolMemberships()      // memberships in 7 and 8; BotRef.school(3,7) → only school 7
  @Test void sharedBotDropsSchoolsWithActiveBots()          // activeSchoolIds = {7}; SHARED → only school 8
  @Test void sharedBotServesSchoolWhoseBotIsDisabled()      // activeSchoolIds = {} → schools 7 and 8
  @Test void endedMembershipShowsNoSchoolDataInSchoolBot()  // only an inactive membership in 7 → context with empty memberships
  @Test void linkIsLookedUpPerBot()                         // findByChat(BotRef.school(3,7), 5) used, not the shared lookup
  // BaleUpdateHandlerTest
  @Test void schoolBotRepliesWithItsOwnToken()              // botTokens.tokenFor(school ref) = "school-token" → baleClient.sendMessage("school-token", …)
  @Test void nonMemberContactInSchoolBotIsRefused()         // own contact, phone matches, no membership in 7 → NOT_A_MEMBER, no save
  @Test void platformAdminMayLinkToAnySchoolBot()           // no membership, isPlatformAdmin true → saved with botId 3
  @Test void sameChatIdIsLinkedSeparatelyPerBot()           // existing shared link for user; contact in school bot creates a second link (botId 3) and leaves the shared one unchanged
  @Test void ownershipIsStillCheckedBeforeMembership()      // foreign contact in school bot → CONTACT_NOT_OWN, no phone lookup, no membership lookup
  // SchoolBotActivityTest
  @Test void writesAtMostOncePerMinutePerBot()              // touch ×3 within 59 s → one save; after 61 s → second save
  @Test void sharedBotIsNoOp()
  ```

- [ ] **Step 2: Run them and confirm they fail.** Run `./mvnw -s … test -Dtest='BotContextLoaderTest,BaleUpdateHandlerTest,SchoolBotActivityTest'`.

- [ ] **Step 3: Implement** with the interfaces above. The private-chat check and the contact-ownership check from PR #156 stay first and unchanged.

- [ ] **Step 4: Run the tests and confirm they pass.** Run the Step 2 command, then the fast suite.

- [ ] **Step 5: Commit** with message `feat(notification): scope each bot to its school and check membership on link`.

### Task 6: Outbound routing by school

**Files:**
- Modify: `NotificationFacade.java`, `internal/NotificationFacadeImpl.java`, `internal/NotificationChannel.java`, `internal/BaleChannel.java`
- Modify callers: `internal/AbsenceRecordedEventListener.java:70`, `internal/InvoiceIssuedEventListener.java:62`, `internal/EventReminderJob.java:145`
- Create: nothing. Disabling goes through `SchoolBotRepository` directly here and moves behind `SchoolBotService.markDisabled` in Task 7.
- Test: `BaleChannelTest`, `NotificationFacadeImplTest`, `AbsenceRecordedEventListenerTest`, `EventReminderJobTest`, and the invoice listener test if present.

**Interfaces:**
- Consumes: `SchoolBotRepository.findBySchoolIdAndStatus`, `BaleLinkRepository.findByUser`, `BotTokens`, `BaleClient.deliverDetailed`.
- Produces:
  - **`NotificationFacade`** gains `boolean sendMessageToUser(Long userId, Long schoolId, String text)` and `DeliveryResult deliverMessageToUser(Long userId, Long schoolId, String text)`. The two-argument versions stay and route as `schoolId = null`.
  - **`NotificationChannel`** gains `default DeliveryResult deliver(Long userId, Long schoolId, String text)`, which defaults to `deliver(userId, text)`.
  - **`BaleChannel.deliver(userId, schoolId, text)`**:
    - **The school has an ACTIVE bot:** look up `findByUser(BotRef.school(botId, schoolId), userId)`. No link gives `NOT_LINKED`. Otherwise call `deliverDetailed(tokenFor(ref), chatId, text)`. On HTTP `401`, set the bot `DISABLED` with `lastError` «توکن بات نامعتبر شده است» and `lastErrorAt = now`, then return `UNDELIVERABLE`.
    - **Otherwise** (no school, no bot, or a DISABLED bot): use the shared link, as today.
  - **Callers** pass `event.schoolId()` (absence, invoice) and `school.id().value()` (reminder job).

- [ ] **Step 1: Write the failing tests**

  ```java
  // BaleChannelTest
  @Test void schoolWithActiveBotUsesItsLinkAndToken()
  @Test void userNotLinkedToSchoolBotIsNotLinkedEvenWithSharedLink() // shared link exists → NOT_LINKED, no HTTP call
  @Test void schoolWithoutBotUsesSharedLink()
  @Test void disabledSchoolBotRoutesToSharedBot()
  @Test void unauthorizedFromSchoolBotDisablesIt()   // deliverDetailed → (UNDELIVERABLE, 401) → entity status DISABLED, lastError set, result UNDELIVERABLE
  // listener / job tests
  @Test void absenceNotificationCarriesTheSchool()   // verify(notificationFacade).sendMessageToUser(parentId, event.schoolId(), text)
  @Test void reminderCarriesTheSchool()              // verify(...).deliverMessageToUser(userId, schoolId, text)
  ```

- [ ] **Step 2: Run them and confirm they fail.** Run `./mvnw -s … test -Dtest='BaleChannelTest,NotificationFacadeImplTest,AbsenceRecordedEventListenerTest,EventReminderJobTest'`.

- [ ] **Step 3: Implement** with the interfaces above. `NotificationFacadeImpl` keeps its channel loop and worst-result rule, now calling the school-aware `deliver`.

- [ ] **Step 4: Run the tests and confirm they pass.** Run the Step 2 command, then the fast suite.

- [ ] **Step 5: Commit** with message `feat(notification): route school notifications to the school's own bot`.

### Task 7: Register, replace and delete a school bot (service + API)

**Files:**
- Create: `SchoolBotFacade.java`, `internal/SchoolBotService.java`, `internal/RegistrationAttempts.java`
- Create: `shared/NotificationError.java`, `shared/NotificationException.java`, `shared/NotificationExceptionHandler.java`
- Create: `controller/SchoolBotController.java`, `dto/request/RegisterSchoolBotRequest.java`, `dto/response/SchoolBotResponse.java`
- Modify: `internal/BaleChannel.java` (call `markDisabled`), `src/main/java/com/example/roshdeandishe/iam/internal/IamSeedDataRunner.java` (add permission `BALE_BOT_MANAGE` «مدیریت بات بله‌ی مدرسه» in `seedPermissions()` and `grant(roles.get("SCHOOL_ADMIN"), …)` in `seedRolePermissions()`)
- Test: `SchoolBotServiceTest`, `RegistrationAttemptsTest`, `SchoolBotControllerTest` (only if the module has a WebMvc slice precedent; otherwise the integration test in Task 9 covers the controller)

**Interfaces:**
- Consumes: Tasks 1, 2 and 6 (`TokenCipher`, `WebhookSecrets`, `BaleClient.getMe/setWebhook/deleteWebhook`, repositories), plus `IamFacade.currentUserId()`.
- Produces:
  - **`public interface SchoolBotFacade`**:
    - `Optional<SchoolBotInfo> find(Long schoolId)`;
    - `SchoolBotInfo register(Long schoolId, String token)`, which registers or replaces;
    - `void delete(Long schoolId)`;
    - `record SchoolBotInfo(String username, SchoolBotState status, long linkedMembers, Instant lastUpdateAt, String lastError, Instant lastErrorAt)`;
    - `enum SchoolBotState { ACTIVE, DISABLED }`.
  - **`sealed interface NotificationError`**, with:
    - `record BotTokenInvalid()` → 400;
    - `record BotAlreadyRegistered()` → 409 (another school holds that bot);
    - `record BaleUnavailable()` → 503;
    - `record BotEncryptionUnavailable()` → 503;
    - `record TooManyAttempts()` → 429;
    - `record SchoolBotNotFound(Long schoolId)` → 404.

    Plus `NotificationException(NotificationError)` and a handler `@Component("notificationExceptionHandler")` that builds ProblemDetail through the existing `platform/ProblemDetailFactory`, the same as attendance does.
  - **`RegistrationAttempts`** (`@Component`): `RegistrationAttempts(Clock)` plus a public constructor with `Clock.systemUTC()`, and `boolean tryAcquire(Long schoolId)`. It is a sliding window of **10 per school per 3600 s**, held in memory.
  - **`SchoolBotService implements SchoolBotFacade`** (`@Service`, `@Transactional` on mutations). It also has `void markDisabled(Long botId, String error)`. `register` runs these steps in order:
    1. `tryAcquire`, or throw `TooManyAttempts`;
    2. `token = token == null ? "" : token.strip()`; a blank token throws `BotTokenInvalid` without calling Bale;
    3. `cipher.available()`, or throw `BotEncryptionUnavailable`;
    4. `getMe` → `InvalidToken` throws `BotTokenInvalid`, `Unreachable` throws `BaleUnavailable`;
    5. `findByBotUserId(bot.id())` belongs to another school → `BotAlreadyRegistered`;
    6. `secret = newSecret()`; `setWebhook(token, webhookBaseUrl + "/api/bale/webhook/" + secret)`, or throw `BaleUnavailable`. `webhookBaseUrl` comes from `@Value("${roshdeandishe.bale.webhook-base-url}")`, with a trailing `/` stripped;
    7. upsert by `schoolId`. If an existing row had a different `botUserId`, call `deleteByBotId(existing.id)` first. Then set the encrypted token, `hash(secret)`, `ACTIVE`, clear `lastError`/`lastErrorAt`, and set audit fields from `iamFacade.currentUserId()`;
    8. log at INFO `"school bot registered: school={}, bot=@{}, by user={}"` (no token, no secret).
  - **`delete`**:
    - decrypts the token and calls `deleteWebhook` (best effort; a failure is ignored);
    - deletes the row, and the links cascade;
    - logs at INFO `"school bot deleted: school={}, by user={}"`.
  - **`SchoolBotController`**: `@RestController @RequestMapping("/api/admin/schools/{schoolId}/bale-bot")`. Each method calls `TenantContext.requireMatches(schoolId)` and carries `@RequiresPermission("BALE_BOT_MANAGE")`, with no `@RequiresModule`:
    - `GET` → `200 SchoolBotResponse`, or `204` when there is no bot;
    - `PUT` with body `RegisterSchoolBotRequest(String token)` → `200 SchoolBotResponse`;
    - `DELETE` → `204`.
  - **`SchoolBotResponse`**: `(String username, String link, String status, long linkedMembers, Instant lastUpdateAt, String lastError, Instant lastErrorAt)`, with `link = "https://ble.ir/" + username`.

- [ ] **Step 1: Write the failing tests**

  ```java
  // SchoolBotServiceTest (mocks: BaleClient, repositories, IamFacade; real TokenCipher with a test key; fixed Clock)
  @Test void registersAndStoresOnlyEncryptedTokenAndSecretHash() // saved entity: ciphertext != token, decrypts to token; webhookSecretHash == hash(secret in the setWebhook URL); status ACTIVE
  @Test void webhookUrlUsesBaseUrlAndSecret()   // setWebhook URL starts with "https://app.example/api/bale/webhook/" (base configured with trailing slash)
  @Test void tokenIsTrimmedAndBlankIsInvalidWithoutCallingBale() // "  123:abc \n" → getMe("123:abc"); "   " → BotTokenInvalid, verifyNoInteractions(baleClient)
  @Test void invalidTokenIsRefusedAndNothingSaved()
  @Test void unreachableBaleIsRefusedAndNothingSaved()
  @Test void botOfAnotherSchoolIsRefused()       // findByBotUserId → entity of school 99 → BotAlreadyRegistered
  @Test void missingEncryptionKeyIsRefusedBeforeCallingBale()
  @Test void eleventhAttemptInAnHourIsRefused()
  @Test void replacingWithDifferentBotDeletesOldLinksAndSecret() // existing botUserId 1, new getMe id 2 → deleteByBotId(existing.id); new hash != old hash
  @Test void replacingWithSameBotKeepsLinks()    // same botUserId → deleteByBotId never called
  @Test void deleteCallsDeleteWebhookAndRemovesRow() // also when deleteWebhook returns false
  @Test void markDisabledSetsStatusAndError()
  // RegistrationAttemptsTest
  @Test void tenPerHourPerSchool()               // 10 true, 11th false, other school true, after 3601 s true again
  ```

- [ ] **Step 2: Run them and confirm they fail.** Run `./mvnw -s … test -Dtest='SchoolBotServiceTest,RegistrationAttemptsTest'`.

- [ ] **Step 3: Implement** with the interfaces above. Replace the direct repository update in `BaleChannel` (Task 6) with `schoolBotService.markDisabled(...)`. Add the permission seed lines.

- [ ] **Step 4: Run the tests and confirm they pass.** Run the Step 2 command, then the fast suite. `ModularityTests` must accept the new public `SchoolBotFacade`, and `ArchitectureTests` must confirm the controller and DTOs do not touch `@Entity`.

- [ ] **Step 5: Commit** with message `feat(notification): register, replace and delete a school's Bale bot`.

### Task 8: «بات بله‌ی مدرسه» page

Before starting, apply `.agents/skills/design-page/SKILL.md` and read `DESIGN.md` and `UX_ROADMAP.md` §2, as the skill requires. The page states and copy come from the spec section "Pages".

**Files:**
- Create: `src/main/java/com/example/roshdeandishe/web/controller/SchoolBotPageController.java`, `src/main/jte/bale-bot.jte`, `src/main/resources/static/js/bale-bot.js`
- Modify: `src/main/java/com/example/roshdeandishe/web/model/NavCatalog.java`, in group `"school"`: `new Link("/bale-bot", "بات بله‌ی مدرسه", "<desc>", List.of(ADMIN), List.of()).withPermissions("BALE_BOT_MANAGE").withIcon(...)`
- Test: a page render test modelled on an existing `*PageRenderTest`, plus `NavPermissionsFlowIntegrationTest` (CI), which must stay green.

**Interfaces:**
- Consumes: the REST API from Task 7 (`GET`/`PUT`/`DELETE /api/admin/schools/{schoolId}/bale-bot`), called from JS with the `X-School-ID` header like other school pages.
- Produces: `GET /bale-bot` → template `bale-bot`, with only the `navbar` model attribute, following `FinanceAuditPageController`.

- [ ] **Step 1: Write the failing render test**

  ```java
  @Test void pageHasTokenFieldAndPrimaryAction() // rendered HTML contains input type="password", the label «ثبت بات», and the four state containers (none / active / disabled / error) bound in Alpine
  @Test void tokenIsNeverRenderedBack()          // the active-state markup binds username and link only; no element binds a token field from the GET response
  ```

- [ ] **Step 2: Run it and confirm it fails.**

- [ ] **Step 3: Implement the controller, template, JS and nav link.** The page states and their copy follow the spec exactly:
  - none;
  - error messages per `NotificationError` (status 400/409/429/503);
  - active (username, link, linked members, last update, «تعویض توکن», and «حذف بات» with a confirmation that states the consequences);
  - disabled warning.

  The business-API switch is **not** shown in PR 1.

- [ ] **Step 4: Run the tests and confirm they pass.** Run the render test, then the fast suite.

- [ ] **Step 5: Commit** with message `feat(web): school Bale bot settings page`.

### Task 9: End-to-end test and documentation

**Files:**
- Create: `src/test/java/com/example/roshdeandishe/SchoolBotFlowIntegrationTest.java`, following `BaleFlowIntegrationTest`:
  - `@SpringBootTest @AutoConfigureMockMvc @Import(TestcontainersConfiguration.class)`;
  - a JDK `HttpServer` fake Bale API serving `/bot<token>/getMe`, `/setWebhook`, `/deleteWebhook` and `/sendMessage` for both the shared and the school token;
  - a `@DynamicPropertySource` for `api-base-url`, `bot-token`, `webhook-secret`, `token-encryption-key` (a fixed test key) and `webhook-base-url`.
- Modify: `openspec/specs/current-system.md` (Bale section: school bots, routing, scope, page), `DEPLOYMENT.md` (env table: `BALE_TOKEN_ENCRYPTION_KEY`, how to generate it with `openssl rand -base64 32`, and that it must not change once bots are registered, because there is no re-encryption tool yet), `openspec/changes/bale-smart-bot.md` (cross-reference), `openspec/changes/bale-school-bots.md` (status: PR 1 implemented).

- [ ] **Step 1: Write the integration test.** It covers spec acceptance criteria 1–6, 8, 9 and 11–12, and asserts each of the following:
  - `PUT` registration by a school admin of school A → `200`; the fake received `setWebhook` with the secret URL; the database row's `token_ciphertext` ≠ token.
  - An update posted to the captured secret URL is answered through the school token's `/sendMessage`. An unknown secret → `404`.
  - A user who is a member of A and B, linked to both bots (same `chat_id`, which proves V32's uniqueness): «مدرسه‌های من» in A's bot lists only A, and in the shared bot only B.
  - A non-member contact in A's bot gets «شما عضو این مدرسه نیستید.» and no link row.
  - An absence notification for A goes to A's bot. One for B goes to the shared bot.
  - The fake answers `401` for the school token on `/sendMessage` → the bot becomes `DISABLED` → the shared bot serves A again.
  - `DELETE` → links gone; the shared bot serves A.
  - The captured log output (`OutputCaptureExtension`) never contains the school token or the secret.

- [ ] **Step 2: Compile it.** Run `./mvnw -s … test-compile`. Expected: BUILD SUCCESS. The test runs in CI only (no local Docker).

- [ ] **Step 3: Update the four documents** listed above.

- [ ] **Step 4: Run the fast suite and the diagram check.** Run the fast suite and `python3 scripts/check-diagram-parity.py`. Expected: green and "parity OK".

- [ ] **Step 5: Commit** with message `test(notification): school bot end-to-end flow; docs`. Push, and watch CI until `SchoolBotFlowIntegrationTest` passes.
