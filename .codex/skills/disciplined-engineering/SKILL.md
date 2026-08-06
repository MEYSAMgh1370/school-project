---
name: disciplined-engineering
description: Repository-specific disciplined engineering workflow for Roshdeandishe. Use for feature implementation, bug fixes, refactoring, code review, security fixes, performance work, Spring Modulith module-boundary changes, and changes affecting database behavior or transactions. Enforces inspect, clarify, define success, plan, implement minimally, and verify using OpenSpec, source, tests, Java/Spring conventions, review rules, and definition of done.
---

# Disciplined Engineering

## When to use this skill

Use this skill for feature implementation, bug fixes, refactoring, code review, security fixes, performance work, Spring Modulith module-boundary changes, and changes affecting database behavior or transactions.

For new top-level modules, also use `.agents/skills/new-module/SKILL.md` as repository evidence. For new or substantially reworked JTE pages, also inspect `.agents/skills/design-page/SKILL.md`, `DESIGN.md`, `UX_ROADMAP.md`, and neighboring page code.

## Required workflow

### 1. Inspect before assuming

MUST read `openspec/project.md` first, then relevant OpenSpec specs, changes, decisions, legacy docs, implementation, and tests. Source code, migrations, configuration, and tests are stronger evidence than Markdown.

MUST inspect the smallest useful set of neighboring code that establishes the local convention before changing anything.

### 2. Clarify material ambiguity

MUST ask for clarification when an assumption materially affects public behavior, business rules, data model, security, tenant isolation, module boundaries, compatibility, or irreversible changes.

SHOULD make a reasonable, explicitly stated assumption for minor uncertainty that does not affect those areas. Do not interrupt for every small unknown.

### 3. Define a verifiable outcome

MUST convert vague requests into testable outcomes before implementation:

- "Add validation" means specific invalid cases and expected HTTP/domain responses.
- "Improve performance" means a baseline, target, and measurement path.
- "Fix authorization" means protected scenarios plus negative tests.
- "Refactor" means preserved behavior and passing regression tests.

### 4. Plan the smallest safe change

SHOULD state a short plan for non-trivial work. The plan MUST identify the files or modules likely to change, the verification target, and any OpenSpec update needed for intentional behavior changes.

### 5. Implement surgically

MUST make the smallest safe patch. Every changed file and meaningful diff section MUST trace back to the task.

MUST report adjacent bugs, security concerns, or stale documentation separately unless fixing them is required for correctness or safety.

### 6. Verify the result

MUST run the narrowest meaningful verification first, then broaden when the change touches shared behavior, module boundaries, migrations, security, tenant isolation, or user-facing flows.

MUST report commands executed, results, tests not run and why, and remaining risks.

## Evidence-first rules

MUST inspect, as applicable:

- existing implementation and neighboring code;
- related unit, integration, flow, and modularity tests;
- `pom.xml`, Maven plugins, and configured static analysis or formatting;
- database migrations in `src/main/resources/db/migration/`;
- `src/main/resources/application*.yml` for Flyway, Hibernate, security, tenant, and feature configuration without copying secrets;
- relevant OpenSpec files under `openspec/`;
- repository-level instructions: `AGENTS.md`, `CLAUDE.md`, `openspec/conventions.md`, `README.md`, `CLAUDE_LEGACY.md` when legacy context is relevant, and relevant frontend docs when UI is involved.

MUST distinguish confirmed repository facts, assumptions, proposed changes, and documentation that may be outdated.

MUST report documentation/code conflicts instead of silently choosing one. Do not edit documentation merely to justify a bug or accidental behavior.

## Simplicity and scope control

MUST follow YAGNI: no speculative abstractions, no unnecessary interfaces, no generic solutions to specific problems, and no config options nobody requested.

SHOULD prefer existing patterns over introducing a second style.

MUST avoid unrelated cleanup, dependency upgrades, file reorganization, and broad reformatting unless requested or required for the fix.

SHOULD prefer boring, obvious code and the smallest safe patch.

## Java conventions

MUST use constructor injection in production code. Do not use field injection, and do not add unnecessary `@Autowired` when Spring can wire a single constructor.

SHOULD keep facade implementations, entities, repositories, and internal services package-private by default. Public types are for facade interfaces, REST/page controllers, and explicitly exposed `@NamedInterface` contracts.

SHOULD use `record` for DTOs, facade return types, IDs, and value objects unless a mutable JPA entity or established neighboring pattern requires otherwise.

SHOULD keep Lombok restricted to low-value boilerplate already established locally, primarily JPA entities with `@Getter`, `@Setter`, and `@NoArgsConstructor`. Do not introduce Lombok in facades, services, or DTOs; prefer explicit constructors outside entities unless matching a verified local exception-handler pattern.

MUST model module domain errors as sealed module-specific error types with module-specific exceptions and handlers.

SHOULD use `Instant` for events, audit rows, token expiry, and timestamps crossing module or API boundaries. Use `LocalDate` or `LocalDateTime` only where neighboring code models school calendar, exam, or study-plan schedule concepts that are intentionally local.

MUST handle absent repository results with `Optional` and module-specific domain errors at facade or application boundaries. Do not leak raw `NoSuchElementException` or `null` from facade methods.

SHOULD write comments only for non-obvious reasoning, invariants, security/tenant concerns, or documented tradeoffs. Do not restate well-named code.

## Spring and Spring Modulith conventions

MUST treat each module's public Facade as the normal cross-module contract. Other modules MUST NOT depend on another module's `internal/` package. Use explicitly exposed `@NamedInterface` packages only when the facade is not the right contract.

MUST keep `platform` as a shared kernel with no domain dependency back on `iam`, `school`, `academic`, `finance`, or other feature modules.

SHOULD place transaction ownership at the facade or application boundary that owns the mutation. Mutating facade methods commonly use `@Transactional`; repository methods and controllers should not own transactions unless an existing application-boundary composition pattern requires it and tests cover it.

MUST publish cross-module domain events only after the durable state change they describe has succeeded. Event records SHOULD live in the publishing module's outer package and carry `schoolId`, identifiers, optional actor where relevant, and `Instant occurredAt` when the event is temporal.

MUST restore tenant context in `@ApplicationModuleListener` handlers from the event payload and clear it in `finally`; listeners run after commit on another thread.

MUST use the repository authorization model for protected school-scoped REST endpoints: `@RequiresModule("<MODULE>")` for entitlement and `@RequiresPermission("<ACTION>")` for permission. Do not replace it with Spring Security `hasRole()` or `GrantedAuthority` checks. Document and verify intentional exceptions such as `/api/me/**`, self-service `my` endpoints, Bale webhooks, and exam-take token endpoints.

MUST validate `X-School-ID`/`TenantContext` against any `{schoolId}` path variable before calling a facade, using the established `TenantContext.requireMatches` pattern.

MUST give `@RestControllerAdvice` beans explicit unique names with `@Component("...")`; do not use `@RestControllerAdvice("...")` as a bean name.

MUST keep REST controllers at the module root, next to the facade, not under `internal/`. Cross-module page controllers live in `web`; pure auth/identity page controllers may live in `iam.shared`. Do not put REST endpoints in `web`.

MUST preserve Flyway ownership of schemas and migrations. Add a new `V<n>__description.sql` for schema changes, add a schema to `spring.flyway.schemas` only for a new module schema, keep Hibernate in `ddl-auto: validate`, and do not edit applied migrations.

MUST run `ModularityTests.verifyModularStructure()` after module-boundary changes.

SHOULD use existing `*FlowIntegrationTest` style for behavior crossing HTTP, security, tenant, transaction, module, or async event boundaries.

## Review-task rules

MUST lead review responses with evidence-based findings ordered by severity.

Every confirmed finding MUST include severity, exact file and line, evidence, concrete failure scenario, impact, and the smallest safe fix.

MUST NOT report generic best-practice findings without a demonstrated repository issue.

MUST clearly separate confirmed defects, possible risks, suggestions, and missing tests.

## Required verification

MUST discover commands from the repository before running them. Known current commands include:

- `./mvnw test -Dtest=<RelevantFlowIntegrationTest>` for a targeted flow;
- `./mvnw test -Dtest=ModularityTests` for module-boundary changes;
- `./mvnw test` for the normal test suite;
- `./mvnw clean verify` for the broad CI-equivalent check;
- `git diff --check` for documentation-only or whitespace validation.

SHOULD choose the narrowest meaningful check first. Broaden to flow tests, modularity tests, migration validation, or `./mvnw clean verify` when the risk or scope warrants it.

MUST disclose when Docker/Testcontainers, database availability, time, or sandbox restrictions prevent running a relevant test.

## Definition of done

- [ ] Relevant implementation and tests were inspected.
- [ ] Material assumptions were clarified or stated.
- [ ] The requested behavior has a verifiable success condition.
- [ ] The implementation is the smallest safe change.
- [ ] No unrelated cleanup was included.
- [ ] Relevant tests pass, or unrun tests are named with reasons.
- [ ] Module boundaries remain valid.
- [ ] Tenant and authorization behavior were checked where applicable.
- [ ] Database migrations and transaction behavior were checked where applicable.
- [ ] OpenSpec was updated only if intended behavior changed.
- [ ] Documentation-code conflicts were reported.
- [ ] Remaining risks and unverified areas were disclosed.
