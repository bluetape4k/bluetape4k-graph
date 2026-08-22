# graph-memgraph

Memgraph 그래프 데이터베이스를 위한 `GraphOperations` / `GraphSuspendOperations` 구현 모듈.

> 🇺🇸 [English](README.md)

## 개요

[Memgraph](https://memgraph.com/)는 Neo4j Bolt 프로토콜과 openCypher를 완전 호환하는 인메모리 그래프 DB다.
`neo4j-java-driver`를 그대로 사용해 연결할 수 있다.

## 아키텍처 다이어그램

![graph-memgraph architecture](../../docs/images/readme-diagrams/graph-graph-memgraph-architecture-01.png)

`graph-memgraph`는 `graph-core` repository 계약을 유지하면서 Neo4j Java Driver, Memgraph Cypher 문법, 숫자형 `id()` 값, schema DDL, JVM algorithm fallback으로 Memgraph에 연결한다.

## 주요 클래스

| 클래스 | 설명 |
|--------|------|
| `MemgraphGraphOperations` | 동기(blocking) 방식 그래프 연산 |
| `MemgraphGraphSuspendOperations` | 코루틴(suspend/Flow) 방식 그래프 연산 |
| `CachingMemgraphGraphOperations` | Caffeine bounded/expiring 기반 캐싱 데코레이터 |
| `MemgraphGraphSchemaManager` | Memgraph index와 unique constraint용 SchemaManager |

## 사용법

```kotlin
import java.time.Duration

val driver = GraphDatabase.driver("bolt://localhost:7687", AuthTokens.none())

// 동기 방식
val ops = MemgraphGraphOperations(driver)
val vertex = ops.createVertex("Person", mapOf("name" to "Alice"))

// 코루틴 방식
val suspendOps = MemgraphGraphSuspendOperations(driver)
val vertex = suspendOps.createVertex("Person", mapOf("name" to "Alice"))
```

## Schema / Index Management

`MemgraphGraphOperations`는 `schemaManager()`를 통해 index와 unique constraint를 생성·조회·삭제할 수 있다.

```kotlin
import io.bluetape4k.graph.schema.schemaManager

val schema = ops.schemaManager()
schema.createIndex("Person", "email")
schema.createUniqueConstraint("Person", "email")
val indexes = schema.listIndexes()
```

## Merge / Upsert and Transaction DSL

Memgraph backend는 Cypher `MERGE` 기반 `GraphMergeOperations`와 repository-style `Transaction DSL`을 지원한다.
`matchProperties`는 vertex identity key로 사용되며, `transaction { }` block은 실패 시 rollback되어야 한다.

```kotlin
import io.bluetape4k.graph.repository.mergeVertex
import io.bluetape4k.graph.repository.transaction

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

## Neo4j와의 차이점

| 항목 | Neo4j | Memgraph |
|------|-------|----------|
| 기본 database 파라미터 | `"neo4j"` | `"memgraph"` |
| `elementId()` 지원 | O (5.x) | O (2.x+) |
| `shortestPath` | O | O |
| 인증 | basic auth | 기본 없음 (AuthTokens.none()) |

## 그래프 알고리즘

Memgraph는 Neo4j Bolt 프로토콜을 공유하므로 동일한 driver API를 사용하지만,
모듈은 `graph-neo4j` 구현 모듈에 의존하지 않습니다. Caffeine cache도
Memgraph 모듈이 직접 implementation dependency로 선언합니다.

### 알고리즘 지원 매트릭스

| 알고리즘 | 구현 방식 |
|----------|-----------|
| `degreeCentrality` | Cypher native (`OPTIONAL MATCH ... count`) |
| `bfs` / `dfs` | JVM fallback (`BfsDfsRunner`) |
| `detectCycles` | Cypher native (variable-length path) |
| `connectedComponents` | JVM fallback (`UnionFind`) |
| `pageRank` | JVM fallback (`PageRankCalculator`) — Memgraph MAGE 모듈은 별도 계획 |

현재 PageRank fallback은 `GraphAlgorithmExecutionObservable.lastAlgorithmExecution`으로
관찰할 수 있으며 provider `jvm-fallback`, 경로 `JVM_FALLBACK`, 이유
`NO_PROVIDER`를 기록한다. 선택적 MAGE 모듈은 dependency-free
`graph-core` provider SPI를 사용할 수 있고, 기본 Memgraph 모듈은 MAGE를
설치하지 않으며 native 결과 실패를 JVM 결과로 조용히 바꾸지 않는다.

### 사용 예제

```kotlin
val driver = GraphDatabase.driver("bolt://localhost:7687", AuthTokens.none())
val ops = MemgraphGraphOperations(driver)

val degree = ops.degreeCentrality(alice.id, DegreeOptions(edgeLabel = "KNOWS"))
val cycles = ops.detectCycles(CycleOptions(edgeLabel = "KNOWS", maxDepth = 5))
val top10  = ops.pageRank(PageRankOptions(vertexLabel = "Person", topK = 10))
```

## 캐싱 데코레이터

`CachingMemgraphGraphOperations`는 `MemgraphGraphOperations`를 Caffeine 기반 bounded/expiring 캐시로 감싸는 데코레이터다.
읽기 결과를 메모이제이션하며 모든 읽기 캐시에 `maxSize` 엔트리 상한과 `expireAfterWrite` TTL을 적용한다.
반복 읽기가 많은 벤치마크 및 워크로드에 적합하다.

### 캐시 동작

| 연산 | 효과 |
|------|------|
| `findVertexById`, `findVerticesByLabel`, `neighbors`, `shortestPath`, `allPaths`, `findEdgesByLabel` | 첫 번째 호출 시 DB 조회 후 캐시 저장, 이후 호출은 캐시 히트 |
| `maxSize`, `expireAfterWrite` | 모든 읽기 캐시에 적용되며 두 값 모두 양수여야 한다 |
| `createVertex`, `createEdge` | 동일 인자라도 매번 기본 연산에 위임하여 새 레코드를 생성합니다. 생성 후 읽기 캐시를 무효화합니다 |
| `updateVertex`, `deleteVertex`, `deleteEdge` | 읽기 캐시 전체를 무효화합니다 |
| `dropGraph` | 먼저 기본 연산에 위임하고 graph 삭제가 성공하면 읽기 캐시 전체를 무효화합니다 |
| `transaction { ... }` | backend transaction capability를 전달하며 commit 후에는 읽기 캐시를 무효화하고 rollback 후에는 기존 캐시를 유지합니다 |

각 cache miss는 delegate 읽기 전에 generation을 캡처합니다. wrapper를 통한 쓰기, `dropGraph`, 또는 commit된 transaction이 읽기 중 generation을 증가시키면 해당 결과를 캐시에 재적재하지 않습니다. 이미 진행 중인 호출은 쓰기 전에 읽은 값을 반환할 수 있으며, 다른 delegate 인스턴스에서 직접 수행한 쓰기는 이 wrapper의 무효화 경계 밖입니다.

### 사용 예제

```kotlin
import java.time.Duration

val driver = GraphDatabase.driver("bolt://localhost:7687", AuthTokens.none())
val baseOps = MemgraphGraphOperations(driver)

// bounded/expiring 캐싱 데코레이터로 감싸기
val ops = CachingMemgraphGraphOperations(
    baseOps,
    maxSize = 1_000,
    expireAfterWrite = Duration.ofMinutes(5),
)

// 첫 번째 조회: DB 호출
val alice = ops.findVertexById("Person", aliceId)

// 두 번째 조회: 캐시 히트
val aliceCached = ops.findVertexById("Person", aliceId)

// 지원하는 쓰기 연산 후 읽기 캐시 자동 무효화
ops.deleteVertex("Person", aliceId)
val afterDelete = ops.findVertexById("Person", aliceId)  // null (캐시 미스 → DB 재조회)
```

## 테스트

Testcontainers를 통해 `memgraph/memgraph:3.12.0` 이미지를 자동으로 실행한다.

```bash
./gradlew :graph-memgraph:test
```
