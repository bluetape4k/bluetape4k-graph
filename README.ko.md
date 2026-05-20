# bluetape4k-graph

[![CI](https://github.com/bluetape4k/bluetape4k-graph/actions/workflows/ci.yml/badge.svg)](https://github.com/bluetape4k/bluetape4k-graph/actions/workflows/ci.yml)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3-7F52FF?logo=kotlin)](https://kotlinlang.org)
[![JVM](https://img.shields.io/badge/JVM-21-ED8B00?logo=openjdk)](https://openjdk.org)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

![bluetape4k-graph workbench](docs/assets/bluetape4k-graph-workbench.png)

bluetape4k 생태계의 그래프 데이터베이스 통합 라이브러리. Apache AGE, Neo4j, Memgraph, Apache TinkerPop, FalkorDB를 단일 Kotlin API로 사용할 수 있게 하고, 벌크 임포트/익스포트, Ktor 통합, Spring Boot 4 자동 설정, 예제, 벤치마크, 의존성 정렬용 BOM을 함께 제공한다.

> 🇺🇸 [English](README.md)

## 프로젝트가 제공하는 것

`bluetape4k-graph`는 애플리케이션 코드가 그래프 작업을 한 번 정의하고 여러 그래프 데이터베이스에서 실행할 수 있게 한다. 핵심 모듈은 안정적인 도메인 모델, repository 계약, traversal API, schema DSL, optional capability 인터페이스를 담당하고, 백엔드 모듈은 이를 각 데이터베이스의 드라이버와 쿼리 모델로 변환한다.

이 프로젝트는 다음 경우에 적합하다.

- Neo4j, Memgraph, AGE, TinkerGraph, FalkorDB 사이를 이동할 수 있는 공통 그래프 추상화가 필요할 때
- blocking API와 coroutine API를 함께 제공하고, blocking 백엔드에 virtual-thread adapter를 붙이고 싶을 때
- batch insert, schema/index 관리, merge/upsert, transaction block, weighted path, graph algorithm 같은 공통 기능이 필요할 때
- CSV, NDJSON, GraphML, OkIO stream 기반의 이식 가능한 그래프 벌크 I/O가 필요할 때
- code graph, social graph, fraud detection, recommendation, knowledge graph, Ktor integration 예제를 바로 실행해 보고 싶을 때

## 아키텍처

![graph Architecture diagram](docs/images/readme-diagrams/bluetape4k-graph-architecture-01.png)

![graph Class Structure 2 diagram](docs/images/readme-diagrams/bluetape4k-graph-class-02.png)

## 지원하는 그래프 데이터베이스

| Database | Module | Query model | Local testing | 적합한 용도 |
|----------|--------|-------------|---------------|-------------|
| Neo4j | `graph-neo4j` | Neo4j Java Driver 기반 Cypher | Testcontainers `neo4j:5` | 성숙한 도구, index, transaction, Cypher 지원이 필요한 production graph database |
| Memgraph | `graph-memgraph` | Neo4j-compatible protocol 기반 Cypher | Testcontainers `memgraph/memgraph` | 낮은 지연 시간의 graph workload와 Neo4j 유사 개발 경험 |
| Apache AGE | `graph-age` | PostgreSQL/JDBC 기반 Cypher-over-SQL | Testcontainers `apache/age:PG16_latest` | 별도 graph server 없이 PostgreSQL 중심 배포에서 graph modeling이 필요할 때 |
| TinkerPop / TinkerGraph | `graph-tinkerpop` | Gremlin | JVM in-memory graph, 외부 서비스 불필요 | 빠른 테스트, 예제, 로컬 데모, Gremlin traversal |
| FalkorDB | `graph-falkordb` | Redis module 위 openCypher subset | Testcontainers `falkordb/falkordb:v4.18.1` | Redis 기반 graph workload와 가벼운 graph service 배포 |

Amazon Neptune은 별도 future backend 작업으로 추적한다. 의미 있는 지원은 local/integration test 가능성에 달려 있으므로, `graph-neptune` 구현 전에 feasibility research를 먼저 진행한다.

## 모듈 구조

```
bom/             # module version 정렬용 BOM project (`bluetape4k-graph-bom`)
graph/
  graph-core       # 백엔드 독립 모델·인터페이스 (모든 모듈의 기반)
  graph-age        # Apache AGE (PostgreSQL 그래프 확장) 구현
  graph-neo4j      # Neo4j Java Driver 구현
  graph-memgraph   # Memgraph (Neo4j 프로토콜 호환) 구현
  graph-tinkerpop  # Apache TinkerPop / TinkerGraph 인메모리 구현
  graph-falkordb   # FalkorDB (Redis 기반) 구현 — jfalkordb 0.7.0
graph-io/
  core             # 공유 계약·모델·옵션·헬퍼
  csv              # CSV 벌크 임포트/익스포트 (Sync / VirtualThread / Coroutine)
  jackson2         # Jackson 2.x NDJSON 벌크 임포트/익스포트
  jackson3         # Jackson 3.x NDJSON 벌크 임포트/익스포트
  graphml          # GraphML (XML/StAX) 벌크 임포트/익스포트
  okio             # OkIO streaming, compression chaining, DAEAD encryption
benchmark/
  graph-benchmark     # JMH 벤치마크 — Sync vs VirtualThread 그래프 연산
  graph-io-benchmark  # JMH 벤치마크 — CSV / NDJSON / GraphML 벌크 I/O 성능
  graph-age-benchmark # Apache AGE backend benchmark
  graph-neo4j-benchmark # Neo4j backend benchmark
spring-boot/
  graph-spring-boot  # Spring Boot 4.x AutoConfiguration
ktor/
  graph-ktor                  # Ktor 3.x ApplicationPlugin integration
examples/
  code-graph-examples     # 코드 의존성 그래프 예시 (AGE, Neo4j, Memgraph, TinkerGraph, FalkorDB 통합)
  fraud-detection-examples # transaction fraud graph 예시
  knowledge-graph-examples # document/entity knowledge graph 예시
  linkedin-graph-examples # LinkedIn 소셜 그래프 예시 (AGE, Neo4j, Memgraph, TinkerGraph, FalkorDB 통합)
  recommendation-examples # product/user recommendation graph 예시
  ktor-graph-examples     # TinkerGraph 기반 Ktor GraphPlugin 예시
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

### 배치 삽입 API

`GraphVertexRepository.createVertices(...)`와 `GraphEdgeRepository.createEdges(...)`는 여러 그래프 요소를 한 번의 호출로 생성합니다. 기본 인터페이스 메서드는 단건 API를 반복 호출해 소스 호환성을 유지하고, Neo4j, Memgraph, FalkorDB, AGE, TinkerGraph는 백엔드별 배치 경로를 제공합니다.

반환 순서는 입력 순서와 같습니다. 네이티브 간선 배치는 쓰기 전에 엔드포인트를 검증하므로, 누락된 엔드포인트가 있으면 일부 간선을 남기지 않고 전체 배치가 실패합니다.

```kotlin
val people = ops.createVertices(
    "Person",
    listOf(
        mapOf("name" to "Alice"),
        mapOf("name" to "Bob"),
    )
)

val edges = ops.createEdges(
    "KNOWS",
    listOf(BatchEdge(people[0].id, people[1].id, mapOf("since" to 2026)))
)
```

동기, suspend, Virtual Thread 어댑터 모두 같은 계약(`createVertices`, `createEdges`, `createVerticesAsync`, `createEdgesAsync`)을 제공합니다. 1만 건 삽입 smoke 근거는 [2026-05 테스트 로그](docs/testlogs/2026-05.md)에 기록되어 있습니다.

### Schema, Merge, Transaction capabilities

`graph-core`는 base `GraphOperations` source contract를 깨지 않으면서 backend가 더 강한 write-time guarantee를
제공할 수 있도록 optional capability interfaces를 정의한다.

| Capability | API | 목적 |
|------------|-----|------|
| Schema manager | `ops.schemaManager()` / `suspendOps.schemaManager()` | 공통 metadata model로 index와 constraint 생성·조회·삭제 |
| Merge / Upsert | `ops.mergeVertex(...)`, `ops.mergeEdge(...)` | 안정적인 `matchProperties` 기반 idempotent vertex/edge write |
| Transaction DSL | `ops.transaction { }`, `suspendOps.suspendTransaction { }` | repository-style vertex/edge work block을 atomic하게 실행 |

```kotlin
import io.bluetape4k.graph.repository.mergeVertex
import io.bluetape4k.graph.repository.transaction
import io.bluetape4k.graph.schema.schemaManager

ops.schemaManager().createIndex("Person", "email")

val alice = ops.mergeVertex(
    label = "Person",
    matchProperties = mapOf("email" to "alice@example.com"),
    setProperties = mapOf("name" to "Alice"),
)

val edge = ops.transaction {
    val bob = createVertex("Person", mapOf("email" to "bob@example.com"))
    createEdge(alice.id, bob.id, "KNOWS")
}
```

## 벌크 임포트/익스포트 (`graph-io`)

`graph-io` 패밀리는 포맷 독립적인 대용량 I/O를 세 가지 실행 모델(Sync, VirtualThread, Coroutine)로 제공한다.

```kotlin
// CSV 익스포트 — 동기
val sink = CsvGraphExportSink(
    GraphExportSink.PathSink(Path.of("vertices.csv")),
    GraphExportSink.PathSink(Path.of("edges.csv"))
)
CsvGraphBulkExporter().exportGraph(sink, ops, GraphExportOptions(
    vertexLabels = setOf("Person"),
    edgeLabels   = setOf("KNOWS")
))

// Jackson2 NDJSON 익스포트 — Virtual Thread
Jackson2NdJsonVirtualThreadBulkExporter()
    .exportGraphAsync(GraphExportSink.PathSink(Path.of("graph.ndjson")), ops, options)
    .get()

// GraphML 익스포트 — 코루틴 suspend
SuspendGraphMlBulkExporter().exportGraphSuspending(
    GraphExportSink.PathSink(Path.of("graph.graphml")), suspendOps, options
)
```

임포터는 `GraphImportOptions.batchSize`를 백엔드 쓰기 플러시 크기로 사용합니다. 대기 중인 정점과 간선을 라벨별로 묶어 `createVertices`/`createEdges`로 플러시하며, 중복 ID와 누락 엔드포인트 정책의 의미는 그대로 유지됩니다.

| 모듈 | 포맷 | 문서 |
|------|------|------|
| `graph-io-core` | 공유 계약·모델·옵션·헬퍼 (`GraphBulkImporter`, `GraphBulkExporter`, `GraphIoPaths`, `GraphIoExternalIdMap`) | [README](graph-io/core/README.ko.md) |
| `graph-io-csv` | CSV (정점/간선 분리 파일) | [README](graph-io/csv/README.ko.md) |
| `graph-io-jackson2` | NDJSON (Jackson 2.x) | [README](graph-io/jackson2/README.ko.md) |
| `graph-io-jackson3` | NDJSON (Jackson 3.x) | [README](graph-io/jackson3/README.ko.md) |
| `graph-io-graphml` | GraphML XML (StAX) | [README](graph-io/graphml/README.ko.md) |
| `graph-okio` | OkIO 기반 통합 어댑터 — 세그먼트 스트리밍, 압축 체이닝, FakeFileSystem 지원, DAEAD chunk encryption | [README](graph-io/okio/README.ko.md) |

> **벤치마크 결과**: [2026-04-18 graph-io 벌크 I/O 결과](docs/benchmark/2026-04-18-graph-io-bulk-results.md)

---

## 의존성 추가

### BOM (권장)

```kotlin
// build.gradle.kts
dependencyManagement {
    imports {
        mavenBom("io.github.bluetape4k.graph:bluetape4k-graph-bom:<version>")
    }
}

dependencies {
    implementation("io.github.bluetape4k.graph:bluetape4k-graph-neo4j")   // 버전 생략 가능
    implementation("io.github.bluetape4k.graph:bluetape4k-graph-age")
}
```

### 개별 모듈

```kotlin
dependencies {
    implementation("io.github.bluetape4k.graph:bluetape4k-graph-core:<version>")
    implementation("io.github.bluetape4k.graph:bluetape4k-graph-neo4j:<version>")
    // graph-age | graph-memgraph | graph-tinkerpop | graph-ktor
}
```

## 빠른 시작

### Neo4j

```kotlin
val driver = GraphDatabase.driver(Neo4jServer.Launcher.neo4j.boltUrl, AuthTokens.none())
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
val ops = AgeGraphOperations("my_graph")

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

### FalkorDB (Redis 기반)

```kotlin
import com.falkordb.FalkorDB
import io.bluetape4k.graph.falkordb.FalkorDBGraphOperations

val driver = FalkorDB.driver("localhost", 6379)
val ops = FalkorDBGraphOperations(driver, graphName = "social")

val alice = ops.createVertex("Person", mapOf("name" to "Alice", "age" to 30))
val bob   = ops.createVertex("Person", mapOf("name" to "Bob",   "age" to 25))
ops.createEdge(alice.id, bob.id, "KNOWS", mapOf("since" to 2024))

val path = ops.shortestPath(alice.id, bob.id, "KNOWS", maxDepth = 5)
driver.close()
```

## 백엔드 비교

| 항목 | graph-age | graph-neo4j | graph-memgraph | graph-tinkerpop | graph-falkordb |
|------|-----------|-------------|----------------|-----------------|----------------|
| 쿼리 언어 | Cypher-over-SQL | Cypher | Cypher | Gremlin | openCypher (부분집합) |
| 인프라 | PostgreSQL + AGE | Neo4j | Memgraph | JVM 인메모리 | Redis 모듈 |
| 드라이버 | JDBC + Exposed | Neo4j Java Driver | Neo4j Java Driver (호환) | TinkerPop | jfalkordb 0.7.0 |
| 테스트 컨테이너 | `apache/age:PG16_latest` | `neo4j:5` | `memgraph/memgraph:latest` | 불필요 | `falkordb/falkordb:v4.18.1` |
| 가장 강한 로컬 역할 | PostgreSQL-native graph | 성숙한 graph server | low-latency Cypher server | unit/integration tests | Redis-backed graph service |

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
./gradlew :fraud-detection-examples:test
./gradlew :recommendation-examples:test
./gradlew :knowledge-graph-examples:test

# 특정 클래스
./gradlew :graph-neo4j:test --tests "io.bluetape4k.graph.neo4j.Neo4jGraphOperationsTest"
```

GitHub Actions는 전용 `Examples` workflow도 실행한다. 이 workflow는 매일 한 번, 그리고 example, graph, graph-io, Ktor, Gradle, workflow 파일 변경 시 실행된다. 예제 검증은 Nightly에서 제외해 backend/integration smoke coverage와 별도 신호로 관리한다.

## 예시 모듈 구조 (`examples/`)

각 예시 모듈은 **추상 테스트 클래스 패턴**을 사용한다. 공통 테스트 로직은 한 곳에, 백엔드별 설정만 구체 클래스에서 오버라이드한다.

| 추상 클래스 | 구체 클래스 (백엔드) |
|------------|---------------------|
| `AbstractCodeGraphTest` | `Neo4j/Memgraph/TinkerGraph/Age/FalkorDBCodeGraphTest` |
| `AbstractCodeGraphSuspendTest` | `Neo4j/Memgraph/TinkerGraph/Age/FalkorDBCodeGraphSuspendTest` |
| `AbstractFraudDetectionTest` | fraud ring, merchant, card, transaction graph 예시 |
| `AbstractKnowledgeGraphTest` | document/entity/relation knowledge graph 예시 |
| `AbstractLinkedInGraphTest` | `Neo4j/Memgraph/TinkerGraph/Age/FalkorDBLinkedInGraphTest` |
| `AbstractLinkedInGraphSuspendTest` | `Neo4j/Memgraph/TinkerGraph/Age/FalkorDBLinkedInGraphSuspendTest` |
| `AbstractRecommendationTest` | user/product/category recommendation graph 예시 |
| `KtorGraphAppTest` | TinkerGraph 기반 Ktor `GraphPlugin` smoke 예시 |
| `FalkorDBKtorGraphAppTest` | FalkorDB 기반 Ktor `GraphPlugin` smoke 예시 |

구체 클래스는 `ops` (`GraphOperations` 또는 `GraphSuspendOperations`) 와 서버 라이프사이클(`@BeforeAll`/`@AfterAll`)만 구현하면 된다.

## 요구 사항

- Java 21 (preview 기능 활성화)
- Kotlin 2.3
- Docker (통합 테스트용)

## 기술 스택

- **Kotlin** 2.3 + Coroutines 1.10
- **Neo4j Java Driver** 5.x
- **JetBrains Exposed** (Apache AGE용 JDBC)
- **Apache TinkerPop** (Gremlin)
- **Ktor** 3.x (ApplicationPlugin integration)
- **jfalkordb** 0.7.0 (FalkorDB / Redis 모듈 그래프)
- **Testcontainers** (통합 테스트)
- **bluetape4k** 1.7.x (공통 유틸리티)

## 문서

- [Graph Database 장단점 및 선택 가이드](docs/graphdb-tradeoffs.md) — GraphDB의 장단점과 bluetape4k-graph 백엔드(Neo4j, Memgraph, AGE, TinkerPop) 선택 가이드
