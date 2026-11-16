# Bale Bot Admin Grade Query (Step 2) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a school admin ask the bot for the students of one grade whose score is above a threshold («همه‌ی دانش‌آموزان پایه‌ی هفتم با نمره‌ی بالای ۱۵»), first from buttons alone (`BOT_LLM_ENABLED=false`), then from free text. The model only names the intent; the grade and the threshold are parsed on the server, every choice is re-checked against server data, and the reply is built from `BotTexts` templates.

**Architecture:** The new intent `ADMIN_GRADE_ABOVE` is a *funnel* with three parameters: school, grade, threshold. One router method walks the funnel from whatever is already known and asks for the next missing parameter with inline buttons. The same method serves the menu button (nothing known), a button click (`callback_data` carries what is known so far, no stored state) and the free-text path (the server-side parser fills in grade and threshold from the text; the model fills in nothing but the intent).

```
menu button ──────────────┐
callback ag:<school>[:<grade>[:<threshold>]] ──┤
free text ─ LLM picks ADMIN_GRADE_ABOVE ───────┤   (AdminQueryParser ran on the server before the model call)
                                               ▼
                       BotRouter.adminQuery(ctx, school?, grade?, threshold?)
                         ├─ no school  → school buttons   (targets from SubjectResolver, ADMIN_SCHOOL)
                         ├─ no grade   → grade buttons    (the school's grades of the current year)
                         ├─ no threshold → threshold buttons (fixed choices)
                         └─ all three  → AdminAnswers.gradeAbove(...)  → BotTexts
```

**Tech Stack:** Java 21, Spring Boot, JUnit 5, Mockito, AssertJ. Python 3 stdlib for the evaluation script.

**Spec:** `openspec/changes/bale-smart-bot.md` ("Phase-1 intents", "Common rules", "LLM contract and privacy", "Errors and limits"); `openspec/decisions/2026-10-15-llm-in-bale-bot.md` (privacy rules 1–6). This document is step 2 of the feature: the implementation plan. **The spec puts admin statistics out of phase 1; this is the first admin intent and extends the spec. Task 7 records that in the change proposal, and the PO confirms before the feature is switched on for real schools.**

## Global Constraints

- **The LLM is a router only.** It chooses the intent (and, for parents, a child label) from closed lists. It never sees data. **The grade and the threshold are parsed on the server and are never sent to the model**: the parser runs on the original text and the matched numbers are replaced by «[پایه]» and «[نمره]» in the text that goes out (decision D2 below).
- **Role gate on the server.** `ADMIN_GRADE_ABOVE` has target kind `ADMIN_SCHOOL`: a school where the user has an *active* `SCHOOL_ADMIN` membership (as filtered by `BotContextLoader`, so a school whose bot settings exclude `SCHOOL_ADMIN` gives nothing). `BotRouter.allowedIntents` (menu, help, the model's option list) and `SubjectResolver.targets` (every answer) both come from this rule, so no other role sees the button, the hint or the data. A forged callback or a model answer naming the intent for a non-admin ends in `NOT_AVAILABLE` / the menu.
- **Scope and reads.** Every read passes the school id taken from `SubjectResolver.targets`, never from the update. Reads go through `AcademicFacade`, `IamFacade` and `SchoolFacade` only, inside the handler's existing read transactions (`handle`, `answerFreeText`); nothing is written.
- **Copy** in `BotTexts` only (decision 10 of the spec). **No user text in logs**; the parser, the router and the answer class log nothing but intent, ids and exception class names. The parsed grade and threshold are not logged either.
- **Buttons first.** Tasks 1–5 make the whole feature work with `BOT_LLM_ENABLED=false`. Tasks 6–7 add the free-text entry point. **No secrets** are touched anywhere in this plan.
- **No stored conversation state** (spec, "Common rules"): what is known so far travels in `callback_data`, at most 64 bytes.
- **Packaging:** package-private classes in `com.example.roshdeandishe.notification.internal`, same as the existing bot classes.
- **Test command** (same as the stage-3 plan): `./mvnw -s <settings> test -Dtest=<Class>`, and the fast suite `./mvnw -s <settings> test -Dtest='!*IntegrationTest,!*BootTest,!Demo5ApplicationTests,!CurriculumVisibleQuestionsTest,!PhoneNumberCanonicalizationMigrationTest' -Dsurefire.failIfNoSpecifiedTests=false`, which includes `ModularityTests` and `ArchitectureTests`.

### Decisions made in this plan (not by the PO; listed for review)

- **D1. "Score" means the overall numeric grade of the current year.** A student's score is the grade recorded with `courseId == null` («نمرهٔ کل») for the school's current academic year; when both terms exist, `SECOND` wins over `FIRST`. Per-course grades are not considered. A grade level whose classes are `DESCRIPTIVE` has no numeric score and answers «ارزشیابی توصیفی». *(assumption; an open question below)*
- **D2. Numbers parsed for this intent are redacted before the model call.** The matched grade (digits or ordinal word after «پایه»/«کلاس») and the matched threshold become «[پایه]» and «[نمره]» in the pseudonymised text. Other short numbers keep the existing known limit (only runs of five or more digits are replaced).
- **D3. "Above" is strict.** «بالای ۱۵», «بیشتر از ۱۵», «از ۱۵ بالاتر», «۱۵ به بالا» all mean `score > 15`. The inclusive reading of «به بالا» is listed as an open question; not implemented.
- **D4. The parser never guesses.** A number without a marker («۷ ۱۵») fills nothing; the funnel then asks with buttons. A grade outside the school's current grade list, or a threshold outside 0–20, is treated as missing and the funnel asks again with a clear sentence.
- **D5. Threshold buttons are a fixed list**: 10, 12, 15, 17, 18 (whole numbers). Decimals are reachable only through text. *(assumption)*
- **D6. The reply shows the count, then names with scores per class, capped at `roshdeandishe.bot.admin-query.max-rows` (default 30).** When the cap cuts the list, the last line says how many more there are. Both the names and the cap are open questions; the count line stays whatever is decided, so flipping either later touches only `AdminAnswers` and `BotTexts`.
- **D7. No keyword matcher.** With the LLM off, free text that happens to contain «پایه ۷ بالای ۱۵» still gets the menu, exactly like every other intent. The parser is used only after the model has chosen the intent.
- **D8. Callback format** `ag:<schoolId>[:<grade>[:<threshold>]]`, decoded by a separate `CallbackCodec.decodeAdminQuery` so the existing `<code>:<id>` decoder keeps rejecting anything with a second colon. Threshold is ASCII with `.`, at most two fraction digits (`15`, `17.5`). Longest value: `ag:` + 19 + `:` + 2 + `:` + 5 = 31 bytes.

## Review Focus

1. **A non-admin reaches the intent** through a forged callback `ag:10:7:15`, through the menu text, or through a model answer `ADMIN_GRADE_ABOVE`. Expected: `NOT_AVAILABLE` or the menu, and `AdminAnswers` is never called. Pinned in Task 3 (`BotRouterTest`), Task 6 (`ClassificationValidatorTest`, `BaleUpdateHandlerFreeTextTest`).
2. **An admin of school A asks about school B** (callback with B's id, or the user lost the A membership between two clicks). Expected: `NOT_AVAILABLE`; the school id in the callback is only a choice and must be among the user's `ADMIN_SCHOOL` targets at answer time. Pinned in Task 3.
3. **Parsed numbers leak to the model.** Expected: the `FreeTextRequest.text` for an admin contains «[پایه]» and «[نمره]» and none of the typed digits. Pinned in Task 6.
4. **Persian, Arabic-Indic and ASCII digits, decimals with «٫», «.» or «/»**, ZWNJ in «پایه‌ی». Expected: the same parse. Pinned in Task 2.
5. **A descriptive grade (1–6) or a school without a current year.** Expected: a clear sentence, never an empty list that looks like "nobody is above". Pinned in Task 4.
6. **A user who is both admin and parent.** Expected: both menus; «نمره‌های [فرزند_۱]» is still `GRADES`, «پایه‌ی هفتم بالای ۱۵» is `ADMIN_GRADE_ABOVE`. Pinned in the evaluation rows (Task 7); the mixed-role product rule stays an open question.

---

## File Structure

| File | Responsibility |
|---|---|
| `notification/internal/BotIntent.java` | New constant `ADMIN_GRADE_ABOVE("ag", ADMIN_SCHOOL, BTN_ADMIN_GRADE_ABOVE)` and target kind `ADMIN_SCHOOL`. |
| `notification/internal/SubjectResolver.java` | `ADMIN_SCHOOL` in `targets` and `hasAny` (active `SCHOOL_ADMIN` memberships). |
| `notification/internal/AdminQueryParser.java` (new) | Text → `Parsed(grade, threshold, thresholdOutOfRange, redacted)`. Pure, no logging. |
| `notification/internal/AdminQuery.java` (new) | `record AdminQuery(Long schoolId, Integer grade, BigDecimal threshold)` plus the fixed threshold choices. |
| `notification/internal/CallbackCodec.java` | `encodeAdminQuery`, `decodeAdminQuery`. |
| `notification/internal/BotRouter.java` | The funnel `adminQuery(ctx, AdminQuery)`; `onText`/`onCallback`/`resolve` routed into it; grade list from `AcademicFacade`. |
| `notification/internal/AdminAnswers.java` (new) | The read: current year, classes of the grade, overall scores, names; builds the reply. |
| `notification/internal/BotTexts.java` | Button, questions, sentences, line templates, ordinal grade names. |
| `notification/internal/FreeTextRequest.java` | New optional field `adminQuery` (the parsed grade/threshold, server-only). |
| `notification/internal/BaleUpdateHandler.java` | Runs the parser in `prepareFreeText` for admins; routes `ADMIN_GRADE_ABOVE` in `answerFreeText`. |
| `notification/internal/PersianText.java` | `asciiDigits` and `normalizeLetters` shared by the parser and `Pseudonymizer`. |
| `src/main/resources/bot/llm/intent-hints.properties` | `ADMIN_GRADE_ABOVE=…`. |
| `src/main/resources/application.yml` | `roshdeandishe.bot.admin-query.max-rows: 30`. |
| `scripts/bot-llm-eval.py`, `scripts/bot-llm-eval-set.tsv` | `SCHOOL_ADMIN` role, the new intent, new rows. |
| `openspec/changes/bale-smart-bot.md`, `openspec/specs/current-system.md`, `openspec/decisions/2026-10-15-llm-in-bale-bot.md`, `DEPLOYMENT.md` | Spec and docs. |

**Facade methods used (all exist today):**

| Method | Used for |
|---|---|
| `SchoolFacade.findCurrentAcademicYear(SchoolId)` | the year whose grades count; empty → «سال تحصیلی جاری تنظیم نشده» |
| `SchoolFacade.findSchool(SchoolId)` | school name in the header (already through `SubjectResolver`) |
| `AcademicFacade.listClasses(Long schoolId, String academicYear)` | the school's grade list (distinct `ClassInfo.gradeLevel`, sorted), the classes of the chosen grade, their `gradingScheme`, names and sections |
| `AcademicFacade.listStudentsByClass(Long schoolId, Collection<Long> classIds)` | the students of those classes in one read |
| `AcademicFacade.listGradesForStudents(Long schoolId, List<Long> studentIds)` | all their grades in one read; filtered on the server to `academicYear == current`, `courseId == null`, `scheme == NUMERIC` |
| `IamFacade.findMembershipsBySchool(Long schoolId)` | student names (`MembershipInfo.fullName` for `STUDENT` rows) in one read, instead of one `findUser` per student |
| `IamFacade.checkAccess(UserId, ModuleCode("ACADEMIC"), SchoolContext)` | the school must hold the Academic module, the same first layer the grade pages use (`@RequiresModule("ACADEMIC")`); otherwise «در دسترس نیست» |
| `SchoolFacade.formatSolarDate` | not needed; the year is shown by its `code` |

**Facade methods that must be added:** none. The composition above costs four reads per answer. If a batch score method becomes necessary with real data (a grade with hundreds of students), add `AcademicFacade.listOverallScores(schoolId, academicYear, gradeLevel)` then, with its own change note; this plan does not add it.

**Existing conflict to report, not fix here:** `src/test/java/.../BaleUpdateHandlerFreeTextTest.java` is entirely commented out on master, while `openspec/specs/current-system.md` and the stage-3 plan describe its cases (`requestHoldsNoNamesIdsOrPhones`, `forgedLabelNeverAnswersAnotherStudent`) as pinned. Task 6 adds this feature's cases to that file; whether the whole file is re-enabled is a separate decision for the reviewer, because its setup may have drifted from the current `BaleUpdateHandler` constructor.

---

### Task 1: Intent, target kind and role rule

**Files:**
- Modify: `BotIntent.java`, `SubjectResolver.java`, `BotTexts.java`, `BotRouter.java` (help line only)
- Test: `SubjectResolverTest.java`, `BotRouterTest.java`, `IntentHintsTest.java` (will fail until Task 7 adds the hint; add the hint in this task to keep the suite green)

**Interfaces:**
- `BotIntent.ADMIN_GRADE_ABOVE("ag", TargetKind.ADMIN_SCHOOL, BotTexts.BTN_ADMIN_GRADE_ABOVE)`, declared **last**, after `EVENTS`, so existing menu order, `allowedIntents` order and the eval script's `INTENT_ORDER` stay unchanged for every other intent.
- `TargetKind.ADMIN_SCHOOL` — «مدرسه‌هایی که کاربر در آن‌ها مدیرِ فعال (`SCHOOL_ADMIN`) است».
- `SubjectResolver.targets`: `case ADMIN_SCHOOL -> schools(active, "SCHOOL_ADMIN", schoolNames)`. `hasAny`: `active.stream().anyMatch(m -> "SCHOOL_ADMIN".equals(m.roleCode()))`. Both only read `ctx.memberships()`, which `BotContextLoader` already filtered to active, in-scope, bot-allowed memberships.
- `BotTexts`: `BTN_ADMIN_GRADE_ABOVE = "دانش‌آموزان بالای نمره"`, `HELP_ADMIN = "ویژه‌ی مدیر: دانش‌آموزانِ یک پایه با نمره‌ی بالاتر از یک حد"`.
- `BotRouter.help`: `addHelpLine(lines, available, BotTexts.HELP_ADMIN, BotIntent.ADMIN_GRADE_ABOVE)` after the teacher line.
- `intent-hints.properties`: `ADMIN_GRADE_ABOVE=دانش‌آموزانِ یک پایه که نمره‌شان از حدی بالاتر است (پرسشِ مدیر مدرسه)` — `LlmPrompts.intentHints()` requires a hint for every constant, so it lands here, before `NONE`.

- [ ] **Step 1: Write the failing tests**

```java
// SubjectResolverTest
@Test void adminSchoolsComeFromActiveSchoolAdminMembershipsOnly() {
    List<BotTarget> t = resolver.targets(
            ctx(m(10L, "SCHOOL_ADMIN", true), m(20L, "TEACHER", true), m(30L, "FINANCE_ADMIN", true),
                m(40L, "SCHOOL_ADMIN", false)), BotIntent.ADMIN_GRADE_ABOVE);
    assertThat(t).containsExactly(new BotTarget.School(10L, "مدرسه #10"));
    assertThat(resolver.hasAny(ctx(m(20L, "TEACHER", true)), BotIntent.TargetKind.ADMIN_SCHOOL)).isFalse();
    assertThat(resolver.hasAny(ctx(m(10L, "SCHOOL_ADMIN", true)), BotIntent.TargetKind.ADMIN_SCHOOL)).isTrue();
    org.mockito.Mockito.verifyNoInteractions(academic);
}
```

```java
// BotRouterTest
@Test void adminButtonIsHiddenFromParentStudentAndTeacher() {
    when(resolver.hasAny(ctx, BotIntent.TargetKind.STUDENT_TARGET)).thenReturn(true);
    when(resolver.hasAny(ctx, BotIntent.TargetKind.WARD_TARGET)).thenReturn(true);
    when(resolver.hasAny(ctx, BotIntent.TargetKind.TEACHER_SCHOOL)).thenReturn(true);
    when(resolver.hasAny(ctx, BotIntent.TargetKind.MEMBER_SCHOOL)).thenReturn(true);
    List<String> flat = router.menu(ctx).stream().flatMap(List::stream).toList();
    assertThat(flat).doesNotContain(BotTexts.BTN_ADMIN_GRADE_ABOVE);
    assertThat(router.help(ctx)).doesNotContain(BotTexts.HELP_ADMIN);
    assertThat(router.allowedIntents(ctx)).doesNotContain(BotIntent.ADMIN_GRADE_ABOVE);
}
@Test void adminMenuHasTheAdminButtonAndEvents() {
    when(resolver.hasAny(ctx, BotIntent.TargetKind.ADMIN_SCHOOL)).thenReturn(true);
    when(resolver.hasAny(ctx, BotIntent.TargetKind.MEMBER_SCHOOL)).thenReturn(true);
    assertThat(router.menu(ctx)).containsExactly(
            List.of(BotTexts.BTN_EVENTS, BotTexts.BTN_ADMIN_GRADE_ABOVE), List.of(BotTexts.BTN_HELP));
    assertThat(router.help(ctx)).contains(BotTexts.helpLine(List.of(BotTexts.BTN_ADMIN_GRADE_ABOVE), BotTexts.HELP_ADMIN));
}
```

Also update `menuChecksEachTargetKindOnce` to expect 5 `hasAny` calls, and `inactiveMembershipsGiveNoTargets` already loops over every intent and must stay green.

- [ ] **Step 2: Run, expect failures.** `-Dtest='SubjectResolverTest,BotRouterTest,IntentHintsTest'`.
- [ ] **Step 3: Implement** the enum constant, the target kind, the resolver cases, the texts, the help line and the hint.
- [ ] **Step 4: Run, expect PASS.** Also `ModularityTests`.
- [ ] **Step 5: Commit** `feat(notification): ADMIN_GRADE_ABOVE intent gated to active school admins`.

### Task 2: Server-side parser

**Files:**
- Create: `notification/internal/AdminQueryParser.java`
- Modify: `PersianText.java` (`asciiDigits`, `normalizeLetters`), `Pseudonymizer.java` (use them; behaviour unchanged, `PseudonymizerTest` guards it)
- Test: `AdminQueryParserTest.java`

**Interfaces:**
- `PersianText.asciiDigits(String)`: ۰–۹ and ٠–٩ → 0–9. `PersianText.normalizeLetters(String)`: ي→ی, ك→ک, ZWNJ (U+200C) → space, whitespace runs → one space, trim. `Pseudonymizer` calls these instead of its private copy.
- `record AdminQueryParser.Parsed(Integer grade, BigDecimal threshold, boolean thresholdOutOfRange, String redacted)`; `static Parsed parse(String text)`.
- Rules, applied to a normalised copy while the redaction is written back on the original spans:
  1. **Grade** = the first match of `پایه` (optionally followed by `ی`/`‌ی`) or `کلاس`, then a number `1..12` or an ordinal word from the fixed map اول، دوم، سوم، چهارم، پنجم، ششم، هفتم، هشتم، نهم، دهم، یازدهم، دوازدهم (with an optional suffix «ی‌ها»/«ها»/«ی»). A bare ordinal or bare number is **not** a grade (D4). A number outside 1..12 gives `grade = null`.
  2. **Threshold** = the first match of one of: `بالای N`, `بالاتر از N`, `بیشتر از N`, `بیش از N`, `از N بالاتر`, `از N بیشتر`, `N به بالا`, `N و بالاتر`, where `N` is `\d{1,2}([.٫/]\d{1,2})?`. The separator becomes `.`; the value is a `BigDecimal` with scale ≤ 2. Outside `0..20` → `threshold = null`, `thresholdOutOfRange = true`.
  3. **Redaction**: the matched grade number or ordinal becomes «[پایه]», the matched threshold number «[نمره]»; everything else is untouched (the pseudonymiser runs afterwards as today).
  4. `null` or blank input → `Parsed(null, null, false, "")`. The class never logs.

- [ ] **Step 1: Write the failing tests**

```java
class AdminQueryParserTest {
    private static AdminQueryParser.Parsed p(String s) { return AdminQueryParser.parse(s); }

    @Test void persianDigitsWithBalaye() {
        var r = p("همه‌ی دانش‌آموزان پایه ۷ با نمره بالای ۱۵");
        assertThat(r.grade()).isEqualTo(7);
        assertThat(r.threshold()).isEqualByComparingTo("15");
        assertThat(r.redacted()).isEqualTo("همه‌ی دانش‌آموزان پایه [پایه] با نمره بالای [نمره]");
    }
    @Test void ordinalGradeAndDecimalWithMomayez() {
        var r = p("پایه‌ی هفتم بالاتر از ۱۷٫۵");
        assertThat(r.grade()).isEqualTo(7);
        assertThat(r.threshold()).isEqualByComparingTo("17.5");
        assertThat(r.redacted()).isEqualTo("پایه‌ی [پایه] بالاتر از [نمره]");
    }
    @Test void arabicIndicDigits() {
        var r = p("پایه ٩ بیشتر از ١٢");
        assertThat(r.grade()).isEqualTo(9);
        assertThat(r.threshold()).isEqualByComparingTo("12");
    }
    @Test void slashDecimalAndLatinDigits() {
        var r = p("پایه 10 بالای 18/25");
        assertThat(r.grade()).isEqualTo(10);
        assertThat(r.threshold()).isEqualByComparingTo("18.25");
    }
    @Test void thresholdAfterAzAndColloquialSuffix() {
        var r = p("دانش‌آموزای پایه هشتم که نمره‌شون از ۱۴ بیشتره");
        assertThat(r.grade()).isEqualTo(8);
        assertThat(r.threshold()).isEqualByComparingTo("14");
        assertThat(p("کلاس دوازدهمی‌ها ۱۸ به بالا").grade()).isEqualTo(12);
        assertThat(p("کلاس دوازدهمی‌ها ۱۸ به بالا").threshold()).isEqualByComparingTo("18");
    }
    @Test void outOfRangeThresholdIsFlaggedAndGradeKept() {
        var r = p("بالای ۲۵ پایه هفتم");
        assertThat(r.grade()).isEqualTo(7);
        assertThat(r.threshold()).isNull();
        assertThat(r.thresholdOutOfRange()).isTrue();
    }
    @Test void gradeOutOfRangeIsNull() { assertThat(p("پایه ۱۳ بالای ۱۰").grade()).isNull(); }
    @Test void bareNumbersAndBareOrdinalsAreNotGuessed() {
        assertThat(p("۷ ۱۵")).isEqualTo(new AdminQueryParser.Parsed(null, null, false, "۷ ۱۵"));
        assertThat(p("هفتم بالای ۱۵").grade()).isNull();
        assertThat(p("هفتم بالای ۱۵").threshold()).isEqualByComparingTo("15");
    }
    @Test void nothingParsable() {
        var r = p("کی‌ها نمره‌ی خوبی گرفتن؟");
        assertThat(r.grade()).isNull(); assertThat(r.threshold()).isNull();
        assertThat(r.redacted()).isEqualTo("کی‌ها نمره‌ی خوبی گرفتن؟");
    }
    @Test void nullAndBlankAreSafe() {
        assertThat(p(null).redacted()).isEmpty();
        assertThat(p("   ").grade()).isNull();
    }
}
```

- [ ] **Step 2: Run, expect a compile failure.**
- [ ] **Step 3: Implement** `AdminQueryParser` and the two `PersianText` helpers; switch `Pseudonymizer` to them. Keep one compiled `Pattern` per rule as `private static final`; no logging.
- [ ] **Step 4: Run, expect PASS**, including `PseudonymizerTest` and `PersianTextTest`.
- [ ] **Step 5: Commit** `feat(notification): parse grade and threshold of the admin query on the server`.

### Task 3: Callback codec and the router funnel

**Files:**
- Create: `notification/internal/AdminQuery.java`
- Modify: `CallbackCodec.java`, `BotRouter.java`, `BotTexts.java`
- Test: `CallbackCodecTest.java`, `BotRouterTest.java`

**Interfaces:**
- `record AdminQuery(Long schoolId, Integer grade, BigDecimal threshold)` with `static final List<BigDecimal> THRESHOLD_CHOICES = [10, 12, 15, 17, 18]` (D5) and `static boolean validThreshold(BigDecimal t)` (`0 <= t <= 20`, scale ≤ 2).
- `CallbackCodec.encodeAdminQuery(AdminQuery q)` → `"ag:" + schoolId [+ ":" + grade [+ ":" + threshold.stripTrailingZeros().toPlainString()]]`. `static Optional<AdminQuery> decodeAdminQuery(String data)`: prefix must be `ag:`; 2–4 parts; school id digits only; grade `1..12`; threshold `\d{1,2}(\.\d{1,2})?` within 0–20; anything else empty. The existing `decode` is unchanged and still returns empty for `ag:10:7`.
- `BotRouter` gets `AcademicFacade academicFacade` and `AdminAnswers adminAnswers` as constructor arguments (update the three existing tests' constructor calls).
- `BotRouter.adminQuery(BotContext ctx, AdminQuery q)`:
  1. `targets = resolver.targets(ctx, ADMIN_GRADE_ABOVE)`; empty → `NOT_AVAILABLE`.
  2. **School**: `q.schoolId() == null` → one target: take it; several: `choose(...)` as today, but each button's data is `encodeAdminQuery(new AdminQuery(schoolId, null, null))`. `q.schoolId()` not among the targets → `NOT_AVAILABLE` (review focus 2).
  3. **Grade**: `grades = gradeList(schoolId)` = distinct sorted `gradeLevel` of `academicFacade.listClasses(schoolId, currentYear.code())`; no current year → `Reply.plain(header + "\n" + ADMIN_NO_YEAR)`; empty list → `ADMIN_NO_CLASSES`. `q.grade() == null` or not in the list → reply `CHOOSE_GRADE` with one button per grade, label `BotTexts.gradeName(g)` («پایه‌ی هفتم»), data `encodeAdminQuery(new AdminQuery(schoolId, g, null))`, **three per row**. When a grade was given but is not in the list the text is `ADMIN_GRADE_NOT_IN_SCHOOL` instead of `CHOOSE_GRADE`.
  4. **Threshold**: `q.threshold() == null` → reply `CHOOSE_THRESHOLD` with the five buttons `BotTexts.thresholdChoice(t)` («بالای ۱۵»), data `encodeAdminQuery(new AdminQuery(schoolId, grade, t))`. Invalid → the same buttons under `ADMIN_THRESHOLD_INVALID`.
  5. All three known → `Reply.plain(adminAnswers.gradeAbove(ctx, target, grade, threshold))` inside the existing try/catch of `answer` (a `RuntimeException` → `NOT_AVAILABLE` with the ids-only warn log).
- `onText`: `BotTexts.BTN_ADMIN_GRADE_ABOVE` → `adminQuery(ctx, new AdminQuery(null, null, null))`.
- `onCallback`: when `CallbackCodec.decode` is empty, try `decodeAdminQuery`; present → `adminQuery(ctx, q)`; otherwise `NOT_AVAILABLE` as today.
- `resolve(ctx, intent, studentId)`: for `ADMIN_GRADE_ABOVE` delegate to `adminQuery(ctx, new AdminQuery(null, null, null))` (the free-text path with parsed values uses `adminQuery` directly, Task 6).
- `BotTexts` additions (exact copy): `CHOOSE_GRADE = "برای کدام پایه؟"`, `CHOOSE_THRESHOLD = "نمره‌ی بالاتر از چند؟"`, `ADMIN_GRADE_NOT_IN_SCHOOL = "این پایه در سال جاری کلاسی ندارد؛ یکی از پایه‌های زیر را انتخاب کنید:"`, `ADMIN_THRESHOLD_INVALID = "نمره باید بین ۰ و ۲۰ باشد؛ یکی را انتخاب کنید:"`, `ADMIN_NO_YEAR = "سال تحصیلی جاری تنظیم نشده است."`, `ADMIN_NO_CLASSES = "برای سال جاری کلاسی ثبت نشده است."`, `static String gradeName(int grade)` («پایه‌ی هفتم», ordinal words 1–12), `static String thresholdChoice(BigDecimal t)` («بالای ۱۵», `PersianText.localize` of the plain string without trailing zeros).

- [ ] **Step 1: Write the failing tests**
  - `CallbackCodecTest`: `adminQueryRoundTrips` for `(10, null, null)`, `(10, 7, null)`, `(10, 7, 15)`, `(10, 7, 17.5)`; `adminQueryRejectsGarbage` for `ag:`, `ag:x`, `ag:10:13`, `ag:10:7:21`, `ag:10:7:15.555`, `ag:10:7:15:1`, `g:10:7`; `standardDecodeStillRejectsAdminData` (`decode("ag:10:7")` is empty) and `decode("ag:10")` yields `(ADMIN_GRADE_ABOVE, 10)` — note that `onCallback` then goes through `decode` first for `ag:10`; both paths end in `adminQuery` with only the school known, so the result is the same grade question. Pin that in the router test below.
  - `BotRouterTest` (the `ctx` helper gets a second context `admin` with a `SCHOOL_ADMIN` membership; `resolver.targets(admin, ADMIN_GRADE_ABOVE)` returns `School(10, "مدرسه")`):
    - `adminMenuButtonAsksForGradeWhenOneSchool`: `listClasses(10, "1405")` returns classes of grades 7, 8, 7 → reply text `CHOOSE_GRADE`, buttons `[پایه‌ی هفتم, پایه‌ی هشتم]` with data `ag:10:7`, `ag:10:8`; `AdminAnswers` untouched.
    - `adminWithTwoSchoolsAsksForSchoolFirst`: buttons `ag:10`, `ag:20`.
    - `gradeCallbackAsksForThreshold`: `onCallback(admin, "ag:10:7")` → `CHOOSE_THRESHOLD`, five buttons, first data `ag:10:7:10`.
    - `fullCallbackAnswers`: `onCallback(admin, "ag:10:7:15")` → `adminAnswers.gradeAbove(admin, School(10), 7, 15)` is called once, its text returned.
    - `parentStudentTeacherCannotUseAdminCallback`: for `ctx` (no admin target) `onCallback(ctx, "ag:10:7:15")` and `onText(ctx, BTN_ADMIN_GRADE_ABOVE)` are `NOT_AVAILABLE`; `verifyNoInteractions(adminAnswers, academic)`.
    - `foreignSchoolInCallbackIsNotAvailable`: `onCallback(admin, "ag:99:7:15")` → `NOT_AVAILABLE`, no reads.
    - `gradeNotInSchoolAsksAgain`: `ag:10:11:15` with grades [7, 8] → text `ADMIN_GRADE_NOT_IN_SCHOOL`, grade buttons.
    - `noCurrentYearIsSaidClearly`: `findCurrentAcademicYear` empty → text ends with `ADMIN_NO_YEAR`, no buttons.
    - `answerFailureIsNotAvailable`: `gradeAbove` throws → `NOT_AVAILABLE`.
- [ ] **Step 2: Run, expect failures.**
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run, expect PASS.** `-Dtest='CallbackCodecTest,BotRouterTest,SchoolAnswersTest,StudentAnswersTest'`.
- [ ] **Step 5: Commit** `feat(notification): admin grade query funnel with school, grade and threshold buttons`.

### Task 4: The read and the reply (`AdminAnswers`)

**Files:**
- Create: `notification/internal/AdminAnswers.java`
- Modify: `BotTexts.java`, `application.yml` (`roshdeandishe.bot.admin-query.max-rows: 30` under `bot`, next to `llm`)
- Test: `AdminAnswersTest.java`

**Interfaces:**
- `@Component class AdminAnswers` with `IamFacade`, `AcademicFacade`, `SchoolFacade` and `@Value("${roshdeandishe.bot.admin-query.max-rows:30}") int maxRows` (a package-private test constructor takes the int).
- `String gradeAbove(BotContext ctx, BotTarget.School school, int grade, BigDecimal threshold)`, in order:
  1. `iamFacade.checkAccess(ctx.userId(), new ModuleCode("ACADEMIC"), new SchoolContext(schoolId)).isGranted()` else `ADMIN_QUERY_UNAVAILABLE`.
  2. `year = schoolFacade.findCurrentAcademicYear(...)` else `ADMIN_NO_YEAR`.
  3. `classes = academicFacade.listClasses(schoolId, year.code())` filtered to `gradeLevel == grade`; empty → `ADMIN_NO_CLASSES`; all `DESCRIPTIVE` → `ADMIN_DESCRIPTIVE`; `NUMERIC` classes only continue.
  4. `students = academicFacade.listStudentsByClass(schoolId, classIds)` (values flattened; a student in two lists counted once by `StudentId`).
  5. `grades = academicFacade.listGradesForStudents(schoolId, studentIds)` filtered to `academicYear.equals(year.code()) && courseId == null && scheme == NUMERIC && numericScore != null`; per student keep `SECOND` over `FIRST` (D1); keep those with `numericScore.compareTo(threshold) > 0` (D3).
  6. Names: `iamFacade.findMembershipsBySchool(schoolId)` → map `userId → fullName` (any role row; first wins); fallback `BotTexts.studentFallback(userId)`.
  7. Reply (exact templates in `BotTexts`):
     - `schoolHeader(schoolName)` (first line, as every school answer)
     - `adminQueryHeader(gradeName, threshold, yearCode, total)` → «پایه‌ی هفتم، نمره‌ی بالای ۱۵ (سال ۱۴۰۵-۱۴۰۶): ۱۲ دانش‌آموز»
     - total 0 → `ADMIN_NONE_ABOVE = "هیچ دانش‌آموزی بالای این نمره نیست."`
     - per class, in `listClasses` order: `adminClassLine(className, section, count)` → «کلاس هفتم الف: ۷ نفر», then its students sorted by score descending then name: `adminStudentLine(name, score)` → «- زهرا احمدی: ۱۸٫۵» (`PersianText.localize` of the plain score without trailing zeros).
     - after `maxRows` student lines in total: `adminTruncated(remaining)` → «… و ۴ نفر دیگر» and stop (D6).
  - Catches `AcademicException | SchoolException` → warn with ids and class name only, returns `ADMIN_QUERY_UNAVAILABLE = "این پرسش فعلاً در دسترس نیست."`. Nothing else is logged.
  - `BotTexts.ADMIN_DESCRIPTIVE = "این پایه ارزشیابی توصیفی دارد و نمره‌ی عددی ندارد."`.
- The answer never reads a class, student or grade of another school: every facade call carries `school.schoolId()`, and the grade filter is applied on the server to the school's own class list.

- [ ] **Step 1: Write the failing tests** (fake facades with Mockito; a fixed year `"1405-1406"`):
  - `countsNamesAndScoresPerClassAboveStrictThreshold`: classes 7-الف (NUMERIC) and 7-ب (NUMERIC), four students, overall grades 18.5 / 15 / 12 / 16 (the last one has `FIRST` 10 and `SECOND` 16, so 16 counts), threshold 15. Exactly 15 is not above 15 (D3), so the total is 2: the reply contains `adminQueryHeader(…, 2)`, «کلاس هفتم الف: ۱ نفر», «- … : ۱۸٫۵», «- … : ۱۶», and does **not** contain the name of the student with 15.
  - `perCourseGradesAndOtherYearsAreIgnored`: a grade with `courseId=5` of 20 and an overall grade of last year 20 are not counted.
  - `descriptiveGradeIsSaidClearly`: grade 3 with `DESCRIPTIVE` classes → text ends with `ADMIN_DESCRIPTIVE`; `listStudentsByClass` never called.
  - `noCurrentYear`, `noClassesForGrade`, `nobodyAbove`.
  - `listIsCappedWithRemainder`: `maxRows = 2`, five students above → exactly two student lines and `adminTruncated(3)`; the header still says ۵.
  - `nameFallsBackToIdWhenMembershipMissing`.
  - `noAcademicModuleIsUnavailable`: `checkAccess` denied → `ADMIN_QUERY_UNAVAILABLE`, no `AcademicFacade` call.
  - `facadeErrorIsUnavailable`: `listClasses` throws `AcademicException` → `ADMIN_QUERY_UNAVAILABLE`.
  - `everyReadCarriesTheTargetSchoolId`: `verify` that each facade call used `10L` (an `ArgumentCaptor` on the school argument).
- [ ] **Step 2: Run, expect a compile failure.**
- [ ] **Step 3: Implement** `AdminAnswers`, the templates and the YAML key.
- [ ] **Step 4: Run, expect PASS**, then the fast suite (constructor change of `BotRouter` from Task 3 must be reflected in every test that builds it).
- [ ] **Step 5: Commit** `feat(notification): answer the admin grade query from the school's current-year overall grades`.

### Task 5: End-to-end with the LLM off (buttons only)

**Files:**
- Test: `BaleUpdateHandlerTest.java` (new cases, same mock style)

No production change. These tests prove acceptance with `BOT_LLM_ENABLED=false`:

- [ ] **Step 1: Write the tests**
  - `adminPressesButtonAndGetsGradeChoices`: linked `SCHOOL_ADMIN` of school 10, text `BTN_ADMIN_GRADE_ABOVE`, `enabled=false` → `sendMessage(tok, chat, CHOOSE_GRADE, choices)` with `ag:10:7`-style data; `handle` returns empty.
  - `callbackChainEndsInAnswer`: callback `ag:10:7:15` → `answerCallbackQuery` first, then `sendMessage` with the `AdminAnswers` text.
  - `parentCallbackIsRefused`: linked `PARENT` with a ward, callback `ag:10:7:15` → `NOT_AVAILABLE` and `AdminAnswers`/`AcademicFacade.listClasses` never called (review focus 1).
  - `blockedBySchoolSettingsGetsDisabledText`: a school whose bot roles exclude `SCHOOL_ADMIN` → `BOT_DISABLED_FOR_SCHOOL` (already the handler's rule; pinned for this role).
- [ ] **Step 2: Run, expect PASS** (these should pass on the code of Tasks 1–4; a failure here is a wiring bug to fix before continuing).
- [ ] **Step 3: Commit** `test(notification): admin grade query works from buttons with the LLM off`.

### Task 6: Free-text entry point (server-side parameters, model picks the intent only)

**Files:**
- Modify: `FreeTextRequest.java`, `BaleUpdateHandler.java`, `BotRouter.java` (none beyond Task 3 if `adminQuery` is package-private), `ClassificationValidatorTest.java`
- Test: `BaleUpdateHandlerFreeTextTest.java` (new cases; see the conflict note in File Structure), `FreeTextResponderTest.java` (one case)

**Interfaces:**
- `FreeTextRequest` gains `AdminQueryParser.Parsed adminQuery` (nullable; null for everyone who is not an admin). `toString` stays free of text; it prints only whether `adminQuery` is present.
- `BaleUpdateHandler.prepareFreeText`: when `allowed.contains(ADMIN_GRADE_ABOVE)`, run `AdminQueryParser.parse(text)` on the **original** text first; the string handed to `Pseudonymizer.pseudonymize` is `parsed.redacted()` (D2); the `Parsed` goes into the request. Otherwise unchanged.
- `BaleUpdateHandler.answerFreeText`: when `intent == ADMIN_GRADE_ABOVE`, send `botRouter.adminQuery(ctx, new AdminQuery(null, parsed.grade(), parsed.threshold()))` (school unknown: the funnel takes the single admin school or asks). `parsed == null` (should not happen; defensive) → `adminQuery` with all nulls. Every other intent unchanged. When `parsed.thresholdOutOfRange()` the funnel's `ADMIN_THRESHOLD_INVALID` branch already explains it.
- `FreeTextResponder`: unchanged. The model receives `ADMIN_GRADE_ABOVE` in its option list only through `request.allowed()`, which the resolver filled; no other change. The `targetLabels` logic is untouched (an admin with no wards has no labels and no target question).
- Nothing in the validator changes: `ADMIN_GRADE_ABOVE` returned for a user whose `allowed` lacks it is rejected by the existing rule. Add the explicit test anyway.

- [ ] **Step 1: Write the failing tests**
  - `ClassificationValidatorTest.adminIntentForNonAdminIsRejected`: `validate(a("ADMIN_GRADE_ABOVE", .99, null, 0), EnumSet.of(GRADES, EVENTS), List.of(), .7)` is empty.
  - `BaleUpdateHandlerFreeTextTest` (a linked `SCHOOL_ADMIN` «مریم کریمی» of school 10, `enabled=true`):
    - `adminRequestCarriesParsedNumbersOnlyServerSide`: text «دانش‌آموزان پایه ۷ با نمره بالای ۱۵ رو بده» → `request.adminQuery()` is `(7, 15)`; `request.text()` contains «[پایه]» and «[نمره]» and contains neither «۷» nor «۱۵» nor `7` nor `15`; `request.allowed()` contains `ADMIN_GRADE_ABOVE` and `EVENTS` only (review focus 3).
    - `parentRequestHasNoAdminQueryAndNoAdminIntent`: the existing parent fixture → `adminQuery()` null and `allowed()` lacks `ADMIN_GRADE_ABOVE`.
    - `answerFreeTextRunsTheFunnelWithParsedValues`: `answerFreeText(request, Resolved(ADMIN_GRADE_ABOVE, null))` with `(7, 15)` → `AdminAnswers.gradeAbove(ctx, School(10), 7, 15)` called once (through a real `BotRouter` over mocked `AdminAnswers`); with `(7, null)` → `CHOOSE_THRESHOLD` buttons are sent; with `(null, null)` → `CHOOSE_GRADE` buttons.
    - `forgedAdminIntentForParentAnswersMenu`: parent request, `Resolved(ADMIN_GRADE_ABOVE, null)` → `resolve`/`adminQuery` has no targets → `NOT_AVAILABLE`, `AdminAnswers` untouched (defence in depth behind the validator).
  - `FreeTextResponderTest.adminHintIsOfferedOnlyWhenAllowed`: a request with `allowed = {EVENTS}` sends intent options without `ADMIN_GRADE_ABOVE`; with `{EVENTS, ADMIN_GRADE_ABOVE}` it is present, last before `NONE`.
- [ ] **Step 2: Run, expect failures.**
- [ ] **Step 3: Implement.** Keep the model-text invariant: the only change to what is sent is the two labels.
- [ ] **Step 4: Run the fast suite, expect PASS.** The log test `logsNeverContainText` must still pass; add «پایه» and «۱۵» to its forbidden list.
- [ ] **Step 5: Commit** `feat(notification): route admin grade questions from free text with server-parsed parameters`.

### Task 7: Model side, evaluation and documentation

**Files:**
- Modify: `scripts/bot-llm-eval.py`, `scripts/bot-llm-eval-set.tsv`, `openspec/changes/bale-smart-bot.md`, `openspec/specs/current-system.md`, `openspec/decisions/2026-10-15-llm-in-bale-bot.md`, `DEPLOYMENT.md` (only if a new env variable is introduced; none is planned — `max-rows` is a YAML key)

**Interfaces:**
- `bot-llm-eval.py`: `INTENT_ORDER` gets `"ADMIN_GRADE_ABOVE"` before `"NONE"`; `ROLE_INTENTS["SCHOOL_ADMIN"] = {"ADMIN_GRADE_ABOVE", "EVENTS"}` (the resolver rule: an admin with no other role has events and the admin query). The dry-run must list the new hint.
- `bot-llm-eval-set.tsv`: **14 new rows** (total 114), already pseudonymised and redacted exactly as `prepareFreeText` would send them:
  - 8 rows `SCHOOL_ADMIN` → `ADMIN_GRADE_ABOVE`, varied phrasing: «همه‌ی دانش‌آموزان پایه‌ی [پایه] با نمره‌ی بالای [نمره]», «کی‌های پایه [پایه] از [نمره] بیشتر گرفتن؟», «لیست بچه‌های [پایه] که نمره‌شون [نمره] به بالاست», «پایه [پایه] بالاتر از [نمره] چند نفرن», «فهرست ممتازهای پایه‌ی [پایه] (بالای [نمره])», «دانش‌آموزان پایه [پایه] با معدل بیشتر از [نمره]», «نمره‌های پایه [پایه] بالای [نمره] رو بفرست», «کدوم بچه‌های پایه‌ی [پایه] بالای [نمره] شدن».
  - 2 rows `SCHOOL_ADMIN` → `EVENTS` («رویداد نزدیکی داریم؟», «برنامه‌ی هفته‌ی بعد مدرسه چیه»).
  - 2 rows `SCHOOL_ADMIN` → `NONE` («حقوق معلم‌ها کی واریز میشه», «پایه [پایه] چند تا کلاس داره» — a grade mentioned without a threshold is not this intent).
  - 2 rows `SCHOOL_ADMIN,PARENT` with label `[فرزند_۱]`: «نمره‌های [فرزند_۱] چی شد» → `GRADES`, and «پایه‌ی [پایه] بالای [نمره] رو می‌خوام» → `ADMIN_GRADE_ABOVE` (review focus 6).
- **Re-tuning the threshold** (manual, not CI, needs `BOT_LLM_BASE_URL`/`BOT_LLM_API_KEY` in the shell, never in the repo):
  1. `python3 scripts/bot-llm-eval.py --model typesafe/jev-1.13 --adapter decision --model qwen/qwen3-32b --adapter openai`.
  2. Read the accuracy-per-threshold table of the decision adapter. Choose the lowest threshold in `{0.5, 0.6, 0.7, 0.8, 0.9}` at which **no row expecting `GRADES`, `EVENTS` or `NONE` is answered `ADMIN_GRADE_ABOVE`** and overall accuracy does not drop by more than two rows against the previous run. A wrong admin answer is harmless in data terms (the funnel still asks and the role gate holds) but confusing; a false negative costs only a menu.
  3. If the chosen value differs from `0.7`, change `min-confidence` in `application.yml`, the sentence in `current-system.md` and the decision record's Consequences, in the same commit as the eval results summary (accuracy numbers only, no texts).
  4. If Jev's accuracy on the new rows is below the rest of the set by a clear margin, revise the hint wording first (hints are the only lever; the instructions file is shared) and run again. Record the final hint.
- Docs:
  - `bale-smart-bot.md`: Status line gets «admin grade query (step 2) implemented <date>»; "Phase-1 intents" gets an **Admin** row (`ADMIN_GRADE_ABOVE`, «همه‌ی دانش‌آموزان پایه‌ی هفتم با نمره‌ی بالای ۱۵», sources: `AcademicFacade.listClasses/listStudentsByClass/listGradesForStudents`); the sentence «admin and finance statistics» under "Out of phase 1" is narrowed to «other admin and finance statistics»; "Common rules" gets «Parameters of an intent (grade, threshold) are parsed on the server and never by the model; missing ones are asked with buttons»; Unresolved questions get the six items below.
  - `current-system.md`, "Bale bot": the intent list says ten intents; a new bullet "Admin grade query" describing the funnel, the callback format, D1–D6 and the reply; "What the model receives" adds the two labels.
  - Decision record: a short "2026-11-16 addendum": privacy rule 2 extended with "parameters parsed on the server are redacted from the text"; the threshold re-tuning result.
  - Run `python3 scripts/check-diagram-parity.py` → `parity OK` (no diagram changes expected).

- [ ] **Step 1: Edit the script and the TSV.**
- [ ] **Step 2: Verify offline.** `python3 scripts/bot-llm-eval.py --model typesafe/jev-1.13 --adapter decision --model qwen/qwen3-32b --adapter openai --dry-run` → exit 0 and the first body contains `ADMIN_GRADE_ABOVE` only when the dry-run row's roles include `SCHOOL_ADMIN` (change the dry-run sample row to `roles="SCHOOL_ADMIN,PARENT"` so both appear). `python3 -c "import csv;r=list(csv.DictReader(open('scripts/bot-llm-eval-set.tsv'),delimiter='\t'));print(len(r))"` → `114`.
- [ ] **Step 3: Run the evaluation** against Liara (manual; skip in CI) and apply the threshold rule above. If the environment has no key, leave `min-confidence` unchanged and say so in the PR.
- [ ] **Step 4: Write the doc updates** and run the parity script.
- [ ] **Step 5: Commit** `docs(notification): admin grade query in spec, evaluation set and decision record`.

---

## Acceptance criteria (for the PR description)

1. With `BOT_LLM_ENABLED=false`, a linked school admin reaches the answer from «دانش‌آموزان بالای نمره» through at most three button steps; no other role sees that button, its help line or its model hint.
2. A forged `callback_data` (`ag:<other school>:…`, `ag:10:7:15` from a parent) answers «این مورد برای شما در دسترس نیست.» and triggers no academic read.
3. Grade and threshold come only from the server-side parser or from buttons; the text sent to the model contains «[پایه]»/«[نمره]» in place of the parsed numbers (asserted on the captured request).
4. The reply is built from `BotTexts` only; a descriptive grade, a missing current year, a missing class and an empty result each have their own sentence.
5. No user text, grade or threshold appears in any log line (existing `logsNeverContainText` extended).
6. The fast suite, `ModularityTests` and `ArchitectureTests` pass; the eval script dry-run passes with 114 rows.

## Open product questions (to list in `bale-smart-bot.md`; not decided here)

1. **Names or counts.** Should the reply list student names with scores (D6, current plan) or only the count per class? A names reply is personal data of minors delivered to a chat; the count alone may be enough for the admin's purpose.
2. **Result-size cap.** Is 30 rows right, should it be per class, and should a capped reply offer a «ادامه» button (which would need a page offset in `callback_data`) instead of «… و N نفر دیگر»?
3. **Which roles.** Is the feature for `SCHOOL_ADMIN` only (current plan, literal role as the task states), or should it follow a permission (e.g. a new `GRADES_VIEW_ALL`, the way `/finance` moved to `FINANCE_VIEW_ALL` in `finance-view-all-permission.md`) so custom roles with grade rights can use it too? The school's bot role setting still applies either way.
4. **Admin who is also a teacher (or a parent).** Today the menus are the union of the roles. Should a teacher-admin's query be limited to their own classes, or does the admin role win? Does a mixed-role user get the higher `SCHOOL_ADMIN` daily limit for free text (it does today: highest of the roles)?
5. **What "score" is** (D1): overall grade of the current year, latest term; per-course grades and averages are out. Should an average over courses or a specific course («ریاضی بالای ۱۵») be supported, and should «۱۵ به بالا» be inclusive (D3)?
6. **Spec scope.** The spec lists admin statistics outside phase 1; confirm that this first admin intent is in scope now, and whether the `SCHOOL_ADMIN` daily free-text limit of 100 (an assumption) is right once admins have an intent of their own.
