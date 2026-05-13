# WIP - bluetape4k-graph

Snapshot: 2026-05-13 KST
Scope: open GitHub issues assigned to `debop`, created on or after 2026-01-01.
Open count: 8 issues.

## Recently Completed

Core graph API foundation and several adoption lanes are now closed and should
stay in issue/PR history rather than the active queue:

- `#13` transaction DSL, `#32` schema/index manager, `#34` merge/upsert, and
  `#33` batch insert are merged.
- `#75` graph-core capability docs closed by PR #97.
- `#40` weighted path suspend tests closed by PR #98.
- `#96` graph-ktor module closed by PR #100, with CI/Nightly coverage added by
  PR #102 and preserved design docs added by PR #105.
- `#99` graph-spring-boot module naming closed by PR #106.
- `#17` build cache optimization closed by PR #107.
- `#16` public API KDoc examples closed by PR #109.
- `#10` domain example modules closed by PR #110.
- Example build/test coverage was split into the dedicated daily `Examples`
  workflow by PR #112.
- `#49` graph-okio DAEAD encrypted streaming closed by PR #114, with PR #115
  closing the post-review gaps.

## Current Direction

The project has a stable core API surface, refreshed Spring Boot/Ktor module
identity, dedicated example coverage, and graph-okio encryption. The next queue
should avoid starting the Neptune backend until local/integration testability is
proven.

1. Finish `#113` before `#30`; Neptune support is only meaningful if it can be
   tested locally or with a reliable integration substitute.
2. Keep CI/dependency governance healthy with `#18` and `#19`.
3. Use `#111` to improve example adoption by loading sample datasets through
   graph-io instead of hand-built fixtures.
4. Defer benchmark expansion until the backend and examples surface stays green.

## Priority Queue

| Priority | Issue | Difficulty | Notes |
|---|---|---:|---|
| P1 | [#113](https://github.com/bluetape4k/bluetape4k-graph/issues/113) Neptune local testability research | M | Required predecessor for `#30`; verify LocalStack/MiniStack/containers/official options before implementation. |
| P1 | [#18](https://github.com/bluetape4k/bluetape4k-graph/issues/18) CI quality gates | M | Add quality gates only when they are actionable and not noisy for container-heavy modules. |
| P1 | [#19](https://github.com/bluetape4k/bluetape4k-graph/issues/19) Dependabot / Renovate automation | S | Governance baseline exists; verify remaining automation gap before closing. |
| P2 | [#111](https://github.com/bluetape4k/bluetape4k-graph/issues/111) graph-io backed sample dataset loaders | M | Improves examples and onboarding without changing core APIs. |
| P2 | [#30](https://github.com/bluetape4k/bluetape4k-graph/issues/30) Amazon Neptune backend | XL | Blocked on `#113`; do not implement against mocks only. |
| P3 | [#14](https://github.com/bluetape4k/bluetape4k-graph/issues/14) backend JMH benchmark | M | Useful after backend paths and CI gates settle. |
| P3 | [#15](https://github.com/bluetape4k/bluetape4k-graph/issues/15) runtime comparison benchmark | M | Follows stable Sync/VT/Coroutine baselines. |
| P3 | [#41](https://github.com/bluetape4k/bluetape4k-graph/issues/41) weighted path benchmark | S | Follows merged `#40` suspend test baseline. |

## Dependency Map

```text
#13 transaction DSL (closed)
#32 schema/index API (closed)
#34 merge/upsert (closed)
#33 batch insert (closed)
  -> #30 Neptune backend
  -> #111 graph-io sample dataset loaders

#113 Neptune local testability research
  -> #30 Neptune backend

#96 graph-ktor (closed)
  -> Ktor examples use merged graph-ktor APIs

#10 domain examples (closed)
  -> #111 graph-io backed sample dataset loaders

#40 weighted path suspend tests (closed)
  -> #41 weighted path benchmark

#17 build cache (closed)
#18 quality gates
#19 dependency automation
  -> safer large graph changes

#76 graph-okio rename (closed)
#49 graph-okio encrypted streaming (closed)
  -> graph-okio docs and adoption work
```

## WIP Limits

| Lane | Limit | Current next |
|---|---:|---|
| Research / backend readiness | 1 | `#113` before `#30`. |
| CI / automation | 1 | `#18` or `#19`, one at a time. |
| Backend expansion | 1 | `#30` only after `#113` proves a test path. |
| Examples / adoption | 1 | `#111` after current docs PR lands. |
| Benchmarks | 1 | `#14`, `#15`, or `#41` after CI remains green. |
