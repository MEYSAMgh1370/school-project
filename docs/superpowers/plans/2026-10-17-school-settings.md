# School Settings Page and Finance Overview Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a `/settings` page backed by a `school.school_settings` model (current year, auto-promotion, instalments, reminder roles, Bale bot on/off and roles) and rework `/finance` into a per-student overview with a scope filter and name search.

**Architecture:** The school module owns settings storage (new table plus `SchoolFacade` methods); existing per-setting endpoints keep working on top of the new table (dual-writing the old columns until a later migration drops them). `/settings` is a thin JTE shell with an Alpine page script that reads and writes through the existing endpoints plus one new aggregated read and one new bot write. Finance gets a new server-side student overview endpoint; the finance page switches from invoice rows to student rows.

**Tech Stack:** Java/Spring Boot + Spring Modulith, Flyway (SQL migrations), JTE + Alpine.js + Bulma, JUnit 5 + Testcontainers (integration), `node --test` (pure JS logic), `./mvnw`.

**Spec:** `docs/superpowers/specs/2026-10-17-school-settings-design.md`

## Global Constraints

- UI text is Persian and RTL; Latin data gets local `dir="ltr"`; no inline `<style>`/`style=`, no `alert()`/`confirm()`, no physical left/right CSS (`DESIGN.md`, `openspec/conventions.md`).
- Navbar visibility is display-only; authorization is enforced on the REST layer (`@RequiresPermission`) and `TenantContext.requireMatches(schoolId)` on every admin endpoint.
- Migration `V33__school_settings.sql` (highest on master is `V32`, so ours is V33); Flyway owns the schema (`decisions/2026-07-30-flyway-schema-ownership.md`).
- Old columns `school.schools.installments_enabled` (V28) and `school.schools.auto_promotion` (V11) are **kept** and dual-written; dropping them is a later migration, not this plan.
- New permission code `SCHOOL_SETTINGS_MANAGE`, granted to `SCHOOL_ADMIN`; `bot_enabled` defaults to `true`; empty `bot_roles` = every role.
- Week start, time zone and locale are not part of settings.
- Cross-module access only through facades (`ModularityTests`, `ArchitectureTests` must stay green).
- Invalid input answers `400 invalid-setting` (existing `SchoolError.InvalidSetting`).
- Integration tests need Docker (Testcontainers); if Docker is unavailable, report that instead of skipping silently.
- Commit trailer on every commit: `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>` and `Claude-Session: https://claude.ai/code/session_012jn2hYfz6hWH9EDLRD6hfo`.

## Review Focus

1. School with **no current academic year**: finance overview must still list its students (every invoice counts as "current", previous debt is 0).
2. Invoices with **null `academic_year`** in a school that has a current year: counted as previous-year debt, never dropped from the overview.
3. A debtor who is `LEFT`/`GRADUATED` or whose user row has no name: still listed (status tag shown, name falls back to «نامشخص»).
4. Name search with Arabic `ي`/`ك`, ZWNJ, repeated spaces, single-word names (empty last name), and a Persian query against stored text.
5. Bot gate for a user with several schools where only one disables the bot, and a custom role excluded by `bot_roles`; event reminders are **not** affected by `bot_enabled`.
6. First read of a school with no `school_settings` row (lazy create, safe under concurrent first reads).

---

### Task 1: Settings storage and facade

**Files:**
- Create: `src/main/resources/db/migration/V33__school_settings.sql`
- Create: `src/main/java/com/example/roshdeandishe/school/internal/SchoolSettingsEntity.java`, `SchoolSettingsRepository.java`, `BotRoleEntity.java`, `BotRoleRepository.java`
- Modify: `school/SchoolFacade.java`, `school/internal/SchoolFacadeImpl.java` (installments/auto-promotion methods, ~lines 105-110 of the facade)
- Test: `src/test/java/com/example/roshdeandishe/SchoolSettingsFlowIntegrationTest.java`

**Interfaces:**
- Produces (in `SchoolFacade`):
  - `record BotSettings(boolean enabled, java.util.Set<String> roles)` — `roles` empty = all roles
  - `BotSettings getBotSettings(SchoolId schoolId)`
  - `void setBotSettings(SchoolId schoolId, boolean enabled, java.util.Set<String> roles)`
  - `boolean isBotAllowed(SchoolId schoolId, java.util.Set<String> roleCodes)` — true when enabled and (`roles` empty or intersects `roleCodes`)
  - existing `isInstallmentsEnabled/setInstallmentsEnabled/isAutoPromotion/setAutoPromotion` keep their signatures but read/write `school.school_settings` (and also write the old `schools` column)
- Consumes: `SchoolId`, `SchoolError.SchoolNotFound`, `SchoolError.InvalidSetting`.

- [ ] **Step 1: Write failing tests** in `SchoolSettingsFlowIntegrationTest`:
  - `migrationCopiesExistingValues`: insert a school with `installments_enabled=true, auto_promotion=true` into `school.schools` via `JdbcTemplate`, delete nothing, run the V33 copy statement logic by asserting `schoolFacade.isInstallmentsEnabled(id)` and `isAutoPromotion(id)` are `true` (settings row created lazily from the old columns when absent).
  - `newSchoolDefaults`: `assertThat(facade.getBotSettings(id)).isEqualTo(new BotSettings(true, Set.of()))`; `isBotAllowed(id, Set.of("PARENT"))` is `true`.
  - `botRolesRestrictAccess`: after `setBotSettings(id, true, Set.of("PARENT"))` → `isBotAllowed(id, Set.of("PARENT"))` true, `isBotAllowed(id, Set.of("TEACHER"))` false; `setBotSettings(id, false, Set.of())` → false for every role.
  - `dualWrite`: after `setInstallmentsEnabled(id, true)` the old `school.schools.installments_enabled` column is `true` too.
  - `concurrentFirstReadCreatesOneRow`: two threads call `getBotSettings` for a school with no row; `select count(*) from school.school_settings where school_id = ?` is `1`.
- [ ] **Step 2: Run** `./mvnw test -Dtest=SchoolSettingsFlowIntegrationTest` — expect FAIL (methods undefined).
- [ ] **Step 3: Implement** `V33`: `school.school_settings(school_id bigint primary key references school.schools(id), installments_enabled boolean not null default false, auto_promotion boolean not null default false, bot_enabled boolean not null default true)` and `school.bot_roles(school_id bigint not null references school.schools(id), role_code varchar(64) not null, primary key (school_id, role_code))`, then `insert into school.school_settings select id, installments_enabled, auto_promotion, true from school.schools`. In `SchoolFacadeImpl` load-or-create the row with `insert ... on conflict do nothing` (follow `ReminderRoleEntity` for the roles table); the four existing methods and the three new ones use it; the old-column writes stay.
- [ ] **Step 4: Run** the same command — expect PASS. Also run `./mvnw test -Dtest=ModularityTests,ArchitectureTests`.
- [ ] **Step 5: Commit** `feat(school): school_settings table and facade (installments, auto-promotion, bot)`.

### Task 2: `SCHOOL_SETTINGS_MANAGE` permission

**Files:**
- Modify: `src/main/java/com/example/roshdeandishe/iam/internal/IamSeedDataRunner.java` (permission list near line 213; `SCHOOL_ADMIN` grants near line 294)
- Test: `src/test/java/com/example/roshdeandishe/NavPermissionsFlowIntegrationTest.java` (add a case) or a new `SchoolSettingsPermissionTest`

**Interfaces:** Produces permission code `SCHOOL_SETTINGS_MANAGE` («مدیریت تنظیمات مدرسه»), granted to `SCHOOL_ADMIN` only.

- [ ] **Step 1: Write failing test** `schoolAdminHoldsSettingsManageAndOthersDoNot`: a seeded school admin passes a `@RequiresPermission("SCHOOL_SETTINGS_MANAGE")` probe (use the endpoint from Task 3 once it exists; until then assert via `iamFacade` permission check used by `AcademicYearVisibility`) while a teacher and a parent do not.
- [ ] **Step 2: Run** the test — expect FAIL.
- [ ] **Step 3: Add** `permission(permissions, "SCHOOL_SETTINGS_MANAGE", "مدیریت تنظیمات مدرسه")` and `grant(roles.get("SCHOOL_ADMIN"), permissions.get("SCHOOL_SETTINGS_MANAGE"))`.
- [ ] **Step 4: Run** — expect PASS. **Step 5: Commit** `feat(iam): SCHOOL_SETTINGS_MANAGE permission`.

### Task 3: Settings API

**Files:**
- Create: `school/controller/SchoolSettingsController.java`, `school/dto/request/SetBotSettingsRequest.java`, `school/dto/response/SchoolSettingsResponse.java`
- Test: extend `SchoolSettingsFlowIntegrationTest`

**Interfaces:**
- Consumes: Task 1 facade methods; the role-list/validation `EventReminderController` already uses for reminder roles (extract to a shared package-private helper if reuse would copy code).
- Produces:
  - `GET /api/admin/schools/{schoolId}/settings` → `SchoolSettingsResponse(AcademicYearResponse currentYear, Boolean autoPromotion, Boolean installmentsEnabled, List<String> reminderRoles, BotSection bot)`; each section is `null` when the caller lacks its permission (`ACADEMIC_YEAR_MANAGE` → year/autoPromotion, `FINANCE_MANAGE` → installments, `YEARLY_PLAN_MANAGE` → reminderRoles, `SCHOOL_SETTINGS_MANAGE` → bot); `403` when the caller holds none of the four.
  - `PUT /api/admin/schools/{schoolId}/settings/bot` body `{enabled: Boolean, roles: [String]}` (`SCHOOL_SETTINGS_MANAGE`) → `BotSection(enabled, roles)`; null `enabled` or an unknown/`PLATFORM_*` role code → `400 invalid-setting`.
  - Bot setting changes are logged at INFO (school id, enabled, role codes; no user text) — the finance audit table is finance-only, so this deviates from "audited like INSTALLMENTS_SETTING_CHANGED"; flag it in the PR.

- [ ] **Step 1: Write failing tests**: `getReturnsOnlyPermittedSections` (admin sees all four; finance admin sees only `installmentsEnabled`; teacher gets `403`), `putBotValidatesRoles` (`roles:["NOPE"]` → 400 `invalid-setting`; `roles:["PLATFORM_ADMIN"]` → 400), `putBotRoundTrips`, `putBotRequiresPermission` (finance admin → 403), `tenantMismatchIsRejected`.
- [ ] **Step 2: Run** — expect FAIL. **Step 3: Implement** the controller (pattern: `AcademicYearController`). **Step 4: Run** — PASS. **Step 5: Commit** `feat(school): settings read and bot settings write endpoints`.

### Task 4: Bot enforcement

**Files:**
- Modify: `notification/internal/BaleUpdateHandler.java` (`describeMemberships`, `describeWards`, around lines 119-120 and 165-192)
- Test: `src/test/java/com/example/roshdeandishe/notification/internal/BaleUpdateHandlerTest.java`, `BaleFlowIntegrationTest.java`

**Interfaces:** Consumes `SchoolFacade.isBotAllowed(SchoolId, Set<String>)` with `BotContext.rolesIn(schoolId)`.

- [ ] **Step 1: Write failing tests** in `BaleUpdateHandlerTest`: `disabledSchoolIsHiddenFromMySchools`; `excludedRoleGetsDisabledMessage` (reply text exactly «بات برای مدرسه‌ی شما فعال نیست.» when every school of the user is disallowed); `onlyAllowedSchoolsAreListed` (two schools, one disabled → only the other appears); `wardsOfDisallowedSchoolAreHidden`; `helpAlwaysWorks`; `contactLinkingIsNotBlocked`. In `BaleFlowIntegrationTest`: `eventRemindersStillSendWhenBotDisabled`.
- [ ] **Step 2: Run** `./mvnw test -Dtest=BaleUpdateHandlerTest,BaleFlowIntegrationTest,EventReminderFlowIntegrationTest` — FAIL.
- [ ] **Step 3: Implement** the filter in the two describe methods; «راهنما» and contact sharing are untouched.
- [ ] **Step 4: Run** — PASS. **Step 5: Commit** `feat(notification): honour per-school bot settings`.

### Task 5: Finance student overview (backend)

**Files:**
- Create: `finance/internal/StudentNameMatcher.java`, `finance/dto/response/StudentFinanceRowResponse.java`, `StudentFinancePageResponse.java`
- Modify: `finance/FinanceFacade.java`, `finance/internal/FinanceFacadeImpl.java`, `finance/controller/FinanceController.java`
- Test: `src/test/java/com/example/roshdeandishe/FinanceStudentOverviewFlowIntegrationTest.java`, `finance/internal/StudentNameMatcherTest.java`

**Interfaces:**
- Produces (in `FinanceFacade`):
  - `enum StudentScope { ALL, CURRENT, PREVIOUS }`, `enum DebtFilter { ANY, OWING, OVERDUE, SETTLED }`
  - `record StudentFinanceQuery(StudentScope scope, String firstName, String lastName, String q, DebtFilter debt, int page, int size)`
  - `record StudentFinanceRow(Long studentId, String fullName, String studentStatus, InvoiceStatus currentStatus, BigDecimal currentRemaining, BigDecimal previousDebt, BigDecimal totalRemaining, boolean overdue)`
  - `record StudentFinancePage(List<StudentFinanceRow> items, long total, Map<StudentScope, Long> counts, int page, int size)`
  - `StudentFinancePage listStudentFinance(Long schoolId, StudentFinanceQuery query)`
  - `List<InvoiceInfo> listInvoicesOfStudent(Long schoolId, Long studentId)` — every year, newest first
  - `static boolean StudentNameMatcher.matches(String fullName, String firstName, String lastName, String q)` (package-private in `internal`)
- Endpoints (`FINANCE_MANAGE`, `TenantContext.requireMatches`): `GET /api/admin/schools/{schoolId}/finance/students?scope=all|current|previous&firstName=&lastName=&q=&debt=any|owing|overdue|settled&page=&size=` (`size` 1..100, default 50; bad values → `400 invalid-filter`), `GET .../finance/students/{studentId}/invoices`.
- Rules: previous debt = Σ(payable − amountPaid) over invoices whose year ≠ the school's current year (null year counts as previous when a current year exists; with no current year every invoice is "current"); `CURRENT` = has a current-year invoice; `PREVIOUS` = previous debt > 0 (debtors only); `ALL` = either. `counts` use the same name and debt filters, differing only by scope. Names come from `iamFacade.findUser` via `academicFacade.listStudents(schoolId)` (all statuses); missing name → «نامشخص». Matching: `firstName` = first whitespace token of the normalized full name, `lastName` = the rest, `q` = anywhere; normalization maps `ي→ی`, `ك→ک`, strips ZWNJ and tatweel, collapses spaces, case-folds.

- [ ] **Step 1: Write failing tests.** `StudentNameMatcherTest`: `matches("علی رضایی","علی","","")` true; `matches("علی رضایی","","رضايي","")` true (Arabic yeh); `matches("علی","", "رضایی","")` false; `matches("علی رضایی","", "","ضای")` true; blank filters match everything. `FinanceStudentOverviewFlowIntegrationTest`: `allScopeListsCurrentInvoiceAndPriorDebtors`; `previousScopeIsDebtorsOnly` (settled prior-year student absent); `leftAndGraduatedDebtorsAreListed`; `nullYearInvoiceCountsAsPreviousDebt`; `schoolWithoutCurrentYearStillLists`; `countsFollowFiltersButNotScope`; `pagingAndSizeBounds` (`size=101` → 400 `invalid-filter`); `parentGetsForbidden` (403); `invoicesOfStudentSpanAllYears`.
- [ ] **Step 2: Run** `./mvnw test -Dtest=StudentNameMatcherTest,FinanceStudentOverviewFlowIntegrationTest` — FAIL.
- [ ] **Step 3: Implement** facade, matcher and endpoints. Aggregate in memory over the school's invoices (one query), then page; fine for school-sized data.
- [ ] **Step 4: Run** — PASS. **Step 5: Commit** `feat(finance): per-student overview with scope, debt filter and name search`.

### Task 6: Finance page rework

**Files:**
- Modify: `src/main/jte/finance.jte`, `src/main/resources/static/js/finance.js`, `finance-logic.js`
- Test: `src/test/js/finance-logic.test.js`, `src/test/java/com/example/roshdeandishe/FinancePageRenderTest.java`

**Interfaces:**
- Consumes: Task 5 endpoints.
- Produces: `FinanceLogic.buildStudentsQuery({scope, firstName, lastName, q, debt, page, size}) -> string` (query string without `?`, omitting empty values, `encodeURIComponent` on text) in `finance-logic.js`, using the file's existing export style.

- [ ] **Step 1: Write failing tests.** JS: `buildStudentsQuery` omits empty fields, encodes Persian text, defaults `page=0,size=50`. Render: page contains scope buttons «همه»/«فقط امسال»/«سال‌های پیش» with counts, inputs `#financeFirstName`, `#financeLastName`, `#financeSearch`; does **not** contain `id="financeYear"`, `financeUnattributedBanner` or `financeInstallmentsSettingBox`; still contains the tuition box and «صدور فاکتورهای این سال برای دانش‌آموزان بدون فاکتور».
- [ ] **Step 2: Run** `node --test src/test/js/finance-logic.test.js` and `./mvnw test -Dtest=FinancePageRenderTest` — FAIL.
- [ ] **Step 3: Implement.** Student rows (name, current status tag, «بدهی پیشین», «مانده‌ی کل», student-status tag for non-active); expanding a row loads `/finance/students/{id}/invoices` and shows one panel per invoice reusing the existing payments/discount/instalment panel markup; debounce search 300 ms; tuition amount and backfill always target the settings year (no year argument sent); remove the year select, unattributed banner and installments box.
- [ ] **Step 4: Run** both commands — PASS; then `./mvnw test -Dtest='Finance*'` — PASS. **Step 5: Commit** `feat(web): finance page shows students with scope filter and name search`.

### Task 7: `/settings` page

**Files:**
- Create: `web/controller/SettingsPageController.java`, `src/main/jte/settings.jte`, `src/main/resources/static/js/settings.js`
- Test: `src/test/java/com/example/roshdeandishe/SettingsPageRenderTest.java`

**Interfaces:**
- Consumes: Task 3 `GET .../settings`, `PUT .../settings/bot`; existing `PUT .../academic-year`, `PUT .../academic-year/auto-promotion`, `POST .../academic-year/attribution`, `POST .../finance/attribute-year`, `PUT .../finance/settings/installments`, `PUT .../yearly-plan/reminder-roles`.
- Produces: `GET /settings` → template `settings`; Alpine `settingsPage()`.

- [ ] **Step 1: Write failing render tests** (pattern: `AcademicYearPageRenderTest`): four sections with ids `settingsYear`, `settingsFinance`, `settingsReminders`, `settingsBot`; each `x-show` is driven by the section being non-null in the GET response; the old-data attribution notice (classes/students and finance) lives in `settingsYear`; each section has its own save button and its own error area (`role="alert"`), loading text carries `role="status"`; scripts `/js/settings.js`, `/js/jalali.js`, `/js/calendar.js`, `/js/modal-focus.js`.
- [ ] **Step 2: Run** `./mvnw test -Dtest=SettingsPageRenderTest` — FAIL.
- [ ] **Step 3: Implement.** Move the year select, first-choice warning, auto-promotion checkbox, confirm modal and attribution blocks from `academic-year.jte`/`academic-year.js`; installments checkbox with its help text from `finance.jte`; reminder-roles checkboxes (as a `fieldset`/`legend`) from the yearly-plan modal; bot on/off plus role checkboxes. Follow `DESIGN.md` and the `design-page` skill.
- [ ] **Step 4: Run** — PASS. Verify in the browser (Chromium via the `run` skill) that each section saves and a failing save shows its error beside that section only. **Step 5: Commit** `feat(web): school settings page`.

### Task 8: Cutover and year selectors

**Files:**
- Modify: `web/controller/AcademicYearPageController.java` (return `"redirect:/settings"`), `web/model/NavCatalog.java` (replace the `/academic-year` link with `/settings`, «تنظیمات مدرسه», `List.of(ADMIN)`, permissions `ACADEMIC_YEAR_MANAGE` or `FINANCE_MANAGE` or `YEARLY_PLAN_MANAGE` or `SCHOOL_SETTINGS_MANAGE`), `yearly-plan.jte`/`yearly-plan.js` (remove the «گیرندگان یادآوری» button and modal), `classes.jte`/`classes.js` (remove `#classYear`, `#classYearFilter`), `grades.jte`/`grades.js` (remove `#gradeYear`)
- Delete: `academic-year.jte`, `static/js/academic-year.js`, `AcademicYearPageRenderTest.java`
- Test: `NavCatalogTest.java`, `NavPermissionsFlowIntegrationTest.java`, `YearlyPlan*`/`Classes*`/`Grades*` render tests that mention removed ids

**Interfaces:** `/promotion` and the yearly-plan year selector stay (they act on specific years by nature). The REST APIs still accept `academicYear`, so no backend change.

- [ ] **Step 1: Write failing tests:** `GET /academic-year` answers `302` to `/settings`; `NavCatalogTest` expects the `/settings` link for admin and finance admin, none for student; classes/grades pages no longer contain the year ids and the page scripts send no `academicYear`; yearly-plan page no longer contains the recipients button.
- [ ] **Step 2: Run** `./mvnw test -Dtest='NavCatalogTest,NavPermissionsFlowIntegrationTest,Classes*,Grades*,YearlyPlan*'` — FAIL.
- [ ] **Step 3: Implement** the changes listed above.
- [ ] **Step 4: Run** the same command plus `node --test src/test/js/` (includes `page-scripts.test.js`) — PASS.
- [ ] **Step 5: Commit** `feat(web): move year selection to settings, redirect /academic-year, drop per-page year selectors`.

### Task 9: Documentation and full verification

**Files:**
- Create: `openspec/changes/school-settings.md` (status, decisions D1-D6 from the spec, the bot-audit deviation, the spec's "later migration drops old columns")
- Modify: `openspec/specs/current-system.md` (settings page, finance overview, bot gate), `openspec/project.md` index entry, `DESIGN.md` if a new pattern was introduced (scope chips)

- [ ] **Step 1: Write the docs.** **Step 2: Run** `./mvnw clean verify` and `node --test src/test/js/` — expect all green; run `git diff --check`.
- [ ] **Step 3: Commit** `docs: record school settings and finance overview`; push and update PR #161 (mark ready only when the owner agrees).
