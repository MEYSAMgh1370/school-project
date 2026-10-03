# Custom Roles In The Role Switcher And Shortcut Cards; Self-Service Gates Made Per-Role

Status: proposed and **implemented 2026-10-03**. Follow-up to
[`self-service-gate-nav-permissions-bad-requests.md`](self-service-gate-nav-permissions-bad-requests.md),
which listed three of these as out of scope. Decisions below were confirmed by the
product owner on 2026-10-03.

## Verified Baseline

- The role switcher (`layout/navbar.jte`, top menu and sidebar account block) and the
  dashboard memberships table printed `RoleLabels.persian(code)`. `RoleLabels` knows only
  the system roles, so a custom role (for example `DEPUTY`) showed its raw code. The
  `/people` page already resolves custom role titles from `/roles`.
- Dashboard shortcut cards were gated by `Link.shortcutRoles` only. A custom role that
  holds a page's permissions saw the sidebar link (previous change) but no card.
- `MyTimetableController` requires, per view, an active membership with the matching
  role (`STUDENT` for `OWN`, `PARENT` for `WARD`, `TEACHER` for `TEACHING`), while the
  other `/api/me/**` endpoints (since the previous change) required only an active
  membership in the school, with any role.

## Decisions

1. **Role switcher and memberships table show a custom role's title.**
   `IamFacade.findCustomRoleDisplayNames(schoolId, roleCodes)` returns the titles of the
   school's own custom roles (one query; system roles, unknown codes and `PLATFORM_ADMIN`
   are not returned). `NavbarModelAssembler` asks for every role the user holds in each
   school, active or not (the dashboard table lists inactive memberships too), and
   `NavbarModel.roleLabel(schoolId, code)` returns the title, else the system label,
   else the raw code.
2. **Every shortcut card is shown to a custom role that holds all of the card's link
   permissions** (same all-of rule as the sidebar). System roles keep the curated
   `shortcutRoles` mapping: the school admin holds `EXPENSE_VIEW` but the expenses card
   is deliberately for the finance role only, so the permission branch applies only to a
   role that is not one of the five system roles. Self-service cards (`/my-*`) have no
   permissions and stay role-based.
3. **Every `/api/me/**` endpoint requires the active role that fits it** (the class
   timetable's rule becomes the common one; confirmed by the product owner, who chose
   this over relaxing the timetable). `IamFacade.findActiveRoleCodes(userId, schoolId)`
   replaces `hasActiveMembership`:
   - grades and invoices: the user's own data needs an active `STUDENT` membership,
     the linked children's data an active `PARENT` membership; the union of the two is
     returned;
   - enrollment, exams and study intervals: active `STUDENT`;
   - class timetable: unchanged (`STUDENT` → `OWN`, `PARENT` → `WARD`, `TEACHER` →
     `TEACHING`).
   The dashboard's grade-picker names (`WardsAssembler.wardRefsForCurrentUser`) follow
   the same rule so the picker and the grades endpoint agree.

## Consequences To Know

- Reverses the "union, any role" decision of the previous change: a user whose student
  membership is deactivated but who is an active teacher no longer sees their own grades,
  invoices, enrollment, exams or study intervals; a teacher whose parent membership is
  inactive no longer sees a linked child's grades or invoices.
- Data of a role is bound to that role's membership; enrollments and guardian links are
  still kept when a membership is deactivated and come back on reactivation.
- A custom role holding many permissions gets many shortcut cards; there is no cap.

## Out Of Scope

- Revoking an exam session token (separate change).
- Other places that print a role code (none found besides the two above).

## Acceptance Criteria

- The role switcher and the dashboard table show a custom role's title in two schools
  with the same code and different titles, never the raw code.
- A custom role sees the shortcut cards whose permissions it holds and no others; the
  five system roles see exactly the cards they saw before.
- Each `/api/me/**` endpoint returns nothing (empty list, `204`, or the existing domain
  error) to a user without the matching active role in that school, and the data returns
  on reactivation.

## Tests

- `NavSharedSourceRenderTest`, `NavCatalogTest`, `NavPermissionsFlowIntegrationTest`
  (title through the real dashboard, facade scoping), `SelfServiceMembershipGateTest` and
  `SelfServiceMembershipFlowIntegrationTest` (role-specific gate), `WardsAssemblerTest`.
