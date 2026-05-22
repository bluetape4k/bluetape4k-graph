# WIP - bluetape4k-graph

Snapshot: 2026-05-22 KST
Scope: open GitHub issues assigned to `debop`.
Open count: 1 issue.

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
