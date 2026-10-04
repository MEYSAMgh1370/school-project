# Notification Channel Abstraction

Status: proposed and implemented 2026-10-02, as the follow-up that
[`rename-bale-module-to-notification.md`](rename-bale-module-to-notification.md)
deliberately deferred. The current-state statement lives in
[`architecture.md`](../architecture.md) (module list).

## Verified Baseline

After the rename, `NotificationFacadeImpl.sendMessageToUser` still contained the
Bale logic directly: look up the user's `BaleLinkEntity`, then call
`BaleClient.sendMessage(chatId, text)`; `false` when the user has no link or the
send failed. The only caller is `InvoiceIssuedEventListener`, which ignores the
result (best-effort by contract). `iam` sends OTP SMS through its own
`SmsDriver`, not through this module.

## Proposed Contract

- Package-private `NotificationChannel` (`boolean send(Long userId, String text)`)
  in `notification/internal`. `false` means the user is unreachable on that
  channel or the send failed; implementations do not throw for either.
- `BaleChannel` is the first implementation and contains exactly the previous
  lookup-and-send logic. `BaleClient`, the webhook flow and
  `processIncomingUpdate` are untouched.
- `NotificationFacadeImpl` receives every `NotificationChannel` bean and tries
  them in registration order, returning `true` at the first channel that
  delivers, `false` if none does (including when there are none).
- `NotificationFacade`'s public API is unchanged; no other module is affected.
- With one channel the behavior is identical to before.

## Supersedes

`CLAUDE_LEGACY.md` §10 said not to build a generic notification/messaging
abstraction and to give a second channel its own module. That rule is
superseded (struck through there with a 2026-10-02 note): it was a YAGNI
guardrail with no recorded technical incident behind it, and the owner asked for
the rename and the channel layer. The same YAGNI concern still governs what is
not built — a second channel, a multi-channel policy, or anything in `platform`.
A second channel is added inside this module.

## Compatibility And Security

No REST, permission, tenant, schema, configuration or event change. No new
beans of public scope; the interface and channel are `internal`.

## Unresolved Questions

- **Multi-channel policy.** "First channel that delivers" is a placeholder that
  is correct for one channel. Whether a second channel should be a fallback or
  receive every message, and in which order, is a product decision to make when
  that channel is added.
- **Second channel.** None is built. Routing OTP/invite SMS (`iam.SmsDriver`)
  through this module is a separate decision and is not implied here.

## Acceptance Criteria

- `sendMessageToUser` returns what the previous implementation returned for a
  user with no link, a linked user with a successful send, and a failed send.
- `BaleFlowIntegrationTest` (webhook linking and invoice notification) passes
  unchanged.
- `ModularityTests.verifyModularStructure()` passes.

## Tests

- `NotificationFacadeImplTest` — single channel delivers/fails, no channel,
  stops at the first delivering channel, falls through a failing one.
- `BaleChannelTest` — unlinked user is unreachable and nothing is sent; linked
  user gets the message on the linked chat; a failed send is `false`.
- `BaleFlowIntegrationTest` — end-to-end behavior unchanged (CI, needs Docker).
