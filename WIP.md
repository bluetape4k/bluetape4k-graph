# WIP - bluetape4k-graph

Snapshot: 2026-06-26 KST
Scope: open GitHub issues assigned to `debop`.
Open count after merging the issue #233/#234 PRs: 2 issues.

## Current Direction

The `0.5.0` stable line has been published and consumed by
`bluetape4k-dependencies` `1.2.0`. Development now moves to `0.6.0` with
`snapshotVersion=` kept empty for workflow-injected snapshot publication.

## Active Queue

| Priority | Issue | Milestone | Notes |
|---|---|---|---|
| P3 | [#215](https://github.com/bluetape4k/bluetape4k-graph/issues/215) research: revalidate Amazon Neptune backend feasibility | backlog | Required before reviving #30. |
| P3 | [#30](https://github.com/bluetape4k/bluetape4k-graph/issues/30) [Epic] Amazon Neptune 그래프 DB 백엔드 구현 (graph-neptune) | backlog | Keep blocked while marked `invalid`/research; do not implement against mocks only. |

## Recently Completed

- `0.5.0` Ktor managed backend DSL work is complete for Neo4j, Memgraph,
  FalkorDB, and Apache AGE DataSource ownership.
- `0.5.0` domain example suite is complete for observability, IAM access paths,
  supply-chain impact, data lineage, network topology, and security attack paths.
- [#234](https://github.com/bluetape4k/bluetape4k-graph/issues/234)
  backend-native bulk loader research is documented. Recommendation: defer
  Neo4j/Memgraph/AGE/FalkorDB native fast paths from `0.6.0`, reject
  TinkerPop/TinkerGraph as a native-loader lane, and keep issue #233 as the
  next separate graph-io implementation PR.
- [#233](https://github.com/bluetape4k/bluetape4k-graph/issues/233)
  chunked graph export cursor API is implemented with a TinkerGraph reference
  path and Jackson3 NDJSON exporter proof.
- Root README English/Korean module lists and example test commands already
  include the 0.5.0 example modules.
- Aligned Ktor examples with shared bluetape4k-ktor-core modules.

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
- Keep backend-native loader work research-only for `0.6.0` unless a separate
  backend-native SPI is designed and scoped after issue #233.
- Keep Neptune work in backlog until local or reliable integration testability
  is proven.
