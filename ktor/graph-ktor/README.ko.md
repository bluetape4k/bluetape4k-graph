# graph-ktor

> 🇺🇸 [English](README.md)

`bluetape4k-graph`를 Ktor 3.x application에 연결하는 plugin module입니다. Backend를 명시적으로 선택하고,
`GraphOperations`와 `GraphSuspendOperations`를 Ktor `Application` / `ApplicationCall` extension으로 노출합니다.

## 아키텍처

```mermaid
flowchart LR
    App[Ktor Application] --> Install[install(GraphPlugin)]
    Install --> Config[GraphPluginConfig]
    Config --> State[GraphPluginState]
    State --> Sync[GraphOperations]
    State --> Suspend[GraphSuspendOperations]
    Route[Route Handler] --> CallExt[call.graphSuspendOperations()]
    CallExt --> Suspend
```

## 주요 기능

- Ktor `createApplicationPlugin(...)` 기반 integration.
- Production 오동작 방지를 위한 explicit backend selection.
- `Application.graphOperations()` / `Application.graphSuspendOperations()`.
- Route handler용 `ApplicationCall.graphOperations()` / `ApplicationCall.graphSuspendOperations()`.
- TinkerGraph, Neo4j, Memgraph, Apache AGE, FalkorDB backend helper.
- Plugin-owned resource lifecycle cleanup.

## 의존성

`graph-ktor`와 실제 application에서 사용할 backend module을 함께 선언합니다.

```kotlin
dependencies {
    implementation("io.github.bluetape4k.graph:graph-ktor")
    implementation("io.github.bluetape4k.graph:graph-tinkerpop") // 또는 graph-neo4j, graph-age, ...
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

## Backend 참고

| Backend | Helper | Lifecycle |
|---|---|---|
| TinkerGraph | `tinkerGraph()` | Plugin이 in-memory graph delegate를 생성하고 닫습니다. |
| Neo4j | `neo4j(driver, database)` | Driver는 caller-owned이며 plugin이 닫지 않습니다. |
| Memgraph | `memgraph(driver, database)` | Driver는 caller-owned이며 plugin이 닫지 않습니다. |
| Apache AGE | `age(graphName)` | Graph 사용 전에 caller가 Exposed `Database.connect(...)`를 호출해야 합니다. |
| FalkorDB | `falkorDB(driver, graphName)` | Driver는 caller-owned이며 plugin이 닫지 않습니다. |

## 테스트

```bash
./gradlew :graph-ktor:test
```
