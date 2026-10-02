# Rename The `bale` Module To `notification`

Status: proposed and implemented 2026-10-02. The current-state statement lives in
[`architecture.md`](../architecture.md) (module list).

## Verified Baseline

The `bale` Spring Modulith module owns the Bale bot integration: outbound
messages to users who linked a Bale account (today, invoice-issued notices) and
the inbound webhook that links accounts and answers self-service queries.

- Public API: `BaleFacade` (`sendMessageToUser`, `processIncomingUpdate`). No
  other module imports it; the only mentions outside the module are comments.
- `InvoiceIssuedEventListener` (`@ApplicationModuleListener`) consumes the
  finance `InvoiceIssuedEvent`; the module depends on `academic` and `finance`
  through their public facades/events only.
- External contracts: the webhook path `/api/bale/webhook/**` (registered with
  Bale, and named in `SecurityConfig` and `TenantResolverFilter`), the
  `roshdeandishe.bale.*` properties, the `BALE_BOT_TOKEN` and
  `BALE_WEBHOOK_SECRET` environment variables, and the `bale` database schema
  with `bale.bale_links` (`V1__baseline_schema.sql`, `spring.flyway.schemas`).

## Proposed Contract

The module is named for what it does for the rest of the system — notifying
users — with Bale as its (only) channel.

- Package `…roshdeandishe.bale` becomes `…roshdeandishe.notification`;
  `BaleFacade`/`BaleFacadeImpl` become `NotificationFacade`/
  `NotificationFacadeImpl`; the module's display name is "Notification".
- The Bale-specific classes (`BaleClient`, `BaleUpdate`, `BaleUpdateHandler`,
  `BaleWebhookService`, `BaleLinkEntity`/`Repository`, `BaleWebhookController`)
  keep their names and move with the package: they are the Bale channel.
- Method names and behavior are unchanged.

**Deliberately unchanged (external contracts):** webhook path, property keys,
environment variables, database schema and table names. Changing them would
need a new Flyway migration, re-registering the webhook with Bale and changing
production environment variables, and is not part of this change.

**Deliberately not built:** a channel abstraction. With one channel it would be
speculative; add it when a second channel (for example SMS, which `iam` already
sends for OTP) is actually routed through this module.

## Compatibility And Deployment

No REST payload, permission, tenant, schema or configuration change.

One deployment effect to know about: Spring Modulith persists events in
`iam.event_publication` and records each listener by its fully qualified method
name. `InvoiceIssuedEventListener`, the module's only listener, now has a new
package, so an **incomplete** publication written before the deploy
refers to a listener id that no longer exists and will not be redelivered. The
affected notices are best-effort invoice messages. To avoid dropping any, deploy
when this returns zero:

```sql
select count(*) from iam.event_publication
where completion_date is null and listener_id like '%.bale.%';
```

## Acceptance Criteria

- The application starts and `ModularityTests.verifyModularStructure()` passes
  with the module named `notification`.
- `BaleFlowIntegrationTest` (linking, webhook secret handling, invoice
  notification) passes unchanged in behavior.
- No code outside the module references the old package.
