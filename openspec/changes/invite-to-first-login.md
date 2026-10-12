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

## Addendum: the user with no school (2026-10-12)

Observed: a user who registers on their own and is later added by a school admin
with the same email gets no invitation (`issueInviteToken` is a no-op for an
account that already has a password) and nothing tells them they were added;
the dashboard kept saying "wait for the school's invitation". Decisions: the
roleless dashboard shows two clear paths and the account email to give the
school; plain `/register` tells invitees not to register; an already-active user
who is added to a school gets an email, and the admin is told when it could not
be sent.

- `/dashboard` without memberships: «مدیر یا صاحب مدرسه‌ام» (to `/schools/new`)
  and «از طرف مدرسه دعوت شده‌ام» (the account email with a copy button, a link to
  `/activate`, and the note that the school appears here once added).
- Plain `/register` (not `next=/schools/new`) carries the hint «اگر مدرسه‌تان شما را
  دعوت کرده، ثبت‌نام لازم نیست…».
- `POST /api/admin/schools/{id}/memberships` returns the usual fields plus
  `notified`; `IamFacade.notifyAddedToSchool` sends a best-effort «شما به‌عنوان
  <role> در <school> اضافه شدید» email only to a user who already has a password.
  A new user (no password) keeps getting the activation invite instead.
- `/people` shows «اضافه شد و ایمیل اطلاع‌رسانی برایش فرستاده شد» or, when the email
  could not be sent, «…خودتان به او خبر بدهید».
- Not changed: there is still no way for a visitor to ask a school to add them;
  the school's entry page `/s/{slug}` is unchanged.
