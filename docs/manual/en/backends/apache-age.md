# Apache AGE

![Backend decision map](../../assets/backends/backend-decision-map.png)

Apache AGE is the choice when graph data must live inside an existing PostgreSQL operational boundary. Queries cross a Cypher-over-SQL layer, so JDBC connection state, graph context, PostgreSQL transactions, and AGE types are part of diagnosis.

The synchronous and coroutine adapters are [`AgeGraphOperations.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-age/src/main/kotlin/io/bluetape4k/graph/age/AgeGraphOperations.kt) and [`AgeGraphSuspendOperations.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-age/src/main/kotlin/io/bluetape4k/graph/age/AgeGraphSuspendOperations.kt). Transaction wiring is visible in [`JdbcTransactionExtensions.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-age/src/main/kotlin/io/bluetape4k/graph/age/JdbcTransactionExtensions.kt), with rollback/cancellation evidence in [`AgeGraphSuspendOperationsTest.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-age/src/test/kotlin/io/bluetape4k/graph/age/AgeGraphSuspendOperationsTest.kt).

Do not assume every common schema operation maps safely to AGE. Inspect [`AgeGraphSchemaManager.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-age/src/main/kotlin/io/bluetape4k/graph/age/AgeGraphSchemaManager.kt) and its [tests](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-age/src/test/kotlin/io/bluetape4k/graph/age/AgeGraphSchemaManagerTest.kt). Verify with the `apache/age:release_PG18_1.7.0` test fixture, then repeat against the production PostgreSQL/AGE combination.

Watch database connections, locks, SQL/Cypher error detail, query plans, and rollback counts. When a failure appears only after pooled connection reuse, inspect session initialization and graph selection before changing domain queries.

## Fixture, session, and transaction check

```kotlin
dependencies {
    implementation(platform("io.github.bluetape4k:bluetape4k-dependencies:<ecosystem-version>"))
    implementation("io.github.bluetape4k:bluetape4k-graph-age")
}
```

```bash
./gradlew :bluetape4k-graph-age:test --tests '*AgeGraphOperationsTest'
./gradlew :bluetape4k-graph-age:test --tests '*AgeGraphSuspendOperationsTest'
```

The fixture starts PostgreSQL with AGE, creates/selects the graph, and initializes the connection before Cypher-over-SQL. Exercise the JDBC transaction boundary:

```kotlin
val before = ops.countVertices("Person")
runCatching {
    ops.transaction {
        createVertex("Person", mapOf("email" to "rollback@example.com"))
        error("force rollback")
    }
}
check(ops.countVertices("Person") == before)
```

## Diagnose session and schema failures

Expected: the exception escapes and the count is unchanged. A missing graph, lost `search_path`, or connection reused without AGE session initialization usually surfaces as SQL/Cypher resolution errors before domain assertions. Reproduce on a newly borrowed pooled connection. Schema-manager limitations are explicit; never replace an unsupported DDL result with a silent success. Close operations, then the fixture-owned DataSource/container; do not close a caller-owned DataSource from graph code.
