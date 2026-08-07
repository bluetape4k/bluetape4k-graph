# Bluetape4k Graph 0.5 manual

This manual describes the stable `0.6.0` contract at commit `72c0256e2e1cf61101d29852210e3c827ca93bc0`. It covers the common model, paired synchronous/coroutine APIs, five supported backends, graph-io, and framework integration. Amazon Neptune is **not supported** in 0.6.0; backlog issues are not part of this contract.

## Core capabilities

- **Backend-independent graph model:** The [core model](architecture/core-model.md) gives every backend the same vertex, edge, path, and element-ID vocabulary.
- **Synchronous and coroutine APIs:** [Paired APIs](architecture/paired-apis.md) keep repository, traversal, batch, merge, and transaction operations aligned across blocking and suspending execution.
- **Five database backends:** The [backend selection guide](backends/selection-guide.md) compares Neo4j, Memgraph, Apache AGE, TinkerPop/TinkerGraph, and FalkorDB by query language, transaction behavior, and operational fit.
- **Schema, traversal, and transactions:** [Schema and transactions](architecture/schema-and-transactions.md) explains labels, indexes, constraints, merge semantics, paths, and ownership boundaries.
- **Graph import and export:** [graph-io formats](graph-io/formats.md), [execution models](graph-io/execution-model.md), and [OkIO security](graph-io/okio-security.md) cover CSV, NDJSON, GraphML, compression, and authenticated encryption.
- **Application integration and examples:** [Spring Boot](frameworks/spring-boot.md), [Ktor](frameworks/ktor.md), and the [learning path](guides/learning-path.md) connect the common API to runnable domain examples and production checks.

## Start with a decision

1. Follow [Getting started](getting-started.md) to import the ecosystem BOM and run one operation.
2. Read the [backend selection guide](backends/selection-guide.md) before adopting a driver.
3. Learn the [core model](architecture/core-model.md), [paired APIs](architecture/paired-apis.md), and [transaction boundary](architecture/schema-and-transactions.md).
4. Choose a [learning path](guides/learning-path.md), then use the testing and operations guides before production.

![Repository learning map](../assets/overview/repository-learning-map.png)

The API center is [`GraphOperations`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-core/src/main/kotlin/io/bluetape4k/graph/repository/GraphOperations.kt) and [`GraphSuspendOperations`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-core/src/main/kotlin/io/bluetape4k/graph/repository/GraphSuspendOperations.kt). Both return the backend-independent models defined under [`graph-core/model`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-core/src/main/kotlin/io/bluetape4k/graph/model/GraphVertex.kt).

## Manual map

- Architecture: repository layout, model, API composition, schema, merge/batch, traversal, and transactions.
- Backends: Neo4j, Memgraph, Apache AGE, TinkerPop/TinkerGraph, and FalkorDB.
- graph-io: format boundaries, execution models, OkIO compression and authenticated encryption.
- Frameworks: Ktor plugin and Spring Boot auto-configuration lifetimes.
- Guides: staged learning, tests, operations, cancellation, and benchmark interpretation.

Version selection belongs to `bluetape4k-dependencies`, not to individual graph libraries or the graph BOM. Every dependency example in this manual therefore imports the ecosystem BOM and leaves module coordinates unversioned.

## Guides and concepts

- Start: [Getting started](getting-started.md), [learning path](guides/learning-path.md)
- Architecture: [repository map](architecture/repository-map.md), [core model](architecture/core-model.md), [paired APIs](architecture/paired-apis.md), [schema and transactions](architecture/schema-and-transactions.md)
- Backends: [selection guide](backends/selection-guide.md), [Neo4j and Memgraph](backends/neo4j-and-memgraph.md), [Apache AGE](backends/apache-age.md), [TinkerPop](backends/tinkerpop.md), [FalkorDB](backends/falkordb.md)
- graph-io: [formats](graph-io/formats.md), [execution model](graph-io/execution-model.md), [OkIO security](graph-io/okio-security.md)
- Frameworks: [Spring Boot](frameworks/spring-boot.md), [Ktor](frameworks/ktor.md)
- Production guides: [testing](guides/testing.md), [operations](guides/operations.md), [failure and cancellation](guides/failure-and-cancellation.md), [benchmark-based selection](guides/benchmark-based-selection.md)

## Published libraries

- Platform and core: [graph BOM](modules/bluetape4k-graph-bom.md), [graph core](modules/bluetape4k-graph-core.md)
- Backends: [Neo4j](modules/bluetape4k-graph-neo4j.md), [Memgraph](modules/bluetape4k-graph-memgraph.md), [Apache AGE](modules/bluetape4k-graph-age.md), [TinkerPop](modules/bluetape4k-graph-tinkerpop.md), [FalkorDB](modules/bluetape4k-graph-falkordb.md)
- graph-io: [core](modules/bluetape4k-graph-io-core.md), [CSV](modules/bluetape4k-graph-io-csv.md), [Jackson 2](modules/bluetape4k-graph-io-jackson2.md), [Jackson 3](modules/bluetape4k-graph-io-jackson3.md), [GraphML](modules/bluetape4k-graph-io-graphml.md), [OkIO](modules/graph-okio.md)
- Frameworks: [Spring Boot](modules/bluetape4k-graph-spring-boot.md), [Ktor](modules/bluetape4k-graph-ktor.md)

## Examples

- Modeling: [code graph](examples/code-graph.md), [knowledge graph](examples/knowledge-graph.md), [LinkedIn graph](examples/linkedin-graph.md), [recommendation](examples/recommendation.md)
- Risk and operations: [fraud detection](examples/fraud-detection.md), [IAM access graph](examples/iam-access-graph.md), [observability graph](examples/observability-graph.md), [security attack path](examples/security-attack-path.md)
- Systems: [supply chain](examples/supply-chain-graph.md), [data lineage](examples/data-lineage.md), [network topology](examples/network-topology.md), [Ktor graph](examples/ktor-graph.md)

## Benchmarks

- [Benchmark overview](benchmarks/overview.md)
- [Graph operations](benchmarks/graph-operations.md)
- [graph-io](benchmarks/graph-io.md)
- [AGE and Neo4j](benchmarks/age-and-neo4j.md)
