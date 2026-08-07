# bluetape4k-graph-neo4j

## Before you run

Use this module for Neo4j Java Driver, Bolt, Cypher, native sessions, schema management, merge, traversal, and paired sync/suspend APIs. Choose it when Neo4j is the operational authority. Avoid it for an embedded graph or when PostgreSQL/AGE must own the data boundary. Entry points are [Neo4jGraphOperations.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-neo4j/src/main/kotlin/io/bluetape4k/graph/neo4j/Neo4jGraphOperations.kt) and [Neo4jGraphSuspendOperations.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-neo4j/src/main/kotlin/io/bluetape4k/graph/neo4j/Neo4jGraphSuspendOperations.kt).


Execution mode: **release-fixture linked**. The snippet assumes `password` comes from `NEO4J_PASSWORD`; the linked test creates the container, Driver, `ops`, test data, and closes operations before Driver/container.

## Run

```kotlin
dependencies {
    implementation(platform("io.github.bluetape4k:bluetape4k-dependencies:<ecosystem-version>"))
    implementation("io.github.bluetape4k:bluetape4k-graph-neo4j")
}
```

```kotlin
val driver = GraphDatabase.driver("bolt://localhost:7687", AuthTokens.basic("neo4j", password))
val ops = Neo4jGraphOperations(driver, database = "neo4j")
val alice = ops.mergeVertex("Person", mapOf("email" to "a@example.com"), mapOf("name" to "Alice"))
val bob = ops.createVertex("Person", mapOf("name" to "Bob"))
ops.createEdge(alice.id, bob.id, "KNOWS")
check(ops.neighbors(alice.id, NeighborOptions(edgeLabel = "KNOWS")).single().id == bob.id)
ops.close()
driver.close()
```

## Expected result

Expected: native merge preserves Alice's identity, traversal returns Bob, and the caller closes the injected Driver.

## Transactions, capabilities, and ownership

`transaction { }` executes through a Neo4j transaction; an exception must roll back writes. Schema/index operations are implemented by [Neo4jGraphSchemaManager.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-neo4j/src/main/kotlin/io/bluetape4k/graph/neo4j/Neo4jGraphSchemaManager.kt). Element IDs use Neo4j `elementId()`; do not treat them as stable numeric IDs.

The operations object closes its sessions, not an injected Driver. Framework-managed constructors may own both; follow the Ktor or Spring page for that path.

## Operations checklist

- Record server/image version and selected graph/database.
- Watch connection-pool pressure and query latency.
- Verify transaction rollback and schema capability separately.
- Close operations before caller-owned Driver/DataSource.

## Failure and recovery

Symptom: authentication/service-unavailable differs from Cypher or schema errors. Repair credentials/network first, then query/index state; clear partial test data before rerunning transaction assertions.

Separate authentication/service-unavailable errors from Cypher, schema, and transaction errors. Observe driver pool acquisition, retry count, query latency, server logs, database name, indexes, and transaction rollback. Parameterize values; labels and relationship types require validated identifiers.

```bash
./gradlew :bluetape4k-graph-neo4j:test --tests '*Neo4jGraphOperationsTest' --tests '*Neo4jGraphMergeOperationsTest'
```

Expected: the Neo4j 5 fixture passes CRUD, traversal, merge, and rollback. A Memgraph pass is not equivalent evidence because schema DDL and supported Cypher differ.

## Complete release example

The pinned [Neo4jGraphOperationsTest](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-neo4j/src/test/kotlin/io/bluetape4k/graph/neo4j/Neo4jGraphOperationsTest.kt) defines the fixture values and is the complete executable release example. Run:

```bash
./gradlew :bluetape4k-graph-neo4j:test --tests '*Neo4jGraphOperationsTest'
```

Expected: the fixture starts, assertions pass, and owned resources close in the documented order.

## Non-goals and related guides

See [Neo4j and Memgraph](../backends/neo4j-and-memgraph.md), [testing](../guides/testing.md), and [operations](../guides/operations.md). This module does not provision Neo4j, own injected drivers, or guarantee that every Cypher statement is portable to another Bolt server.

<!-- release-readme-diagrams:start -->
## Release diagrams {#release-diagrams}

These diagrams are loaded directly from README assets published with the `0.6.0` release and pinned to its immutable commit. They describe this manual's released structure and runtime flows, not later Snapshot changes. Select a preview to open the SVG at the same release commit.

### Overview diagram

[![Overview diagram](https://raw.githubusercontent.com/bluetape4k/bluetape4k-graph/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-neo4j-architecture-01.png)](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-neo4j-architecture-01.svg)

_Release README: [`graph/graph-neo4j/README.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-neo4j/README.md)_

### Reactive-Coroutine diagram

[![Reactive-Coroutine diagram](https://raw.githubusercontent.com/bluetape4k/bluetape4k-graph/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-neo4j-architecture-02.png)](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-neo4j-architecture-02.svg)

_Release README: [`graph/graph-neo4j/README.ko.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-neo4j/README.ko.md)_

### neighbors Cypher diagram

[![neighbors Cypher diagram](https://raw.githubusercontent.com/bluetape4k/bluetape4k-graph/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-neo4j-architecture-09.png)](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-neo4j-architecture-09.svg)

_Release README: [`graph/graph-neo4j/README.ko.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-neo4j/README.ko.md)_

### Neo4j diagram

[![Neo4j diagram](https://raw.githubusercontent.com/bluetape4k/bluetape4k-graph/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-neo4j-architecture-11.png)](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-neo4j-architecture-11.svg)

_Release README: [`graph/graph-neo4j/README.ko.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-neo4j/README.ko.md)_

### graph neo4j Architecture 12 diagram

[![graph neo4j Architecture 12 diagram](https://raw.githubusercontent.com/bluetape4k/bluetape4k-graph/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-neo4j-architecture-12.png)](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-neo4j-architecture-12.svg)

_Release README: [`graph/graph-neo4j/README.ko.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-neo4j/README.ko.md)_

### Neo4jGraphOperations diagram

[![Neo4jGraphOperations diagram](https://raw.githubusercontent.com/bluetape4k/bluetape4k-graph/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-neo4j-class-03.png)](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-neo4j-class-03.svg)

_Release README: [`graph/graph-neo4j/README.ko.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-neo4j/README.ko.md)_

### Neo4jCoroutineSession diagram

[![Neo4jCoroutineSession diagram](https://raw.githubusercontent.com/bluetape4k/bluetape4k-graph/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-neo4j-class-04.png)](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-neo4j-class-04.svg)

_Release README: [`graph/graph-neo4j/README.ko.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-neo4j/README.ko.md)_

### Neo4jRecordMapper diagram

[![Neo4jRecordMapper diagram](https://raw.githubusercontent.com/bluetape4k/bluetape4k-graph/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-neo4j-class-05.png)](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-neo4j-class-05.svg)

_Release README: [`graph/graph-neo4j/README.ko.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-neo4j/README.ko.md)_

### createVertex diagram

[![createVertex diagram](https://raw.githubusercontent.com/bluetape4k/bluetape4k-graph/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-neo4j-sequence-06.png)](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-neo4j-sequence-06.svg)

_Release README: [`graph/graph-neo4j/README.ko.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-neo4j/README.ko.md)_

### createEdge diagram

[![createEdge diagram](https://raw.githubusercontent.com/bluetape4k/bluetape4k-graph/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-neo4j-sequence-07.png)](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-neo4j-sequence-07.svg)

_Release README: [`graph/graph-neo4j/README.ko.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-neo4j/README.ko.md)_

### shortestPath diagram

[![shortestPath diagram](https://raw.githubusercontent.com/bluetape4k/bluetape4k-graph/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-neo4j-sequence-08.png)](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-neo4j-sequence-08.svg)

_Release README: [`graph/graph-neo4j/README.ko.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-neo4j/README.ko.md)_

### Publisher → Coroutine diagram

[![Publisher → Coroutine diagram](https://raw.githubusercontent.com/bluetape4k/bluetape4k-graph/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-neo4j-sequence-10.png)](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-neo4j-sequence-10.svg)

_Release README: [`graph/graph-neo4j/README.ko.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-neo4j/README.ko.md)_

<!-- release-readme-diagrams:end -->
