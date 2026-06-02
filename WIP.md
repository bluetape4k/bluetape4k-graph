# WIP - bluetape4k-graph

Snapshot: 2026-06-01 KST
Scope: open GitHub issues assigned to `debop`.
Open count: 4 issues.

## Current Direction

The `0.5.0` stable line has been published and consumed by
`bluetape4k-dependencies` `1.2.0`. Development now moves to `0.6.0` with
`snapshotVersion=` kept empty for workflow-injected snapshot publication.

## Active Queue

| Priority | Issue | Milestone | Notes |
|---|---|---|---|
| P1 | [#233](https://github.com/bluetape4k/bluetape4k-graph/issues/233) feat: add chunked graph export cursor API for graph-io streaming | 0.6.0 | Next graph-io streaming feature lane after 0.5.0 release. |
| P2 | [#234](https://github.com/bluetape4k/bluetape4k-graph/issues/234) research: evaluate backend-native bulk loaders for graph-io | 0.6.0 | Research lane for backend-native bulk import/export tradeoffs. |
| P3 | [#215](https://github.com/bluetape4k/bluetape4k-graph/issues/215) research: revalidate Amazon Neptune backend feasibility | backlog | Required before reviving #30. |
| P3 | [#30](https://github.com/bluetape4k/bluetape4k-graph/issues/30) [Epic] Amazon Neptune 그래프 DB 백엔드 구현 (graph-neptune) | backlog | Keep blocked while marked `invalid`/research; do not implement against mocks only. |

## Recently Completed

- `0.5.0` Ktor managed backend DSL work is complete for Neo4j, Memgraph,
  FalkorDB, and Apache AGE DataSource ownership.
- `0.5.0` domain example suite is complete for observability, IAM access paths,
  supply-chain impact, data lineage, network topology, and security attack
  paths.
- Root README English/Korean module lists and example test commands already
  include the 0.5.0 example modules.

## Verification Evidence

- GitHub milestone `0.5.0`: open issues `0`, closed issues `35`.
- Open PRs: none.
- CI: `https://github.com/bluetape4k/bluetape4k-graph/actions/runs/26733305940`
  succeeded on commit `8e4abdd`.
- Examples: `https://github.com/bluetape4k/bluetape4k-graph/actions/runs/26733305942`
  succeeded on commit `8e4abdd`.
- Maven Central release check:
  `io.github.bluetape4k.graph:bluetape4k-graph-bom:0.5.0` is published and
  managed by `bluetape4k-dependencies` `1.2.0`.

## Release Notes

- Use `0.6.0` for graph-io streaming and backend-native bulk loader work.
- Keep Neptune work in backlog until local or reliable integration testability
  is proven.
