# Bale Smart Bot — Stage 2 (Full Button Bot) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Every phase-1 intent is answerable from a role-based Bale menu with buttons. No LLM yet. Updates from one chat are handled in order.

**Architecture:** `BaleWebhookService` hands updates to a per-chat serial executor, so different chats still run in parallel. `BaleUpdateHandler` loads a slim `BotContext` (memberships only) and passes the menu text or callback to `BotRouter`. `BotRouter` maps it to a `BotIntent`, resolves the target, and calls one answer class.
- The target is a student the user may see (a linked ward, or the user's own enrolment) or a school where the user has the needed role.
- The target is resolved lazily, through `SubjectResolver`, and only for intents that need it.
- When there are several possible targets, the bot asks with inline buttons. Each button's `callback_data` carries the intent and the target id, and the server re-checks that id against the user on every click.
- All Persian copy lives in `BotTexts`, so wording can change after user testing or A/B trials without touching logic.

**Tech Stack:** Java 21, Spring Boot, Spring Modulith, JUnit 5, AssertJ, Mockito, Testcontainers (CI only).

**Spec:** `openspec/changes/bale-smart-bot.md`: stage 2 of "Delivery stages", the phase-1 intent table and "Common rules", plus the first three items of "Carried forward from the stage 1 review".

## Global Constraints

- **Phase-1 intents are read-only.** No intent writes data.
- **Roles come from active memberships.** An intent that does not fit the user answers `BotTexts.NOT_AVAILABLE`, never data.
- **Students never see finance.** `INVOICES` resolves only to wards reached through an active `PARENT` membership, never to the user's own enrolment.
- **`userId` comes only from `BaleLinkEntity`.** Ids in `callback_data` are only a *choice*. They are always re-validated against the targets resolved for this user, and an unknown or foreign id answers `NOT_AVAILABLE`.
- **`callback_data` is 1–64 UTF-8 bytes.** This is enforced by `BaleClient`.
- **No stored conversation state.**
- **Money is shown in rial**, with Persian digits, the Persian thousands separator `٬` and the label «ریال» (product owner, 2026-10-16). Remaining = `max(0, amount − discountTotal − amountPaid)`, the same formula as `my-invoices`. The currency comes from one seam, `MoneyText.currencyOf(schoolId)`, which returns rial for every school until per-school currency (W4 in `changes/eu-market-readiness.md`) exists.
- **New "today" code uses `SchoolInfo.zoneId()`**, as `EventReminderController` does. Existing `TodayAttendanceReader` behaviour is unchanged (its fallback is a separate scheduled item).
- **Existing commands keep working:** `/start`, contact linking, «راهنما», «مدرسه‌های من», «فرزندان من».
- **Do not declare a bean of type `Executor`/`TaskExecutor`.** No schema change. No new dependency.
- **Comments** follow the surrounding style (Persian Javadoc, same density).
- **Maven:** commands here need `-s /tmp/claude-0/-home-user-school-project/d33e7fdc-c4d1-5a0a-8b9b-2145c3339491/scratchpad/maven-settings.xml` because Central returns 429. There is no Docker locally, so Testcontainers tests run only in CI.

## Review Focus

1. **Many updates from one chat at once:** they are answered in arrival order, while another chat is not blocked behind them. Pinned in Task 1.
2. **A callback whose id belongs to another family**, for example a `callback_data` hand-edited to another student's id, answers `NOT_AVAILABLE` and reads no data. Pinned in Task 5.
3. **A user who is both a parent and a student**, or a parent in two schools, gets one button per target, labelled with name and school. Picking one answers only for that target. Pinned in Task 3.
4. **A ward with no class, or a day that is closed or has no timetable rows**, gets a clear sentence, not an empty message or an exception. Pinned in Task 4.
5. **An invoice with a discount or a partial payment** shows the correct remaining amount. Fully paid invoices are not listed. Pinned in Task 4.

---

### Task 1: Per-chat ordering in `BaleWebhookService`

**Files:**
- Create: `src/main/java/com/example/roshdeandishe/notification/internal/ChatSerialExecutor.java`
- Modify: `src/main/java/com/example/roshdeandishe/notification/internal/BaleWebhookService.java`
- Test: `src/test/java/com/example/roshdeandishe/notification/internal/ChatSerialExecutorTest.java`, `BaleWebhookServiceTest.java`

**Interfaces:**
- Produces:
  ```java
  final class ChatSerialExecutor {
      ChatSerialExecutor(Executor pool, int maxPending);
      /** Tasks with the same key run one at a time, in submission order; different keys may run in parallel.
       *  key == null runs without ordering. Throws RejectedExecutionException when maxPending tasks are
       *  already waiting or the pool rejects; a rejected task is never run. */
      void execute(Long key, Runnable task);
  }
  ```
  - Algorithm: a `synchronized` map from key to `ArrayDeque<Runnable>`. The first task for a key creates the queue and submits one *drain* job to `pool`. That job polls and runs tasks until the queue is empty, then removes the key. A later task for a busy key is only appended. If `pool` rejects the drain job, remove the key and rethrow. Exceptions from a task are already caught in `BaleWebhookService.handleSafely`. The drain loop still catches `RuntimeException` per task, so one bad task cannot stall the chat.
  - `maxPending` counts queued tasks that have not started. `BaleWebhookService` uses `100`, the old queue size.
- `BaleWebhookService`: the key is `update.message().chat().id()` or `update.callbackQuery().message().chat().id()`, otherwise `null`. A rejection keeps the existing `forget(updateId)` and the existing log line.

- [ ] **Step 1: Write the failing tests**
  ```java
  // ChatSerialExecutorTest (real ThreadPoolExecutor of 4 threads)
  @Test void sameKeyRunsInSubmissionOrder()      // 50 tasks on key 1 append to a list -> list == 0..49
  @Test void differentKeysRunInParallel()        // key 1 task blocks on a latch; a key 2 task still completes
  @Test void nullKeyRunsWithoutOrdering()        // two null-key tasks both run
  @Test void overMaxPendingIsRejected()          // maxPending 2, key 1 blocked: 3rd waiting task throws RejectedExecutionException
  @Test void aFailingTaskDoesNotStallTheChat()   // task 1 throws, task 2 on the same key still runs
  // BaleWebhookServiceTest
  @Test void updatesOfOneChatAreHandledInOrder() // two updates, chat 5, ids 1 and 2, handler records order -> [1, 2]
  ```
- [ ] **Step 2: Run them to verify they fail**
  Run: `./mvnw -q -s <settings> test -Dtest='ChatSerialExecutorTest,BaleWebhookServiceTest'`
  Expected: compilation failure (`ChatSerialExecutor` missing).
- [ ] **Step 3: Implement** `ChatSerialExecutor`. Wire it into `BaleWebhookService`, wrapping the existing private pool. Keep the test constructor's shape, with `Executor` becoming the pool under the serial executor.
- [ ] **Step 4: Run the tests**
  Run: `./mvnw -q -s <settings> test -Dtest='ChatSerialExecutorTest,BaleWebhookServiceTest,RecentUpdateIdsTest'`
  Expected: all PASS.
- [ ] **Step 5: Commit** `feat(notification): handle Bale updates of one chat in order`

---

### Task 2: Copy, money and the menu keyboard

**Files:**
- Create: `src/main/java/com/example/roshdeandishe/notification/internal/BotTexts.java`
- Create: `src/main/java/com/example/roshdeandishe/notification/internal/PersianText.java`
- Create: `src/main/java/com/example/roshdeandishe/notification/internal/MoneyText.java`
- Modify: `BaleClient.java` (add the menu method), `EventReminderJob.java` (use `PersianText.digits`), `BaleUpdateHandler.java` (move its text constants into `BotTexts`, unchanged)
- Test: `PersianTextTest.java`, `MoneyTextTest.java`, `BaleClientMenuTest.java` (local `HttpServer`, as in `BaleClientInlineKeyboardTest`)

**Interfaces:**
- Produces:
  ```java
  final class PersianText {
      static String digits(long n);                 // 1405 -> "۱۴۰۵"; moved from EventReminderJob.persianDigits
      static String grouped(BigDecimal amount);     // 45000000 -> "۴۵٬۰۰۰٬۰۰۰"; scale dropped (rial is whole)
  }
  final class MoneyText {
      enum Currency { IRR }
      static Currency currencyOf(Long schoolId);    // IRR for every school until W4
      static String format(BigDecimal amount, Currency c); // IRR -> grouped(amount) + " ریال"
  }
  // BaleClient: a persistent reply keyboard (resize_keyboard=true, one_time_keyboard=false)
  boolean sendMenu(Long chatId, String text, List<List<String>> rows);
  ```
- `BotTexts` holds every user-facing string and template as `static final String` constants or small `static String` builders. It holds no logic beyond string assembly. The existing `WELCOME_TEXT`, `NOT_LINKED_TEXT`, `HELP_TEXT` and the «مدرسه‌های من» / «فرزندان من» wording move there unchanged.

**Copy to use** (the product owner will tune it after user testing; keep it all in `BotTexts`):

| Constant | Text |
|---|---|
| `BTN_ATTENDANCE_TODAY` | `حضور امروز` |
| `BTN_ATTENDANCE_SUMMARY` | `غیبت‌ها` |
| `BTN_GRADES` | `نمره‌ها` |
| `BTN_EXAMS` | `امتحان‌ها` |
| `BTN_TIMETABLE_TODAY` | `برنامه‌ی امروز` |
| `BTN_INVOICES` | `شهریه` |
| `BTN_TEACHER_TODAY` | `کلاس‌های امروز من` |
| `BTN_TEACHER_ROLL` | `حضور و غیاب مانده` |
| `BTN_EVENTS` | `رویدادهای نزدیک` |
| `BTN_HELP` | `راهنما` |
| `MENU_PROMPT` | `از دکمه‌های پایین انتخاب کنید.` |
| `UNKNOWN_TEXT` | `متوجه نشدم. لطفاً از دکمه‌های پایین انتخاب کنید.` |
| `NOT_AVAILABLE` | `این مورد برای شما در دسترس نیست.` |
| `CHOOSE_STUDENT` | `برای کدام دانش‌آموز؟` |
| `CHOOSE_SCHOOL` | `برای کدام مدرسه؟` |
| `NO_STUDENT` | `دانش‌آموزی برای نمایش پیدا نشد.` |

- [ ] **Step 1: Write the failing tests**
  ```java
  @Test void digitsAreConvertedToPersian()   { assertThat(PersianText.digits(1405)).isEqualTo("۱۴۰۵"); }
  @Test void amountsAreGroupedWithPersianSeparator() { assertThat(PersianText.grouped(new BigDecimal("45000000.00"))).isEqualTo("۴۵٬۰۰۰٬۰۰۰"); }
  @Test void rialIsLabelled()  { assertThat(MoneyText.format(new BigDecimal("1200"), MoneyText.Currency.IRR)).isEqualTo("۱٬۲۰۰ ریال"); }
  @Test void everySchoolIsRialForNow() { assertThat(MoneyText.currencyOf(7L)).isEqualTo(MoneyText.Currency.IRR); }
  @Test void sendsPersistentReplyKeyboard()  // body has "keyboard":[["حضور امروز"]], "resize_keyboard":true, "one_time_keyboard":false
  @Test void menuWithBlankTokenSendsNothing()
  ```
- [ ] **Step 2: Run them to verify they fail.**
- [ ] **Step 3: Implement.** Point `EventReminderJob` at `PersianText.digits` and delete its private copy. `EventReminderJobTest` must still pass unchanged.
- [ ] **Step 4: Run** `PersianTextTest,MoneyTextTest,BaleClientMenuTest,BaleClientDeliverTest,BaleClientInlineKeyboardTest,EventReminderJobTest,BaleUpdateHandlerTest`. Expected: all PASS.
- [ ] **Step 5: Commit** `feat(notification): Bale bot copy, rial formatting and a menu keyboard`

---

### Task 3: Intents, targets and the callback codec

This task also implements the stage-1 carry-overs "lazy `BotContext`" and "use or remove unused groundwork".

**Files:**
- Create: `notification/internal/BotIntent.java`, `notification/internal/BotTarget.java`, `notification/internal/SubjectResolver.java`, `notification/internal/CallbackCodec.java`
- Modify: `BotContext.java` (drop `wards`; keep `chatId`, `userId`, `fullName`, active `memberships`, `rolesIn`, `hasRole`), `BotContextLoader.java` (memberships only), `BaleUpdateHandler.java` («فرزندان من» reads wards from `SubjectResolver`, with the text unchanged)
- Test: `CallbackCodecTest.java`, `SubjectResolverTest.java`, and adjust `BotContextLoaderTest.java` and `BaleUpdateHandlerTest.java`

**Interfaces:**
- Produces (used by Tasks 4–5):
  ```java
  enum BotIntent {   // code: 1–2 ASCII letters used in callback_data
      ATTENDANCE_TODAY("a", STUDENT_TARGET), ATTENDANCE_SUMMARY("s", STUDENT_TARGET), GRADES("g", STUDENT_TARGET),
      EXAMS("e", STUDENT_TARGET), TIMETABLE_TODAY("t", STUDENT_TARGET), INVOICES("i", WARD_TARGET),
      TEACHER_TODAY("tt", TEACHER_SCHOOL), TEACHER_ROLL("tr", TEACHER_SCHOOL), EVENTS("ev", MEMBER_SCHOOL);
      String code(); TargetKind targetKind(); String button();   // button() returns the BotTexts.BTN_* text
      static Optional<BotIntent> byButton(String text); static Optional<BotIntent> byCode(String code);
      enum TargetKind { STUDENT_TARGET, WARD_TARGET, TEACHER_SCHOOL, MEMBER_SCHOOL }
  }
  sealed interface BotTarget {
      record Student(Long schoolId, Long studentId, Long studentUserId, Long classId, String name,
                     String schoolName, boolean ward) implements BotTarget {}   // ward=false: the user's own enrolment
      record School(Long schoolId, String schoolName) implements BotTarget {}
      long id();   // studentId or schoolId
  }
  @Component class SubjectResolver {
      /** Targets this user may ask about for this intent, built from server data only. */
      List<BotTarget> targets(BotContext ctx, BotIntent intent);
  }
  final class CallbackCodec {
      static String encode(BotIntent intent, BotTarget target);   // "<code>:<id>", e.g. "g:123"
      static Optional<Decoded> decode(String data);              // empty for anything malformed or an unknown code
      record Decoded(BotIntent intent, long id) {}
  }
  ```
- `targets` rules:
  - `STUDENT_TARGET` covers wards through active `PARENT` memberships (`AcademicFacade.listWards`) plus the user's own enrolment through an active `STUDENT` membership (`AcademicFacade.findMyEnrollment`).
  - `WARD_TARGET` covers wards only.
  - `TEACHER_SCHOOL` covers schools with an active `TEACHER` membership.
  - `MEMBER_SCHOOL` covers schools with any active membership.
  - Names and school names use the same fallbacks as the current «فرزندان من» text.
  - Order is stable: by school, then by name.

- [ ] **Step 1: Write the failing tests**
  ```java
  // CallbackCodecTest
  @Test void roundTrips()               { assertThat(CallbackCodec.decode(CallbackCodec.encode(GRADES, student(123L)))).contains(new Decoded(GRADES, 123L)); }
  @Test void rejectsMalformed()         // "", "g", "g:", "g:x", "zz:1", ":1", "g:1:2", "g:-1" -> empty
  @Test void staysWithin64Bytes()       // encode(TEACHER_ROLL, school(Long.MAX_VALUE)) has <= 64 UTF-8 bytes
  // SubjectResolverTest (BotContext built in the test; mocked AcademicFacade, SchoolFacade, IamFacade)
  @Test void studentSeesOwnEnrolment()
  @Test void parentWhoIsAlsoAStudentGetsBoth()            // ward + own enrolment, labelled with school
  @Test void invoicesNeverTargetTheUsersOwnEnrolment()    // WARD_TARGET for a pure student -> empty
  @Test void inactiveMembershipsGiveNoTargets()
  @Test void teacherSchoolsComeFromActiveTeacherMemberships()
  ```
- [ ] **Step 2: Run them to verify they fail.**
- [ ] **Step 3: Implement.** Slim down `BotContext`/`BotContextLoader`: it no longer calls `listWards` or `findUser` per ward. `describeWards` uses `targets(ctx, INVOICES)` (wards only) and keeps its exact output.
- [ ] **Step 4: Run** `CallbackCodecTest,SubjectResolverTest,BotContextLoaderTest,BaleUpdateHandlerTest,ModularityTests`. Expected: all PASS, with «فرزندان من» text identical to before.
- [ ] **Step 5: Commit** `feat(notification): bot intents, lazily resolved targets and callback codec`

---

### Task 4: Answers for every intent

**Files:**
- Create: `notification/internal/StudentAnswers.java` (`ATTENDANCE_TODAY`, `ATTENDANCE_SUMMARY`, `GRADES`, `EXAMS`), `notification/internal/TimetableAnswers.java` (`TIMETABLE_TODAY`, `TEACHER_TODAY`), `notification/internal/SchoolAnswers.java` (`INVOICES`, `TEACHER_ROLL`, `EVENTS`)
- Test: `StudentAnswersTest.java`, `TimetableAnswersTest.java`, `SchoolAnswersTest.java`

**Interfaces:**
- Consumes: `BotIntent`, `BotTarget`, `BotContext` (Task 3), `BotTexts`, `MoneyText`, `PersianText` (Task 2).
- Produces (used by Task 5): each class has `String answer(BotIntent intent, BotContext ctx, BotTarget target)`. The caller guarantees the target came from `SubjectResolver.targets(ctx, intent)`. The first line names the student or school, for example `«علی رضایی — مدرسه‌ی نمونه»`.
- Sources and wording (put the templates in `BotTexts`):
  - **`ATTENDANCE_TODAY`:** `StudentViewFacade.todayAttendance(ctx.userId(), schoolId, classId, studentId)`. The text per state:

    | State | Text |
    |---|---|
    | `PRESENT` | «امروز حاضر بود.» |
    | `LATE` | «امروز با تأخیر آمد.» |
    | `ABSENT` | «امروز غایب بود.» |
    | `ABSENT_EXCUSED` | «امروز غایب بود (موجه).» |
    | `PENDING` | «حضور و غیاب امروز هنوز ثبت نشده است.» |
    | `NOT_SCHOOL_DAY` | «امروز روز درسی نیست.» |
    | `UNAVAILABLE` | «اطلاعات حضور و غیاب در دسترس نیست.» |

    For a student's own enrolment, use the same states in the first person: «امروز حاضر بودی.» and so on.
  - **`ATTENDANCE_SUMMARY`, `GRADES`, `EXAMS`:** `StudentViewFacade.summary(...)`.
    - Summary: «غیبت بدون عذر: ۲ — موجه: ۱ — تأخیر: ۳ (امسال، فقط روزهای ثبت‌شده)». A null attendance answers the `UNAVAILABLE` text.
    - Grades: one line per `GradeLine`, `«ریاضی — نوبت اول: ۱۸٫۵»`, or «هنوز نمره‌ای ثبت نشده است.». No average.
    - Exams: `«آزمون هفتگی — ۱۴ مهر ۱۴۰۵»`, or «امتحان پیش‌رویی ندارد.».
  - **`TIMETABLE_TODAY` / `TEACHER_TODAY`:** today is `LocalDate.now(SchoolInfo.zoneId())`.
    - If `isClosedDay`, answer «امروز تعطیل است.».
    - Otherwise use the current academic year's `listPeriods`, plus `listEntriesForClass(classId)` for a student or `listEntriesForTeacher(userId)` for a teacher, filtered to today's `TimetableDay` and sorted by period position.
    - One line per entry: `«زنگ ۱ (۸:۰۰ تا ۸:۴۵): ریاضی»`, with course or activity name, and for a teacher also the class name.
    - With no rows: «امروز کلاسی ثبت نشده است.». A student with no class: «کلاسی برای این دانش‌آموز ثبت نشده است.».
  - **`INVOICES`:** `FinanceFacade.listInvoicesForStudents(schoolId, [studentId])`. Keep invoices with status `UNPAID`, `PARTIAL` or `OVERDUE`.
    - With none: «فاکتور بازی ندارد.».
    - Otherwise: «مانده‌ی کل: X ریال» (sum of remaining), «سررسید گذشته: N فاکتور» (only when N > 0), and the earliest unpaid or overdue instalment from `listInstallmentsForInvoices`: «قسط بعدی: Y ریال تا ۲۰ آبان ۱۴۰۵».
    - Amounts go through `MoneyText.format(amount, MoneyText.currencyOf(schoolId))`.
  - **`TEACHER_ROLL`:** `AttendanceFacade.listRecordableClasses(schoolId, ctx.userId(), false, today)`, keeping those with `taken == false`. Answer «حضور و غیاب این کلاس‌ها هنوز ثبت نشده: …», or «حضور و غیاب همه‌ی کلاس‌های امروز ثبت شده است.». A non-school day gives an empty list, which answers «امروز روز درسی نیست.» when `isClosedDay`.
  - **`EVENTS`:** the same rule as `EventReminderController.upcomingEvents`:
    - If `reminderRoleCodes` is present and the user holds none of those roles in the school, answer «رویداد نزدیکی نیست.».
    - Otherwise use `dueReminderEvents(school, today)`: `«جشن شروع سال — ۱ مهر ۱۴۰۵ (۳ روز دیگر)»`, «امروز» or «فردا».
  - Domain exceptions from a facade (`AttendanceException`, or a not-found) answer the intent's "not available" sentence and are logged at `warn`. Nothing propagates to the webhook.

- [ ] **Step 1: Write the failing tests** (mocked facades, fixed clock via the school zone and a mocked `isClosedDay`). Test names and key assertions:
  ```java
  @Test void pendingRollIsNeverShownAsPresent()                       // PENDING -> contains "هنوز ثبت نشده"
  @Test void ownEnrolmentIsAddressedInFirstPerson()
  @Test void gradesListLatestWithoutAverage()                          // no "میانگین", one line per GradeLine
  @Test void timetableIsSortedByPeriodAndFilteredToToday()
  @Test void closedDaySaysHoliday()                                    // "امروز تعطیل است."
  @Test void wardWithoutClassGetsAClearSentence()
  @Test void remainingSubtractsDiscountAndPayment() {                  // amount 10_000_000, discount 1_000_000, paid 2_000_000
      assertThat(answer).contains("مانده‌ی کل: ۷٬۰۰۰٬۰۰۰ ریال"); }
  @Test void paidInvoicesAreNotCounted()
  @Test void nextInstalmentIsTheEarliestUnpaid()
  @Test void eventsRespectReminderRoles()                              // selected roles exclude the user -> "رویداد نزدیکی نیست."
  @Test void teacherRollListsOnlyUntakenClasses()
  @Test void facadeErrorBecomesNotAvailableSentence()
  ```
- [ ] **Step 2: Run them to verify they fail.**
- [ ] **Step 3: Implement** the three classes as package-private `@Component`s.
- [ ] **Step 4: Run** `StudentAnswersTest,TimetableAnswersTest,SchoolAnswersTest`. Expected: all PASS.
- [ ] **Step 5: Commit** `feat(notification): answers for every phase-1 bot intent`

---

### Task 5: Router, role menu and wiring into the handler

**Files:**
- Create: `notification/internal/BotRouter.java`
- Modify: `BaleUpdateHandler.java`
- Test: `BotRouterTest.java`, `BaleUpdateHandlerTest.java`, `src/test/java/com/example/roshdeandishe/BaleFlowIntegrationTest.java`

**Interfaces:**
- Consumes: everything from Tasks 2–4.
- Produces:
  ```java
  @Component class BotRouter {
      List<List<String>> menu(BotContext ctx);   // only buttons whose intent has >= 1 target, two per row, BTN_HELP last
      Reply onText(BotContext ctx, String text); // a BotIntent button, or null for unknown text
      Reply onCallback(BotContext ctx, String data);
      record Reply(String text, List<List<BaleClient.InlineButton>> choices) {}   // choices empty = plain answer
  }
  ```
  - **Button text:** look up the intent's targets. Zero targets → `NOT_AVAILABLE`. One target → its answer. Several → `CHOOSE_STUDENT`/`CHOOSE_SCHOOL` with one inline button per target, labelled `name — schoolName` (or `schoolName`) and with data `CallbackCodec.encode(intent, target)`.
  - **Callback:** `decode`. Malformed data → `NOT_AVAILABLE`. Otherwise find the target with that id among `targets(ctx, intent)`. Not found → `NOT_AVAILABLE`, and no answer class is called. Found → the answer.
  - **`BaleUpdateHandler`:**
    - Unknown text gets `UNKNOWN_TEXT` together with the menu, sent through `sendMenu`.
    - «راهنما», «مدرسه‌های من» and «فرزندان من» keep their current answers. «راهنما» now also sends the menu.
    - After a successful contact link, the confirmation is sent through `sendMenu`.
    - A reply with choices is sent through `sendMessage(chatId, text, choices)`. Otherwise it goes through `sendMessage`.
    - Callbacks are acknowledged first, as today.
  - `HELP_TEXT` is updated to describe the buttons. This is the only change to existing wording.

- [ ] **Step 1: Write the failing tests**
  ```java
  // BotRouterTest (mocked SubjectResolver + answer classes)
  @Test void studentMenuHasNoInvoiceButton()
  @Test void teacherMenuHasTeacherButtons()
  @Test void singleTargetAnswersDirectly()
  @Test void severalTargetsAskWithButtons()            // choices data == CallbackCodec.encode(...)
  @Test void callbackForAForeignStudentIsNotAvailable() { // targets() has 123, data "g:999"
      assertThat(router.onCallback(ctx, "g:999").text()).isEqualTo(BotTexts.NOT_AVAILABLE);
      verifyNoInteractions(studentAnswers); }
  @Test void malformedCallbackIsNotAvailable()
  @Test void invoiceCallbackForOwnEnrolmentIsNotAvailable()  // student's own studentId with "i:" -> NOT_AVAILABLE
  // BaleUpdateHandlerTest
  @Test void unknownTextShowsTheMenu()
  @Test void linkingSendsTheMenu()
  @Test void existingCommandsAnswerAsBefore()
  // BaleFlowIntegrationTest (CI)
  @Test void linkedParentAsksGradesFromTheMenu()        // link, post "نمره‌ها" -> one message naming the ward
  ```
- [ ] **Step 2: Run them to verify they fail.**
- [ ] **Step 3: Implement** `BotRouter` and the handler wiring.
- [ ] **Step 4: Run** `BotRouterTest,BaleUpdateHandlerTest,ModularityTests,ArchitectureTests`, plus `test-compile` for the integration test. Expected: all PASS. The integration test runs in CI.
- [ ] **Step 5: Commit** `feat(notification): role-based Bale menu answering every phase-1 intent`

---

### Task 6: Documentation

**Files:**
- Modify: `openspec/changes/bale-smart-bot.md`:
  - Status: "stage 2 implemented 2026-10-xx".
  - Tick the three carried-forward stage-2 items.
  - Record in Decisions: money shown in rial in bot messages (PO, 2026-10-16); copy kept in `BotTexts` and tuned after user testing or A/B (PO, 2026-10-16).
- Modify: `openspec/changes/finance-monetary-unit.md`. Under the 2026-10-08 note, add "**Decided 2026-10-16 (by the user):** Bale bot messages show amounts in Rial, labelled «ریال»; the web read-only display unit is still open."
- Modify: `openspec/specs/current-system.md` (Bale bot section: menu per role, the intents, the callback re-check, per-chat ordering; remove the "no buttons yet" and "ordering not guaranteed" limitations).
- Modify: `openspec/architecture.md` (notification entry) and `openspec/project.md` (proposal line).
- Modify: `docs/superpowers/plans/2026-10-16-bale-smart-bot-stage-1.md` (tick the three stage-2 follow-ups).

- [ ] **Step 1: Edit the documents.** Describe only what Tasks 1–5 built, verified against code.
- [ ] **Step 2: Run** `./mvnw -q -s <settings> test -Dtest='!*IntegrationTest,!*BootTest,!Demo5ApplicationTests,!CurriculumVisibleQuestionsTest,!PhoneNumberCanonicalizationMigrationTest' -Dsurefire.failIfNoSpecifiedTests=false && python3 scripts/check-diagram-parity.py`
  Expected: BUILD SUCCESS and parity OK.
- [ ] **Step 3: Commit** `docs(openspec): record Bale smart bot stage 2 as implemented`
