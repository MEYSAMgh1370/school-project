# Password Reset By Email

Status: proposed 2026-10-12; not implemented; blocked from real use until SMTP
is configured (not configured as of 2026-10-12). Approach decided with the product
owner on 2026-10-12 (a separate hashed reset-token flow, not a reuse of invite
tokens). Delivery channel for this proposal is email only. Nothing here is
current behavior until it moves into
[`specs/current-system.md`](../specs/current-system.md).

## Verified Baseline

Read in code on 2026-10-12.

- There is no way to recover a forgotten password. No «forgot password» link
  exists on `/login`, and no reset endpoint, page or token exists. Evidence:
  [`login.jte`](../../src/main/jte/login.jte); a search for `forgot`/`reset` in
  `src/main/jte` and `src/main/java` finds nothing relevant to credentials.
- The only password-setting token is the invite token
  ([`InviteTokenEntity`](../../src/main/java/com/example/roshdeandishe/iam/internal/InviteTokenEntity.java),
  table `iam.invite_tokens`, 7-day TTL `INVITE_TOKEN_TTL_DAYS`). Its value is
  stored in the clear, there is no purpose column, and the mail text says
  «فعال‌سازی حساب». `IamFacade.issueInviteToken` is a deliberate no-op for a user
  who already has a password, and returns an existing unredeemed token instead
  of minting a new one. Evidence:
  [`IamFacade.java`](../../src/main/java/com/example/roshdeandishe/iam/IamFacade.java),
  [`IamFacadeImpl.java`](../../src/main/java/com/example/roshdeandishe/iam/internal/IamFacadeImpl.java).
- Email goes through
  [`InviteEmailSender`](../../src/main/java/com/example/roshdeandishe/iam/internal/InviteEmailSender.java)
  and is best-effort: a failed send returns `false` and is logged, never
  thrown. The link base comes from `APP_BASE_URL`. In production SMTP is
  deliberately optional
  ([`application-prod.yml`](../../src/main/resources/application-prod.yml),
  `DEPLOYMENT.md` §`MAIL_*`), so email may not be delivered at all.
- `POST /api/auth/register` answers `409 email-already-registered` with the
  address in the message, so account existence is already observable from the
  public registration endpoint
  ([`IamProblemResolver.java`](../../src/main/java/com/example/roshdeandishe/iam/shared/exception/IamProblemResolver.java)).
- No rate limiting exists anywhere in the application (`current-system.md`
  Current Limitations lists OTP rate limiting as not implemented).
- Web sessions live in PostgreSQL (`iam.spring_session`, Flyway `V20`, Spring
  Session JDBC), so a user's sessions can be located and removed. Mobile access
  uses stateless JWT from `POST /api/auth/token`.
- `SecurityConfig` permits `/api/auth/**` anonymously; page routes `/login`,
  `/register`, `/activate` are listed individually, so new public pages must be
  added to that list.
- Minimum password length is 8 (`MIN_PASSWORD_LENGTH`).

## Why Not Reuse Invite Tokens

`redeemInviteToken` would technically accept a reset (it never checks for an
existing password), but it would mean a 7-day link, activation wording, a
plaintext token at rest, a token that may be an old invite rather than a fresh
reset, and no session invalidation. A separate, short-lived, hashed token avoids
all of those, and keeps invite semantics unchanged.

## Proposed Behavior

### Request

- `POST /api/auth/password-reset/request` with `{ email }`. Always answers `200`
  with the same body, whether or not an account exists, whether or not an email
  was sent, and whether or not SMTP works: «اگر حسابی با این ایمیل وجود داشته
  باشد، لینک بازیابی رمز برایش فرستاده شد.»
- For an existing, active user who has a password, a token is minted and mailed:
  random, at least 32 bytes, URL-safe; only its hash (SHA-256) is stored in a new
  table `iam.password_reset_tokens` (`id`, `user_id`, `token_hash` unique,
  `created_at`, `expires_at`, `used_at`). Default lifetime one hour.
- Cooldown: if the user already has a reset token created within the last
  `COOLDOWN` (default 5 minutes), no new token is minted and no email is sent;
  the response is still the same `200`. This bounds email bombing of one
  address. There is no per-IP limit (see Open Questions).
- A new request invalidates the user's earlier unused reset tokens, so at most
  one is usable at a time.
- Email: a dedicated subject and body (not the activation text), the link
  `APP_BASE_URL/reset-password?token=…`, the lifetime, and «اگر شما درخواست
  نکرده‌اید، این ایمیل را نادیده بگیرید.» Sending reuses the existing
  best-effort `InviteEmailSender` transport; failure is logged and invisible to
  the caller.
- The request does not reveal timing differences by account existence beyond
  what the unavoidable mail send costs; the send is done after the response
  decision, not before it.

### Confirm

- `POST /api/auth/password-reset/confirm` with `{ token, newPassword }`.
  Validates: token exists (by hash), not used, not expired, password meets
  `MIN_PASSWORD_LENGTH`. Errors are distinguishable to the client only as
  «invalid or expired link» versus «password invalid», so a probed token reveals
  nothing else.
- On success: set the user's password hash; mark this token used; invalidate all
  the user's other reset tokens; delete the user's web sessions from
  `iam.spring_session` so a stolen session does not survive the change. The
  caller is **not** logged in automatically; the success page links to `/login`.
- Already-issued mobile JWTs are not revoked (no revocation store exists); see
  Open Questions.

### Pages

- `/forgot-password`: one email field, the generic confirmation after submit, a
  link back to `/login`. Linked from `/login` as «رمز عبور را فراموش کرده‌اید؟».
- `/reset-password?token=…`: new password and confirmation with the same
  show/hide control and mismatch hint as registration. The token is read as
  inert DOM data (as `/activate` does), never interpolated into an expression.
  A missing/invalid/expired token shows the failure with a link to request a new
  one.
- Both pages are public (added to the `SecurityConfig` permit list, like
  `/activate`), Persian and RTL, and follow `DESIGN.md`.

## Prerequisite

Real SMTP. Confirmed by the product owner on 2026-10-12: **SMTP is not
configured**, and there is no production deployment yet, only a staging one.
Until `MAIL_HOST`/credentials are set and delivery is verified on staging, the
feature works in tests and development, but a staging or production user never
receives the link. Consequences for this proposal:

- Implementation can go ahead and be tested without SMTP (mail is captured in
  tests; development can use a local catcher such as Mailpit, see `README.md`).
- Enabling it for real users is gated on choosing and configuring an SMTP
  provider for staging first. That provider choice is not part of this
  proposal and is not decided.
- Until then, staging users who forget a password have no recovery path, and
  the link on `/login` should either be hidden or say so (to be decided with
  Open Question 7).

## Open Questions (unresolved; do not invent answers in code)

1. A user invited but not yet activated (no password): do nothing, or send a
   fresh activation link? Default until decided: do nothing, same generic reply.
2. What can a school admin do for a member whose email never arrives? Today an
   admin can re-issue only an activation link, and only for a user without a
   password. An admin-initiated reset link is a separate, permission-gated
   proposal.
3. Per-IP limiting across many different emails needs a store or in-memory
   structure; the project has no rate-limit infrastructure and is memory
   constrained (`changes/production-memory-footprint.md`).
4. Revoking already-issued JWTs on reset (no revocation list exists).
5. Reset by the Bale bot for users with a linked phone, later.
6. Should the requester be notified at the *old* email when a reset completes
   (it is the same mailbox here, so likely not needed)?
7. Should `/login` show the «forgot password» link only when mail delivery is
   known to work (the same idea as the SMS tab, `IamFacade.isOtpDeliveryAvailable`),
   so staging without SMTP does not promise a link that never arrives?

## Acceptance Criteria

- `/login` links to `/forgot-password`; both new pages render anonymously.
- The request endpoint returns an identical `200` for a known user, an unknown
  email, a user inside the cooldown, and an SMTP failure.
- A known user receives exactly one reset email per cooldown window; the stored
  token is a hash, never the emailed value.
- A valid token sets the new password, can be used once, and the old password no
  longer logs in. An expired, used, superseded or unknown token is rejected with
  the same generic message.
- After a successful reset the user's rows in `iam.spring_session` are gone, and
  no other reset token of that user stays usable.
- A password shorter than 8 characters is rejected without consuming the token.
- Invite tokens, activation and OTP behavior are unchanged.

## Migration And Compatibility

- One Flyway migration creating `iam.password_reset_tokens` (the next free
  `V…` at implementation time; `V25` is the latest on `master` on 2026-10-12)
  plus an index on `user_id`. No data migration. No change to existing tables.
- Optional housekeeping: expired/used rows are deleted by a periodic job or on
  next request for the same user (to be chosen at implementation).
- Adds methods to the public `IamFacade` for request and confirm; no other
  module depends on them. The `iam` module boundary and `ModularityTests` must
  stay green.

## Tests

- A flow test for request → email captured → confirm → login with the new
  password, using the existing mail test setup.
- Anti-enumeration: identical status and body for known, unknown, cooldown.
- Token rules: single use, expiry, supersession, hash-at-rest.
- Session invalidation after confirm.
- Page tests for both pages and the link on `/login`; a browser check at desktop
  and mobile widths.
