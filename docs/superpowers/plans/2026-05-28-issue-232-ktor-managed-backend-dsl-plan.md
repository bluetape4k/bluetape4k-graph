# Issue #232 Ktor Managed Backend DSL Plan

> Spec: [2026-05-28-issue-232-ktor-managed-backend-dsl-design.md](../specs/2026-05-28-issue-232-ktor-managed-backend-dsl-design.md)
> Related issue: [#232](https://github.com/bluetape4k/bluetape4k-graph/issues/232)

## Tasks

| Task | Scope | Verification |
|---|---|---|
| T1. Managed config API | Add Neo4j, Memgraph, FalkorDB property DSL overloads under `ktor/graph-ktor` | `:bluetape4k-graph-ktor:compileKotlin` |
| T2. Lifecycle wiring | Register operation close actions and managed driver close actions in stop order | `:bluetape4k-graph-ktor:test` |
| T3. Runtime tests | Update backend Ktor smoke tests to use managed DSL for Neo4j/Memgraph/FalkorDB | targeted test task |
| T4. Docs | Update `README.md` / `README.ko.md` and record AGE split to #254 | `git diff --check` |
| T5. Lesson | Add future guard for example issues to use the latest Ktor DSL | content review |

## Validation Commands

```bash
./gradlew :bluetape4k-graph-ktor:compileKotlin :bluetape4k-graph-ktor:compileTestKotlin --no-daemon
./gradlew :bluetape4k-graph-ktor:test --no-daemon
git diff --check
```

If Testcontainers or Docker is unavailable, rerun the non-container compile/test subset and record the
environment gap instead of claiming full backend runtime proof.

## Plan Review

Local 7-tier plan review:

- P0/P1: none.
- P2: AGE managed `DataSource` could be perceived as part of #232. Mitigation: create #254 and document the exclusion.
- P2: managed DSL may tempt examples to hide backend dependencies. Mitigation: README states applications still declare the concrete backend module.

Convergence: P0 = 0, P1 = 0.
