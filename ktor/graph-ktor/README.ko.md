# graph-ktor

> 🇺🇸 [English](README.md)

`bluetape4k-graph`를 Ktor 3.x application에 연결하는 plugin module입니다. Backend를 명시적으로 선택하고,
`GraphOperations`와 `GraphSuspendOperations`를 Ktor `Application` / `ApplicationCall` extension으로 노출합니다.

## 아키텍처

![graph ktor Architecture diagram](../../docs/images/readme-diagrams/ktor-graph-ktor-architecture-01.png)

## 주요 기능

- Ktor `createApplicationPlugin(...)` 기반 integration.
- Production 오동작 방지를 위한 explicit backend selection.
- `Application.graphOperations()` / `Application.graphSuspendOperations()`.
- Route handler용 `ApplicationCall.graphOperations()` / `ApplicationCall.graphSuspendOperations()`.
- TinkerGraph, Neo4j, Memgraph, Apache AGE, FalkorDB backend helper.
- Neo4j, Memgraph, FalkorDB용 managed-driver property DSL.
- Plugin-owned resource lifecycle cleanup.

## 의존성

`graph-ktor`와 실제 application에서 사용할 backend module을 함께 선언합니다.

```kotlin
dependencies {
    implementation("io.github.bluetape4k.graph:bluetape4k-graph-ktor")
    implementation("io.github.bluetape4k.graph:bluetape4k-graph-tinkerpop") // 또는 graph-neo4j, graph-age, ...
    implementation("io.ktor:ktor-server-core")
}
```

## 사용 예

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

### 기존 Operations 주입

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

같은 managed-driver pattern은 `memgraph { ... }`, `falkorDB { ... }`에서도 사용할 수 있습니다.
Application은 여전히 실제 사용할 backend module dependency를 직접 선언해야 합니다.

## Backend 참고

| Backend | Helper | Lifecycle |
|---|---|---|
| TinkerGraph | `tinkerGraph()` | Plugin이 in-memory graph delegate를 생성하고 닫습니다. |
| Neo4j | `neo4j(driver, database)` | Driver는 caller-owned이며 plugin이 닫지 않습니다. |
| Neo4j | `neo4j { uri; username; password; database }` | Plugin이 driver를 생성하고 닫습니다. |
| Memgraph | `memgraph(driver, database)` | Driver는 caller-owned이며 plugin이 닫지 않습니다. |
| Memgraph | `memgraph { uri; username; password; database }` | Plugin이 driver를 생성하고 닫습니다. |
| Apache AGE | `age(graphName)` | Graph 사용 전에 caller가 Exposed `Database.connect(...)`를 호출해야 합니다. |
| FalkorDB | `falkorDB(driver, graphName)` | Driver는 caller-owned이며 plugin이 닫지 않습니다. |
| FalkorDB | `falkorDB { host; port; username; password; graphName }` | Plugin이 driver를 생성하고 닫습니다. |

Apache AGE managed `DataSource` 생성은 Exposed transaction manager와 pool ownership 계약이 필요하므로
[#254](https://github.com/bluetape4k/bluetape4k-graph/issues/254)에서 별도로 다룹니다.

## 테스트

```bash
./gradlew :graph-ktor:test
```

테스트에는 Ktor `testApplication` 검증과 Neo4j, Memgraph, Apache AGE, FalkorDB helper wiring을
확인하는 작은 Testcontainers smoke가 포함됩니다. 이 backend runtime 검증에는 Docker가 필요합니다.
