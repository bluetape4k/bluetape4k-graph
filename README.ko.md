# bluetape4k-graph

bluetape4k 생태계의 그래프 데이터베이스 통합 라이브러리. Apache AGE, Neo4j, Memgraph, Apache TinkerPop을 단일 추상 API로 사용할 수 있도록 한다.

> 🇺🇸 [English](README.md)

## 모듈 구조

```
graph/
  graph-core       # 백엔드 독립 모델·인터페이스 (모든 모듈의 기반)
  graph-age        # Apache AGE (PostgreSQL 그래프 확장) 구현
  graph-neo4j      # Neo4j Java Driver 구현
  graph-memgraph   # Memgraph (Neo4j 프로토콜 호환) 구현
  graph-tinkerpop  # Apache TinkerPop / TinkerGraph 인메모리 구현
  graph-servers    # 테스트용 Testcontainers 서버 팩토리
examples/
  code-graph-examples     # 코드 의존성 그래프 예시 (AGE, Neo4j, Memgraph, TinkerGraph 통합)
  linkedin-graph-examples # LinkedIn 소셜 그래프 예시 (AGE, Neo4j, Memgraph, TinkerGraph 통합)
```

## 핵심 추상화 (`graph-core`)

모든 백엔드 구현이 공통으로 준수하는 인터페이스 계층.

### 이중 API 패턴

동기(blocking)와 코루틴(suspend/Flow) API를 함께 제공한다.

```
GraphOperations        = GraphSession + GraphVertexRepository + GraphEdgeRepository + GraphTraversalRepository
GraphSuspendOperations = GraphSuspendSession + ... (suspend 함수 버전)
```

### 도메인 모델

```kotlin
data class GraphVertex(val id: GraphElementId, val label: String, val properties: Map<String, Any?>)
data class GraphEdge(val id: GraphElementId, val label: String, val startId: GraphElementId, val endId: GraphElementId, val properties: Map<String, Any?>)
data class GraphPath(val steps: List<PathStep>)   // VertexStep | EdgeStep
```

### 스키마 DSL

Exposed Table 스타일의 선언적 스키마 정의. 백엔드에 무관하게 동작한다.

```kotlin
object PersonLabel : VertexLabel("Person") {
    val name = string("name")
    val age  = integer("age")
}

object KnowsLabel : EdgeLabel("KNOWS") {
    val since = localDate("since")
}
```

## 의존성 추가

### BOM (권장)

```kotlin
// build.gradle.kts
dependencyManagement {
    imports {
        mavenBom("io.github.bluetape4k.graph:bluetape4k-graph-bom:0.0.1")
    }
}

dependencies {
    implementation("io.github.bluetape4k.graph:graph-neo4j")   // 버전 생략 가능
    implementation("io.github.bluetape4k.graph:graph-age")
}
```

### 개별 모듈

```kotlin
dependencies {
    implementation("io.github.bluetape4k.graph:graph-core:0.0.1")
    implementation("io.github.bluetape4k.graph:graph-neo4j:0.0.1")
    // graph-age | graph-memgraph | graph-tinkerpop
}
```

## 빠른 시작

### Neo4j

```kotlin
val driver = GraphDatabase.driver(Neo4jServer.boltUrl, AuthTokens.none())
val ops = Neo4jGraphOperations(driver)

val alice = ops.createVertex("Person", mapOf("name" to "Alice", "age" to 30))
val bob   = ops.createVertex("Person", mapOf("name" to "Bob",   "age" to 28))
ops.createEdge(alice.id, bob.id, "KNOWS", mapOf("since" to LocalDate.now()))

val path = ops.shortestPath(alice.id, bob.id, "KNOWS", maxDepth = 5)
```

### Apache AGE (PostgreSQL)

```kotlin
val hikariConfig = HikariConfig().apply {
    jdbcUrl = "jdbc:postgresql://localhost:5432/postgres"
    connectionInitSql = "LOAD 'age'; SET search_path = ag_catalog, \"${'$'}user\", public"
}
val db = Database.connect(HikariDataSource(hikariConfig))
val ops = AgeGraphOperations(db, graphName = "my_graph")

ops.createGraph("my_graph")
val alice = ops.createVertex("Person", mapOf("name" to "Alice"))
```

### TinkerPop (인메모리, 외부 서버 불필요)

```kotlin
val ops = TinkerGraphOperations()
val alice = ops.createVertex("Person", mapOf("name" to "Alice"))
val bob   = ops.createVertex("Person", mapOf("name" to "Bob"))
ops.createEdge(alice.id, bob.id, "KNOWS", emptyMap())

val neighbors = ops.neighbors(alice.id, "KNOWS", Direction.OUTGOING, depth = 1)
ops.close()
```

## 백엔드 비교

| 항목 | graph-age | graph-neo4j | graph-memgraph | graph-tinkerpop |
|------|-----------|-------------|----------------|-----------------|
| 쿼리 언어 | Cypher-over-SQL | Cypher | Cypher | Gremlin |
| 인프라 | PostgreSQL + AGE | Neo4j | Memgraph | JVM 인메모리 |
| 드라이버 | JDBC + Exposed | Neo4j Java Driver | Neo4j Java Driver (호환) | TinkerPop |
| 테스트 컨테이너 | `apache/age:PG16_latest` | `neo4j:5` | `memgraph/memgraph:latest` | 불필요 |

## 테스트 실행

테스트는 Testcontainers를 통해 Docker 컨테이너를 자동 실행한다. Docker가 필요하다.

```bash
# 전체 테스트
./gradlew test

# 특정 모듈 테스트
./gradlew :graph-neo4j:test
./gradlew :graph-age:test
./gradlew :code-graph-examples:test
./gradlew :linkedin-graph-examples:test

# 특정 클래스
./gradlew :graph-neo4j:test --tests "io.bluetape4k.graph.neo4j.Neo4jGraphOperationsTest"
```

## 예시 모듈 구조 (`examples/`)

각 예시 모듈은 **추상 테스트 클래스 패턴**을 사용한다. 공통 테스트 로직은 한 곳에, 백엔드별 설정만 구체 클래스에서 오버라이드한다.

| 추상 클래스 | 구체 클래스 (백엔드) |
|------------|---------------------|
| `AbstractCodeGraphTest` | `Neo4j/Memgraph/TinkerGraph/AgeCodeGraphTest` |
| `AbstractCodeGraphSuspendTest` | `Neo4j/Memgraph/TinkerGraph/AgeCodeGraphSuspendTest` |
| `AbstractLinkedInGraphTest` | `Neo4j/Memgraph/TinkerGraph/AgeLinkedInGraphTest` |
| `AbstractLinkedInGraphSuspendTest` | `Neo4j/Memgraph/TinkerGraph/AgeLinkedInGraphSuspendTest` |

구체 클래스는 `ops` (`GraphOperations` 또는 `GraphSuspendOperations`) 와 서버 라이프사이클(`@BeforeAll`/`@AfterAll`)만 구현하면 된다.

## 요구 사항

- Java 25 (preview 기능 활성화)
- Kotlin 2.3
- Docker (통합 테스트용)

## 기술 스택

- **Kotlin** 2.3 + Coroutines 1.10
- **Neo4j Java Driver** 5.x
- **JetBrains Exposed** (Apache AGE용 JDBC)
- **Apache TinkerPop** (Gremlin)
- **Testcontainers** (통합 테스트)
- **bluetape4k** 1.5.x (공통 유틸리티)

## 문서

- [Graph Database 장단점 및 선택 가이드](docs/graphdb-tradeoffs.md) — GraphDB의 장단점과 bluetape4k-graph 백엔드(Neo4j, Memgraph, AGE, TinkerPop) 선택 가이드
