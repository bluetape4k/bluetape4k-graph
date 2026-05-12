# WIP - bluetape4k-graph

Snapshot: 2026-05-13 KST
Scope: open GitHub issues assigned to `debop`, created on or after 2026-01-01.
Open count: 11 issues.

## Recently Completed

Core graph API foundation work remains closed and should stay in issue/PR history rather than the active queue:

- `#13` transaction DSL, `#32` schema/index manager, `#34` merge/upsert, and `#33` batch insert are merged.
- `#75` graph-core capability docs closed by PR #97.
- `#40` weighted path suspend tests closed by PR #98.
- `#96` graph-ktor module closed by PR #100.
- PR #101 captured merged PR lessons for #97/#98/#100.
- PR #102 added graph-ktor CI/Nightly verification.
- PR #105 preserved issue #96 planning/design docs after completion.
- PR #103 refreshed `WIP.md` on `main`, but the active default branch is `develop`; this refresh realigns `develop`.

## Current Direction

The graph-ktor lane is merged. The immediate focus is pre-release naming cleanup, then CI/backend work in that order.

1. Handle `#99` soon because it is pre-release module naming cleanup for `graph-spring-boot4-starter`.
2. Keep CI/build automation healthy before starting the Amazon Neptune backend.
3. Let Ktor/example modules follow the merged graph-ktor surface instead of driving new API design.
4. Defer weighted path benchmark work until the suspend test baseline remains stable.

## Priority Queue

| Priority | Issue | Difficulty | Notes |
|---|---|---:|---|
| P1 | [#99](https://github.com/bluetape4k/bluetape4k-graph/issues/99) graph-spring-boot module naming | M | Pre-release naming cleanup; do before release-facing docs or backend expansion. |
| P1 | [#17](https://github.com/bluetape4k/bluetape4k-graph/issues/17) build cache optimization | M | Pays back during container-heavy backend work. |
| P1 | [#18](https://github.com/bluetape4k/bluetape4k-graph/issues/18) CI quality gates | M | Tune carefully now that core API churn has settled. |
| P1 | [#30](https://github.com/bluetape4k/bluetape4k-graph/issues/30) Amazon Neptune backend | XL | Start after naming cleanup and CI/build health are settled. |
| P2 | [#19](https://github.com/bluetape4k/bluetape4k-graph/issues/19) Dependabot / Renovate automation | S | Governance baseline exists; verify whether the issue can close. |
| P2 | [#49](https://github.com/bluetape4k/bluetape4k-graph/issues/49) graph-okio encrypted streaming | L | Separate from graph core; depends on okio/Tink conventions. |
| P2 | [#10](https://github.com/bluetape4k/bluetape4k-graph/issues/10) extra example modules | L | Plan against stable transaction/schema/merge/batch APIs and graph-ktor merge result. |
| P3 | [#14](https://github.com/bluetape4k/bluetape4k-graph/issues/14) backend JMH benchmark | M | Useful after Neptune/backend paths settle. |
| P3 | [#15](https://github.com/bluetape4k/bluetape4k-graph/issues/15) runtime comparison benchmark | M | After backend baseline APIs are stable. |
| P3 | [#41](https://github.com/bluetape4k/bluetape4k-graph/issues/41) weighted path benchmark | S | Follows merged `#40` suspend test baseline. |
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

#96 graph-ktor (closed by PR #100)
  -> Ktor examples can now use merged graph-ktor APIs

#99 graph-spring-boot module naming
  -> release-facing docs and adoption cleanup before backend expansion

#40 weighted path suspend tests (closed by PR #98)
  -> #41 weighted path benchmark

#17/#18 automation
  -> safer large graph changes

#76 graph-okio rename (closed)
  -> #49 graph-okio encrypted streaming
```

## WIP Limits

| Lane | Limit | Current next |
|---|---:|---|
| Naming cleanup | 1 | `#99` before release-facing docs or broad feature work. |
| CI/automation | 1 | `#17` or `#18` before another large backend PR. |
| Backend expansion | 1 | `#30` after API contracts, naming, and CI health are settled. |
| Examples/docs | 1 | `#10` only after owning APIs are verified in released docs. |
| Narrow quality | 1 | `#41` after weighted suspend tests stay green. |
