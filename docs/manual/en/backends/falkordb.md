# FalkorDB

![Backend decision map](../../assets/backends/backend-decision-map.png)

Choose FalkorDB when a Redis-shaped deployed service and its openCypher subset match the system boundary. The module uses jfalkordb; do not treat it as a drop-in Neo4j server merely because both accept Cypher-like queries.

Read [`FalkorDBGraphOperations.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-falkordb/src/main/kotlin/io/bluetape4k/graph/falkordb/FalkorDBGraphOperations.kt) and [`FalkorDBGraphSchemaManager.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-falkordb/src/main/kotlin/io/bluetape4k/graph/falkordb/FalkorDBGraphSchemaManager.kt). Container-backed CRUD, merge, batch, and schema evidence is in [`FalkorDBGraphOperationsTest.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-falkordb/src/test/kotlin/io/bluetape4k/graph/falkordb/FalkorDBGraphOperationsTest.kt) and neighboring tests.

Transaction semantics are the main portability boundary. In 0.6.0 the suspend transaction test records an unsupported repository DSL path rather than pretending atomicity: [`FalkorDBGraphSuspendOperationsTest.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-falkordb/src/test/kotlin/io/bluetape4k/graph/falkordb/FalkorDBGraphSuspendOperationsTest.kt). Design multi-write workflows around verified server/library guarantees.

Observe pool/Redis connectivity, query latency, memory, slow queries, and index creation. Reproduce against the release container, then the exact production image and configuration.

## Start the verified server/client path

```kotlin
dependencies {
    implementation(platform("io.github.bluetape4k:bluetape4k-dependencies:<ecosystem-version>"))
    implementation("io.github.bluetape4k:bluetape4k-graph-falkordb")
}
```

```bash
./gradlew :bluetape4k-graph-falkordb:test --tests '*FalkorDBGraphOperationsTest'
./gradlew :bluetape4k-graph-falkordb:test --tests '*FalkorDBGraphSuspendOperationsTest'
```

In the fixture, create two vertices and an edge, then call `neighbors`. Expected: the container-backed client returns the target vertex and schema tests report the indexes FalkorDB actually exposes. Next run `suspendTransaction { ... }`: the 0.6.0 test expects the repository DSL path to be unsupported, not rolled back as a native multi-statement transaction.

## Diagnose server and transaction restrictions

If connection succeeds but transaction assertions fail, do not add a client-side read/write fallback. Split the workflow into explicitly idempotent operations or choose a backend with the required atomic boundary. Diagnose server readiness, authentication/network, client pool, query subset, then index support in that order. The fixture owns the server and created client; externally supplied clients stay caller-owned.
