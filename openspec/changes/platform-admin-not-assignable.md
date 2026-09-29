# PLATFORM_ADMIN Is Not Assignable

Status: proposed 2026-09-29, implemented in the same change. The current-state
statement lives in [`specs/current-system.md`](../specs/current-system.md) under
Current Limitations. Raised as issue #75, which followed from the documentation
correction in #74.

## Verified Baseline

`PLATFORM_ADMIN` is a seeded system role with no permission grants and no
permission path, but it is assignable: a `MEMBERSHIP_MANAGE` holder can post it
as `roleCode` and persist a membership in it.

- `IamSeedDataRunner.java:170` saves the `PLATFORM_ADMIN` `RoleEntity`. The code
  appears exactly once in that file and is never passed to `grant(...)`, so it
  has zero `role_permissions` rows.
- `IamFacadeImpl.createMembership` resolved a school-custom role first, then any
  system role (`schoolId IS NULL`) by code, with no exclusion.
- `MembershipController.java:36-46` gates the `POST` on `MEMBERSHIP_MANAGE`
  alone, and `MembershipResponse` carries `roleCode`, so the created membership
  was visible in both the `POST` response and `GET .../memberships`.
- `PermissionDecisionService.decide` denies any context that is not a
  `SchoolContext`; the platform-scoped branch is explicitly deferred (line 44).
- `IamFacadeImpl.listRoles` filtered the same code out with the comment that it
  must not appear in any UI, and
  `RoleManagementFlowIntegrationTest.adminSeesSystemPermissionsAndRolesButNotPlatformAdmin`
  asserted that contract on the read side only.

So the membership conferred nothing today. The problem is ordering: rows could
accumulate now and would begin granting the moment a platform permission path is
built. Rejecting them now is cheaper than cleaning them up later.

## Proposed Contract

- `createMembership` rejects `roleCode` `PLATFORM_ADMIN` before any role lookup.
- The rejection reuses `IamError.RoleNotFound`, the same error a nonexistent
  code produces — `400 role-not-found`. The response must not distinguish the
  reserved code from an undefined one, because `listRoles` and `web.RoleLabels`
  already contract that this code appears in no UI.
- Every other system and school-custom role stays assignable, unchanged.
- The read-side filter in `listRoles` is unchanged; both enforcement points now
  share one `PLATFORM_ADMIN_ROLE_CODE` constant so they cannot drift apart.

## Compatibility And Security

No REST payload, permission, tenant, schema, or session model changes. The only
behavioral difference is that a request which previously returned `200` with a
`PLATFORM_ADMIN` membership now returns `400 role-not-found`. No supported UI
path could send that value, since no role picker offers it.

This is a defense-in-depth ordering fix, not a live-escalation fix: the role
confers nothing until a platform permission path exists.

Deliberately out of scope:

- **No cleanup migration.** Whether `PLATFORM_ADMIN` membership rows already
  exist in the deployed database is unverified — this change was written from
  code inspection only. A destructive migration should not land before that row
  count is known. The guard stops new rows; any cleanup is a separate, informed
  change.
- **No shared assignment helper.** The guard lives in `createMembership`, the
  only assignment path today. A shared helper would be speculative until a
  platform-scoped assignment path exists that must deliberately reserve the code.

## Acceptance Criteria

- A `MEMBERSHIP_MANAGE` holder posting `roleCode: "PLATFORM_ADMIN"` gets
  `400` with type `role-not-found`, and no membership row is created.
- The same holder can still assign other system roles.
- `listRoles` still omits the code.
- `ModularityTests.verifyModularStructure()` still passes.

## Tests

- `MembershipManagementFlowIntegrationTest.adminCannotAssignPlatformAdminRole` —
  the write-side counterpart to the existing read-side assertion, covering both
  the status/type and the absence of a persisted row.
- `MembershipManagementFlowIntegrationTest.otherSystemRolesRemainAssignable` —
  proves the guard did not widen into a general rejection of system roles.
- `RoleManagementFlowIntegrationTest.adminSeesSystemPermissionsAndRolesButNotPlatformAdmin`
  — unchanged, continues to cover the read side.

## Unresolved Questions

- Whether `PLATFORM_ADMIN` membership rows exist in the deployed database, and
  whether a cleanup migration is warranted. Requires a query against that
  database; not answerable from the repository.
- Whether a future platform-scoped assignment path should reserve this code
  through a shared helper rather than a per-method guard. Depends on whether such
  a path is planned.
