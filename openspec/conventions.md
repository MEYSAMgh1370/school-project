# Conventions

Status: current guidance checked against the repository on 2026-08-06.

## Documentation

- Start with [`project.md`](project.md), then read the relevant current spec, change proposal, decision record, and legacy source document.
- Verify current behavior in code and tests before changing docs or implementation.
- Report documentation/code conflicts. Do not silently rewrite a planned feature into current behavior.
- Keep current specs separate from future ideas and historical context.
- Create or update a change proposal before substantial behavioral changes.
- Update current specs when behavior intentionally changes.
- Do not edit docs merely to justify a bug.
- Before substantive work on explicitly deferred, open, or roadmap items, check the current default branch and in-flight work when available. Report already-resolved or overlapping work instead of rebuilding it silently.

## Engineering Discipline

- Inspect before coding. State assumptions that affect the approach.
- Ask for clarification when ambiguity materially affects public behavior, business rules, data model, security, tenant isolation, module boundaries, compatibility, or irreversible changes.
- For minor uncertainty, make a reasonable assumption and state it.
- Turn vague requests into verifiable outcomes before implementation. Validation work needs invalid cases and expected responses; performance work needs a baseline and target; authorization work needs protected and negative scenarios; refactors preserve behavior and pass regression tests.
- Prefer the smallest safe change. YAGNI is the default: no speculative abstractions, unnecessary interfaces, unrequested config options, dependency upgrades, unrelated cleanup, or broad reformatting.
- Every changed file and meaningful diff section should trace back to the task.
- Report adjacent bugs, security concerns, documentation conflicts, and out-of-scope cleanup separately unless fixing them is required for correctness or safety.
- Final reports should include verification commands, results, tests not run with reasons, and remaining risks.

## Backend

Use these repository conventions, verified against current code and tests:

- Constructor injection only in production code; no field injection and no unnecessary `@Autowired` when Spring can wire a single constructor.
- Package-private implementations, entities, repositories, constructors, and internal services by default. Public types are facade interfaces, REST/page controllers, and explicitly exposed `@NamedInterface` contracts.
- Facade interfaces are the normal cross-module contracts. Do not depend on another module's `internal/` package.
- `record` DTOs, facade return types, IDs, and value objects unless a mutable JPA entity or neighboring pattern requires otherwise.
- Keep Lombok to low-value boilerplate already established locally, primarily JPA entities with `@Getter`, `@Setter`, and `@NoArgsConstructor`. Prefer explicit constructors outside entities unless matching a verified local exception-handler pattern.
- Use sealed module-specific error types with module-specific exceptions and handlers.
- Use `Instant` for events, audit rows, token expiry, and timestamps crossing module or API boundaries. Use `LocalDate` or `LocalDateTime` only where neighboring code models school calendar, exam, or study-plan schedule concepts that are intentionally local.
- Handle absent repository results with `Optional` and module-specific domain errors at facade or application boundaries. Do not leak raw `NoSuchElementException` or `null` from facade methods.
- Comments should explain non-obvious reasoning, invariants, security/tenant concerns, or documented tradeoffs, not restate well-named code.
- Place transaction ownership at the facade or application boundary that owns the mutation. Mutating facade methods commonly use `@Transactional`; repository methods and controllers should not own transactions unless an existing application-boundary composition pattern requires it and tests cover it.
- Publish cross-module domain events only after the durable state change they describe has succeeded.
- Restore tenant context in `@ApplicationModuleListener` handlers from the event payload and clear it in `finally`.
- Protected school-scoped REST endpoints use `@RequiresModule("<MODULE>")` for entitlement and `@RequiresPermission("<ACTION>")` for permission. Do not replace this with Spring Security `hasRole()` or `GrantedAuthority` checks.
- Validate `X-School-ID`/`TenantContext` against any `{schoolId}` path variable before calling a facade, using `TenantContext.requireMatches`.
- `@RestControllerAdvice` beans need explicit unique names through `@Component("...")`; do not use `@RestControllerAdvice("...")` as a bean name.
- REST controllers live at module root, next to the facade, not under `internal/`.
- Cross-module page controllers live in `web`; pure auth/identity page controllers may live in `iam.shared`. Do not put REST endpoints in `web`.
- Run `ModularityTests.verifyModularStructure()` after module-boundary changes.
- Use existing `*FlowIntegrationTest` style for behavior crossing HTTP, security, tenant, transaction, module, or async event boundaries.

For schema changes:

- Flyway owns migrations and schema creation.
- Add a new `V<n>__description.sql` migration.
- Add a new schema to `spring.flyway.schemas` only when introducing a new module schema.
- Keep Hibernate in validation mode; do not use `ddl-auto: update` as a migration strategy.
- Do not edit applied migrations.

## Frontend

Use [`../FRONTEND.md`](../FRONTEND.md), [`../UX_ROADMAP.md`](../UX_ROADMAP.md), and [`../DESIGN.md`](../DESIGN.md) together, but verify stale roadmap statements against code.

Current frontend constraints:

- JTE + Alpine.js + Bulma + WebJars only.
- No npm, webpack, Vite, SPA rewrite, or CDN dependency unless explicitly approved.
- `layout/main.jte` owns the document head.
- Page scripts are static files under `src/main/resources/static/js/` when they contain `fetch`, regexes, or non-trivial logic.
- Custom CSS belongs in external CSS files, not inline `<style>`.
- UI text is Persian and RTL; Latin data gets local `dir="ltr"` where needed.
- Navbar visibility is display-only. Backend access is enforced by API authorization.

## Tests

Run the smallest relevant test set first, then broaden when the change touches shared behavior or module boundaries.

Useful commands:

```bash
./mvnw test
./mvnw test -Dtest=ModularityTests
./mvnw clean verify
```

Most flow coverage in this repository is currently expressed as Spring Boot integration tests using Testcontainers. Docker must be available for those tests.

Documentation-only changes do not require application tests, but run `git diff --check` before finishing.
