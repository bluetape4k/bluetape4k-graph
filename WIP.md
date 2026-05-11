# WIP - bluetape4k-graph

Snapshot: 2026-05-11 KST
Scope: open GitHub issues assigned to `debop`, created on or after 2026-01-01.
Open count: 10 issues.

## Recently Completed

Core graph API foundation work is now merged and should stay in issue/PR history rather than the active queue:

- `#13` transaction DSL — closed before this refresh.
- `#32` schema/index manager — closed before this refresh.
- `#34` merge/upsert — PR #72 merged.
- `#33` batch insert — PR #78 merged.
- `#76` graph-okio module rename — PR #77 merged.
- Governance/doc maintenance merged today: PR #79 Nightly smoke/full lanes, PR #80 lessons guidance, PR #81 Kover policy, PR #82 Dependabot baseline, PR #91 unassigned Dependabot updates.

## Current Direction

The foundational operation APIs are now stable enough to move from core API shape into backend expansion, automation hardening, and examples:

1. Keep CI/build automation healthy before opening another XL backend lane.
2. Start Amazon Neptune backend only after confirming the merged transaction/schema/merge/batch contracts cover the required backend semantics.
3. Let example modules follow the stable API surface instead of driving new API design.

This file is the active backlog source. The former `TODO.md` content was consolidated here and in `CHANGELOG.md`.

## Priority Queue

| Priority | Issue | Difficulty | Notes |
|---|---|---:|---|
| P1 | [#17](https://github.com/bluetape4k/bluetape4k-graph/issues/17) build cache optimization | M | Pays back during container-heavy backend work. |
| P1 | [#18](https://github.com/bluetape4k/bluetape4k-graph/issues/18) CI quality gates | M | Tune carefully now that core API churn has settled. |
| P1 | [#30](https://github.com/bluetape4k/bluetape4k-graph/issues/30) Amazon Neptune backend | XL | Next backend expansion candidate after API foundation merge verification. |
| P2 | [#49](https://github.com/bluetape4k/bluetape4k-graph/issues/49) graph-okio encrypted streaming | L | Separate from graph core; depends on okio/Tink conventions. |
| P2 | [#10](https://github.com/bluetape4k/bluetape4k-graph/issues/10) extra example modules | L | Can now be planned against stable transaction/schema/merge/batch APIs. |
| P3 | [#14](https://github.com/bluetape4k/bluetape4k-graph/issues/14) backend JMH benchmark | M | Useful after Neptune/backend paths settle. |
| P3 | [#15](https://github.com/bluetape4k/bluetape4k-graph/issues/15) runtime comparison benchmark | M | After backend baseline APIs are stable. |
| P3 | [#40](https://github.com/bluetape4k/bluetape4k-graph/issues/40) weighted path suspend tests | S | Narrow quality task. |
| P3 | [#41](https://github.com/bluetape4k/bluetape4k-graph/issues/41) weighted path benchmark | S | Follows `#40`. |
| P4 | [#16](https://github.com/bluetape4k/bluetape4k-graph/issues/16) public API KDoc examples | M | Defer broad sweep; add KDoc when touching public APIs. |

## Dependency Map

```text
#13 transaction DSL ✅
#32 schema/index API ✅
#34 merge/upsert ✅
#33 batch insert ✅
  -> #30 Neptune backend
  -> #10 extra examples
      -> workshop graph examples

#40 weighted path suspend tests
  -> #41 weighted path benchmark

#17/#18 automation
  -> safer large graph changes

#76 graph-okio rename ✅
  -> #49 graph-okio encrypted streaming
```

## WIP Limits

| Lane | Limit | Current next |
|---|---:|---|
| CI/automation | 1 | `#17` or `#18` before another large backend PR. |
| Backend expansion | 1 | `#30` after confirming merged API contracts. |
| Examples/docs | 1 | `#10` can start only after owning APIs are verified in released docs. |
| Narrow quality | 1 | `#40`, then `#41`. |
