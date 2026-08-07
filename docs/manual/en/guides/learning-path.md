# Learning path

## Stage 1: model one relationship

Run [Getting started](../getting-started.md) with TinkerGraph. Learn opaque IDs, directed edges, and returned snapshots. Observe generated IDs and neighbor direction. If no neighbor appears, inspect `startId`/`endId`, `Direction`, and label filters. Then read [`GraphVertexTest.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-core/src/test/kotlin/io/bluetape4k/graph/model/GraphVertexTest.kt).

## Stage 2: read a domain example

Open the [code graph schema](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/code-graph-examples/src/main/kotlin/io/bluetape4k/graph/examples/code/schema/CodeGraphSchema.kt), then follow [`AbstractCodeGraphTest.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/code-graph-examples/src/test/kotlin/io/bluetape4k/graph/examples/code/AbstractCodeGraphTest.kt). Learn schema declarations, writes, traversal, and the assertions that define useful output. Run one concrete backend test and compare IDs/query logs.

## Stage 3: add write semantics

Exercise merge, batch, and `transaction {}`. Observe duplicate handling, output order, commit, and rollback. Diagnose partial writes by comparing pre/post counts. Use [`GraphBatchOperationsTest.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-core/src/test/kotlin/io/bluetape4k/graph/repository/GraphBatchOperationsTest.kt) as the contract map.

## Stage 4: change backend

Use the [selection guide](../backends/selection-guide.md). Run the same example against two candidates and record schema, property types, transaction and traversal differences. A compile success is not semantic proof.

## Stage 5: transfer and operate

Round-trip a small dataset with [graph-io](../graph-io/formats.md), inject malformed/truncated input, then establish metrics and recovery steps from [Operations](operations.md). Only then benchmark the representative workload.

## Executable five-stage worksheet

### Stage 1 — core model and direction

Prerequisite: Java 21. Run:

```bash
./gradlew :bluetape4k-graph-core:test --tests '*GraphElementIdTest' --tests '*GraphPathTest'
./gradlew :bluetape4k-graph-tinkerpop:test --tests '*TinkerGraphOperationsTest'
```

Open `GraphElementIdTest` to learn why IDs are opaque, then `TinkerGraphOperationsTest` to see CRUD/traversal assertions. Expect tests to create two vertices, preserve edge direction, and return paths with explicit vertex/edge counts. Inject `GraphElementId.of("")` and reverse the neighbor direction. Validation failure means model input; an empty neighbor result means traversal options/data direction.

### Stage 2 — one domain graph on several backends

Prerequisite: Docker for container-backed variants. Open the code-graph schema because it shows labels before the shared test shows the user-facing query.

```bash
./gradlew :code-graph-examples:test --tests '*TinkerGraphCodeGraphTest'
./gradlew :code-graph-examples:test --tests '*Neo4jCodeGraphTest'
```

Expect the same domain assertions and path/count result, while IDs and backend logs differ. Stop the Neo4j container to inject connectivity failure. If TinkerGraph passes and Neo4j fails before assertions, diagnose fixture/server/driver; if both reach assertions and disagree, inspect property/query semantics.

### Stage 3 — write guarantees

```bash
./gradlew :bluetape4k-graph-core:test --tests '*GraphMergeOperationsTest' --tests '*GraphBatchOperationsTest' --tests '*GraphTransactionExtensionsTest'
./gradlew :bluetape4k-graph-neo4j:test --tests '*Neo4jGraphMergeOperationsTest' --tests '*Neo4jGraphSuspendOperationsTest'
```

Expect duplicate merge to preserve one ID, ordered batches, and rollback after injected exceptions/cancellation. Add an empty merge key to reproduce validation failure. A mid-batch failure may leave rows only on the sequential default; compare post-failure counts to the selected backend override.

### Stage 4 — schema and backend decision

```bash
./gradlew :bluetape4k-graph-neo4j:test --tests '*Neo4jGraphSchemaManagerTest'
./gradlew :bluetape4k-graph-age:test --tests '*AgeGraphSchemaManagerTest'
```

Open each schema manager because the common API intentionally does not hide unsupported DDL. Record created/listed/dropped indexes and the exact unsupported exception. An AGE session/search-path error is setup; `UnsupportedOperationException` is a capability result.

### Stage 5 — transfer, security, and operations

```bash
./gradlew :bluetape4k-graph-io-jackson3:test --tests '*Jackson3RoundTripTest' --tests '*Jackson3EdgeBufferOverflowTest'
./gradlew :bluetape4k-graph-okio:test --tests '*OkioRoundTripTest' --tests '*NegativePathTest'
```

Expect round-trip counts to match, early-edge overflow to stay bounded, wrong DAEAD context/truncation to fail, and atomic write failure to preserve the old target. Open `NegativePathTest` because it turns ownership and cleanup rules into observable file-system assertions. Only after these gates, run the benchmark module that matches the surviving decision and record its environment.
