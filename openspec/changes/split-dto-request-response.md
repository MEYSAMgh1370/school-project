# Split `dto/` into request and response, move page models to `model/`

Status: implemented.

## Change

- `academic/dto/PromotionDtos` and `TimetableDtos` (grouped nested records) became
  one top-level record per file under `academic/dto/request/` (inputs and their
  payload records `Decision`, `PlacementItem`) and `academic/dto/response/`
  (everything else, including `MyTimetableView`). Record names unchanged.
- Page models and row types that are neither requests nor responses moved to
  `model/`: `iam/dto/ActivatePageModel` → `iam/model/`; `web/dto/{DashboardModel,
  MembershipRow, SchoolEntryModel, SchoolEntryRow, WardRef, WardRow}` →
  `web/model/` (beside `NavbarModel`). `web/dto/` no longer exists.
- JTE `@import` lines and tests were updated; no behavior change.

## Open

- Other modules' `dto/` packages still mix requests and responses in one package.
