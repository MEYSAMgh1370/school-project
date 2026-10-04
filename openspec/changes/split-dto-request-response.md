# Split `dto/` Into `dto/request` And `dto/response`

Status: implemented 2026-10-04. Current behavior is reflected in `conventions.md` and
`architecture.md`. Both open questions were resolved with the proposed defaults.

## Current Behavior

`package-by-type-within-modules.md` (implemented 2026-09-28) put each module's
controller request/response records in a flat `<module>/dto/` package. Inspected
2026-10-04 (`find src/main/java -type d -name dto`):

| Module | Files | `*Request` | `*Response` | Other |
|---|---|---|---|---|
| academic | 13 | 4 | 7 | `PromotionDtos`, `TimetableDtos` (aggregate holders, 12 and 10 nested records) |
| curriculum | 5 | 2 | 3 | |
| exam | 14 | 5 | 9 | |
| finance | 23 | 6 | 17 | |
| iam | 22 | 9 | 12 | `ActivatePageModel` (JTE page model) |
| school | 7 | 3 | 4 | |
| studyplan | 14 | 7 | 7 | |
| web | 6 | 0 | 0 | JTE page models/rows (`DashboardModel`, `WardRow`, ...) |

`dto/` carries no `@NamedInterface`. Production code outside a module never
imports another module's `dto`; the only cross-module imports are in tests
(`web.dto` and `studyplan.dto` from root-package tests). JTE templates import
`web.dto.*` and `iam.dto.ActivatePageModel`.

## Proposed Behavior

Inside each module that has request/response records:

```
<module>/dto/request/    inbound bodies (*Request, and nested inbound payload items)
<module>/dto/response/   outbound bodies (*Response, views)
```

- Pure move plus package rename. No type is renamed, no visibility changes, no
  field or JSON shape changes. Nothing moves into a module base package.
- Aggregate holders `PromotionDtos` and `TimetableDtos` are split into one
  top-level record per file, placed by direction. Inbound payload parts
  (`Decision`, `PlacementItem`) go to `request/`; views such as
  `MyTimetableView` go to `response/`. Assumption (default): split them, since
  leaving them would make two files straddle both packages. `from(...)`
  factories stay on the records.
- JTE page models (`web/dto/*`, `iam/dto/ActivatePageModel`) are neither request
  nor response. Default: leave them where they are, out of scope. Moving them
  is a separate question (see below).

## Acceptance Criteria

- No `*Request`/`*Response` type remains directly in any `dto/`.
- `./mvnw test -Dtest=ModularityTests` passes (baseline passed 2026-10-04).
- `./mvnw clean verify` passes where Docker is available; JSON contract tests
  unchanged and green.
- Diff is moves and import/package edits only (`git diff -M --stat`), plus
  `conventions.md`, `architecture.md`, and `project.md` updates.

## Migration And Compatibility

- Internal refactor; HTTP/JSON contracts are unchanged. Jackson serialises
  records by shape, not package.
- ~136 main and 8 test files import `*.dto`; wildcard imports of the nested
  records (`PromotionDtos.*`, `TimetableDtos.*`) need explicit replacement.
- Work proceeds one module per commit, smallest first: curriculum, school,
  academic, exam, studyplan, iam, finance. Each commit compiles and passes
  `ModularityTests`.

## Tests

`ModularityTests.verifyModularStructure()` per module commit; full suite at the
end. As in the prior change, it cannot detect an accidental base-package
addition, so review `git diff -M` for any file leaving `dto/`.

## Open Questions

1. Split the two aggregate holders into one file per record (proposed), or keep
   them and place the whole holder under one package?
2. Should `web/dto` and `iam/dto/ActivatePageModel` (page models) get their own
   package such as `dto/model`, or stay as is (proposed)?
