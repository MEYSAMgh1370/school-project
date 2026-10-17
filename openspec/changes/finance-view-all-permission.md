# School-Wide Finance Visibility By Permission

Status: proposed and **implemented 2026-10-17**. The product owner chose the rule on
2026-10-17 (a new permission, not `FINANCE_MANAGE`), and the same day decided the
other literal-role staff checks (see Follow-Up).

## Verified Baseline

- `FinanceController.hasSchoolWideFinanceVisibility` returned true only for an active
  membership in the school whose role code was literally `SCHOOL_ADMIN` or
  `FINANCE_ADMIN`. `GET /api/admin/schools/{id}/invoices` and the per-invoice reads
  (`.../payments`, `.../discounts`, `.../installments`, through
  `requireInvoiceVisibleToCurrentUser`) used it. Everyone else saw only the invoices
  of the children linked to them (`AcademicFacade.listWards`).
- The reason (Javadoc on that method): `PARENT` also holds `FINANCE_VIEW`, so
  `FINANCE_VIEW` cannot tell parents apart from finance staff.
- A school can create a custom role on `/roles` with any catalog permission. A custom
  role such as «حسابدار» with `FINANCE_VIEW` and `FINANCE_MANAGE` passed
  `@RequiresPermission("FINANCE_VIEW")` but saw **no** invoices on `/finance` and got
  `404 invoice-not-found` on every per-invoice read, even though it could record
  payments (`FINANCE_MANAGE` endpoints are not scoped).
- `PermissionDecisionService.decide` grants a permission when **any** active
  membership of the user in the school holds it, whatever role is selected in the
  role switcher.

## Decision

A new permission `FINANCE_VIEW_ALL` («مشاهده‌ی وضعیت مالی همه‌ی دانش‌آموزان»), seeded
by `IamSeedDataRunner` and granted to `SCHOOL_ADMIN` and `FINANCE_ADMIN`, decides
school-wide finance visibility through `IamFacade.checkPermission`. No role code
is compared.

- The endpoints keep `@RequiresPermission("FINANCE_VIEW")`; `FINANCE_VIEW_ALL` only
  widens the scope from linked children to the whole school. A role with
  `FINANCE_VIEW_ALL` but without `FINANCE_VIEW` still gets `403`.
- `PARENT` does not get `FINANCE_VIEW_ALL`.
- `FINANCE_MANAGE` was rejected as the rule. It would have made a finance role that
  can only view (an auditor) impossible. This is the same split as
  `ATTENDANCE_RECORD` / `ATTENDANCE_VIEW_ALL`.

## Compatibility

- The seed runner is idempotent per code and runs in every profile, so existing
  databases (staging) get the permission and the two system-role grants at the next
  start. No migration.
- `SCHOOL_ADMIN` and `FINANCE_ADMIN` keep their exact behavior.
- **Existing custom roles do not change.** A school admin must add
  «مشاهده‌ی وضعیت مالی همه‌ی دانش‌آموزان» to an existing finance role on `/roles`.
  Those roles saw no invoices before this change either.

## Acceptance Criteria

- A custom role with `FINANCE_VIEW` and `FINANCE_VIEW_ALL` lists every invoice of the
  school and can read any invoice's payments.
- A `PARENT` still lists only linked children's invoices and gets `404` on another
  student's invoice.
- A user who is both `PARENT` and a member of a role with `FINANCE_VIEW_ALL` gets full
  visibility, whichever role is selected.
- `SCHOOL_ADMIN` keeps full visibility.

## Tests

`FinancePaymentFlowIntegrationTest`:
`adminInvoicesEndpointScopesParentToOwnWardsButAdminKeepsFullVisibility` (existing,
parent and admin) and `financeViewAllGrantsSchoolWideVisibilityToCustomRoleAndToParentHoldingIt`
(new).

## Follow-Up: Other Literal-Role Staff Checks

The product owner decided the remaining staff checks the same day (2026-10-17). Each
now goes through `IamFacade.checkPermission`. On the dashboard, the boxes are also shown on the client by permission, using `$store.nav.permissions` for the selected role, instead of by `selectedRole === 'SCHOOL_ADMIN'`:

| Place | Before | After |
|---|---|---|
| Dashboard «وضعیت شهریه» | literal `SCHOOL_ADMIN` | `FINANCE_VIEW_ALL` (so `FINANCE_ADMIN` also gets it) |
| Dashboard «حضور و غیاب امروز» | literal `SCHOOL_ADMIN` | `ATTENDANCE_VIEW_ALL` |
| Dashboard setup checklist | literal `SCHOOL_ADMIN` | `MEMBERSHIP_MANAGE` and `CLASS_MANAGE` |
| Timetable slot teacher (`requireActiveTeacher`, `GET .../timetable/teachers`) | literal `TEACHER` | new `TIMETABLE_ASSIGNABLE` («قابل انتخاب به‌عنوان معلمِ خانه‌های برنامهٔ کلاسی»), granted to `TEACHER` |
| Question box: edit or delete anyone's item (`EditRule`) | literal `SCHOOL_ADMIN` | new `QUESTION_BOX_MANAGE_ANY` («ویرایش و حذف سؤال، سربرگ و برگه‌ی دیگران در باکس سؤالات»), granted to `SCHOOL_ADMIN` |
| Question box: orphaned item | creator is neither `TEACHER` nor `SCHOOL_ADMIN` | creator no longer holds `QUESTION_BOX_MANAGE` |
| `/finance` sidebar link | `FINANCE_MANAGE` + `FINANCE_VIEW` | also `FINANCE_VIEW` + `FINANCE_VIEW_ALL` (display only) |

Unchanged on purpose:
- the dashboard school-entry box (literal `SCHOOL_ADMIN`, the school owner's address);
- «کلاس‌های من» (literal `TEACHER`; it lists the classes the user teaches);
- `/api/me/**` checks for `STUDENT`, `PARENT` and `TEACHER`, and the Bale bot's `PARENT` intents, because they are about the user's own records and linked children.

For system roles, behavior is the same except that `FINANCE_ADMIN` now sees «وضعیت شهریه». Existing custom roles get nothing new until an admin adds the permissions on `/roles`. New staff intents in the Bale bot should use `checkPermission` too.

Follow-up tests: `TeacherDashboardFlowIntegrationTest.aFinanceAdminGetsTheTuitionBoxByPermissionButNotTheAdminOnlyBoxes`,
`NavCatalogTest.financeLinkAlsoShowsForAViewOnlyCustomRoleWithFinanceViewAll`,
`TimetableFlowIntegrationTest.slotTeacherEligibilityFollowsTimetableAssignableNotTheRoleCode`,
`QuestionBoxQuestionsFlowIntegrationTest.editingOthersFollowsManageAnyAndOrphanFollowsManage`, and the updated
`TimetableFacadeTest` stubs.
