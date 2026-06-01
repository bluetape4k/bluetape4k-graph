# WIP - bluetape4k-graph

Snapshot: 2026-06-01 KST
Scope: open GitHub issues assigned to `debop`.
Open count: 4 issues.

## Current Release Gate

Prepare and publish `0.5.0`.

The `0.5.0` milestone has zero open issues and no open pull requests. The
release line consumes `io.github.bluetape4k:bluetape4k-bom:1.10.0`, keeps
`snapshotVersion=` empty, and has recent `develop` CI plus Examples workflow
success on commit `8e4abdd`.

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
- Maven Central pre-release check:
  `io.github.bluetape4k.graph:bluetape4k-graph-bom:0.5.0` is not yet
  published, as expected before the stable release dispatch.

## Release Notes

- Keep `0.5.0` focused on graph-ktor managed backend setup plus runnable domain
  graph examples.
- Move any new streaming, backend-native bulk loader, or Neptune work to `0.6.0`
  or backlog unless it is release fallout.
