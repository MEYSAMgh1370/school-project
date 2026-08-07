# UI/UX Implementation Report

Date: 2026-08-07

This report covers the safe implementation pass performed with the UI/UX audit. It intentionally preserves business endpoints, role permissions, module boundaries, SSR/JTE rendering, Alpine/fetch behavior, and existing dependencies. No broad navigation rewrite, table API redesign, attendance module, billing flow, or messaging UI was attempted.

## Scope And Change Ownership

The repository was already dirty before this audit. Existing user-owned changes affected many JTE templates and `app.css`; they included navbar action semantics, table header scopes, expense labels, logical CSS utilities, and visual refinements. They were inspected and preserved. Audit-authored changes overlap some of those files, so the aggregate Git diff is not solely the work described here.

The audit did not create an OpenSpec proposal. These changes close verified security, identity, data-durability and accessibility defects while preserving documented contracts. The larger unresolved work is recorded as roadmap/proposal work in `docs/ui-ux-audit.md`.

The design-page brief used for the safe fixes was:

- Goal: invited users activate safely; users can register a canonical phone and later use OTP; teachers identify the correct learner; students know whether an exam answer is saved; keyboard users can enter and leave dialogs predictably.
- Roles/access: public auth and tokenized exam paths are unchanged; management pages use existing permissions, specifically the narrow `MEMBER_NAMES_VIEW` contract; no permission was broadened.
- Owning surfaces: IAM public/auth and phone boundary; web page shells; academic/finance/study/exam client composition; existing exam token API.
- Navigation/hierarchy: no new page or broad IA; activation success gains one login CTA; visual tab groups stop claiming an unsupported ARIA Tabs contract.
- States: auth success/error, exam saving/saved/failed, modal open/confirm/closed, required identity lookup failure, Persian/Arabic phone input.
- RTL/mobile: Persian messages and accessible names; logical CSS; no framework or layout rewrite.
- Data: activation token remains inert DOM data; phone canonicalizes before persistence/lookup; member names use existing tenant header and bounded GET batches; mutations keep CSRF behavior; exam answer contract is unchanged.

## Changes Made

| Change | Reason | Before | After | Main files |
| --- | --- | --- | --- | --- |
| Removed activation expression injection | A crafted public query token executed after HTML entity decoding | Token was interpolated inside `x-data` JavaScript | Token is an escaped `data-token`; static Alpine component reads `dataset`; regression probe asserts code/data separation | `activate.jte`, `activate.js`, `RegistrationFlowIntegrationTest` |
| Externalized registration behavior | Align templates with the repository code-placement rule and improve states | Large Alpine/fetch object lived in JTE | Static `register.js`; alert semantics, autocomplete and preserved behavior | `register.jte`, `register.js`, `RegistrationFlowIntegrationTest` |
| Canonicalized phone digits at IAM/data boundary | Persian/Arabic and ASCII variants could bypass uniqueness, fail OTP lookup, and legacy rows could become unreachable | Raw phone string was validated, queried and stored; no backfill | Client gives immediate ASCII consistency; server canonicalizes before store/lookup/uniqueness; V3 backfills existing rows, blocks ambiguous ownership and adds a database check | `PhoneNumberCanonicalizer`, `IamFacadeImpl`, `V3__canonicalize_iam_phone_numbers.sql`, dashboard/OTP/migration tests |
| Added least-privilege member-name helper | Teachers/finance users cannot read the full member directory but need names for allowed tasks | Some pages duplicated a narrow lookup; others rendered unknown/raw identities after 403 | Shared helper calls `/memberships/names`, batches at 100 IDs, merges results and throws on HTTP failure; six pages use it | `member-names.js`, page scripts/templates, Node regression test |
| Hardened exam answer persistence | Navigation could abort saves; a first fix used an unbounded page-wide mutex | In-flight save either allowed data loss or locked the entire timed attempt | Pending state is per question; other questions/sections remain usable; captured section, optimistic rollback, `keepalive` and announced status | `exam-take.js`, `exam-take.jte` |
| Completed dialog focus behavior | `aria-modal` was inaccurate without focus containment/return | Open/close toggled Alpine state only | Shared utility captures/restores opener, focuses first/confirmation controls, traps Tab and supports Escape; Persian close names and labelled dialogs | `modal-focus.js`, both study-plan templates/scripts |
| Removed incomplete ARIA Tabs claims | Visual tabs did not implement roving focus, arrow keys or tabpanels | `role=tablist/tab` and `aria-selected` overstated behavior | Groups remain ordinary controls pending a product-level navigation/button/tab decision | Login, exam and study-plan templates |
| Added bypass/main landmarks | Every authenticated page repeated navigation before content | No skip link or main landmark | Shared keyboard-visible skip link and one target/main landmark on every top-level template | `layout/main.jte`, top-level JTE templates, `app.css` |
| Improved form/table semantics | Generated controls and duplicate IDs had ambiguous names | Dashboard phone had no label; payment IDs repeated per invoice; generated grade/study/exam controls were ambiguous; action headers blank | Persistent/dynamic labels, unique payment IDs, search input semantics, screen-reader action headings and explicit button types | Dashboard, grade, finance, expense, exam and study-plan templates |
| Improved auth/async feedback | Several states were visual only | Errors/successes could flash or go unannounced | `x-cloak`, alert/status live behavior, localized recovery copy and activation login action on patched surfaces | Auth templates/scripts, dashboard, exam taking |

## Files Modified By This Audit

Java and tests:

- `src/main/java/com/example/roshdeandishe/iam/IamFacade.java`
- `src/main/java/com/example/roshdeandishe/iam/internal/IamFacadeImpl.java`
- `src/main/java/com/example/roshdeandishe/iam/internal/PhoneNumberCanonicalizer.java`
- `src/main/resources/db/migration/V3__canonicalize_iam_phone_numbers.sql`
- `src/test/java/com/example/roshdeandishe/GradesPageFlowIntegrationTest.java`
- `src/test/java/com/example/roshdeandishe/OtpLoginFlowIntegrationTest.java`
- `src/test/java/com/example/roshdeandishe/RegistrationFlowIntegrationTest.java`
- `src/test/java/com/example/roshdeandishe/PhoneNumberCanonicalizationMigrationTest.java`
- `src/test/js/member-names.test.js`

Shared assets and page behavior:

- `src/main/resources/static/css/app.css`
- `src/main/resources/static/js/activate.js`
- `src/main/resources/static/js/classes.js`
- `src/main/resources/static/js/dashboard.js`
- `src/main/resources/static/js/exam-take.js`
- `src/main/resources/static/js/exams.js`
- `src/main/resources/static/js/finance.js`
- `src/main/resources/static/js/grades.js`
- `src/main/resources/static/js/member-names.js`
- `src/main/resources/static/js/modal-focus.js`
- `src/main/resources/static/js/my-study-plan.js`
- `src/main/resources/static/js/register.js`
- `src/main/resources/static/js/students.js`
- `src/main/resources/static/js/study-plans.js`

Templates:

- `src/main/jte/activate.jte`
- `src/main/jte/classes.jte`
- `src/main/jte/curriculum.jte`
- `src/main/jte/dashboard.jte`
- `src/main/jte/exam-take.jte`
- `src/main/jte/exams.jte`
- `src/main/jte/expenses.jte`
- `src/main/jte/finance.jte`
- `src/main/jte/grades.jte`
- `src/main/jte/landing.jte`
- `src/main/jte/layout/main.jte`
- `src/main/jte/login.jte`
- `src/main/jte/my-exams.jte`
- `src/main/jte/my-grades.jte`
- `src/main/jte/my-invoices.jte`
- `src/main/jte/my-study-plan.jte`
- `src/main/jte/people.jte`
- `src/main/jte/register.jte`
- `src/main/jte/roles.jte`
- `src/main/jte/school-entry.jte`
- `src/main/jte/school-new.jte`
- `src/main/jte/students.jte`
- `src/main/jte/study-plans.jte`

Documentation:

- `docs/ui-ux-audit.md`
- `docs/ui-ux-implementation-report.md`

`src/main/jte/layout/navbar.jte` remains modified in the working tree but was a pre-existing user change and was not edited by this audit. `.air/worktree.json` is environment metadata and was not modified or evaluated as product code.

## Verification Performed

| Command/check | Result |
| --- | --- |
| `./mvnw -DskipTests compile` | Passed; 24 JTE files generated and 266 Java sources compiled |
| `node --check` for every static JavaScript file | Passed |
| `node --test src/test/js/member-names.test.js` | Passed; 350 IDs produced 100/100/100/50 batches and 350 merged names |
| `./mvnw -Dtest=OtpLoginFlowIntegrationTest,RegistrationFlowIntegrationTest test` | Passed: 16 tests, 0 failures/errors |
| `./mvnw -Dtest=PhoneNumberCanonicalizationMigrationTest test` | Passed: 2 tests; legacy Persian/Arabic rows backfilled, check constraint enforced, ambiguous ownership aborts transaction |
| `./mvnw -Dtest=GradesPageFlowIntegrationTest,MoshavereFrontendPageFlowIntegrationTest,LoginDashboardFlowIntegrationTest,MyStudyPlanFlowIntegrationTest,StudyPlanManagementFlowIntegrationTest,ExamFlowIntegrationTest test` | Passed: 44 tests, 0 failures/errors |
| `./mvnw test` | Passed: complete suite, 181 tests, 0 failures/errors |
| `git diff --check` | Passed at the time of implementation review |
| Browser rendering | Reviewed at 1440px and 390px for landing, login, dashboard, people, finance, expenses and study plans |
| Activation injection probe | Reproduced before fix with harmless payload; post-fix headless Chrome confirmed the decoded token remains inert data and does not set the probe attribute |

An early targeted Maven attempt did not complete after Docker/Testcontainers was unavailable inside the restricted execution context and the run was interrupted. Docker was then checked with approved host access; both targeted suites above were rerun successfully. The successful results supersede that environmental failure.

The passing suite still emits pre-existing operational/deprecation warnings: expected SMTP connection failures in tests that verify graceful mail outage, `spring.jpa.open-in-view` enabled, and Mockito dynamic agent attachment. None failed the build, but they should not be mistaken for audit-created regressions.

## Remaining Problems

- Desktop navbar overflow and custom-role navigation remain high-priority architectural UI defects.
- Expense cancel, attachment delete, membership deactivate, clear week and exam regeneration still need consistent confirmation.
- Large tables still lack server pagination/filter/sort and a touch-safe action layout.
- Currency unit, Jalali date entry and number formatting require product decisions before a safe global formatter can be implemented.
- Most management-page errors still need live-region, field association, required/partial dependency classification and retry.
- Attendance/absence notification, full messaging, billing/upgrade and password recovery are absent product journeys.
- Self-service pages still expose internal learner/topic IDs in several places.
- The study calendar stylesheet still conflicts with token, RTL and touch-target requirements.
- V3 intentionally aborts if two legacy accounts canonicalize to the same phone. This preserves identity safety but requires an operator runbook to resolve ownership before deployment can continue.
- Computed contrast, screen-reader output, browser back/forward state and real-device touch behavior were not automatically tested.

## Decisions Requiring Product-Owner Input

1. What is the canonical financial unit in persistence and display (rial/toman), and what are conversion/rounding rules?
2. Must all date entry be Jalali, or may native Gregorian technical inputs remain with a Jalali preview?
3. Should navigation represent effective permissions across all active roles, and what purpose does the display-role selector serve?
4. For view-without-manage permission, should actions be omitted, disabled with explanation, or shown only after capability checks?
5. What is the supported upgrade/contact path for paid modules?
6. Are attendance and messaging web priorities, Bale workflows, native-app workflows, or shared capabilities?
7. Should self-service DTOs include safe child/topic display labels?
8. What is the canonical phone format beyond Unicode digit translation (country code, separators, leading zero), and how should legacy collisions be resolved?
9. Are visual tab groups meant to be addressable navigation, simple mode buttons, or full ARIA Tabs?
