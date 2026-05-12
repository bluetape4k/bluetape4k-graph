# WIP - bluetape4k-graph

Snapshot: 2026-05-12 KST
Scope: open GitHub issues assigned to `debop`, created on or after 2026-01-01.
Open count: 14 issues.

## Refresh Notes

Verified with GitHub connector on 2026-05-12 KST. `gh` CLI was not used because the local token is invalid.

Open PRs to watch:

- PR #100 `feat: graph-ktor Ktor plugin module 추가`, closes #96.
- PR #98 `test: suspend weighted path 통합 검증 추가`, closes #40.
- PR #97 `docs: graph-core capability 문서 정합성 확보`, closes #75.

Recently completed and no longer part of the active implementation queue:

- `#13` transaction DSL, `#32` schema/index manager, `#34` merge/upsert, and `#33` batch insert are merged.
- Governance/doc maintenance merged through PR #79, #80, #81, #82, and #91.

## Current Direction

The core graph API foundation is stable. The immediate focus is merge hygiene, Ktor integration, naming cleanup before release, and CI/backend work in that order.

1. Merge or close PR #100, #98, and #97 before opening another broad feature branch.
2. Handle `#99` soon because it is pre-release module naming cleanup for `graph-spring-boot4-starter`.
3. Keep CI/build automation healthy before starting the Amazon Neptune backend.
4. Let example modules follow the stable API surface instead of driving new API design.

## Priority Queue

| Priority | Issue | Difficulty | Notes |
|---|---|---:|---|
| P0 | [#96](https://github.com/bluetape4k/bluetape4k-graph/issues/96) graph-ktor module | L | PR #100 open. Merge before expanding Ktor examples. |
| P0 | [#40](https://github.com/bluetape4k/bluetape4k-graph/issues/40) weighted path suspend tests | S | PR #98 open. |
| P0 | [#75](https://github.com/bluetape4k/bluetape4k-graph/issues/75) capability docs/KDoc sync | S | PR #97 open. |
| P1 | [#99](https://github.com/bluetape4k/bluetape4k-graph/issues/99) graph-spring-boot module naming | M | Pre-release naming cleanup; keep separate from graph-ktor. |
| P1 | [#17](https://github.com/bluetape4k/bluetape4k-graph/issues/17) build cache optimization | M | Pays back during container-heavy backend work. |
| P1 | [#18](https://github.com/bluetape4k/bluetape4k-graph/issues/18) CI quality gates | M | Tune carefully now that core API churn has settled. |
| P1 | [#30](https://github.com/bluetape4k/bluetape4k-graph/issues/30) Amazon Neptune backend | XL | Start after merge-wait and naming cleanup are settled. |
| P2 | [#19](https://github.com/bluetape4k/bluetape4k-graph/issues/19) Dependabot / Renovate automation | S | Governance baseline exists; verify whether the issue can close. |
| P2 | [#49](https://github.com/bluetape4k/bluetape4k-graph/issues/49) graph-okio encrypted streaming | L | Separate from graph core; depends on okio/Tink conventions. |
| P2 | [#10](https://github.com/bluetape4k/bluetape4k-graph/issues/10) extra example modules | L | Plan against stable transaction/schema/merge/batch APIs and graph-ktor merge result. |
| P3 | [#14](https://github.com/bluetape4k/bluetape4k-graph/issues/14) backend JMH benchmark | M | Useful after Neptune/backend paths settle. |
| P3 | [#15](https://github.com/bluetape4k/bluetape4k-graph/issues/15) runtime comparison benchmark | M | After backend baseline APIs are stable. |
| P3 | [#41](https://github.com/bluetape4k/bluetape4k-graph/issues/41) weighted path benchmark | S | Follows PR #98 merge. |
| P4 | [#16](https://github.com/bluetape4k/bluetape4k-graph/issues/16) public API KDoc examples | M | Defer broad sweep; add KDoc when touching public APIs. |

## Dependency Map

```text
#13 transaction DSL (closed)
#32 schema/index API (closed)
#34 merge/upsert (closed)
#33 batch insert (closed)
  -> #30 Neptune backend
  -> #10 extra examples
      -> workshop graph examples

#96 graph-ktor (PR #100 open)
  -> #99 graph-spring-boot module naming remains separate
  -> Ktor examples after PR #100 merge

#40 weighted path suspend tests (PR #98 open)
  -> #41 weighted path benchmark

#17/#18 automation
  -> safer large graph changes

#76 graph-okio rename (closed)
  -> #49 graph-okio encrypted streaming
```

## WIP Limits

| Lane | Limit | Current next |
|---|---:|---|
| Merge wait | 3 PRs | PR #100, #98, #97. |
| Naming cleanup | 1 | `#99` after graph-ktor PR state is clear. |
| CI/automation | 1 | `#17` or `#18` before another large backend PR. |
| Backend expansion | 1 | `#30` after API contracts and naming are settled. |
| Examples/docs | 1 | `#10` only after owning APIs are verified in released docs. |
