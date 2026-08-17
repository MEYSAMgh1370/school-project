# Web Design Follow-Ups

Status: partially implemented on 2026-08-16; the canonical financial unit
remains unresolved.

## Current Behavior

- `app.css` already applies the proposed desaturated ink-blue primary color, but
  `DESIGN.md` still describes it as pending sign-off;
- the web UI uses Bulma's system font stack and loads no Persian font asset;
- finance forms label raw amounts as Toman, while `scripts/seed-demo-data.py`
  labels the same persisted/API value shape as Rial;
- read-only tuition, invoice, payment, expense, and summary amounts render as
  ungrouped Latin digits;
- finance amount inputs bind and serialize browser values without normalizing
  Persian or Arabic-Indic digits;
- no page currently establishes a need for a shared skeleton-loading component.

Evidence:

- `src/main/resources/static/css/app.css`;
- `src/main/jte/finance.jte` and `src/main/resources/static/js/finance.js`;
- `src/main/jte/expenses.jte` and `src/main/resources/static/js/expenses.js`;
- `src/main/jte/my-invoices.jte` and
  `src/main/resources/static/js/my-invoices.js`;
- `scripts/seed-demo-data.py`;
- `docs/ui-ux-audit.md` and `docs/ui-ux-implementation-report.md`.

## Accepted Behavior

- adopt the existing ink-blue `hsl(210, 40%, 38%)` (`#3A5F84`) as the web
  primary color;
- retain the system font stack and add no font dependency or hosted font asset;
- display existing finance amounts neutrally with Persian digits and the
  Arabic thousands separator (`٬`), without appending Rial or Toman;
- accept Persian and Arabic-Indic digits in finance amount fields, normalize
  them to ASCII during editing, and normalize again before serialization;
- preserve existing API/database numeric values without conversion;
- continue to defer a skeleton component until a concrete page needs one.

Android remains outside this change and continues to use its own Material
design decisions.

## Acceptance Criteria

- `1234567` renders as `۱٬۲۳۴٬۵۶۷` in all three current finance views, with no
  currency label;
- decimal values preserve at most two fractional digits;
- missing or invalid display values render as `—` rather than `NaN`;
- input values such as `۱۲۳` and `١٢٣` become ASCII `123` before browser
  validation and in request payloads;
- finance form payloads and backend behavior do not change;
- the finance, expenses, and self-service invoice pages load the same shared
  formatter before their Alpine component scripts;
- `DESIGN.md`, the current-system specification, and the proposed-work index
  reflect the resolved decisions.

## Compatibility And Verification

This is a presentation-only change. It adds no dependency, migration, REST
field, or monetary conversion. The neutral display is an interim safety
boundary, not a decision that the stored value is Rial or Toman.

Completed verification:

- JavaScript formatter and normalizer regression tests covering ASCII,
  Persian, and Arabic-Indic inputs;
- JTE generation, Java compilation, and 16 focused integration tests across
  the finance, expenses, and self-service invoice pages, including input and
  send-path wiring assertions;
- `git diff --check`.

Before choosing Rial or Toman, a separate product decision must define the
canonical persisted unit, entry unit, display unit, conversion direction, and
rounding rules, then reconcile the demo seeder and every finance DTO/page.
