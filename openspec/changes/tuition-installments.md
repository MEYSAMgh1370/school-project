# Tuition Installments And Discounts

Status: proposed and implemented 2026-10-13 (`V28`, `V29`, `V30`, `V31`, the `/finance` and `/my-invoices`
pages); current behavior is in [`specs/current-system.md`](../specs/current-system.md) under Finance And Academic.
This record keeps the product-owner answers and the rationale. Not yet walked in a browser.

## Verified Baseline

Read in code on 2026-10-13 (master @ `093c902`).

- An invoice is one row with one `amount`, a running `amountPaid` and one nullable
  `dueDate`; there is no instalment and no discount concept.
  Evidence: [`InvoiceEntity.java`](../../src/main/java/com/example/roshdeandishe/finance/internal/InvoiceEntity.java).
- Status (`PAID`/`PARTIAL`/`UNPAID`/`OVERDUE`) is derived at read time, never stored
  (`FinanceFacadeImpl#deriveStatus`). `recordPayment` locks the invoice row, rejects an
  amount above `amount - amountPaid` and adds to `amountPaid`.
- Per-school flags live as columns on `school.schools` with an `isX`/`setX` pair on
  `SchoolFacade` (`autoPromotion` is the pattern). `ACADEMIC_YEAR_MANAGE` guards that one,
  and `FINANCE_ADMIN` does not hold it.
- `InvoiceIssuedEvent` is consumed by `notification`; its shape must not change.

## Decisions (product owner, 2026-10-13)

1. The school admin **turns instalments on** per school (default off).
2. The admin enters only the **number** of instalments; the system splits the payable
   amount evenly. The admin may then **edit the amounts by hand**, provided they still
   sum to the payable amount.
3. **Discounts**: the admin may give a discount at the start or forgive part of the
   remainder later; both are the same thing, a discount on the invoice.
4. If the admin changes the **number** of instalments, only the **unpaid** instalments
   are replaced; fully paid ones stay.
5. Each instalment has a **due date** (the «تاریخ تسویه» of that instalment); the last
   one is the deadline for settling the whole tuition.

## Design

**Discount.** `finance.invoice_discounts` (amount > 0, reason, actor, time) and a
denormalized `invoices.discount_total`, both maintained under the same row lock as
payments. Payable = `amount - discount_total`; an invoice is `PAID` when
`amountPaid >= payable`. A discount that would take payable below `amountPaid` is
refused (`409`). Discounts work with instalments off. Endpoints (`FINANCE_MANAGE` to
write, `FINANCE_VIEW` to read, parent visibility as for payments):
`POST|GET /invoices/{id}/discounts`. A new event `InvoiceDiscountedEvent` feeds the
finance audit; `InvoiceIssuedEvent` and `InvoiceInfo`'s existing fields are unchanged
(`discountTotal` is added).

**Setting.** `school.schools.installments_enabled boolean not null default false`,
`SchoolFacade.isInstallmentsEnabled/setInstallmentsEnabled`; endpoints on
`FinanceController` (`GET /finance/settings`, `PUT /finance/settings/installments`,
`FINANCE_MANAGE` to write) because `FINANCE_ADMIN` lacks `ACADEMIC_YEAR_MANAGE`.
Turning it off only blocks creating or editing plans; an existing plan keeps working.

**Instalments.** `finance.installments` (invoice FK within the module, `seq`, `amount`,
`amount_paid`, `due_date` as a calendar date). Plan operations need the setting on
(`409 installments-not-enabled`):

- *Generate*: `{count, firstDueDate, intervalMonths}` replaces the plan: fully paid
  instalments stay, and the remainder (`payable - sum of kept paid`) is split into
  `count` unpaid instalments, the last absorbing the rounding remainder, due dates a
  whole number of Jalali months apart (day clamped to the month's length).
  `count = 0` is allowed only when nothing remains.
- *Edit*: unpaid instalments' amounts and due dates by hand; the unpaid sum must equal
  `payable - sum of paid` (`400 installments-sum-mismatch`); a fully paid instalment
  cannot be edited.
- A discount on an invoice that has a plan re-splits the unpaid instalments evenly over
  their existing dates (unpaid ones disappear when nothing remains).
- Payments keep their API and are allocated to instalments oldest-due-date first, so
  `sum(payments) = amountPaid` is unchanged. Instalment status is derived (paid,
  partial, unpaid, overdue against the school's timezone). An invoice with a plan is
  `OVERDUE` when any instalment is overdue and unpaid; its `dueDate` follows the last
  instalment's date.
- `GET /invoices/{id}/installments`; the self-service invoices response carries the
  instalments, with the same per-student visibility as today.

## Addendum: Conditional Discounts (product owner, 2026-10-13; implemented the same day, `V30`)

A school may attach a condition to a discount, typically one given at the start of the
year: «if the whole payable amount is settled by date D, the discount stays».
Answers: the condition is **full settlement by D** (end of day D in the school's
timezone); the discount counts **from the moment it is given** (the family sees the lower
payable at once, with D written beside it); if D passes unmet it **lapses automatically**.

- `invoice_discounts` gains `deadline date` (null = unconditional), `status`
  (`APPLIED` unconditional, `PENDING` conditional and open, `EARNED`, `LAPSED`) and
  `resolved_at` (`V30`). `invoices.discount_total` keeps meaning "discounts in force": it
  counts `APPLIED`, `PENDING` and `EARNED`, so payable, status and every reader stay as
  they are; only a lapse lowers it.
- A conditional discount must have a deadline today or later (`400 invalid-discount`).
- **Earned:** after a payment, or right after the discount is given (a discount that itself settles the invoice must not lapse later), if `amountPaid >= payable` and a pending discount's deadline
  has not passed, it becomes `EARNED` (permanent).
- **Lapsed:** a discount still `PENDING` after its deadline becomes `LAPSED`, `discount_total`
  drops by its amount and the unpaid instalments are re-split over their existing dates
  (manual edits are lost; the admin can edit again; if the remainder has no unpaid instalment to take it,
  see «Undated remainder»). It happens (a) lazily, under the invoice
  lock, at the start of any payment, discount or instalment write on that invoice, so no
  write ever sees stale data, and (b) in an hourly job that sweeps overdue pending discounts.
  Both are idempotent. Events `DiscountEarnedEvent` / `DiscountLapsedEvent` feed the audit
  log (`DISCOUNT_EARNED`, `DISCOUNT_LAPSED`, no actor).
- The discount list shows each discount's status; the self-service invoice response carries
  `pendingDiscount` (amount) and `pendingDiscountDeadline` so the parent page can say
  «در صورت تسویه‌ی کامل تا … این تخفیف باقی می‌ماند».
- Out of scope: conditions other than full settlement (a minimum amount, a rate by date),
  a message to the parent before the deadline.

## Audit (product owner, 2026-10-13; `V31`)

Money needs a complete trail: every change to financial state leaves an immutable `finance.finance_audit` row
(actor, null = system; time; school; student; **invoice**; and **what changed**). `V31` adds `invoice_id` (no
cross-module FK) and `details` (compact JSON built with Jackson, free text cut to 512 characters). Rows are
written only by the `@ApplicationModuleListener` listener from durable events; the repository exposes `save` only.

- `PAYMENT_RECORDED` `{paymentId, method, note}`; `INVOICE_DISCOUNTED` `{discountId, reason, deadline, status}`;
  `DISCOUNT_EARNED` / `DISCOUNT_LAPSED` `{discountId, deadline, trigger}` (`trigger` = sweep, payment, discount or
  installments; actor null).
- `INSTALLMENTS_CHANGED` `{cause, before:[{amount, amountPaid, dueDate}], after:[...]}`, `amount` = sum of `after`;
  causes `GENERATED`, `EDITED`, `DISCOUNT_RESPLIT`, `DISCOUNT_LAPSE_RESPLIT` (so automatic re-splits that discard manual
  edits are audited too). Not written when before equals after.
- `INSTALLMENTS_SETTING_CHANGED` `{from, to}` (no student, no invoice), only when the switch really changes; the
  controller goes through `FinanceFacade.setInstallmentsEnabled`.
- `V31` also widens `iam.event_publication.serialized_event` from `varchar(255)` to `text`: the event registry
  rejected any event whose JSON was longer (a long payment note, an instalment snapshot).

## Undated remainder (product owner, 2026-10-13; `V31`)

When a discount lapses and there is a remainder but no unpaid instalment, the system does **not** invent a due date
(the earlier «due the next day» is dropped): it creates one unpaid instalment with `due_date = NULL`
(«بدون سررسید») that the admin dates later. Undated instalments sort last everywhere and are filled by payments
after the dated ones; they are never overdue (so they never make the invoice `OVERDUE`); `invoice.dueDate` follows the
last **dated** instalment (untouched when none is dated). In `PUT .../installments` an item's `dueDate` may be null
only while that instalment is undated; giving it a date is allowed, clearing a date is `400 invalid-installments`.
`generate` still requires `firstDueDate`.

## Out Of Scope

A per-student tuition amount, a reminder message per instalment, choosing which
instalment a payment goes to, cheque lifecycle, online payment. The monetary-unit
question stays as in [`finance-monetary-unit.md`](finance-monetary-unit.md).

## Open Questions

- Whether a parent should be told when a discount is given (no message in this change).
- Whether an admin may delete a discount (this change: no, it is append-only).
