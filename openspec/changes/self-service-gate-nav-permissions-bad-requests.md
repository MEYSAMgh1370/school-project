# Self-Service Membership Gate, Permission-Aware Navigation, Client-Error Statuses

Status: proposed and **implemented 2026-10-03**. Three small technical items that
were reported as adjacent findings while building the class timetable
([`class-timetable.md`](class-timetable.md)). None needed a product decision beyond
what is recorded under Decisions; the assumptions are listed so they can be
overturned.

## Verified Baseline

From code and tests, not from memory:

- **Self-service gap.** The `/api/me/**` endpoints carry no permission: they return
  the caller's own data (`MyGradesController`, `MyInvoicesController`,
  `MyEnrollmentController`, `MyExamsController`, `MyStudyIntervalController`). They
  resolve the data from `AcademicFacade.findMyEnrollment` and `listWards`, which look
  at the student row and the guardian links. Deactivating a membership
  (`school_memberships.active = false`) deletes neither, so a former member kept
  receiving grades, invoices, exams and study intervals. The permission-gated admin
  endpoints were already safe (an inactive membership grants no permission).
  `MyGradesController` and `WardsAssembler` document that these endpoints
  intentionally do **not** gate on role (the union of the user's own enrollment and
  linked children).
- **Navigation.** `NavCatalog` decided link visibility only by built-in role codes. A
  custom role that holds a management permission (for example a deputy with
  `TIMETABLE_MANAGE`) could call the API but got no sidebar link.
- **Client errors.** `PlatformExceptionHandler` maps every unhandled exception to
  `500`, so a malformed JSON body or an unknown enum value, an invalid or missing query
  parameter, an unsupported method or content type all answered `500 internal-error`
  instead of a `4xx`.

## Decisions (assumptions to confirm)

1. **Gate = at least one active membership in the school, any role.** Not a
   role-matching gate: that would remove the documented union behavior (a teacher
   whose own student membership is deactivated still sees their data while another
   membership is active). A user with no active membership at all gets nothing:
   lists are empty, `/api/me/enrollment` is `204`, and starting an exam attempt or
   editing a study interval fails with the existing "you are not a student here"
   domain errors. The class timetable keeps its stricter per-role gate, which is
   specific to timetable views.
2. **Navigation shows a management link to a custom role that holds the page's
   management permission**, in addition to the built-in role rule. Only the sidebar
   and group headers use it; the dashboard shortcut cards stay role-based (the card
   mapping is curated and not a function of permissions, for example the expenses card
   is for the finance role only).
3. **Client errors answer `4xx`** with the existing problem-detail shape and stable
   type slugs, without leaking internal class or parser messages.

## Contract

### Self-service gate

- `IamFacade.hasActiveMembership(userId, schoolId)`: true when the user has any
  active membership in that school (every role is its own membership).
- Applied in `MyGradesController`, `MyInvoicesController` (empty list),
  `MyEnrollmentController` (`204`), and in `MyExamsController` /
  `MyStudyIntervalController` where the student is resolved (no exams, no
  intervals, and the write operations fail as for a non-student).
- Not changed: exam-taking with an already-issued session token
  (`ExamTakeController`) keeps working until the token expires; the finance/admin
  endpoints are permission-gated and already refuse inactive memberships.
- Data is kept: reactivating the membership restores everything.

### Permission-aware navigation

- `NavCatalog.Link.permissions` (**all-of**): the management permission of the page plus
  any permission its *blocking* first load needs, because permissions are chosen
  independently in a custom role and a page whose first request answers `403` closes
  itself: `MEMBERSHIP_MANAGE`, `STUDENT_ENROLL` + `STUDENT_VIEW`, `ROLE_MANAGE`,
  `ACADEMIC_YEAR_MANAGE`, `CLASS_VIEW`, `TIMETABLE_MANAGE`, `CURRICULUM_VIEW`,
  `STUDYPLAN_VIEW`, `EXAM_VIEW`, `GRADES_EDIT` + `CLASS_VIEW`, `FINANCE_MANAGE` +
  `FINANCE_VIEW`, `EXPENSE_VIEW`. Secondary loads that each page already treats as
  best-effort (names, class lists, ...) are not required. The self-service «من» links
  have none (their permissions such as `GRADES_VIEW` are shared with other roles).
- Sidebar condition: `roles.includes(selectedRole) || ['A','B'].every(p =>
  selectedRole's permissions.includes(p))`. A group shows when any of its links does.
- `IamFacade.findPermissionCodesByRole(schoolId, roleCodes)` returns the permissions of
  the user's roles with a fixed number of queries (not one per role/permission), the
  navbar model carries them per school and role, and `navbar.js` mirrors the selected
  role's permissions into Alpine (`permissions`, `$store.nav.permissions`), refreshed
  on every school or role change (the same custom-role code can hold different
  permissions in different schools).
- Invariant, enforced by `NavPermissionsFlowIntegrationTest`: for every seeded system
  role, "holds all of the link's permissions" is exactly "the role is in the link's
  roles", so no link appears or disappears for an existing role.

### Client errors

`PlatformExceptionHandler` maps `HttpMessageNotReadableException` to
`400 malformed-request-body`, `MethodArgumentTypeMismatchException` to
`400 invalid-parameter`, `MissingServletRequestParameterException` to
`400 missing-parameter`, `HttpRequestMethodNotSupportedException` to
`405 method-not-allowed` and `HttpMediaTypeNotSupportedException` to
`415 unsupported-media-type`; anything else is still `500 internal-error`.

## Out Of Scope

- Role display names for custom roles in the role switcher (it still shows the role
  code; `RoleLabels` knows only the system roles). *Done in
  [`custom-role-ui-and-timetable-gate.md`](custom-role-ui-and-timetable-gate.md).*
- Dashboard shortcut cards for custom roles. *Done in the same change.*
- Revoking an exam session token when a membership is deactivated. *Done in [`exam-token-membership-check.md`](exam-token-membership-check.md).*
- Making the class timetable's per-role gate match this one. *Done in the same change.*
- Other Spring MVC exceptions (not-acceptable, request-body size, ...).

## Acceptance Criteria

- A user whose every membership in a school is inactive receives no grades, invoices,
  enrollment, exams or study intervals from `/api/me/**` for that school, and gets them
  back on reactivation; a user with another active membership keeps their own data.
- A custom role holding a page's management permission sees that link (and only such
  links); system roles see exactly the links they saw before.
- The five client-error cases above answer the listed `4xx` with a problem-detail body
  and no internal class or parser text.

## Tests

- Unit (no Docker): `SelfServiceMembershipGateTest` (a former member never reaches
  the academic, finance, exam or study-plan facades), `NavCatalogTest`,
  `NavSharedSourceRenderTest`.
- Integration (CI): `SelfServiceMembershipFlowIntegrationTest` (deactivate and
  reactivate; parent; the not-role-based case), `NavPermissionsFlowIntegrationTest`
  (the system-role invariant, a custom role reaching the navbar),
  `PlatformExceptionHandlerFlowIntegrationTest` (the five `4xx` cases).
- Browser, mocked data: sidebar for a custom role in two schools with different
  permissions, a system teacher and a student switching roles.
