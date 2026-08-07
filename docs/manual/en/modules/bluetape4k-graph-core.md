# bluetape4k-graph-core

## Before you run

Core defines backend-neutral models and paired synchronous, virtual-thread, and coroutine repository contracts. It supplies `GraphVertex`, `GraphEdge`, `GraphPath`, traversal options, merge interfaces, schema DSL types, transaction scopes, and fallback algorithms. Source anchors: [GraphOperations.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-core/src/main/kotlin/io/bluetape4k/graph/repository/GraphOperations.kt), [GraphVertexRepository.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-core/src/main/kotlin/io/bluetape4k/graph/repository/GraphVertexRepository.kt), and [GraphTraversalRepository.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-core/src/main/kotlin/io/bluetape4k/graph/repository/GraphTraversalRepository.kt).

Use it when implementing against the common contract or building a backend adapter. Do not select it alone when an application needs a concrete graph; it has no storage engine or network driver.


Execution mode: **standalone in-memory example**. `bluetape4k-graph-tinkerpop` supplies an executable `GraphOperations`; core-API consumers must select the concrete graph module required by deployment.

## Run

```kotlin
dependencies {
    implementation(platform("io.github.bluetape4k:bluetape4k-dependencies:<ecosystem-version>"))
    implementation("io.github.bluetape4k:bluetape4k-graph-core")
    implementation("io.github.bluetape4k:bluetape4k-graph-tinkerpop") // executable implementation
}
```

Backend modules already bring core transitively; direct application dependencies are mainly useful for API-only modules.

## Core API and quick start

A runnable in-memory start needs the TinkerPop adapter in addition to core:

```kotlin
import io.bluetape4k.graph.model.NeighborOptions
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations

TinkerGraphOperations().use { ops ->
    val alice = ops.createVertex("Person", mapOf("email" to "a@example.com"))
    val bob = ops.mergeVertex("Person", mapOf("email" to "b@example.com"), mapOf("name" to "Bob"))
    ops.createEdge(alice.id, bob.id, "KNOWS")
    check(ops.neighbors(alice.id, NeighborOptions(edgeLabel = "KNOWS")).single().id == bob.id)
}
```

## Expected result

Expected: two vertices, one directed edge, and one outgoing neighbor. `mergeVertex` is a capability interface implemented per backend; it is not a universal SQL-like semantic guarantee.

## Behavior, transactions, and resources

The facade combines session, vertex, edge, and traversal repositories. Transaction extensions require `GraphTransactionalOperations`; unsupported implementations fail rather than simulate atomicity. Suspend and virtual-thread adapters change execution shape, not database guarantees. IDs are opaque `GraphElementId` values; never parse them to infer a backend identity.

Core owns no server resource. The concrete operations object, injected driver, data source, and framework container define ownership.

## Operations checklist

- Treat `GraphElementId` as opaque.
- Record the concrete graph implementation beside test results.
- Check transaction and schema capabilities before optional extensions.
- Bound traversal depth and batch size.

## Failure and recovery

Symptom: an extension throws an unsupported-capability exception. Select an implementation that provides the interface or remove the call; never turn it into silent success.

- Unsupported transaction/schema/algorithm calls mean the adapter lacks that capability; do not catch and pretend success.
- Missing edge endpoints and duplicate external identities must be handled at the caller or graph-io policy boundary.
- Fallback traversals can have different cost from native queries. Verify depth, weight, and missing-weight policy.
- A core test pass proves model and adapter utilities, not a backend.

```bash
./gradlew :bluetape4k-graph-core:test --tests '*GraphMergeOperationsTest' --tests '*GraphTransactionExtensionsTest'
```

Expected: merge helpers and capability guards pass. If only a backend fails, move diagnosis to that adapter's query mapping and transaction implementation.

## Complete release example

The release-pinned [`TinkerGraphOperationsTest`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-tinkerpop/src/test/kotlin/io/bluetape4k/graph/tinkerpop/TinkerGraphOperationsTest.kt) is a complete in-memory example that constructs the graph, owns its lifecycle, and exercises the core operations through the TinkerPop adapter.

```bash
./gradlew :bluetape4k-graph-tinkerpop:test --tests '*TinkerGraphOperationsTest'
```

## Non-goals and related guides

Track query latency, traversal depth, batch size, error type, and backend counts around multi-step work. See [core model](../architecture/core-model.md), [paired APIs](../architecture/paired-apis.md), [schema and transactions](../architecture/schema-and-transactions.md), and [operations](../guides/operations.md). Core does not normalize all backend features, provision databases, or make multi-call workflows atomic.

<!-- release-readme-diagrams:start -->
## Release diagrams {#release-diagrams}

These diagrams are loaded directly from README assets published with the `0.6.0` release and pinned to its immutable commit. They describe this manual's released structure and runtime flows, not later Snapshot changes. Select a preview to open the SVG at the same release commit.

### Architecture Overview diagram

[![Architecture Overview diagram](https://raw.githubusercontent.com/bluetape4k/bluetape4k-graph/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-core-architecture-01.png)](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-core-architecture-01.svg)

_Release README: [`graph/graph-core/README.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-core/README.md)_




### CRUD diagram

[![CRUD diagram](https://raw.githubusercontent.com/bluetape4k/bluetape4k-graph/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-core-architecture-13.png)](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-core-architecture-13.svg)

_Release README: [`graph/graph-core/README.ko.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-core/README.ko.md)_

### graph core Architecture 14 diagram

[![graph core Architecture 14 diagram](https://raw.githubusercontent.com/bluetape4k/bluetape4k-graph/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-core-architecture-14.png)](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-core-architecture-14.svg)

_Release README: [`graph/graph-core/README.ko.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-core/README.ko.md)_

### : GraphElementId, GraphVertex, GraphEdge diagram

[![: GraphElementId, GraphVertex, GraphEdge diagram](https://raw.githubusercontent.com/bluetape4k/bluetape4k-graph/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-core-class-02.png)](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-core-class-02.svg)

_Release README: [`graph/graph-core/README.ko.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-core/README.ko.md)_

### PathStep GraphPath diagram

[![PathStep GraphPath diagram](https://raw.githubusercontent.com/bluetape4k/bluetape4k-graph/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-core-class-03.png)](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-core-class-03.svg)

_Release README: [`graph/graph-core/README.ko.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-core/README.ko.md)_

### Repository diagram

[![Repository diagram](https://raw.githubusercontent.com/bluetape4k/bluetape4k-graph/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-core-class-04.png)](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-core-class-04.svg)

_Release README: [`graph/graph-core/README.ko.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-core/README.ko.md)_


### createVertex diagram

[![createVertex diagram](https://raw.githubusercontent.com/bluetape4k/bluetape4k-graph/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-core-sequence-06.png)](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-core-sequence-06.svg)

_Release README: [`graph/graph-core/README.ko.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-core/README.ko.md)_

### shortestPath diagram

[![shortestPath diagram](https://raw.githubusercontent.com/bluetape4k/bluetape4k-graph/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-core-sequence-07.png)](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-core-sequence-07.svg)

_Release README: [`graph/graph-core/README.ko.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-core/README.ko.md)_

### neighbors diagram

[![neighbors diagram](https://raw.githubusercontent.com/bluetape4k/bluetape4k-graph/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-core-sequence-08.png)](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-core-sequence-08.svg)

_Release README: [`graph/graph-core/README.ko.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-core/README.ko.md)_

### createEdge diagram

[![createEdge diagram](https://raw.githubusercontent.com/bluetape4k/bluetape4k-graph/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-core-sequence-09.png)](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-core-sequence-09.svg)

_Release README: [`graph/graph-core/README.ko.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-core/README.ko.md)_

<!-- release-readme-diagrams:end -->
