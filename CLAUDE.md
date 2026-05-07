# CLAUDE.md — bluetape4k-graph

그래프 DB 라이브러리. Neo4j·Memgraph·AGE(PostgreSQL)·TinkerPop·FalkorDB 지원.
동기/코루틴 이중 API 패턴. Spring Boot 3.5 + 4.0 AutoConfiguration 포함.

- **Kotlin**: 2.3 · **Java**: 25 (preview 활성화 — `--enable-preview`)
- **의존성 버전**: `buildSrc/src/main/kotlin/Libs.kt`

## Project Structure

```
graph/
  graph-core/       # 추상 모델 & 인터페이스
  graph-age/        # Apache AGE (PostgreSQL extension)
  graph-neo4j/      # Neo4j Java Driver
  graph-memgraph/   # Memgraph (Neo4j 프로토콜 호환)
  graph-tinkerpop/  # Apache TinkerPop/Gremlin
  graph-falkordb/   # FalkorDB (Redis 기반) — jfalkordb 0.7.0
graph-io/
  core/             # 공유 계약·모델·옵션 (GraphIoPaths: Buffered I/O)
  csv/              # CSV 벌크 임포트/익스포트 × Sync/VT/Suspend
  jackson2/         # Jackson 2.x NDJSON
  jackson3/         # Jackson 3.x NDJSON
  graphml/          # GraphML XML/StAX × Sync/VT/Suspend
benchmark/
  graph-benchmark/    # JMH — Sync vs VirtualThread
  graph-io-benchmark/ # JMH — CSV/NDJSON/GraphML 벌크 I/O
spring-boot3/
  graph-spring-boot3-starter/
spring-boot4/
  graph-spring-boot4-starter/
examples/
  code-graph-examples/     # 코드 의존성 그래프 예시
  linkedin-graph-examples/ # LinkedIn 소셜 그래프 예시
```

## Build Commands

```bash
./gradlew build -x test
./gradlew test
./gradlew :graph-neo4j:build
./gradlew :code-graph-examples:test
./gradlew :graph-neo4j:test --tests "io.bluetape4k.graph.neo4j.Neo4jGraphOperationsTest"
./gradlew publishBluetapeGraphPublicationToMavenLocalRepository   # local
./gradlew publishAggregationToCentralPortal                       # Maven Central
```

## Architecture

### 핵심 추상화 (`graph-core`)

**이중 API 패턴**: 동기(`Graph*`) + 코루틴(`GraphSuspend*`) 쌍.

```
GraphOperations = GraphSession + GraphVertexRepository + GraphEdgeRepository + GraphTraversalRepository
GraphSuspendOperations = GraphSuspendSession + GraphSuspendVertexRepository + ...
```

**모델 타입**: `GraphVertex(id, label, properties)`, `GraphEdge(id, label, startId, endId, properties)`, `GraphPath`, `GraphElementId`

**스키마 DSL** (`VertexLabel`, `EdgeLabel`) — Exposed Table 스타일 선언:

```kotlin
object PersonLabel : VertexLabel("Person") {
    val name = string("name")
    val age = integer("age")
}
```

### 백엔드

| 모듈 | 드라이버 | 쿼리 언어 |
|------|---------|---------|
| `graph-neo4j` | Neo4j Java Driver | Cypher |
| `graph-memgraph` | Neo4j Java Driver (호환) | Cypher |
| `graph-age` | PostgreSQL JDBC + Exposed | Cypher-over-SQL (AGE) |
| `graph-tinkerpop` | TinkerGraph (인메모리) | Gremlin |
| `graph-falkordb` | jfalkordb 0.7.0 (Jedis 기반) | openCypher (부분집합) |

## 테스트 패턴

Testcontainers: `io.bluetape4k.testcontainers.graphdb` 패키지 singleton 사용:

```kotlin
import io.bluetape4k.testcontainers.graphdb.Neo4jServer
val driver = GraphDatabase.driver(Neo4jServer.Launcher.neo4j.boltUrl, AuthTokens.none())
```

**examples 추상 테스트 패턴**: 공통 로직은 `Abstract*Test`, 백엔드별 `ops` 는 구체 클래스 오버라이드:

```kotlin
class Neo4jCodeGraphTest : AbstractCodeGraphTest() {
    private val driver = GraphDatabase.driver(Neo4jServer.Launcher.neo4j.boltUrl, AuthTokens.none())
    override val ops = Neo4jGraphOperations(driver)
    @AfterAll fun teardown() { driver.close() }
}
```

테스트 병렬 실행 시 `testMutex` BuildService 로 순차 실행 강제 (컨테이너 충돌 방지).
`examples/` 모듈은 Maven Central 배포 제외.
