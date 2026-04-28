# graph-falkordb

FalkorDB graph database backend for bluetape4k-graph.

## Overview

[FalkorDB](https://falkordb.com/) is a Redis-module based graph database supporting openCypher queries.
This module provides sync and coroutine implementations of `GraphOperations` / `GraphSuspendOperations`
using the [jfalkordb](https://github.com/FalkorDB/jfalkordb) 0.7.0 Java driver.

```mermaid
graph TD
    App["Application"]
    OpsIface["GraphOperations<br/>(graph-core)"]
    SuspendIface["GraphSuspendOperations<br/>(graph-core)"]
    Impl["FalkorDBGraphOperations"]
    SuspendImpl["FalkorDBGraphSuspendOperations"]
    Mapper["FalkorDBRecordMapper"]
    Session["FalkorDBSessionSupport"]
    Driver["FalkorDB Driver<br/>(jfalkordb)"]
    Cypher["openCypher Engine"]
    DB["FalkorDB<br/>(Redis module)"]

    App --> OpsIface
    App --> SuspendIface
    OpsIface <|.. Impl
    SuspendIface <|.. SuspendImpl
    Impl --> Mapper
    Impl --> Session
    SuspendImpl --> Mapper
    SuspendImpl --> Session
    Session --> Driver
    Driver --> Cypher
    Cypher --> DB
```

## Dependencies

```kotlin
// build.gradle.kts
dependencies {
    implementation("io.bluetape4k:graph-falkordb:<version>")
}
```

## Usage

```kotlin
import com.falkordb.FalkorDB
import io.bluetape4k.graph.falkordb.FalkorDBGraphOperations

val driver = FalkorDB.driver("localhost", 6379)
val ops = FalkorDBGraphOperations(driver, graphName = "social")

val alice = ops.createVertex("Person", mapOf("name" to "Alice", "age" to 30))
val bob   = ops.createVertex("Person", mapOf("name" to "Bob",   "age" to 25))
ops.createEdge(alice.id, bob.id, "KNOWS", mapOf("since" to 2024))

val count = ops.countVertices("Person")  // 2
driver.close()
```

### Coroutine (Suspend) API

```kotlin
import io.bluetape4k.graph.falkordb.FalkorDBGraphSuspendOperations

val driver = FalkorDB.driver("localhost", 6379)
val ops = FalkorDBGraphSuspendOperations(driver, graphName = "social")

runBlocking {
    val alice = ops.createVertex("Person", mapOf("name" to "Alice"))
    val neighbors = ops.neighbors(alice.id, NeighborOptions()).toList()
}
driver.close()
```

## Testing

Uses Testcontainers (`FalkorDBServer`) to spin up `falkordb/falkordb:v4.18.1`:

```kotlin
val server = FalkorDBServer.Launcher.falkordb
val driver = FalkorDB.driver(server.host, server.port)
```

```bash
./gradlew :graph-falkordb:test
```

## Notes

- FalkorDB Cypher subset does **not** support `$props` map-expansion in `CREATE` — properties are passed as individual named parameters.
- Node IDs are integers; `GraphElementId.value` must be numeric for ID-based lookups.
- `FalkorDB.driver()` is the entry point (not Neo4j Driver).
