# Best Practices

Referenced from `CLAUDE.md`. Applies to every coding task in this repo.

---

# Disciplined Engineering

Non-negotiable working discipline for any coding task. Apply these rules in
order: clarify -> plan -> minimal implementation -> verify.

## 1. Think before coding

- State your assumptions out loud before writing code. If an assumption
  materially affects the design, get confirmation first.
- If the request is ambiguous or underspecified, ask - do not assume. Picking
  one interpretation and running with it is a failure mode, not initiative.
- If you become confused mid-task, stop and name exactly what is unclear. Do
  not paper over confusion with plausible-looking code.
- If a simpler approach exists than what the user asked for, push back and
  propose it. Do not agree by default.

## 2. Goal-driven execution

Turn vague instructions into verifiable targets before writing a line of code:

- "Add validation" -> "Write tests for invalid inputs, then make them pass."
- "Improve performance" -> "Establish a baseline measurement, define the
  target, verify against it."
- "Handle errors better" -> "Enumerate the failure cases, define expected
  behavior for each, test them."

If a request cannot be turned into a verifiable target, that is a signal it's
underspecified - go back to step 1 and ask.

## 3. Simplicity first

Write the minimum code that solves the problem.

- No speculative abstractions. No interfaces with one implementation "for
  flexibility." No config options nobody asked for. No generic solutions to
  specific problems.
- Do not build for hypothetical future requirements. YAGNI is the default;
  abstraction must be justified by a concrete, present need.
- The litmus test before finishing: would a senior engineer reviewing this
  call it overcomplicated? If yes, simplify before presenting.
- Prefer boring, obvious code over clever code.

## 4. Surgical changes

Touch only what the task requires.

- Every changed line must trace back to the request. If you can't explain why
  a diff line exists in terms of the user's ask, revert it.
- Do not improve, reformat, rename, or "clean up" neighboring code. Do not
  refactor what is not broken.
- Do not upgrade dependencies, change formatting styles, or reorganize files
  unless that is the task.
- If you notice genuinely problematic adjacent code (bugs, security issues),
  mention it separately - do not silently fix it inside the same change.

## 5. Push back on scope creep

- If the request grows mid-task ("while you're at it..."), name the scope
  change explicitly and confirm before absorbing it.
- If the user proposes premature abstraction or overengineering, say so
  directly with a concrete simpler alternative. Being agreeable is not being
  helpful.
- Disagreement should be technical and specific: state the cost of the
  proposed approach and the benefit of the alternative.

## Self-check before presenting any code

- Did I state my assumptions?
- Is there a verifiable definition of done, and does the code meet it?
- Could this be simpler? (Senior-engineer test)
- Does every changed line trace to the request?
- Did I flag - rather than silently absorb - anything out of scope?

---

# Java conventions (this codebase)

These are the patterns actually in use in Roshdeandishe — match them, don't
introduce a competing style.

- **Constructor injection only**, no field injection, no `@Autowired` (Spring
  auto-wires a single constructor). See `AcademicFacadeImpl`.
- **Package-private by default.** Facade *implementations*, entities, and
  their constructors are package-private (`class AcademicFacadeImpl`, not
  `public class`). Only the Facade interface, REST controllers, and things a
  `@NamedInterface` explicitly exposes are `public`.
- **Lombok only on entities**, and only `@Getter`, `@Setter`,
  `@NoArgsConstructor` — never on Facades, services, or DTOs. Application/
  domain logic classes write explicit constructors by hand (see
  `AcademicFacadeImpl`'s constructor).
- **`record` for value objects and DTOs** — Facade return types (`StudentId`,
  `ClassId`), REST request/response bodies (`CreateClassRequest`,
  `ClassResponse`). Never a mutable class for these.
- **`sealed` interfaces for error types** (`<Module>Error`) — see §6 in
  `CLAUDE.md`. Never an open exception hierarchy for domain errors.
- **`Instant` for timestamps**, not `Date`/`LocalDateTime`, for anything that
  crosses a module boundary (events, audit rows).
- **Doc comments explain the non-obvious "why," not the "what."** Existing
  comments in this codebase call out invariants like "this is not the
  identity, see CLAUDE.md §4" or "intentionally nullable because X" — match
  that register. Don't add comments that restate what a well-named method
  already says.
- **`Optional`/`findBy...().orElseThrow(...)` at repository boundaries**,
  throwing the module's own sealed exception (see `getStudentInfo` in
  `AcademicFacadeImpl`) — never let a raw `NoSuchElementException` or `null`
  leak out of a Facade method.

# Spring / Spring Modulith conventions (this codebase)

- **Facade is the only cross-module contract.** A module's own controllers and
  internal services call its own `FacadeImpl` directly, but never bypass
  another module's Facade to reach its internals.
- **`@Transactional` on Facade methods that mutate state**, not on repository
  or controller methods. Read-only Facade methods (`getStudentInfo`,
  `listClasses`) have no `@Transactional`.
- **Publish domain events through `ApplicationEventPublisher` after the
  transactional work succeeds**, never before (see `enrollStudent`).
- **`@ApplicationModuleListener` handlers must restore `TenantContext` from
  the event and clear it in a `finally` block** — they run on a new thread
  post-commit; `TenantContext` is `ThreadLocal` and does not cross threads.
  See the audit pattern in `CLAUDE.md` §3.
- **Two-layer access control on every protected endpoint**:
  `@RequiresModule("<CODE>")` then `@RequiresPermission("<ACTION>")`, in that
  order — never only one. Never use Spring Security's `hasRole()`/
  `GrantedAuthority` for authorization; that machinery is authentication-only
  here (roles are per-school, not global per principal).
- **Verify `X-School-ID` (`TenantContext`) against any `{schoolId}` path
  variable explicitly**, in the controller, before calling the Facade — copy
  the `requireHeaderMatchesPath` pattern from `ClassController`/
  `MembershipController`/`StudentController`. Do not trust the path variable.
- **`@RestControllerAdvice` beans need an explicit unique name.** Never rely on
  the Spring-derived default from the class name (see `CLAUDE.md` §3 — this
  has caused a real `ConflictingBeanDefinitionException`).
- **REST controllers live at module root**, next to the Facade — not in
  `internal/`.
- **Run `ModularityTests.verifyModularStructure()` after any module-boundary
  change** — it's the actual enforcement mechanism, not just a convention to
  remember by hand.
- **Testing is currently thin** (only `ModularityTests` +
  `Demo5ApplicationTests` exist). When adding real test coverage, prefer
  Spring Modulith's `@ApplicationModuleTest` for facade-level tests (it boots
  only the module under test plus its declared dependencies) over a full
  `@SpringBootTest` unless the test genuinely needs the whole context.
