# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# 전체 빌드 (테스트 제외)
./gradlew build -x test

# 전체 테스트 실행
./gradlew test

# 특정 모듈만 빌드
./gradlew :graph-neo4j:build

# 예시 모듈 테스트
./gradlew :code-graph-examples:test
./gradlew :linkedin-graph-examples:test

# 특정 테스트 클래스 실행
./gradlew :graph-neo4j:test --tests "io.bluetape4k.graph.neo4j.Neo4jGraphOperationsTest"
./gradlew :code-graph-examples:test --tests "io.bluetape4k.graph.examples.code.Neo4jCodeGraphTest"

# 특정 테스트 메서드 실행
./gradlew :graph-neo4j:test --tests "io.bluetape4k.graph.neo4j.Neo4jGraphOperationsTest.addVertex"

# 로컬 Maven 저장소에 배포
./gradlew publishBluetapeGraphPublicationToMavenLocalRepository

# Maven Central 배포
./gradlew clean build
./gradlew publishAggregationToCentralPortal
```

## Project Structure

멀티모듈 Gradle 프로젝트. `graph/`는 라이브러리 모듈, `examples/`는 사용 예시.

```
graph/
  graph-core/       # 추상 모델 & 인터페이스 (다른 모듈의 기반)
  graph-age/        # Apache AGE (PostgreSQL extension) 구현
  graph-neo4j/      # Neo4j Java Driver 기반 구현
  graph-memgraph/   # Memgraph (Neo4j 프로토콜 호환) 구현
  graph-tinkerpop/  # Apache TinkerPop/Gremlin 구현
  graph-servers/    # 테스트용 Testcontainers 서버 팩토리 (Neo4j, Memgraph, PostgreSQL+AGE)
examples/
  code-graph-examples/     # 코드 의존성 그래프 예시 (AGE, Neo4j, Memgraph, TinkerGraph 통합)
  linkedin-graph-examples/ # LinkedIn 소셜 그래프 예시 (AGE, Neo4j, Memgraph, TinkerGraph 통합)
```

### 예시 모듈 테스트 패턴

`examples/` 모듈은 **추상 테스트 클래스 패턴**을 사용한다. 공통 테스트 로직은 `Abstract*Test`에, 백엔드별 `ops` 설정은 구체 클래스에서 오버라이드한다.

```kotlin
// 구체 클래스는 ops와 서버 라이프사이클만 구현
class Neo4jCodeGraphTest : AbstractCodeGraphTest() {
    override val ops = Neo4jGraphOperations(Neo4jServer.instance.driver)
}
```

| 추상 클래스 | 구체 클래스 |
|------------|------------|
| `AbstractCodeGraphTest` | `Neo4j/Memgraph/TinkerGraph/AgeCodeGraphTest` |
| `AbstractCodeGraphSuspendTest` | `Neo4j/Memgraph/TinkerGraph/AgeCodeGraphSuspendTest` |
| `AbstractLinkedInGraphTest` | `Neo4j/Memgraph/TinkerGraph/AgeLinkedInGraphTest` |
| `AbstractLinkedInGraphSuspendTest` | `Neo4j/Memgraph/TinkerGraph/AgeLinkedInGraphSuspendTest` |

## Architecture

### 핵심 추상화 (`graph-core`)

**이중 API 패턴**: 모든 주요 인터페이스가 동기(`Graph*`) + 코루틴(`GraphSuspend*`) 쌍으로 존재한다.

```
GraphOperations = GraphSession + GraphVertexRepository + GraphEdgeRepository + GraphTraversalRepository
GraphSuspendOperations = GraphSuspendSession + GraphSuspendVertexRepository + ...
```

**모델 타입:**
- `GraphVertex(id, label, properties)` — 정점
- `GraphEdge(id, label, startId, endId, properties)` — 간선
- `GraphPath` — 경로 (정점 + 간선 리스트)
- `GraphElementId` — 백엔드 독립적 ID (`String` 기반)

**스키마 DSL** (`VertexLabel`, `EdgeLabel`): Exposed Table 스타일로 스키마를 선언하고 각 백엔드에서 활용.

```kotlin
object PersonLabel : VertexLabel("Person") {
    val name = string("name")
    val age = integer("age")
}
```

### 백엔드 구현

| 모듈 | 드라이버 | 쿼리 언어 |
|------|---------|---------|
| `graph-neo4j` | Neo4j Java Driver | Cypher |
| `graph-memgraph` | Neo4j Java Driver (호환) | Cypher |
| `graph-age` | PostgreSQL JDBC + Exposed | Cypher-over-SQL (AGE) |
| `graph-tinkerpop` | TinkerGraph (인메모리) | Gremlin |

### 테스트 패턴

모든 통합 테스트는 `graph-servers`의 Testcontainers 싱글턴을 사용한다.

```kotlin
// 공유 컨테이너 (테스트 간 재사용)
val driver = GraphDatabase.driver(Neo4jServer.boltUrl, AuthTokens.none())
val ops = Neo4jGraphOperations(driver)
```

테스트는 `@TestInstance(PER_CLASS)` + `@BeforeAll`/`@AfterAll`로 컨테이너 라이프사이클을 관리한다.

## Key Conventions

- **Kotlin 2.3 + Java 25** (preview 기능 활성화 — `--enable-preview`)
- **코루틴 기본**: suspend 함수와 Flow 우선 설계; 동기 API는 호환성용
- **로깅**: `KLogging` companion object (`bluetape4k-logging` 패턴)
- **의존성 버전**: `buildSrc/src/main/kotlin/Libs.kt`에서 관리
- `examples/` 모듈은 Maven Central 배포 대상에서 제외됨
- 테스트 병렬 실행 시 `testMutex` BuildService로 순차 실행 강제 (컨테이너 충돌 방지)
