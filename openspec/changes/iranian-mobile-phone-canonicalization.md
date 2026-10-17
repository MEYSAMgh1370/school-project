# Iranian Mobile Phone Canonicalization

Status: proposed and implemented 2026-10-17. Product decision made by the
product owner on 2026-10-17. Current behavior is in
[`specs/current-system.md`](../specs/current-system.md) (search "IAM
canonicalizes phone numbers").

## Problem

The Bale bot links a chat to a user by the phone number Bale shares with the
contact. Bale sends it in international form (`989335551287`, sometimes
`+98…`), while parents usually type `09335551287` on the site. The lookup in
`IamFacade.findUserByPhoneNumber` was an exact match after digit conversion, so
the bot answered «شماره‌ی به‌اشتراک‌گذاشته‌شده با هیچ حسابی در سامانه مطابقت ندارد».

## Verified Baseline

Read in code on 2026-10-17.

- [`PhoneNumberCanonicalizer`](../../src/main/java/com/example/roshdeandishe/iam/internal/PhoneNumberCanonicalizer.java)
  only stripped boundary whitespace and converted Persian/Arabic-Indic digits.
  Its Javadoc said country-code and separator rules needed a product decision.
- It is called by every phone path in
  [`IamFacadeImpl`](../../src/main/java/com/example/roshdeandishe/iam/internal/IamFacadeImpl.java):
  `setPhoneNumber`, `findUserByPhoneNumber` (Bale bot), `requestOtp` (which
  passes the canonical value to `SmsDriver.sendOtp`) and `verifyOtp`.
- `V3` and `V4` canonicalized existing `iam.users.phone_number` rows and added
  `ck_users_phone_number_canonical`, refusing to guess an owner on collision.

## Decision

Iranian mobile numbers have one canonical form, `09XXXXXXXXX`.

1. After digit conversion and boundary trim, remove spaces, `-`, `(`, `)` and
   `.`. If the result is `09XXXXXXXXX`, `9XXXXXXXXX`, `989XXXXXXXXX`,
   `+989XXXXXXXXX` or `00989XXXXXXXXX`, the canonical value is `0` followed by
   the 10 digits starting with `9`.
2. Any other number (landline, foreign) keeps today's behavior exactly,
   including its punctuation.
3. The rule lives in `PhoneNumberCanonicalizer`, so it applies to all callers
   above without changing them.
4. New migration `V32__canonicalize_iam_iranian_mobile_numbers.sql` rewrites
   existing rows with the same rule, aborts before changing anything if two
   users would end up with the same number, and adds
   `ck_users_phone_number_iranian_mobile_canonical` so a non-canonical mobile
   cannot be written later. Older migrations are not edited.
5. The dashboard phone field (the only UI that calls `PUT /api/me/phone`) gets
   the hint «شماره‌ی موبایلی که با آن در بله هستید، مثلاً ۰۹۱۲۳۴۵۶۷۸۹» as a
   Bulma `help` line linked with `aria-describedby`.

## Verification

- `PhoneNumberCanonicalizerTest`: every mobile form (ASCII, Persian, Arabic-Indic,
  with separators) maps to `09335551287`; landline and foreign numbers are
  unchanged.
- `PhoneNumberCanonicalizationMigrationTest`: `V32` rewrites mobile forms,
  leaves other numbers, enforces the new constraint, and refuses collisions.
- `BaleFlowIntegrationTest.internationalContactShareLinksUserStoredInNationalForm`:
  a `98912…` contact share links a user stored as `0912…`.
- `LoginDashboardFlowIntegrationTest`: the dashboard renders the hint.

## Open Questions

- Separators inside the number are limited to the ASCII characters listed
  above; a non-breaking space in the middle of a number is not removed. Not
  raised by the product owner; left as is.
- `V32` aborts on deployment if a database already holds the same mobile in two
  forms for two users. An operator must resolve such accounts by hand.
