# Custom Roles In The Role Switcher And Shortcut Cards; Timetable Gate Aligned

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
- `MyTimetableController` required, per view, an active membership with the matching
  role (`STUDENT` for `OWN`, `PARENT` for `WARD`, `TEACHER` for `TEACHING`), while the
  other `/api/me/**` endpoints require only an active membership in the school.

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
3. **The class timetable uses the shared self-service gate** (`hasActiveMembership`,
   any role). The views are decided by the data: `OWN` if the user has an enrollment,
   `WARD` per linked child, `TEACHING` if the user has cells as a teacher in the current
   year. A user with none of these gets the empty result.

## Consequences To Know

- A teacher with no timetable cells no longer gets an empty `TEACHING` view; the page
  shows its generic "no timetable recorded" message instead of a dedicated "no hours"
  state.
- A member whose student role is deactivated but who holds another active role in the
  same school still sees their own enrollment's timetable, as with grades and invoices
  (documented union behavior).
- A custom role holding many permissions gets many cards; there is no cap.

## Out Of Scope

- Revoking an exam session token (separate change).
- Other places that print a role code (none found besides the two above).

## Acceptance Criteria

- The role switcher and the dashboard table show a custom role's title in two schools
  with the same code and different titles, never the raw code.
- A custom role sees the shortcut cards whose permissions it holds and no others; the
  five system roles see exactly the cards they saw before.
- `/api/me/schools/{id}/timetable`: non-member and inactive member get the empty
  result; an active member gets the views their data supports regardless of role.

## Tests

- `NavSharedSourceRenderTest`, `NavCatalogTest`, `NavPermissionsFlowIntegrationTest`
  (title through the real dashboard, facade scoping), `TimetableFlowIntegrationTest`
  (idle teacher, data-driven views, deactivated memberships).
