# bluetape4k-graph-age

## Before you run

AGE keeps graph data inside PostgreSQL and executes Cypher through SQL. Choose it when PostgreSQL ownership, backup, and transaction boundaries are required. Avoid it when the workload requires Bolt-native behavior, vendor-specific procedures, or a uniform superset of Neo4j capabilities. The adapter is [AgeGraphOperations.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/graph/graph-age/src/main/kotlin/io/bluetape4k/graph/age/AgeGraphOperations.kt).


Execution mode: **release-fixture linked**. The snippet shows the essential service setup; the complete test fixture starts PostgreSQL AGE, creates `DataSource`/Exposed state and `ops`, and closes operations, DataSource, and container in that order.

## Run

```kotlin
dependencies {
    implementation(platform("io.github.bluetape4k:bluetape4k-dependencies:<ecosystem-version>"))
    implementation("io.github.bluetape4k:bluetape4k-graph-age")
}
```

```kotlin
val dataSource = HikariDataSource(HikariConfig().apply {
    jdbcUrl = "jdbc:postgresql://localhost:5432/postgres"
    username = "postgres"
    password = "password"
    connectionInitSql = "LOAD 'age'; SET search_path = ag_catalog, \"\$user\", public;"
})
Database.connect(dataSource)
val ops = AgeGraphOperations("social")
ops.createGraph("social")
val a = ops.createVertex("Person", mapOf("name" to "Alice"))
val b = ops.mergeVertex("Person", mapOf("email" to "b@example.com"), mapOf("name" to "Bob"))
ops.createEdge(a.id, b.id, "KNOWS")
check(ops.neighbors(a.id, NeighborOptions(edgeLabel = "KNOWS")).single().id == b.id)
```

## Expected result

Expected: AGE returns numeric element IDs and the outgoing traversal finds Bob.

## Semantics and capability boundary

AGE relies on Exposed/JDBC transaction context. `transaction { }` shares the PostgreSQL atomic boundary; pooled connections must run `LOAD 'age'` and set `search_path`. Merge is translated for AGE and must be verified with [AgeGraphMergeOperationsTest.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/graph/graph-age/src/test/kotlin/io/bluetape4k/graph/age/AgeGraphMergeOperationsTest.kt). Schema behavior is narrower than a generic graph DDL surface; inspect [AgeGraphSchemaManager.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/graph/graph-age/src/main/kotlin/io/bluetape4k/graph/age/AgeGraphSchemaManager.kt).

The caller owns `HikariDataSource`. Closing operations does not replace data-source shutdown.

## Operations checklist

- Record server/image version and selected graph/database.
- Watch connection-pool pressure and query latency.
- Verify transaction rollback and schema capability separately.
- Close operations before caller-owned Driver/DataSource.

## Failure and recovery

Symptom: SQL/agtype resolution fails before graph assertions. Evict the bad pooled connection, restore `LOAD 'age'` and `search_path`, verify the graph exists, then rerun on a fresh connection.

A missing graph, absent extension, stale `search_path`, or connection borrowed without initialization usually fails as SQL/agtype resolution before a domain assertion. Check PostgreSQL logs, pool acquisition, locks, SQLSTATE, graph name, and AGE version. Property conversion is bounded by [AgeTypeParser.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/graph/graph-age/src/main/kotlin/io/bluetape4k/graph/age/sql/AgeTypeParser.kt).

```bash
./gradlew :bluetape4k-graph-age:test --tests '*AgeGraphOperationsTest' --tests '*AgeGraphMergeOperationsTest'
```

Expected: the Testcontainers AGE fixture creates, merges, traverses, and rolls back correctly. Retry-only success needs a connection-initialization or container-readiness note.

## Complete release example

The pinned [AgeGraphOperationsTest](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/graph/graph-age/src/test/kotlin/io/bluetape4k/graph/age/AgeGraphOperationsTest.kt) defines the fixture values and is the complete executable release example. Run:

```bash
./gradlew :bluetape4k-graph-age:test --tests '*AgeGraphOperationsTest'
```

Expected: the fixture starts, assertions pass, and owned resources close in the documented order.

## Non-goals and related guides

See [Apache AGE guide](../backends/apache-age.md), [backend selection](../backends/selection-guide.md), and [schema and transactions](../architecture/schema-and-transactions.md). This module does not manage PostgreSQL, hide AGE type limits, or promise Bolt/Cypher parity.
