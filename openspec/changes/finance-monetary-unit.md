# Finance Monetary-Unit Contract

Status: proposed for product approval on 2026-08-17. This is a
decision-first, documentation-only change. It changes no application behavior.

## Decision Requested

Approve this target profile for every finance value created or exposed by the
new unit-aware contract:

- **Canonical persisted unit:** whole Rial.
- **Versioned REST request and response unit:** whole Rial.
- **Web form-entry and read-only display unit:** Toman.
- **Printed, notification, and demo wording:** Toman, explicitly labelled
  `تومان`.
- **Legacy-data policy:** classify provenance before conversion; preserve
  unclassified values and never apply a blanket multiply/divide migration.
- **Compatibility policy:** introduce a versioned API; keep the current
  unlabeled endpoints numerically unchanged until their consumers are retired.

This proposal is complete enough to approve or reject as a single product
decision. It is not evidence that the target behavior is current; no code,
data, migration, test, or current-system specification changes are authorized
by this document.

## Verified Baseline

The present implementation deliberately avoids a currency decision:

- [`money.js`](../../src/main/resources/static/js/money.js) groups values and
  renders Persian digits, but it performs no Rial/Toman conversion or unit
  labelling. [`money.test.js`](../../src/test/js/money.test.js) asserts that
  neutral behavior and digit normalization.
- [`FinanceController.java`](../../src/main/java/com/example/roshdeandishe/finance/controller/FinanceController.java),
  [`ExpenseController.java`](../../src/main/java/com/example/roshdeandishe/finance/controller/ExpenseController.java),
  and [`MyInvoicesController.java`](../../src/main/java/com/example/roshdeandishe/finance/controller/MyInvoicesController.java)
  expose `BigDecimal` amount fields without a unit contract.
- The finance, expense, and self-service invoice pages display those raw API
  fields through the shared formatter and serialize normalized raw form input.
- [`V1__baseline_schema.sql`](../../src/main/resources/db/migration/V1__baseline_schema.sql)
  stores tuition, invoice, paid, payment, expense, and finance-audit amounts as
  `numeric(12,2)`.
- [`seed-demo-data.py`](../../scripts/seed-demo-data.py) posts `45000000` and
  calls that raw value `ریال`. Earlier finance-form labels called equivalent
  raw values Toman. Neither is an accepted persisted or API contract, so the
  label conflict cannot justify a conversion.
- Finance audit events carry amounts and
  [`InvoiceIssuedEventListener.java`](../../src/main/java/com/example/roshdeandishe/bale/internal/InvoiceIssuedEventListener.java)
  currently inserts an unlabelled raw amount into the invoice-notification
  text.

Documentation conflict: the **Current Behavior** section of
[`web-design-followups.md`](web-design-followups.md) still says that amounts
are ungrouped Latin digits and inputs are not normalized. The implementation,
its JavaScript tests, and the accepted section of that document show the later
neutral Persian formatting and normalization behavior. This proposal relies on
the source and tests; it does not alter that historical document in this
documentation-only phase.

## Target Unit Contract

The following table names one target unit for each monetary boundary. “Derived”
means that a user cannot enter that field independently; its value is still
represented and displayed under the stated unit.

| Value | Canonical persistence | V2 API request/response | Form entry | Read-only, print, demo, and notification display |
|---|---|---|---|---|
| Tuition fee (`tuition_fees.amount`) | Rial | Rial | Toman | Toman (`تومان`) |
| Invoice total (`invoices.amount`) | Rial | Rial | Toman, derived from the tuition fee | Toman (`تومان`) |
| Paid total (`invoices.amount_paid`) | Rial | Rial | Toman, derived from recorded payments | Toman (`تومان`) |
| Payment (`payments.amount`) | Rial | Rial | Toman | Toman (`تومان`) |
| Expense (`expenses.amount`) | Rial | Rial | Toman | Toman (`تومان`) |
| Expense report totals (`total`, `income`, `expense`) | Rial (derived total) | Rial | Toman (derived; no direct form) | Toman (`تومان`) |
| Finance audit amount (`finance_audit.amount`) and finance event amounts | Rial | Rial (internal event; no public REST) | Toman (derived; no direct form) | Toman (`تومان`) whenever shown or exported |

An expense-report total is a sum of canonical whole-Rial values. The
accumulated value must therefore also be a whole Rial and remain within the
report DTO's numeric range. An absent or nullable audit amount remains absent;
it is not converted to zero.

## Conversion, Scale, and Rounding

One Toman is exactly ten Rials.

```text
canonicalRial = enteredToman × 10
displayToman = canonicalRial ÷ 10
```

The conversion is exact, not an estimate:

- A canonical persisted or V2 API value has scale **0**: it is a whole Rial.
- A Toman entry or display value has scale **0 or 1**. A tenth of a Toman maps
  exactly to one Rial.
- The named Java rule is `setScale(0, RoundingMode.UNNECESSARY)` after
  multiplying an entered Toman value by ten. A value that needs rounding is
  rejected; `HALF_UP`, `HALF_EVEN`, truncation, and JavaScript `Number` rounding
  are not permitted for money conversion.
- Direct V2 API values with a non-zero scale, and Toman inputs with more than
  one fractional digit, are invalid. Existing domain rules continue to decide
  which user-entered fields must be positive; zero remains valid for derived
  values such as `amountPaid`.

| Case | Toman entry or display | Canonical / V2 Rial | Reverse result |
|---|---:|---:|---:|
| Zero | `0` | `0` | `0` |
| Non-multiple of ten | `1,234.5` | `12,345` | `1,234.5` |
| Fractional Toman | `0.1` | `1` | `0.1` |
| Largest supported value | `999,999,999.9` | `9,999,999,999` | `999,999,999.9` |

For example, `0.01` Toman would produce `0.1` Rial and is rejected by
`RoundingMode.UNNECESSARY`; it must not be silently rounded to zero or one.
The four valid examples round-trip exactly through the canonical unit.

## Range and Database Constraint

The current `numeric(12,2)` columns hold at most `9,999,999,999.99`. Because
the proposed canonical Rial scale is zero, the selected maximum is
`9,999,999,999` Rial, or `999,999,999.9` Toman. It fits all current amount
columns, including the nullable finance-audit value, without changing
precision.

A later implementation must add a **new** Flyway migration if it adds a
whole-Rial scale check, legacy classification storage, a data conversion, or a
larger range. It must not edit the applied baseline migration. Any migration
that changes stored values must first produce a preflight count, per-field
minimum/maximum, and amount-total reconciliation report; take a verified
backup; record the classification used; and have a tested restore/rollback
procedure. A requirement above the selected maximum requires a separate
precision-changing migration proposal with the same recovery evidence.

## Legacy Data Classification and Treatment

Existing finance records are **not** presumed to be Rial or Toman. Before a
conversion, the operator must classify each affected school or record from
auditable provenance as one of:

- `VERIFIED_RIAL` — source evidence proves the stored number was a Rial value;
- `VERIFIED_TOMAN` — source evidence proves the stored number was a Toman
  value; or
- `UNCLASSIFIED` — evidence is insufficient or conflicting.

`UNCLASSIFIED` values are preserved exactly and are excluded from automatic
conversion. `VERIFIED_TOMAN` values may be multiplied by ten only in a
reviewed, tested migration that covers the related tuition, invoice, paid,
payment, expense, audit, and derived-report source rows consistently.
`VERIFIED_RIAL` values may remain numerically unchanged only when they satisfy
the whole-Rial scale; fractional historical Rials require explicit manual data
correction, never automatic rounding.

The classification report, migration input set, row counts, per-school totals,
and excluded records are release evidence. Historic labels, current UI labels,
or the demo script alone are insufficient evidence. This policy deliberately
forbids a blind global `×10` or `÷10` migration.

## API Compatibility and Rollout

The current endpoints remain a legacy compatibility surface: their paths,
numeric request payloads, numeric responses, and stored-value handling are not
relabelled or converted in place. Existing clients and scripts therefore keep
their current raw numeric behavior throughout the rollout.

The later implementation introduces the following V2 boundary, whose `amount`,
`amountPaid`, `total`, `income`, and `expense` fields are always whole Rial:

- `/api/v2/admin/schools/{schoolId}/tuition-fee`
- `/api/v2/admin/schools/{schoolId}/invoices` and
  `/api/v2/admin/schools/{schoolId}/invoices/{invoiceId}/payments`
- `/api/v2/admin/schools/{schoolId}/expenses`, expense summaries, and finance
  summary
- `/api/v2/me/schools/{schoolId}/invoices`

The V2 API documentation and response examples must state `unit: Rial` in
prose for every numeric monetary field; the product need not add a redundant
unit field to each value when the versioned contract already fixes it. The web
UI moves to V2 and converts only at its Toman edge. It must never post a Toman
number to V2 without first converting it to whole Rial.

V1 remains available for at least one deployed release after V2 reaches
production and is removed only after an inventory confirms that its web,
seeder, and external consumers have migrated or been explicitly retired. V1
will never acquire a changed amount meaning in place.

## Later Implementation Map

Approval starts a separate implementation change. That change must cover, at a
minimum:

- shared conversion, validation, Persian display, and explicit unit labels in
  [`money.js`](../../src/main/resources/static/js/money.js), plus all three
  page scripts: [`finance.js`](../../src/main/resources/static/js/finance.js),
  [`expenses.js`](../../src/main/resources/static/js/expenses.js), and
  [`my-invoices.js`](../../src/main/resources/static/js/my-invoices.js);
- form labels, table values, remaining balances, report totals, and script
  loading/wiring in [`finance.jte`](../../src/main/jte/finance.jte),
  [`expenses.jte`](../../src/main/jte/expenses.jte), and
  [`my-invoices.jte`](../../src/main/jte/my-invoices.jte);
- V2 DTOs and validation in [`FinanceController.java`](../../src/main/java/com/example/roshdeandishe/finance/controller/FinanceController.java),
  [`ExpenseController.java`](../../src/main/java/com/example/roshdeandishe/finance/controller/ExpenseController.java),
  and [`MyInvoicesController.java`](../../src/main/java/com/example/roshdeandishe/finance/controller/MyInvoicesController.java),
  including the generated OpenAPI documentation configured by
  [`OpenApiConfig.java`](../../src/main/java/com/example/roshdeandishe/platform/config/OpenApiConfig.java);
- canonical validation and serialization through the finance and expense
  facades, implementations, events, entities, audit listener, and audit rows;
- Toman-labelled, formatted Bale invoice messages in
  [`InvoiceIssuedEventListener.java`](../../src/main/java/com/example/roshdeandishe/bale/internal/InvoiceIssuedEventListener.java);
- V2-aware sample data and wording in
  [`seed-demo-data.py`](../../scripts/seed-demo-data.py); and
- a new Flyway migration and migration test only when classification evidence
  authorizes a data change or when schema metadata/constraints are needed.

The later regression suite must include:

- shared JavaScript golden tests for the conversion formula,
  `RoundingMode.UNNECESSARY` rejection cases, Persian/Arabic-Indic input, the
  four tabled round trips, and the maximum value;
- V2 request/response and page-wiring coverage in
  [`FinanceManagementFlowIntegrationTest.java`](../../src/test/java/com/example/roshdeandishe/FinanceManagementFlowIntegrationTest.java),
  [`FinanceFlowIntegrationTest.java`](../../src/test/java/com/example/roshdeandishe/FinanceFlowIntegrationTest.java),
  [`FinancePaymentFlowIntegrationTest.java`](../../src/test/java/com/example/roshdeandishe/FinancePaymentFlowIntegrationTest.java),
  [`ExpenseManagementFlowIntegrationTest.java`](../../src/test/java/com/example/roshdeandishe/ExpenseManagementFlowIntegrationTest.java),
  [`ExpenseReportFlowIntegrationTest.java`](../../src/test/java/com/example/roshdeandishe/ExpenseReportFlowIntegrationTest.java),
  and [`MyInvoicesPageFlowIntegrationTest.java`](../../src/test/java/com/example/roshdeandishe/MyInvoicesPageFlowIntegrationTest.java);
- V1 compatibility tests proving unchanged raw request/response behavior while
  the parallel V2 contract is introduced;
- migration tests for each supported legacy classification, excluded
  unclassified records, totals/range reconciliation, and recovery procedure;
  and
- the existing Bale notification flow plus the broad finance regression run.

No implementation work belongs in this proposal. When the later change is
implemented, update the current-system specification, frontend/API
documentation, and this proposal's status only with passing evidence.

## Acceptance Criteria for Approval

- Every target finance boundary in the contract table has exactly one
  persistence, V2 API, form-entry, and display unit.
- The conversion formulas, permitted scales, `RoundingMode.UNNECESSARY`, and
  the zero, non-multiple-of-ten, fractional, and maximum round-trip results
  are explicit.
- Existing data is classified before conversion; no label-only conversion is
  authorized.
- The selected maximum fits the current `numeric(12,2)` range, and any later
  range or semantic change requires a new migration and recovery evidence.
- Existing V1 clients retain their raw numeric behavior; V2 establishes the
  canonical whole-Rial API contract.
- The later implementation surfaces and regression coverage are enumerated.
- This proposal and the OpenSpec indexes remain future work until a separate
  implementation change is completed.

## Verification for This Documentation-Only Change

Before merge, verify the contract table against repository searches for amount
fields and `Money` formatter/normalizer calls, run `git diff --check`, and
inspect the diff. The only changed files must be this proposal,
[`proposed-work.md`](proposed-work.md), and
[`project.md`](../project.md). Do not run application tests for this
documentation-only phase; the later implementation must run the listed
JavaScript, finance-flow, migration, Bale, and broad regression tests.
