# Bluetape4k Graph 0.5 manual

This manual describes the stable `0.5.1` contract at commit `3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907`. It covers the common model, paired synchronous/coroutine APIs, five supported backends, graph-io, and framework integration. Amazon Neptune is **not supported** in 0.5.1; backlog issues are not part of this contract.

## Start with a decision

1. Follow [Getting started](getting-started.md) to import the ecosystem BOM and run one operation.
2. Read the [backend selection guide](backends/selection-guide.md) before adopting a driver.
3. Learn the [core model](architecture/core-model.md), [paired APIs](architecture/paired-apis.md), and [transaction boundary](architecture/schema-and-transactions.md).
4. Choose a [learning path](guides/learning-path.md), then use the testing and operations guides before production.

<!-- diagram: repository learning map -->

The API center is [`GraphOperations`](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/graph/graph-core/src/main/kotlin/io/bluetape4k/graph/repository/GraphOperations.kt) and [`GraphSuspendOperations`](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/graph/graph-core/src/main/kotlin/io/bluetape4k/graph/repository/GraphSuspendOperations.kt). Both return the backend-independent models defined under [`graph-core/model`](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/graph/graph-core/src/main/kotlin/io/bluetape4k/graph/model/GraphVertex.kt).

## Manual map

- Architecture: repository layout, model, API composition, schema, merge/batch, traversal, and transactions.
- Backends: Neo4j, Memgraph, Apache AGE, TinkerPop/TinkerGraph, and FalkorDB.
- graph-io: format boundaries, execution models, OkIO compression and authenticated encryption.
- Frameworks: Ktor plugin and Spring Boot auto-configuration lifetimes.
- Guides: staged learning, tests, operations, cancellation, and benchmark interpretation.

Version selection belongs to `bluetape4k-dependencies`, not to individual graph libraries or the graph BOM. Every dependency example in this manual therefore imports the ecosystem BOM and leaves module coordinates unversioned.
