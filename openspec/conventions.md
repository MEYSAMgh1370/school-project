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

## Backend

Use the conventions in [`../BEST_PRACTICES.md`](../BEST_PRACTICES.md). The most important local rules are:

- constructor injection only;
- package-private implementations and entities by default;
- facade interfaces as cross-module contracts;
- `record` DTOs and value objects;
- sealed module error types;
- `Instant` for timestamps across boundaries;
- `@Transactional` on mutating facade methods;
- tenant context restored and cleared in asynchronous module listeners;
- protected REST endpoints use `@RequiresModule` before `@RequiresPermission`;
- REST controllers live at module root, not under `internal/`.

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
