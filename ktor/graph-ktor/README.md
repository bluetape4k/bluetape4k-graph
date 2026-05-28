# graph-ktor

> 🇰🇷 [한국어 문서](README.ko.md)

Ktor 3.x plugin integration for `bluetape4k-graph`. It exposes `GraphOperations` and `GraphSuspendOperations` through Ktor `Application` and `ApplicationCall` extensions while keeping backend selection explicit.

## Architecture

![graph ktor Architecture diagram](../../docs/images/readme-diagrams/ktor-graph-ktor-architecture-01.png)

## Features

- Ktor `createApplicationPlugin(...)` based integration.
- Explicit backend selection; no implicit production fallback.
- `Application.graphOperations()` / `Application.graphSuspendOperations()`.
- `ApplicationCall.graphOperations()` / `ApplicationCall.graphSuspendOperations()` for route handlers.
- Backend helper functions for TinkerGraph, Neo4j, Memgraph, Apache AGE, and FalkorDB.
- Managed-driver property DSLs for Neo4j, Memgraph, FalkorDB, and Apache AGE.
- Lifecycle cleanup for plugin-owned resources.

## Dependencies

Use `graph-ktor` with the backend module your application actually runs.

```kotlin
dependencies {
    implementation("io.github.bluetape4k.graph:bluetape4k-graph-ktor")
    implementation("io.github.bluetape4k.graph:bluetape4k-graph-tinkerpop") // or graph-neo4j, graph-age, ...
    implementation("io.ktor:ktor-server-core")
    implementation("com.zaxxer:HikariCP") // required when using ageDataSource { ... }
}
```

## Usage

### TinkerGraph

```kotlin
fun Application.module() {
    install(GraphPlugin) {
        tinkerGraph()
    }

    routing {
        get("/cities/count") {
            call.respondText(call.graphSuspendOperations().countVertices("City").toString())
        }
    }
}
```

### Existing Operations

```kotlin
fun Application.module(syncOps: GraphOperations, suspendOps: GraphSuspendOperations) {
    install(GraphPlugin) {
        operations(syncOps, suspendOps)
    }
}
```

### Neo4j

```kotlin
fun Application.module(driver: Driver) {
    install(GraphPlugin) {
        neo4j(driver, database = "neo4j")
    }
}
```

### Managed Neo4j Driver

```kotlin
fun Application.module() {
    install(GraphPlugin) {
        neo4j {
            uri = "bolt://localhost:7687"
            username = "neo4j"
            password = "secret"
            database = "neo4j"
        }
    }
}
```

The same managed-driver pattern is available through `memgraph { ... }` and
`falkorDB { ... }`. Applications still declare the concrete backend module as a dependency.

### Managed Apache AGE DataSource

```kotlin
fun Application.module() {
    install(GraphPlugin) {
        ageDataSource {
            jdbcUrl = "jdbc:postgresql://localhost:5432/postgres"
            username = "postgres"
            password = "secret"
            graphName = "social"
            connectionInitSql = "LOAD 'age'; SET search_path = ag_catalog, public;"
        }
    }
}
```

`ageDataSource { ... }` creates a Hikari-backed pool, calls Exposed
`Database.connect(dataSource)`, and closes only that plugin-owned pool on
application stop. Use `age(graphName)` when an external DI container already owns
the Exposed `Database`, `DataSource`, or transaction-manager lifecycle.

## Backend Notes

| Backend | Helper | Lifecycle |
|---|---|---|
| TinkerGraph | `tinkerGraph()` | Plugin creates and closes the in-memory graph delegate. |
| Neo4j | `neo4j(driver, database)` | Driver is caller-owned and is not closed by the plugin. |
| Neo4j | `neo4j { uri; username; password; database }` | Plugin creates and closes the driver. |
| Memgraph | `memgraph(driver, database)` | Driver is caller-owned and is not closed by the plugin. |
| Memgraph | `memgraph { uri; username; password; database }` | Plugin creates and closes the driver. |
| Apache AGE | `age(graphName)` | Caller must call Exposed `Database.connect(...)` before graph use. |
| Apache AGE | `ageDataSource { jdbcUrl; username; password; graphName; connectionInitSql }` | Plugin creates the Hikari pool, connects Exposed, and closes only the pool it created. |
| FalkorDB | `falkorDB(driver, graphName)` | Driver is caller-owned and is not closed by the plugin. |
| FalkorDB | `falkorDB { host; port; username; password; graphName }` | Plugin creates and closes the driver. |

## Testing

```bash
./gradlew :graph-ktor:test
```

The test suite includes Ktor `testApplication` checks plus small Testcontainers smoke tests for
Neo4j, Memgraph, Apache AGE, and FalkorDB helper wiring. Docker is required for those backend
runtime checks.
