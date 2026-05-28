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
- Managed-driver property DSLs for Neo4j, Memgraph, and FalkorDB.
- Lifecycle cleanup for plugin-owned resources.

## Dependencies

Use `graph-ktor` with the backend module your application actually runs.

```kotlin
dependencies {
    implementation("io.github.bluetape4k.graph:bluetape4k-graph-ktor")
    implementation("io.github.bluetape4k.graph:bluetape4k-graph-tinkerpop") // or graph-neo4j, graph-age, ...
    implementation("io.ktor:ktor-server-core")
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

## Backend Notes

| Backend | Helper | Lifecycle |
|---|---|---|
| TinkerGraph | `tinkerGraph()` | Plugin creates and closes the in-memory graph delegate. |
| Neo4j | `neo4j(driver, database)` | Driver is caller-owned and is not closed by the plugin. |
| Neo4j | `neo4j { uri; username; password; database }` | Plugin creates and closes the driver. |
| Memgraph | `memgraph(driver, database)` | Driver is caller-owned and is not closed by the plugin. |
| Memgraph | `memgraph { uri; username; password; database }` | Plugin creates and closes the driver. |
| Apache AGE | `age(graphName)` | Caller must call Exposed `Database.connect(...)` before graph use. |
| FalkorDB | `falkorDB(driver, graphName)` | Driver is caller-owned and is not closed by the plugin. |
| FalkorDB | `falkorDB { host; port; username; password; graphName }` | Plugin creates and closes the driver. |

Managed Apache AGE `DataSource` creation is intentionally tracked separately in
[#254](https://github.com/bluetape4k/bluetape4k-graph/issues/254) because Exposed
transaction-manager and pool ownership need a dedicated contract.

## Testing

```bash
./gradlew :graph-ktor:test
```

The test suite includes Ktor `testApplication` checks plus small Testcontainers smoke tests for
Neo4j, Memgraph, Apache AGE, and FalkorDB helper wiring. Docker is required for those backend
runtime checks.
