# graph-falkordb

bluetape4k-graph의 FalkorDB 그래프 데이터베이스 백엔드 모듈.

## 개요

[FalkorDB](https://falkordb.com/)는 Redis 모듈 기반 그래프 데이터베이스로 openCypher 쿼리를 지원합니다.
이 모듈은 [jfalkordb](https://github.com/FalkorDB/jfalkordb) 0.7.0 Java 드라이버를 사용하여
`GraphOperations` / `GraphSuspendOperations`의 동기 및 코루틴 구현을 제공합니다.

## 의존성

```kotlin
// build.gradle.kts
dependencies {
    implementation("io.bluetape4k:graph-falkordb:<version>")
}
```

## 사용법

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

### 코루틴 (Suspend) API

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

## 테스트

Testcontainers (`FalkorDBServer`)로 `falkordb/falkordb:v4.18.1` 컨테이너를 자동 실행합니다:

```kotlin
val server = FalkorDBServer.Launcher.falkordb
val driver = FalkorDB.driver(server.host, server.port)
```

```bash
./gradlew :graph-falkordb:test
```

## 주의 사항

- FalkorDB Cypher 부분집합은 `CREATE` 절에서 `$props` map 확장을 **지원하지 않음** — 속성은 개별 named parameter로 전달합니다.
- 노드 ID는 정수형이므로 ID 기반 조회 시 `GraphElementId.value`가 숫자여야 합니다.
- 진입점은 `FalkorDB.driver()`이며, Neo4j Driver를 사용하지 않습니다.
