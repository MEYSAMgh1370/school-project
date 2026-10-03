# Exam Session Token Honors Membership

Status: proposed and **implemented 2026-10-03**. Follow-up to
[`self-service-gate-nav-permissions-bad-requests.md`](self-service-gate-nav-permissions-bad-requests.md),
which left this out of scope.

## Verified Baseline

- `ExamFacadeImpl.startAttempt` issues a JWT (`exam.token-secret`, subject = academic
  student id, claim `examId`, `exp` = the attempt's `expireAt`). The `/api/exam-take/**`
  endpoints are authorized by that token alone (query parameter `token`), not by the
  web session.
- `requireToken` verified only the signature and the expiry. Nothing in the database
  could invalidate a token, so a student whose membership was deactivated while an
  attempt was running could keep reading questions and submitting answers until the
  token expired (at most the attempt window, bounded by the exam's end).
- There is no way to cancel or delete an exam, and attempts are never deleted; a
  membership being deactivated is the only event that should end a running session.
  Starting an attempt is already gated on an active `STUDENT` membership.

## Decision

On every `/api/exam-take/**` call, after the token decodes, the facade also checks:

1. the academic student named by the token still exists and belongs to the exam's school;
2. the student's user has an **active `STUDENT` membership in the exam's school**
   (`IamFacade.findActiveRoleCodes`, the same per-role rule as `/api/me/**`, see
   [`custom-role-ui-and-timetable-gate.md`](custom-role-ui-and-timetable-gate.md)).

If either fails the call is answered exactly like a bad token:
`401 invalid-exam-token`. No new error type, no new column, no token table; the token
itself is unchanged, so a student whose membership is reactivated before the token
expires can continue.

Assumption to overturn: per-request database checks were chosen over storing a token
id (`jti`) because there is no cancel event to revoke on; a `jti` table would add
state with no trigger.

## Cost

Two extra lookups per take request (student row, active roles). The take
endpoints are small, one student at a time; no caching.

## Acceptance Criteria

- With an active `STUDENT` membership the take flow is unchanged.
- After the membership is deactivated, `GET /api/exam-take/exam`, the section questions
  and `POST /api/exam-take/submit` answer `401 invalid-exam-token`, and no answer is
  stored by the rejected submit.
- After reactivation the same token works again until it expires.

## Tests

`ExamFlowIntegrationTest.deactivatedMembershipEndsARunningExamSession`.
