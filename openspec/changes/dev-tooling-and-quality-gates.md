# Dev Tooling And Quality Gates

Status: proposed (not started). Derived from a reviewed external "Dev Tooling,
Observability & Code Quality" plan, adapted to this repository.

## Intent

Make architecture and quality regressions fail automatically, and give
developers and staging better runtime visibility — in the smallest steps that
pay off for a one-team Spring Boot 4.1 / Java 21 / Modulith 2 project deployed
to a single Liara staging app.

## Verified Baseline

- `ModularityTests` already runs `ApplicationModules.verify()` (plus docs).
  The external plan's Phase 3 is therefore done.
- `spring-boot-starter-actuator` and `spring-modulith-actuator` are present;
  `application-prod.yml` already restricts exposure and guards `/actuator/**`
  in `SecurityConfig`.
- Zero `@Autowired` and zero `System.out/err` uses in 567 main files — the
  "no field injection / no stdout" ArchUnit rules would pass immediately.
- Classes are packaged by type inside modules (`controller`, `dto`, `model`,
  `internal`, `config`, `shared`), not by global layer. The external plan's
  `..web..` / `..service..` / `..repository..` layered rule does not map.
- CI (`.github/workflows`) has deploy, diagram-parity, Claude review and
  maven-publish workflows; no workflow that simply runs `mvn verify` on PRs
  was found. Confirm before building on this.
- No JaCoCo, ArchUnit, Sonar, OpenTelemetry or Spring Boot Admin today.

## Assessment Of The External Plan

Sound in direction, but not fit as written:

| Item | Verdict |
|---|---|
| BootUI (dev profile) | Optional nicety. Young third-party project; verify Boot 4.1 support before adopting. Low priority. |
| ArchUnit | Good, but rewrite rules for by-type-in-module layout. Layered rule and `@RestController` rule must be re-derived (this app is mostly JTE pages). |
| Modulith verify | Already exists. Skip. |
| JaCoCo | Good, cheap. Keep. |
| SonarQube Community | Heavy for a solo/small team (extra Postgres + ~2 GB RAM server to host and patch). Defer; JaCoCo + ArchUnit give most of the value. |
| CI pipeline | Highest value item. Adapt: `mvn verify` on PRs, Sonar step dropped or conditional. |
| Spring Boot Admin | Not worthwhile for one Liara instance; needs a second app. Actuator + Liara logs suffice. Skip. |
| OpenTelemetry → Grafana LGTM | Useful only once a real collector exists on staging. Defer; do log-correlation (trace id in logs) only when tracing is added. |
| Version numbers in plan | Several (BootUI 2.0.0, SBA 4.0.0, JaCoCo 0.8.13) are unverified guesses; re-check on Maven Central. |

## Proposed Work (in order)

1. **PR build workflow** — `.github/workflows/ci.yml`: checkout, Temurin 21,
   Maven cache, `./mvnw -B verify` on `pull_request` and pushes to `master`.
   No Postgres service is needed: about 70 integration tests start their own
   database via Testcontainers (`TestcontainersConfiguration`), and
   `ubuntu-latest` runners provide Docker. Expect a slower run (container pull
   and start). This also exercises `ModularityTests` on every PR.
2. **ArchUnit rules fitted to this repo** — new
   `ArchitectureTests` in `src/test/java/com/example/roshdeandishe/`. Candidates,
   each confirmed against the code before enabling:
   - no field injection, no standard streams, no `java.util.logging`
   - `..controller..` must not depend on `..model..` repositories directly
     (go through facades/services), if that matches current practice
   - request/response `dto` types do not depend on JPA `@Entity` classes
   - no cross-module access to `..internal..` (backs up Modulith)
   Use `FreezingArchRule` only for rules with pre-existing violations, and
   report counts.
3. **JaCoCo report** — plugin with `prepare-agent` + `report` at `verify`;
   upload the XML/HTML as a CI artifact. No coverage threshold at first;
   revisit after seeing the baseline.
4. **(Optional) Actuator metrics on staging** — add Micrometer Prometheus or
   OTLP metrics only when there is somewhere to send them. Today's
   `/actuator/health` check in `liara.json` stays as is.
5. **(Optional) BootUI** in a `dev` Maven profile, after confirming Boot 4.1
   compatibility. Never in `prod`; add `.bootui/` to `.gitignore`.

Out of scope for now: SonarQube server, Spring Boot Admin, Grafana LGTM.

## Acceptance

- A PR with a failing test, Modulith violation or ArchUnit violation turns
  the PR check red.
- `./mvnw verify` locally and in CI produces `target/site/jacoco/jacoco.xml`.
- ArchUnit rule list in the PR states which rules were added, skipped, frozen.
- No change to runtime behavior or production config.

## Decisions

- Integration tests use Testcontainers Postgres; the CI workflow adds no
  database service.
- SonarQube/SonarCloud is deferred. If wanted later, prefer hosted SonarCloud
  over a self-hosted server (check pricing for private repos).
- No metrics/tracing backend on staging for now (owner does not need it);
  step 4 stays optional and unscheduled.
