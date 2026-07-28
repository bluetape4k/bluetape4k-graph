# 이슈 #232 Ktor managed backend DSL 설계

> Related issue: [#232](https://github.com/bluetape4k/bluetape4k-graph/issues/232)

## 맥락

`graph-ktor` currently supports explicit backend selection through caller-owned resources:
`neo4j(driver)`, `memgraph(driver)`, `falkorDB(driver)`, `age(graphName)`, and `tinkerGraph()`.
That is the correct low-level contract, but small Ktor services still need to create backend drivers
outside the plugin before they can install `GraphPlugin`.

The 0.5.0 slice should add a narrowly scoped property DSL for backends where driver ownership is
straightforward. Future example modules should prefer this latest DSL when they demonstrate Ktor
backend setup.

## 범위

Included:

- `neo4j { uri; username; password; database }` managed-driver DSL.
- `memgraph { uri; username; password; database }` managed-driver DSL.
- `falkorDB { host; port; username; password; graphName }` managed-driver DSL.
- Ktor plugin tests proving the managed DSL still exposes sync and suspend operations through routes.
- README examples and lifecycle notes.
- A lesson/future guard telling new example issues to use the latest DSL.

Excluded:

- Apache AGE managed `DataSource` creation. That requires a separate ownership decision for Exposed
  `Database.connect(...)`, process-level transaction manager state, and pool shutdown. Tracked by
  [#254](https://github.com/bluetape4k/bluetape4k-graph/issues/254).
- New dependencies. The DSL reuses backend driver dependencies already required by the existing helpers.

## 설계

기존 helper overload는 caller-owned로 유지된다.

```kotlin
install(GraphPlugin) {
    neo4j(driver, database = "neo4j")
}
```

Managed helper overloads create and own the driver:

```kotlin
install(GraphPlugin) {
    neo4j {
        uri = "bolt://localhost:7687"
        username = "neo4j"
        password = "secret"
        database = "neo4j"
    }
}
```

Lifecycle:

- The plugin owns only drivers created by the managed DSL.
- On `ApplicationStopped`, operation close actions run before the managed driver close action.
- Caller-owned helpers do not close injected drivers.
- AGE remains caller-owned until #254 defines how `DataSource` and Exposed lifecycle should work.

Validation:

- URI/host/database/graphName must not be blank.
- FalkorDB port must be positive.
- Blank username means no-auth for Neo4j/Memgraph and unauthenticated driver creation for FalkorDB.

## 인수 기준 Mapping

| 인수 기준 | 결정 |
|---|---|
| Supports backends where lifecycle ownership is straightforward | Neo4j, Memgraph, FalkorDB managed drivers |
| Explicit ownership and close behavior | Managed DSL closes created driver; caller-owned helpers unchanged |
| Keeps coroutine-first access | `GraphSuspendOperations` remains resolved and exposed by `ApplicationCall.graphSuspendOperations()` |
| Adds tests and README examples | Ktor runtime tests plus README/README.ko updates |
| 정당화되지 않은 새 dependency 없음 | 새 dependency 없음 |

## 리뷰 메모

Local 7-tier design review:

- Security: no credential logging; README uses placeholder passwords.
- Ops/SRE: managed driver close runs through existing isolated close-action path.
- Structural: overloads preserve existing source compatibility.
- Kotlin quality: property config classes are mutable Ktor DSL objects, not data classes.
- Tests: existing backend smoke can exercise managed helper overloads with Testcontainers.
- Performance: no extra driver pools beyond the explicitly selected backend.
- Docs/evidence: README lifecycle table must distinguish caller-owned and managed helpers.

Convergence: P0 = 0, P1 = 0.
