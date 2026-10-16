# Bale Smart Bot — Stage 1 (Groundwork) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prepare the Bale bot for the phase-1 intents with no user-visible change: shared student read logic, callback-query plumbing, a server-built `BotContext`, and asynchronous, de-duplicated webhook processing.

**Architecture:** The student summary and today's-attendance logic move out of `web/model` into a new read-only module `studentview` (a facade over `academic`, `exam`, `attendance`, `school`, `iam`), so the dashboard and the bot give the same answer. Inside `notification`, the webhook parses the update on the request thread, drops repeated `update_id`s, and hands handling to a bounded private thread pool. `BotContext` is built from `BaleLink` and facades only.

**Tech Stack:** Java 21, Spring Boot, Spring Modulith, JUnit 5, AssertJ, Mockito, Awaitility, JTE.

**Spec:** `openspec/changes/bale-smart-bot.md` (stage 1 of "Delivery stages").

## Global Constraints

- No user-visible change. Existing commands (`/start`, contact linking, «راهنما», «مدرسه‌های من», «فرزندان من») answer exactly as today.
- Outbound notifications (`NotificationChannel`, absence, invoice, event reminders) are unchanged.
- No schema change, no new dependency in `pom.xml`.
- `userId` always comes from `BaleLinkEntity`, never from update content beyond `chat.id`.
- The webhook always answers 200 for a valid secret, without waiting for handling.
- Do **not** declare any bean of type `java.util.concurrent.Executor`/`TaskExecutor`: Spring Boot then skips `applicationTaskExecutor`, which async event listeners rely on.
- Module boundaries are enforced by `ModularityTests.verifyModularStructure()`; it must pass.
- Moved logic keeps its behavior bit-for-bit, including "today" falling back to the server zone (`ZoneId.systemDefault()`) when the school's zone is missing or invalid.
- Comments follow the surrounding style (Persian Javadoc, same density).

## Review Focus

1. **The same `update_id` arriving twice at once** (Bale retries while the first is still running) is handled once. Pinned in Task 4 with a concurrent test.
2. **An update without `update_id`** (malformed or test payload) is still handled, never dropped by the de-duplication. Pinned in Task 4.
3. **The pool full** (burst of updates) drops the update with a log line and still answers 200. It must never block the request thread or throw. Pinned in Task 4.
4. **A callback query from an unlinked chat** gets the not-linked message, and the callback is acknowledged (otherwise Bale shows a spinner forever). Pinned in Task 3.
5. **`callback_data` over 64 bytes** (Bale/Telegram limit; Persian text is 2 bytes per letter) is rejected when building the keyboard, not sent and silently truncated. Pinned in Task 2.

---

### Task 1: `studentview` module — move the student summary and today's attendance out of `web`

Apply `.agents/skills/new-module/SKILL.md` first. For this module: §1 layout applies; §2 errors, §3 access control, §4 entitlement and §5 audit do **not** apply (no REST, no failure modes of its own, not subscribable, no writes); say so in `package-info.java`.

**Files:**
- Create: `src/main/java/com/example/roshdeandishe/studentview/package-info.java` (`@ApplicationModule(displayName = "Student view")`)
- Create: `src/main/java/com/example/roshdeandishe/studentview/StudentViewFacade.java`
- Create: `src/main/java/com/example/roshdeandishe/studentview/StudentSummary.java` (moved from `web/model/StudentSummary.java`, unchanged body)
- Create: `src/main/java/com/example/roshdeandishe/studentview/TodayAttendance.java` (moved from `web/model/WardAttendance.java`, renamed; same constants)
- Create: `src/main/java/com/example/roshdeandishe/studentview/internal/StudentSummaryReader.java` (body of `web/model/StudentSummaryAssembler.java`)
- Create: `src/main/java/com/example/roshdeandishe/studentview/internal/TodayAttendanceReader.java` (body of `web/model/WardAttendanceResolver.java` plus a private copy of `AttendanceTodayAssembler.today`)
- Create: `src/main/java/com/example/roshdeandishe/studentview/internal/StudentViewFacadeImpl.java`
- Delete: `web/model/StudentSummaryAssembler.java`, `web/model/WardAttendanceResolver.java`, `web/model/StudentSummary.java`, `web/model/WardAttendance.java`
- Modify: `web/model/WardsAssembler.java`, `web/model/WardRow.java`, `web/model/OwnSummaryRow.java`, `web/controller/DashboardPageController.java`, `src/main/jte/layout/student-summary.jte` (import)
- Move tests: `web/model/StudentSummaryAssemblerTest.java` → `studentview/internal/StudentSummaryReaderTest.java`; `web/model/WardAttendanceResolverTest.java` → `studentview/internal/TodayAttendanceReaderTest.java`
- Modify tests: `web/model/WardsAssemblerTest.java`, `DashboardStudentSummaryRenderTest.java`, `DashboardWardsRenderTest.java` (types and mocks only)
- Modify: `openspec/architecture.md` (module list)

`web/model/AttendanceTodayAssembler.java` (the **admin** day figure) stays in `web`; only its static `today(...)` is copied, not moved.

**Interfaces:**
- Produces (used by Task 3, stage 2 and `web`):
  ```java
  public interface StudentViewFacade {
      /** viewer: the signed-in user (the student or a linked parent); the caller has checked the relation. */
      StudentSummary summary(UserId viewer, Long schoolId, Long studentId);
      /** One row per school where the user has an active STUDENT membership and an enrollment. */
      List<OwnSummary> ownSummaries(UserId userId, List<MembershipInfo> memberships);
      TodayAttendance todayAttendance(UserId viewer, Long schoolId, Long classId, Long studentId);
      record OwnSummary(Long schoolId, StudentSummary summary) {}
  }
  public enum TodayAttendance { UNAVAILABLE, NOT_SCHOOL_DAY, PENDING, PRESENT, LATE, ABSENT, ABSENT_EXCUSED }
  ```
  `StudentSummary` keeps its records `GradeLine`, `ExamLine`, `AttendanceCount` and helper methods unchanged.
- `web`: `OwnSummaryRow(String schoolName, StudentSummary summary)` is built in `DashboardPageController` from `ownSummaries(...)` plus the name lookup it already passes today. `WardRow.attendance` becomes `TodayAttendance`.

- [ ] **Step 1: Move the two test classes** to `studentview/internal/` and point them at `StudentSummaryReader` / `TodayAttendanceReader` with the same constructor arguments and assertions. Do not change any expected value.
- [ ] **Step 2: Run them to verify they fail to compile**

  Run: `./mvnw -q test -Dtest='StudentSummaryReaderTest,TodayAttendanceReaderTest'`
  Expected: compilation failure, `StudentSummaryReader` not found.
- [ ] **Step 3: Create the module** (files above). The readers are package-private `@Component`s with the moved bodies. `StudentViewFacadeImpl` delegates to them. `ownSummaries` uses the same filter as `forOwn` (active, `STUDENT`, first per school, enrollment present).
- [ ] **Step 4: Rewire `web`** to `StudentViewFacade` and delete the four moved files. In the render tests, replace only the type names.
- [ ] **Step 5: Run the moved tests, the web tests and the module check**

  Run: `./mvnw -q test -Dtest='StudentSummaryReaderTest,TodayAttendanceReaderTest,WardsAssemblerTest,AttendanceTodayAssemblerTest,DashboardStudentSummaryRenderTest,DashboardWardsRenderTest,ModularityTests'`
  Expected: all PASS. `ModularityTests` reports no cycle: `studentview` depends on `academic`, `exam`, `attendance`, `school`, `iam`, and none of them depends on it.
- [ ] **Step 6: Add `studentview` to the module list in `openspec/architecture.md`** (one line, same shape as `attendance`).
- [ ] **Step 7: Commit**

  ```bash
  git add -A src/main/java/com/example/roshdeandishe/studentview src/main/java/com/example/roshdeandishe/web src/main/jte/layout/student-summary.jte src/test openspec/architecture.md
  git commit -m "refactor(studentview): move student summary and today's attendance out of web"
  ```

---

### Task 2: Callback queries and inline keyboards in the Bale client

**Files:**
- Modify: `src/main/java/com/example/roshdeandishe/notification/internal/BaleUpdate.java`
- Modify: `src/main/java/com/example/roshdeandishe/notification/internal/BaleClient.java`
- Test: `src/test/java/com/example/roshdeandishe/notification/internal/BaleClientInlineKeyboardTest.java` (same local `HttpServer` pattern as `BaleClientDeliverTest`, capturing request path and body)
- Test: `src/test/java/com/example/roshdeandishe/notification/internal/BaleUpdateParsingTest.java`

**Interfaces:**
- Produces (used by Task 3):
  ```java
  record BaleUpdate(Long updateId, BaleMessage message,
                    @JsonProperty("callback_query") BaleCallbackQuery callbackQuery) {
      record BaleCallbackQuery(String id, BaleMessage message, String data) {}
  }
  // BaleClient
  record InlineButton(String text, String callbackData) {}      // package-private, in BaleClient
  boolean sendMessage(Long chatId, String text, List<List<InlineButton>> inlineKeyboard);
  boolean answerCallbackQuery(String callbackQueryId);
  ```
  On the wire: `reply_markup: {"inline_keyboard": [[{"text": ..., "callback_data": ...}]]}` on `/bot{token}/sendMessage`, and `{"callback_query_id": ...}` on `/bot{token}/answerCallbackQuery`. A blank token logs and returns `false` without HTTP, like the existing methods.

- [ ] **Step 1: Write the failing tests**

  ```java
  // BaleUpdateParsingTest
  @Test void parsesCallbackQueryWithItsChatAndData() {
      BaleUpdate u = new ObjectMapper().readValue("""
          {"update_id":7,"callback_query":{"id":"cb1","data":"X:1","message":{"message_id":3,"chat":{"id":42}}}}""",
          BaleUpdate.class);
      assertThat(u.callbackQuery().id()).isEqualTo("cb1");
      assertThat(u.callbackQuery().data()).isEqualTo("X:1");
      assertThat(u.callbackQuery().message().chat().id()).isEqualTo(42L);
      assertThat(u.message()).isNull();
  }
  // BaleClientInlineKeyboardTest
  @Test void sendsInlineKeyboardAsReplyMarkup()      // body contains "inline_keyboard" and "\"callback_data\":\"X:1\""
  @Test void answersCallbackQueryById()              // path ends with /answerCallbackQuery, body contains "\"callback_query_id\":\"cb1\""
  @Test void blankTokenSendsNothing()                 // returns false, server saw 0 requests
  @Test void rejectsCallbackDataOver64Bytes() {
      String data = "ف".repeat(33);                   // 66 bytes in UTF-8
      assertThatThrownBy(() -> client("token").sendMessage(1L, "x", List.of(List.of(new InlineButton("a", data)))))
              .isInstanceOf(IllegalArgumentException.class);
      assertThat(requests.get()).isZero();
  }
  ```
- [ ] **Step 2: Run them to verify they fail**

  Run: `./mvnw -q test -Dtest='BaleUpdateParsingTest,BaleClientInlineKeyboardTest'`
  Expected: compilation failure (`callbackQuery`, `InlineButton` missing).
- [ ] **Step 3: Implement.** Add `BaleCallbackQuery` to `BaleUpdate` with `@JsonIgnoreProperties(ignoreUnknown = true)`. In `BaleClient`, widen `SendMessageRequest.replyMarkup` to `Object`, add `InlineKeyboardMarkup(@JsonProperty("inline_keyboard") List<List<InlineButtonJson>>)`, and check `callbackData.getBytes(UTF_8).length <= 64` before any HTTP.
- [ ] **Step 4: Run the new tests and `BaleClientDeliverTest`**

  Run: `./mvnw -q test -Dtest='BaleUpdateParsingTest,BaleClientInlineKeyboardTest,BaleClientDeliverTest'`
  Expected: all PASS.
- [ ] **Step 5: Commit**

  ```bash
  git add src/main/java/com/example/roshdeandishe/notification/internal/BaleUpdate.java src/main/java/com/example/roshdeandishe/notification/internal/BaleClient.java src/test/java/com/example/roshdeandishe/notification/internal/BaleUpdateParsingTest.java src/test/java/com/example/roshdeandishe/notification/internal/BaleClientInlineKeyboardTest.java
  git commit -m "feat(notification): parse Bale callback queries and send inline keyboards"
  ```

---

### Task 3: `BotContext` built from server data, used by the handler

Classes stay package-private in `notification/internal` for now. The spec's `bot` sub-package arrives in stage 2, once there are enough classes to justify it, because a sub-package would force `BaleLinkRepository` and these classes to become public.

**Files:**
- Create: `src/main/java/com/example/roshdeandishe/notification/internal/BotContext.java`
- Create: `src/main/java/com/example/roshdeandishe/notification/internal/BotContextLoader.java`
- Modify: `src/main/java/com/example/roshdeandishe/notification/internal/BaleUpdateHandler.java`
- Test: `src/test/java/com/example/roshdeandishe/notification/internal/BotContextLoaderTest.java`
- Test: `src/test/java/com/example/roshdeandishe/notification/internal/BaleUpdateHandlerTest.java`

**Interfaces:**
- Consumes: `BaleUpdate.BaleCallbackQuery`, `BaleClient.answerCallbackQuery` (Task 2).
- Produces (used by stage 2 intent handlers):
  ```java
  record BotContext(Long chatId, UserId userId, String fullName,
                    List<MembershipInfo> memberships,   // active only
                    List<Ward> wards) {                 // only via active PARENT memberships
      record Ward(Long schoolId, Long studentId, Long studentUserId, Long classId, String fullName) {}
      Set<String> rolesIn(Long schoolId);
      boolean hasRole(String roleCode);                 // in any school
      List<Ward> wardsIn(Long schoolId);
  }
  @Component class BotContextLoader {
      Optional<BotContext> load(Long chatId);           // empty: chat not linked, or linked user no longer exists
  }
  ```
  `Ward.fullName` falls back to `"دانش‌آموز #" + studentUserId` and `Ward.classId` is `null` when the student has no class. Both match the current `describeWards`.

- [ ] **Step 1: Write the failing tests** (Mockito mocks for `BaleLinkRepository`, `IamFacade`, `AcademicFacade`):

  ```java
  // BotContextLoaderTest
  @Test void unlinkedChatHasNoContext()                       // findByChatId empty -> Optional.empty()
  @Test void keepsOnlyActiveMemberships()
  @Test void wardsComeOnlyFromActiveParentMemberships()       // STUDENT/TEACHER memberships never call listWards
  @Test void wardOfAnotherSchoolIsNotListedUnderThisSchool()  // wardsIn(schoolA) excludes school B's ward
  // BaleUpdateHandlerTest
  @Test void callbackFromUnlinkedChatIsAnsweredAndGetsNotLinkedText() {
      handler.handle(callbackUpdate("cb1", 42L, "X"));
      verify(client).answerCallbackQuery("cb1");
      verify(client).sendMessage(42L, "حساب شما هنوز متصل نشده است. لطفاً ابتدا /start را ارسال کنید.");
  }
  @Test void callbackFromLinkedChatIsAnsweredAndGetsHelp()    // stage 1 has no callback intents yet
  @Test void myWardsTextGivesTheSameAnswerAsBefore()          // «فرزندان من» text identical to the current format
  ```
- [ ] **Step 2: Run them to verify they fail**

  Run: `./mvnw -q test -Dtest='BotContextLoaderTest,BaleUpdateHandlerTest'`
  Expected: compilation failure (`BotContextLoader` missing).
- [ ] **Step 3: Implement** `BotContext` and `BotContextLoader`. In `BaleUpdateHandler`:
  - A callback query is answered first (`answerCallbackQuery`), then treated like a message from `callbackQuery.message().chat().id()`: not linked → `NOT_LINKED_TEXT`, linked → `HELP_TEXT`.
  - `describeMemberships` and `describeWards` read from `BotContext` instead of querying memberships and wards themselves. The output text is unchanged.
  - Correct the class comment's "CLAUDE.md §10" reference to "`CLAUDE_LEGACY.md` §10, superseded for the free-text router by `openspec/changes/bale-smart-bot.md`".
- [ ] **Step 4: Run the tests**

  Run: `./mvnw -q test -Dtest='BotContextLoaderTest,BaleUpdateHandlerTest,ModularityTests'`
  Expected: all PASS.
- [ ] **Step 5: Commit**

  ```bash
  git add src/main/java/com/example/roshdeandishe/notification/internal src/test/java/com/example/roshdeandishe/notification/internal
  git commit -m "feat(notification): build BotContext from the Bale link and answer callback queries"
  ```

---

### Task 4: Asynchronous, de-duplicated webhook processing

**Files:**
- Create: `src/main/java/com/example/roshdeandishe/notification/internal/RecentUpdateIds.java`
- Modify: `src/main/java/com/example/roshdeandishe/notification/internal/BaleWebhookService.java`
- Modify: `src/test/java/com/example/roshdeandishe/BaleFlowIntegrationTest.java`
- Test: `src/test/java/com/example/roshdeandishe/notification/internal/RecentUpdateIdsTest.java`
- Test: `src/test/java/com/example/roshdeandishe/notification/internal/BaleWebhookServiceTest.java`

**Interfaces:**
- Consumes: `BaleUpdateHandler.handle(BaleUpdate)` (unchanged signature, still `@Transactional`).
- Produces:
  ```java
  final class RecentUpdateIds {
      RecentUpdateIds(int capacity);
      /** true the first time an id is seen; false while it is among the last `capacity` ids. Thread-safe. */
      boolean markSeen(long updateId);
  }
  @Component class BaleWebhookService {
      BaleWebhookService(BaleUpdateHandler handler);                                   // Spring: own pool, capacity 1000
      BaleWebhookService(BaleUpdateHandler handler, Executor executor, RecentUpdateIds seen); // tests
      void processRawUpdate(String rawBody);   // parse on caller thread, de-duplicate, submit; never throws
  }
  ```
  - **Pool** (assumption, record in the spec's unresolved questions): a private `ThreadPoolExecutor` with core 2, max 4, a queue of 100, threads named `bale-update-N`. Rejection logs a warning and drops the update. It is shut down in `@PreDestroy`, waiting up to 10 s. It is **not** a Spring bean (see Global Constraints).
  - **De-duplication:** capacity 1000 (assumption). An update with a `null` `update_id` skips the check and is always processed.
  - An exception inside `handle` is logged on the worker thread, as today.

- [ ] **Step 1: Write the failing tests**

  ```java
  // RecentUpdateIdsTest
  @Test void firstSightingIsNewSecondIsNot()       { var s = new RecentUpdateIds(3); assertThat(s.markSeen(1)).isTrue(); assertThat(s.markSeen(1)).isFalse(); }
  @Test void forgetsTheOldestBeyondCapacity()      // capacity 2: 1,2,3 seen -> markSeen(1) is true again
  @Test void concurrentSameIdIsNewExactlyOnce()    // 16 threads markSeen(5) at once -> exactly one true
  // BaleWebhookServiceTest (direct Executor, mocked handler)
  @Test void handsTheParsedUpdateToTheHandler()
  @Test void sameUpdateIdIsHandledOnce()           // processRawUpdate twice with update_id 9 -> handle() called once
  @Test void updateWithoutIdIsAlwaysHandled()      // two payloads without update_id -> handle() called twice
  @Test void malformedJsonIsIgnoredWithoutThrowing()
  @Test void returnsBeforeHandlingFinishes()       // executor that holds the task: processRawUpdate returns, handle() not yet called
  @Test void fullPoolDropsTheUpdateWithoutThrowing() // executor that throws RejectedExecutionException -> no exception escapes
  ```
- [ ] **Step 2: Run them to verify they fail**

  Run: `./mvnw -q test -Dtest='RecentUpdateIdsTest,BaleWebhookServiceTest'`
  Expected: compilation failure (`RecentUpdateIds` missing).
- [ ] **Step 3: Implement** `RecentUpdateIds` (a `synchronized` access-ordered `LinkedHashMap` with `removeEldestEntry`) and the new `BaleWebhookService` flow.
- [ ] **Step 4: Run the unit tests**

  Run: `./mvnw -q test -Dtest='RecentUpdateIdsTest,BaleWebhookServiceTest'`
  Expected: all PASS.
- [ ] **Step 5: Adapt `BaleFlowIntegrationTest`** to async handling and de-duplication:
  - `updateJsonForText` and `contactShareUpdateJson` take `update_id` from `SEQ.incrementAndGet()` instead of the constant `1`.
  - `linkChatToPhoneNumber` waits for the confirmation (`awaitSentMessageCount(1)`) before clearing `SENT_MESSAGES`.
  - Every database assertion that follows a webhook post (`linkCount`, `rowCount`, `updatedChatId`) is wrapped in `await().atMost(Duration.ofSeconds(10)).untilAsserted(...)`.
  - Add a test: `sameUpdatePostedTwiceIsAnsweredOnce`. Post the same `/start` JSON twice with one `update_id` and assert, after a short `await().during(Duration.ofSeconds(1))`, that exactly one message was sent.
- [ ] **Step 6: Run the integration test** (requires Docker, used by Testcontainers)

  Run: `./mvnw -q test -Dtest='BaleFlowIntegrationTest'`
  Expected: all PASS. Without Docker (this cloud container has none), record "not run locally: no Docker" in the PR, and CI must run it.
- [ ] **Step 7: Commit**

  ```bash
  git add src/main/java/com/example/roshdeandishe/notification/internal/RecentUpdateIds.java src/main/java/com/example/roshdeandishe/notification/internal/BaleWebhookService.java src/test/java/com/example/roshdeandishe/notification/internal/RecentUpdateIdsTest.java src/test/java/com/example/roshdeandishe/notification/internal/BaleWebhookServiceTest.java src/test/java/com/example/roshdeandishe/BaleFlowIntegrationTest.java
  git commit -m "feat(notification): handle Bale updates asynchronously and drop repeated update ids"
  ```

---

### Task 5: Documentation for stage 1

**Files:**
- Modify: `openspec/changes/bale-smart-bot.md`. Status: "stage 1 implemented 2026-10-xx". Add the pool size, queue and de-duplication capacity to "Unresolved questions" as assumptions. Note the `bot` sub-package deferral.
- Modify: `openspec/specs/current-system.md`. In the Bale bot section, add callback queries (acknowledged; help for now), async handling, and de-duplication by `update_id`.
- Modify: `openspec/architecture.md`. The `notification` entry now mentions async webhook handling (`studentview` was added in Task 1).
- Modify: `openspec/project.md`. The proposal line now says "stage 1 implemented".

- [ ] **Step 1: Edit the four documents** as listed. Describe only what Tasks 1–4 built.
- [ ] **Step 2: Run the full fast suite and the diagram check**

  Run: `./mvnw -q test -Dtest='!*IntegrationTest,!*BootTest' && python3 scripts/check-diagram-parity.py`
  Expected: BUILD SUCCESS and `parity OK` for every diagram.
- [ ] **Step 3: Commit**

  ```bash
  git add openspec
  git commit -m "docs(openspec): record Bale smart bot stage 1 as implemented"
  ```

---

## Follow-ups after stage 1 (from the PR #153 review)

Fixed in PR #153 after the review: an update dropped by a full pool is no longer kept as "seen" (`RecentUpdateIds.forget`); empty or missing `callback_data` is rejected like over-long data; the `BaleUpdateHandler` comment no longer claims the legacy "no AI" rule is already superseded.

Scheduled for later stages — the list and reasons live in `openspec/changes/bale-smart-bot.md` under "Carried forward from the stage 1 review", and the stage 2 plan must start from it:

- [ ] Stage 2: serialize update handling per `chat.id` (must do before any new intent).
- [ ] Stage 2: load `BotContext` wards lazily.
- [ ] Stage 2: use or remove the unused groundwork API.
- [ ] Separate small PR: one "today" zone fallback (`SchoolInfo.zoneId()`).
- [ ] With real traffic: revisit in-memory de-duplication scope.
