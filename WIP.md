# WIP - bluetape4k-graph

Snapshot: 2026-05-09 KST
Scope: open GitHub issues assigned to `debop`, created on or after 2026-01-01.
Open count: 15 issues.

## Current Direction

Prioritize graph core API shape before backend expansion, examples, and broad
documentation sweeps. Large example work should wait until transaction,
schema/index, merge/upsert, and batch APIs are stable.

This file is now the active backlog source. The former `TODO.md` content was
consolidated here and in `CHANGELOG.md`.

## Priority Queue

| Priority | Issue | Difficulty | Notes |
|---|---|---:|---|
| P1 | [#13](https://github.com/bluetape4k/bluetape4k-graph/issues/13) transaction DSL | L | Foundational consistency API across backends. |
| P1 | [#32](https://github.com/bluetape4k/bluetape4k-graph/issues/32) schema/index manager | L | Needed for serious backend and example work. |
| P1 | [#34](https://github.com/bluetape4k/bluetape4k-graph/issues/34) merge/upsert | L | Reduces duplicate data handling. |
| P1 | [#33](https://github.com/bluetape4k/bluetape4k-graph/issues/33) batch insert | L | Performance/usability foundation. |
| P2 | [#17](https://github.com/bluetape4k/bluetape4k-graph/issues/17) build cache optimization | M | Pays back during container-heavy backend work. |
| P2 | [#18](https://github.com/bluetape4k/bluetape4k-graph/issues/18) CI quality gates | M | Tune carefully while API churn is active. |
| P2 | [#19](https://github.com/bluetape4k/bluetape4k-graph/issues/19) Dependabot/Renovate | S | Small automation/security win. |
| P2 | [#30](https://github.com/bluetape4k/bluetape4k-graph/issues/30) Amazon Neptune backend | XL | Wait for core APIs and CI stability. |
| P2 | [#49](https://github.com/bluetape4k/bluetape4k-graph/issues/49) graph-okio encrypted streaming | L | Separate from graph core; depends on okio/Tink conventions. |
| P3 | [#10](https://github.com/bluetape4k/bluetape4k-graph/issues/10) extra example modules | L | Should follow core API stability. |
| P3 | [#14](https://github.com/bluetape4k/bluetape4k-graph/issues/14) backend JMH benchmark | M | After operations settle. |
| P3 | [#15](https://github.com/bluetape4k/bluetape4k-graph/issues/15) runtime comparison benchmark | M | After baseline APIs are stable. |
| P3 | [#40](https://github.com/bluetape4k/bluetape4k-graph/issues/40) weighted path suspend tests | S | Narrow quality task. |
| P3 | [#41](https://github.com/bluetape4k/bluetape4k-graph/issues/41) weighted path benchmark | S | Follows `#40`. |
| P4 | [#16](https://github.com/bluetape4k/bluetape4k-graph/issues/16) public API KDoc examples | M | Defer until `#13/#32/#33/#34` settle. |

## Dependency Map

```text
#13 transaction DSL
#32 schema/index API
#34 merge/upsert
#33 batch insert
  -> #30 Neptune backend
  -> #10 extra examples
      -> workshop graph examples

#40 weighted path suspend tests
  -> #41 weighted path benchmark

#17/#18/#19 automation
  -> safer large graph changes
```

## WIP Limits

| Lane | Limit | Current next |
|---|---:|---|
| Core API | 1 | `#13`, then `#32/#34/#33`. |
| CI/automation | 1 | `#19` or `#17`. |
| Examples/docs | 0 until core API settles | `#10/#16` wait. |
