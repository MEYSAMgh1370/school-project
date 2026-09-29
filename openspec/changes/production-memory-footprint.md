# Production Memory Footprint Reduction

Status: proposed on 2026-09-28. **Tier 1 implemented 2026-09-29**; current
behavior is in [`../architecture.md`](../architecture.md). Tier 2 remains
proposed and is not implemented — this document authorizes no dependency or
source change by itself.

Tier 1 as verified on the implementation commit, same tree and same session:
537 MB RSS before, 408 MB after, with zero ArchUnit classes loaded, a clean
`prod` boot, `ModularityTests` green, and `/actuator/modulith` still served on
the development profile.

## Decision Requested

Approve reducing the production container's resident memory by removing work
the application performs at startup but never uses, and by replacing implicit
JVM heap sizing with explicit limits.

Two tiers are proposed. They are independent; Tier 1 can ship alone.

- **Tier 1 — configuration only.** No dependency change, no source change.
  Measured at roughly **535 MB to 400 MB RSS**.
- **Tier 2 — dependency removal.** Drops two libraries that are inert in
  production. Measured at roughly **378 MB RSS**, about 154 MB below baseline.

The motivation is operating cost: the deployment is sized by container memory,
and the current footprint is dominated by work that produces no production
value.

## Verified Baseline

### Measurement method

Measurements were taken on 2026-09-28 in a Linux container, Temurin JDK 21.0.10,
against a real PostgreSQL 16 instance with an **empty database**. The
application was started with `SPRING_PROFILES_ACTIVE=prod`, driven with 100
concurrent and 200 sequential HTTP requests across `/actuator/health`,
`/login`, and `/`, then measured as `VmRSS` from `/proc/<pid>/status`. Region
breakdowns come from Native Memory Tracking (`jcmd VM.native_memory summary`);
live heap is `jcmd GC.heap_info` after a forced full GC. A small plan was
simulated with `-XX:MaxRAM=512m` where the baseline's percentage-based sizing
required it.

Run-to-run variance on RSS was **±10 to 20 MB**. Differences smaller than that
are reported as noise, not as results. Live-heap figures varied by under 1 MB
between runs and are the more reliable signal.

These numbers describe an idle-to-lightly-loaded instance with no real data.
Production RSS will differ; the relative savings are the claim, not the
absolute figures.

The baseline was measured at commit `26e2020`, before
[`package-by-type-within-modules.md`](package-by-type-within-modules.md) landed.
That change relocates types between packages and extracts nested records into
top-level files; it moves no work to startup and changes no dependency, so the
figures below carry over. Re-measure on the deployed container regardless, per
the note above.

### Where the memory goes

Baseline configuration as currently deployed, 532 to 541 MB RSS across two
runs. NMT committed regions:

| Region | Committed | Note |
|---|---|---|
| Java heap | 196 MB | live set after full GC was 93 MB |
| Metaspace | 103 MB | 21,873 classes loaded at startup |
| GC structures (G1) | 46 MB | |
| Symbol table | 42 MB | class metadata |
| Code cache | 42 MB | |

The footprint is dominated by class metadata and JVM native regions, not by
application data. This is the central fact behind every proposal below, and
behind the rejected alternatives.

### Finding 1: ArchUnit runs a full classpath scan on every boot

[`pom.xml`](../../pom.xml) line 182 declares `spring-modulith-actuator`. That
artifact pulls in `spring-modulith-runtime`, whose
`SpringModulithRuntimeAutoConfiguration$ApplicationModulesBootstrap` builds the
Spring Modulith module model at startup by importing the entire classpath
through ArchUnit.

Evidence from `-Xlog:class+load`:

- Baseline: 21,873 classes loaded, of which **888 are ArchUnit**.
- With the module-model bootstrap disabled: 20,684 classes, **0 ArchUnit**.
- Live heap after full GC fell from **93 MB to 54 MB**.

ArchUnit retains a model of every class it imports, which is why the cost lands
on the heap and not only on metaspace.

Production does not use the result.
[`application-prod.yml`](../../src/main/resources/application-prod.yml) line 63
exposes only `health`, so the `modulith` actuator endpoint is unreachable. The
module structure is verified at test time by `ModularityTests`, which is
unaffected by this proposal.

### Finding 2: heap is committed far above the live set

[`Dockerfile`](../../Dockerfile) line 30 sets
`JAVA_OPTS="-XX:MaxRAMPercentage=75"`. On a 512 MB container this commits about
196 MB of heap against a measured live set of 54 to 93 MB, and the default G1
collector adds 46 MB of its own bookkeeping.

### Finding 3: two dependencies are inert in production

- **springdoc-openapi** ([`pom.xml`](../../pom.xml) line 68). Disabled in
  production by `SWAGGER_ENABLED:false`, but present on the classpath, and it
  carries the Swagger UI WebJar.
- **spring-modulith-actuator** ([`pom.xml`](../../pom.xml) line 182). Covered
  in Finding 1; its endpoint is not exposed in production.

### Finding 4: `open-in-view` is enabled by default

Neither [`application.yml`](../../src/main/resources/application.yml) nor
[`application-prod.yml`](../../src/main/resources/application-prod.yml) sets
`spring.jpa.open-in-view`, so it defaults to `true` and Spring logs its warning
on every boot. This keeps a Hibernate session and its first-level cache open for
the whole request, including view rendering.

### Documentation conflict

None found. [`DEPLOYMENT.md`](../../DEPLOYMENT.md) documents
`DATABASE_POOL_SIZE` and the in-memory session constraint but makes no claim
about JVM memory sizing, so nothing in it contradicts this proposal.

## Proposed Changes

### Tier 1 — configuration only

Change [`Dockerfile`](../../Dockerfile) line 30 to set explicit limits:

```
ENV JAVA_OPTS="-Xmx192m -Xms64m -XX:+UseSerialGC -Xss512k -XX:ReservedCodeCacheSize=48m"
```

`MaxRAMPercentage` is replaced by an explicit `-Xmx`. The existing comment on
that line explains that percentage sizing was chosen so the JVM reads the
cgroup limit; that rationale should be rewritten, not deleted, to record why an
explicit cap now supersedes it.

`UseSerialGC` is appropriate here specifically because the deployment is a
single instance with a small heap and low request volume — the same constraint
[`DEPLOYMENT.md`](../../DEPLOYMENT.md) records under "Known limitations" for
in-memory sessions. It reclaimed 45 MB of G1 bookkeeping with no measured
latency cost at the tested load.

Add to [`application-prod.yml`](../../src/main/resources/application-prod.yml):

```yaml
spring:
  autoconfigure:
    exclude:
      - org.springframework.modulith.runtime.autoconfigure.SpringModulithRuntimeAutoConfiguration
      - org.springframework.modulith.actuator.autoconfigure.ApplicationModulesEndpointConfiguration
  jpa:
    open-in-view: false
```

Both exclusions are required. Excluding only the endpoint configuration was
tested and left all 888 ArchUnit classes loading, because the bootstrap lives in
`spring-modulith-runtime`, not in the actuator module.

Scoping this to the production profile preserves the `modulith` actuator
endpoint in development, where
[`application.yml`](../../src/main/resources/application.yml) exposes it.

### Tier 2 — dependency removal

Remove from [`pom.xml`](../../pom.xml):

- `springdoc-openapi-starter-webmvc-ui` (line 68)
- `spring-modulith-actuator` (line 182)

Removing springdoc also requires:

- Deleting
  [`OpenApiConfig.java`](../../src/main/java/com/example/roshdeandishe/platform/config/OpenApiConfig.java),
  which imports `io.swagger.v3.oas.models`.
- Declaring `com.fasterxml.jackson.core:jackson-databind` as a direct
  dependency. Four production files use Jackson 2 —
  [`StudyPlanFacadeImpl.java`](../../src/main/java/com/example/roshdeandishe/studyplan/internal/StudyPlanFacadeImpl.java),
  [`BaleWebhookService.java`](../../src/main/java/com/example/roshdeandishe/bale/internal/BaleWebhookService.java),
  [`BaleUpdate.java`](../../src/main/java/com/example/roshdeandishe/bale/internal/BaleUpdate.java),
  and [`BaleClient.java`](../../src/main/java/com/example/roshdeandishe/bale/internal/BaleClient.java)
  — and `jackson-databind` and `jackson-core` currently reach the classpath
  only as transitive dependencies of springdoc. A build without springdoc and
  without this declaration fails to compile. Declaring a dependency the code
  already uses directly is a correctness fix independent of this proposal's
  memory goal.

Removing the springdoc dependency also removes `SWAGGER_ENABLED` as a
meaningful variable; [`DEPLOYMENT.md`](../../DEPLOYMENT.md) must be updated in
the same change.

## Acceptance Criteria

1. Production RSS under comparable load is at or below 420 MB for Tier 1, and
   at or below 400 MB for Tier 2, measured the same way as the baseline.
2. `-Xlog:class+load` on a production-profile boot reports zero ArchUnit
   classes loaded.
3. `/actuator/health` returns 200 and the Liara health check passes; the
   `startPeriod` of 90 seconds in [`liara.json`](../../liara.json) remains
   sufficient.
4. A production-profile boot logs no `open-in-view` warning and no new warning
   or error.
5. `ModularityTests.verifyModularStructure()` passes, confirming the module
   model is still verified at test time.
6. The development profile still exposes `health`, `info`, and `modulith`.
7. For Tier 2 only: the application starts and the Bale webhook and study-plan
   JSON paths behave unchanged, with no `NoClassDefFoundError` from Jackson.

## Migration and Compatibility Concerns

- **No schema change.** No Flyway migration is required, and Hibernate stays in
  `validate` mode.
- **No API change** in Tier 1. Tier 2 removes `/swagger-ui.html` and
  `/v3/api-docs`, which are already disabled in production; confirm no internal
  consumer depends on reaching them by setting `SWAGGER_ENABLED=true`.
- **Heap headroom is the main risk.** The 192 MB cap sits against a measured
  live set of 54 MB, but that was measured against an **empty database** and
  with no accumulated `HttpSession` state.
  [`DEPLOYMENT.md`](../../DEPLOYMENT.md) records that sessions are held in
  container memory, so live heap grows with concurrent logins in a way this
  measurement does not capture. Expense attachments are also held in heap as
  `byte[]` up to 5 MB per file on upload and download
  ([`ExpenseFacadeImpl.java`](../../src/main/java/com/example/roshdeandishe/finance/internal/ExpenseFacadeImpl.java)).
  Deploy Tier 1 with heap monitoring before committing to a smaller plan.
- **Rollback is a redeploy.** Both tiers are reversible with no data
  implications.

## Verification

Documentation-only at this stage; no application tests are required for this
document. For the implementation change:

```bash
./mvnw test -Dtest=ModularityTests
./mvnw clean verify
git diff --check
```

`clean verify` requires Docker for the Testcontainers suites.

Measurement should be repeated on the deployed container rather than trusted
from this document, since the baseline here was taken against an empty
database.

## Rejected Alternatives

Each was measured, not assumed.

- **Upgrade to Java 25.** Mean RSS 394 MB versus 398 MB on Java 21 over three
  runs each — inside the ±10 to 20 MB noise band. Compact object headers
  (`-XX:+UseCompactObjectHeaders`) consistently cut live heap by about 9%, but
  live heap is only about 13% of RSS. Java 25 is a reasonable upgrade on
  support grounds; it is not a memory lever, and it should not be bundled with
  this change, so that a production RSS measurement attributes cleanly to one
  cause.
- **Upgrade to Java 27.** Rejected on three grounds. Mean RSS 389 MB, again
  within noise. The build fails: Lombok 1.18.46, pinned by the Spring Boot
  4.1.0 parent, throws `ExceptionInInitializerError` in
  `LombokProcessor.init` against JDK 27's javac, and all 41 entities use
  Lombok. And the Temurin build string reports `27+35` with no `-LTS` marker,
  where 25 reports `25+36-LTS`.
- **Class Data Sharing on Java 21.** Increased RSS by about 20 MB, because the
  mapped archive counts against resident memory. It cut startup from 15.3 s to
  12.3 s, so it is worth considering for startup, under a separate proposal.
- **A smaller heap cap (`-Xmx128m`).** Measured 388 MB against 378 MB for the
  same build at `-Xmx192m` — no gain, and it narrows headroom against the
  session-growth risk above.
- **Migrating the four Jackson 2 files to Jackson 3.** This would remove one of
  two JSON stacks from memory; the baseline loads 425 Jackson 2 classes
  alongside 695 Jackson 3 classes. It is excluded from Tier 2 because
  [`StudyPlanFacadeImpl.java`](../../src/main/java/com/example/roshdeandishe/studyplan/internal/StudyPlanFacadeImpl.java)
  lines 44-48 record a deliberate reason for the current arrangement — Spring
  Boot 4 no longer produces a single unambiguous `ObjectMapper` bean once
  `spring-modulith-events-jackson` places Jackson 3 on the classpath — and
  because `StudyPlanFacadeImpl` serializes JSON that is persisted, so a
  serializer change risks altering stored data. It belongs in its own proposal
  with its own data-compatibility analysis.

## Unresolved Questions

1. What is the target container plan? The measured savings make a 512 MB plan
   viable where the baseline's 535 MB does not fit, but the choice depends on
   real session and attachment load, which this measurement does not cover.
2. Is `/swagger-ui.html` used by anyone against the production deployment with
   `SWAGGER_ENABLED=true`? Tier 2 removes that possibility permanently.
3. Should `DATABASE_POOL_SIZE` be lowered from its default of 10? Connection
   buffers were not isolated in this measurement, and the setting is already
   documented as a Postgres-side constraint rather than a memory one.
