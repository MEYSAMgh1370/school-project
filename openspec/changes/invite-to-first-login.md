# Invite To First Login

Status: proposed and implemented 2026-10-12; current behavior is in
[`specs/current-system.md`](../specs/current-system.md) under Identity And Login.
This record keeps the rationale.

## Verified Baseline

Walked in a browser on 2026-10-12 with an invited teacher, and read in code.

- Adding a member and sending the invite were two separate actions on `/people`;
  after «افزودن» only «عضو جدید اضافه شد.» appeared, and the new row showed two
  contradictory tags («فعال» and «در انتظار فعال‌سازی»). The role reset to
  «دانش‌آموز» after every add.
  Evidence: [`people.js`](../../src/main/resources/static/js/people.js),
  [`people.jte`](../../src/main/jte/people.jte).
- The invite email did not say which school or role it was for
  ([`InviteEmailSender`](../../src/main/java/com/example/roshdeandishe/iam/internal/InviteEmailSender.java)),
  and `/activate` showed a raw «کد دعوت» field with no context
  ([`activate.jte`](../../src/main/jte/activate.jte)).
- After activation the form stayed on the page, the user had to go to `/login`
  and retype the email.
- An expired, used or unknown token produced only an error after submitting.

## Decisions (product owner, 2026-10-12)

- «افزودن» issues the invite immediately.
- Activation signs the user in and goes to `/dashboard`.
- A new teacher dashboard (welcome text, no-class guidance) is **not** part of
  this change and stays open.

## Notes

- `iam` must not depend on `school` (the dependency would loop), so the school
  name reaches it through `iam.shared.SchoolNameSource`, implemented by
  `school.internal.SchoolNameSourceImpl`. `InviteDescriber` resolves it lazily.
- The token is per user, not per membership, so the activation page lists every
  active membership of the invited user.
- For a non-valid token only its state is exposed, never the account.

## Tests

`InviteActivationFlowIntegrationTest` (email text, page context, token states,
activation response); `InviteTokenFlowIntegrationTest` and
`RegistrationFlowIntegrationTest` unchanged and green; browser walk of
add → invite → activate → auto-login.
