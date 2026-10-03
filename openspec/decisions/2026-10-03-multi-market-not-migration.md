# ADR: Multi-Market Product, Not A Market Migration

Date: 2026-10-03

Status: accepted. No implementation has occurred; it governs the work items in
[`changes/eu-market-readiness.md`](../changes/eu-market-readiness.md).

## Context

[`changes/eu-market-readiness.md`](../changes/eu-market-readiness.md) audited
where the build is coupled to the Iranian market and listed the first open
question as: is a second market additive, or does the product migrate away from
Iranian schools? Every work item in that proposal reads differently depending
on the answer.

The product owner answered: Iranian schools remain a segment.

## Decision

The product serves more than one market. A second market is additive.

Consequently, each market-specific coupling becomes a **configurable
dimension**, not something to be removed:

- Persian stays a first-class locale, and stays the default for Iranian
  tenants. Localization adds locales; it does not replace Persian.
- The Jalali calendar stays a supported presentation, selected by locale,
  alongside Gregorian.
- RTL layout and the Vazirmatn typeface stay, conditional on locale rather
  than unconditional.
- Bale stays a supported notification channel. A notification port is added so
  other channels can exist beside it; Bale is not replaced or removed.
- Money carries an explicit currency, because two segments mean at least two
  currencies.

## Consequences

**Resolution is per-tenant, not per-deployment.** One deployment may serve
schools from both segments at once, because a school is the tenant. Locale,
calendar, and currency therefore resolve from the tenant (and the acting user),
not from a global application property. Any work item that resolves one of
these dimensions from configuration alone is wrong under this decision.

**The monetary-unit decision is now constrained.**
[`changes/finance-monetary-unit.md`](../changes/finance-monetary-unit.md)
proposes a single canonical unit (whole Rial) for every finance value. Under
this decision that proposal cannot stand unchanged: a second segment means a
second currency, so the canonical contract has to be unit-plus-currency rather
than one unit. That proposal must be revised or superseded before the currency
work item starts. It remains unimplemented, so nothing has to be migrated to
reach the revised contract — but the revision is now required, not optional.

**Scope grows rather than shifts.** Removing Iranian coupling would have been a
replacement; keeping both segments means every dimension needs two working
paths and tests that prove both. Each work item's acceptance criteria must
assert that the Persian/Jalali/RTL rendering is unchanged, not merely that the
new locale works.

**Data protection gets harder, not easier.** A single deployment holding both
Iranian and European school data raises data-residency and transfer questions
that a single-market deployment would not. Whether the segments share a
deployment, and which jurisdiction's profile applies to which tenant, is not
decided here and needs qualified advice; it is recorded as an open question in
the proposal.

## Evidence

- [`changes/eu-market-readiness.md`](../changes/eu-market-readiness.md) — the
  audit this decision governs.
- [`changes/finance-monetary-unit.md`](../changes/finance-monetary-unit.md) —
  the single-unit proposal this decision constrains.
- [`specs/current-system.md`](../specs/current-system.md) — a school is the
  tenant; one account may hold memberships at several schools.
