# graph-ktor

> 🇺🇸 [English](README.md)

`bluetape4k-graph`를 Ktor 3.x application에 연결하는 plugin module입니다. Backend를 명시적으로 선택하고,
`GraphOperations`와 `GraphSuspendOperations`를 Ktor `Application` / `ApplicationCall` extension으로 노출합니다.

## 아키텍처

![graph ktor Architecture diagram](../../docs/images/readme-diagrams/ktor-graph-ktor-architecture-01.png)

`graph-ktor`는 Ktor application에 명시적으로 선택한 backend 하나를 설치하고, 해석된 graph state를 application attribute에 저장합니다:

- `install(GraphPlugin) { ... }`은 sync/suspend graph facade를 구성하는 backend helper가 없으면 install 시점에 실패합니다.
- Backend helper는 caller-owned resource를 감싸거나 managed DSL을 통해 plugin-owned driver/pool을 생성합니다.
- `GraphPluginState`는 `GraphOperations`와 `GraphSuspendOperations`를 함께 노출합니다.
- `Application` / `ApplicationCall` extension은 Ktor attribute에서 state를 읽으며, route handler에서는 suspend facade를 우선 사용합니다.
- `ApplicationStopped`에서는 등록된 close action만 실행하므로 caller-owned driver와 `DataSource`는 plugin lifecycle 밖에 남습니다.

## 주요 기능

- Ktor `createApplicationPlugin(...)` 기반 integration.
- Production 오동작 방지를 위한 explicit backend selection.
- `Application.graphOperations()` / `Application.graphSuspendOperations()`.
- Route handler용 `ApplicationCall.graphOperations()` / `ApplicationCall.graphSuspendOperations()`.
- TinkerGraph, Neo4j, Memgraph, Apache AGE, FalkorDB backend helper.
- Neo4j, Memgraph, FalkorDB, Apache AGE용 managed property DSL.
- Plugin-owned resource lifecycle cleanup.

## 의존성

`graph-ktor`와 실제 application에서 사용할 backend module을 함께 선언합니다.

```kotlin
dependencies {
    implementation("io.github.bluetape4k.graph:bluetape4k-graph-ktor")
    implementation("io.github.bluetape4k.graph:bluetape4k-graph-tinkerpop") // 또는 graph-neo4j, graph-age, ...
    implementation("io.ktor:ktor-server-core")
    implementation("com.zaxxer:HikariCP") // ageDataSource { ... } 사용 시 필요
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

`ageDataSource { ... }`는 Hikari 기반 pool을 생성하고 Exposed
`Database.connect(dataSource)`를 호출한 뒤, application stop 시 plugin이 생성한 pool만 닫습니다.
외부 DI container가 Exposed `Database`, `DataSource`, transaction manager lifecycle을 이미 소유한다면
기존 `age(graphName)` helper를 사용합니다.

## Backend 참고

| Backend | Helper | Lifecycle |
|---|---|---|
| TinkerGraph | `tinkerGraph()` | Plugin이 in-memory graph delegate를 생성하고 닫습니다. |
| Neo4j | `neo4j(driver, database)` | Driver는 caller-owned이며 plugin이 닫지 않습니다. |
| Neo4j | `neo4j { uri; username; password; database }` | Plugin이 driver를 생성하고 닫습니다. |
| Memgraph | `memgraph(driver, database)` | Driver는 caller-owned이며 plugin이 닫지 않습니다. |
| Memgraph | `memgraph { uri; username; password; database }` | Plugin이 driver를 생성하고 닫습니다. |
| Apache AGE | `age(graphName)` | Graph 사용 전에 caller가 Exposed `Database.connect(...)`를 호출해야 합니다. |
| Apache AGE | `ageDataSource { jdbcUrl; username; password; graphName; connectionInitSql }` | Plugin이 Hikari pool을 생성하고 Exposed를 연결한 뒤 생성한 pool만 닫습니다. |
| FalkorDB | `falkorDB(driver, graphName)` | Driver는 caller-owned이며 plugin이 닫지 않습니다. |
| FalkorDB | `falkorDB { host; port; username; password; graphName }` | Plugin이 driver를 생성하고 닫습니다. |

## 테스트

```bash
./gradlew :graph-ktor:test
```

테스트에는 Ktor `testApplication` 검증과 Neo4j, Memgraph, Apache AGE, FalkorDB helper wiring을
확인하는 작은 Testcontainers smoke가 포함됩니다. 이 backend runtime 검증에는 Docker가 필요합니다.
