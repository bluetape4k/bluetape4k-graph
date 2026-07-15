# bluetape4k-graph-core

## What it provides

Core defines backend-neutral models and paired synchronous, virtual-thread, and coroutine repository contracts. It supplies `GraphVertex`, `GraphEdge`, `GraphPath`, traversal options, merge interfaces, schema DSL types, transaction scopes, and fallback algorithms. Source anchors: [GraphOperations.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/graph/graph-core/src/main/kotlin/io/bluetape4k/graph/repository/GraphOperations.kt), [GraphVertexRepository.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/graph/graph-core/src/main/kotlin/io/bluetape4k/graph/repository/GraphVertexRepository.kt), and [GraphTraversalRepository.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/graph/graph-core/src/main/kotlin/io/bluetape4k/graph/repository/GraphTraversalRepository.kt).

Use it when implementing against the common contract or building a backend adapter. Do not select it alone when an application needs a concrete graph; it has no storage engine or network driver.

## Dependency

```kotlin
dependencies {
    implementation(platform("io.github.bluetape4k:bluetape4k-dependencies:<ecosystem-version>"))
    implementation("io.github.bluetape4k:bluetape4k-graph-core")
}
```

Backend modules already bring core transitively; direct application dependencies are mainly useful for API-only modules.

## Core API and quick start

A runnable in-memory start needs the TinkerPop adapter in addition to core:

```kotlin
TinkerGraphOperations().use { ops ->
    val alice = ops.createVertex("Person", mapOf("email" to "a@example.com"))
    val bob = ops.mergeVertex("Person", mapOf("email" to "b@example.com"), mapOf("name" to "Bob"))
    ops.createEdge(alice.id, bob.id, "KNOWS")
    check(ops.neighbors(alice.id, NeighborOptions(edgeLabel = "KNOWS")).single().id == bob.id)
}
```

Expected: two vertices, one directed edge, and one outgoing neighbor. `mergeVertex` is a capability interface implemented per backend; it is not a universal SQL-like semantic guarantee.

## Behavior, transactions, and resources

The facade combines session, vertex, edge, and traversal repositories. Transaction extensions require `GraphTransactionalOperations`; unsupported implementations fail rather than simulate atomicity. Suspend and virtual-thread adapters change execution shape, not database guarantees. IDs are opaque `GraphElementId` values; never parse them to infer a backend identity.

Core owns no server resource. The concrete operations object, injected driver, data source, and framework container define ownership.

## Failure modes and diagnosis

- Unsupported transaction/schema/algorithm calls mean the adapter lacks that capability; do not catch and pretend success.
- Missing edge endpoints and duplicate external identities must be handled at the caller or graph-io policy boundary.
- Fallback traversals can have different cost from native queries. Verify depth, weight, and missing-weight policy.
- A core test pass proves model and adapter utilities, not a backend.

```bash
./gradlew :bluetape4k-graph-core:test --tests '*GraphMergeOperationsTest' --tests '*GraphTransactionExtensionsTest'
```

Expected: merge helpers and capability guards pass. If only a backend fails, move diagnosis to that adapter's query mapping and transaction implementation.

## Operations, related pages, and non-goals

Track query latency, traversal depth, batch size, error type, and backend counts around multi-step work. See [core model](../architecture/core-model.md), [paired APIs](../architecture/paired-apis.md), [schema and transactions](../architecture/schema-and-transactions.md), and [operations](../guides/operations.md). Core does not normalize all backend features, provision databases, or make multi-call workflows atomic.
