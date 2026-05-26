# WIP - bluetape4k-graph

Snapshot: 2026-05-24 KST
Scope: open GitHub issues assigned to `debop`.
Open count: 1 issue.

## 2026-05-24 Milestone Refresh

Current evidence: latest tags `v0.3.0`, `v0.1.0`. Milestones `0.3.1` and
`0.4.0` are complete, `0.4.1` has zero open issues, and backlog has #30 marked
`invalid,Epic,research`.

| Lane | Candidate milestone | Current candidates | Decision |
|---|---|---|---|
| Patch | `0.4.1` | none yet | Keep as the next patch slot only for release fallout, benchmark/report drift, CI failures, or dependency/catalog sync. |
| Minor | `0.5.0` | revalidate #30 first | Do not schedule Amazon Neptune implementation while #30 is still `invalid`/research. Reopen only after AWS SDK, Testcontainers/localstack feasibility, and API boundary are revalidated. |

Recommended order: keep `0.4.1` empty until a real patch appears; revalidate #30
as research before creating a `0.5.0` implementation epic.

## New Milestone Queue - 2026-05-24

### New patch milestone `0.4.1`

1. [#214](https://github.com/bluetape4k/bluetape4k-graph/issues/214)
   `docs: refresh graph benchmark report after 0.4.0 release`

### New minor milestone `0.5.0`

1. [#215](https://github.com/bluetape4k/bluetape4k-graph/issues/215)
   `research: revalidate Amazon Neptune backend feasibility`
2. [#216](https://github.com/bluetape4k/bluetape4k-graph/issues/216)
   `perf: unify AGE and Neo4j benchmark report output`
3. [#217](https://github.com/bluetape4k/bluetape4k-graph/issues/217)
   `docs: map graph backend capability matrix into README diagrams`

### Backlog reference

1. [#30](https://github.com/bluetape4k/bluetape4k-graph/issues/30)
   `[Epic] Amazon Neptune 그래프 DB 백엔드 구현 (graph-neptune)` remains backlog
   research until #215 decides whether to revive it.

## Issue Discovery - 2026-05-24

Patch candidates:

- `docs: refresh graph benchmark report after 0.4.0 release`
  - Keep patch-scoped: no benchmark implementation change unless current reports
    fail to reproduce.

Minor candidates:

- `research: revalidate Amazon Neptune backend feasibility` (#30)
  - Keep as research while #30 is still marked `invalid`.
- `perf: unify AGE and Neo4j benchmark report output`
  - Evidence: `scripts/benchmark-neo4j-age.sh` combines benchmark JSON summaries.
- `docs: map graph backend capability matrix into README diagrams`
  - Candidate only after benchmark/provider direction is settled.

## Recently Completed

- The 0.4.0 release gate is clear. Milestones `0.3.1` and `0.4.0` both have
  zero open issues on GitHub.
- Coroutine cancellation and suspend transaction correctness work is merged:
  #156, #157, #158, and #160.
- FalkorDB Spring Boot/Ktor readiness and documentation work is merged:
  #126, #127, #133, #134, and #135.
- Graph benchmark and self-improve evidence lanes are merged:
  #14, #15, #41, #188, #189, #190, #191, #192, #193, #196, #197, #198, #199,
  and #201.
- Domain example sample loaders are merged through #111.

## Current Direction

Prepare and publish `0.4.0`.

The repository already contains the 0.4.0 benchmark/self-improve work on
`develop`, so publishing a separate `0.3.1` tag from the same head would create
duplicate patch/minor artifacts with the same code. Treat 0.4.0 as the next
release line and keep new backend work out until the release is complete.

## Priority Queue

| Priority | Issue | Difficulty | Notes |
|---|---|---:|---|
| P1 | [#113](https://github.com/bluetape4k/bluetape4k-graph/issues/113) Neptune local testability research | M | Required predecessor for #30; backlog milestone. |
| P3 | [#30](https://github.com/bluetape4k/bluetape4k-graph/issues/30) Amazon Neptune backend | XL | Blocked on #113/research-quality evidence. |

## Dependency Map

```text
#113 Neptune local testability research
  -> #30 Neptune backend

#156/#157 cancellation fixes
#158/#160 suspend transaction fixes
  -> 0.4.0 correctness baseline

#14/#15/#41/#188/#189/#190/#191/#192/#193/#196/#197/#198/#199/#201
  -> 0.4.0 benchmark evidence baseline
```

## WIP Limits

| Lane | Limit | Current next |
|---|---:|---|
| Research / backend readiness | 1 | #113 before #30. |
| Release | 1 | Finish 0.4.0 release before starting more graph feature work. |
