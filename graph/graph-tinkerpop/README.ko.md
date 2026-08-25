# graph-tinkerpop

Apache TinkerPop Gremlin 기반 `GraphOperations` / `GraphSuspendOperations` 구현 모듈.

> 🇺🇸 [English](README.md)

## 개요

TinkerGraph(in-memory JVM 그래프 DB)를 사용하여 `graph-core` 인터페이스를 구현한다.
외부 서버 없이 단독 실행 가능하므로 테스트, 프로토타이핑에 적합하다.

![graph-tinkerpop architecture](../../docs/images/readme-diagrams/graph-graph-tinkerpop-architecture-01.png)

## 주요 클래스

| 클래스 | 설명 |
|--------|------|
| `TinkerGraphOperations` | 동기(blocking) 방식 구현 |
| `TinkerGraphSuspendOperations` | 코루틴(suspend + Flow) 방식 구현 |
| `TinkerGraphSchemaManager` | 테스트 친화적인 in-memory schema/index metadata manager |
| `GremlinRecordMapper` | TinkerPop Vertex/Edge/Path -> GraphVertex/GraphEdge/GraphPath 변환 |

## 클래스 모델

![graph-tinkerpop class model](../../docs/images/readme-diagrams/graph-graph-tinkerpop-class-02.png)

## 의존성

```kotlin
dependencies {
    api("io.github.bluetape4k.graph:bluetape4k-graph-core:${bluetape4kVersion}")
    api(Libs.tinkerpop_gremlin_core)
    api(Libs.tinkergraph_gremlin)
}
```

## 사용 예시

```kotlin
val ops = TinkerGraphOperations()

// Vertex 생성
val alice = ops.createVertex("Person", mapOf("name" to "Alice"))
val bob = ops.createVertex("Person", mapOf("name" to "Bob"))

// Edge 생성
ops.createEdge(alice.id, bob.id, "KNOWS", mapOf("since" to 2024L))

// 이웃 탐색
val neighbors = ops.neighbors(alice.id, NeighborOptions(edgeLabel = "KNOWS"))

ops.close()
```

## Bounded chunk cursor 수명주기

`findVerticesByLabelChunkedCursor`와 `findEdgesByLabelChunkedCursor`는
`CloseableChunkSequence`를 반환한다. cursor는 chunk마다 최대 `chunkSize`개의
record만 소비하고, 전체 소비·실패·명시적 close에서 underlying TinkerPop
traversal을 닫는다. `take(1)`처럼 부분 소비가 끝나면 cursor를 닫아야 한다.

```kotlin
val cursor = ops.findVerticesByLabelChunkedCursor("Person", chunkSize = 256)
try {
    val firstChunk = cursor.take(1).toList()
} finally {
    cursor.close()
}
```

기존 repository ABI를 위해 `findVerticesByLabelChunked`와
`findEdgesByLabelChunked`는 계속 `Sequence`를 반환한다. 이 interface 타입에는
close handle이 없으므로 조기 close 또는 source memory bound가 caller 계약이면
구체적인 `*Cursor` 메서드를 사용한다. suspend/Flow chunk 메서드는 조기 `take`,
cancellation, downstream failure에서도 `finally`로 cursor를 닫는다.

## Schema / Index Management

TinkerGraph는 durable schema DDL이 없지만 `schemaManager()`가 현재 `TinkerGraphOperations` instance 안에
index metadata를 기록한다. Unique constraint는 강제할 수 없으므로 명시적으로 실패한다.

```kotlin
import io.bluetape4k.graph.schema.schemaManager

val schema = ops.schemaManager()
schema.createIndex("Person", "email")
val indexes = schema.listIndexes()
```

## Merge / Upsert and Transaction DSL

TinkerGraph backend는 Gremlin get-or-create/update 방식의 `GraphMergeOperations`와 in-memory `Transaction DSL`을
제공한다. 외부 DB transaction은 없지만, 테스트와 local prototype에서 동일한 API surface를 검증할 수 있다.

```kotlin
import io.bluetape4k.graph.repository.mergeVertex
import io.bluetape4k.graph.repository.transaction

val alice = ops.mergeVertex(
    label = "Person",
    matchProperties = mapOf("email" to "alice@example.com"),
    setProperties = mapOf("name" to "Alice"),
)

ops.transaction {
    val bob = createVertex("Person", mapOf("email" to "bob@example.com"))
    createEdge(alice.id, bob.id, "KNOWS")
}
```

## 그래프 알고리즘

TinkerPop backend는 그래프 접근에 Gremlin traversal을 사용하고, 표준 TinkerGraph에서 GraphComputer 실행 경로가
필요한 기능은 graph-core의 JVM helper로 처리한다. Weighted shortest path와 A* path도 공통 fallback 구현을 사용한다.

### 알고리즘 지원 매트릭스

| 알고리즘 | 구현 방식 |
|----------|-----------|
| `degreeCentrality` | Gremlin edge count (`inE` / `outE`) |
| `bfs` | Gremlin으로 adjacency를 읽고 JVM helper 실행 |
| `dfs` | Gremlin으로 adjacency를 읽고 JVM helper 실행 |
| `detectCycles` | Gremlin으로 adjacency를 읽고 JVM cycle detector 실행 |
| `connectedComponents` | Gremlin edge를 읽고 JVM `UnionFind` 실행 |
| `pageRank` | JVM `PageRankCalculator` 실행 |

### 사용 예제

```kotlin
val ops = TinkerGraphOperations()

// 모든 알고리즘이 TinkerGraph에서 네이티브 실행 (Docker 불필요)
val degree = ops.degreeCentrality(alice.id, DegreeOptions(edgeLabel = "KNOWS"))
println("in=${degree.inDegree} out=${degree.outDegree}")

val visits = ops.bfs(alice.id, BfsDfsOptions(edgeLabel = "KNOWS", maxDepth = 3))
println("BFS 방문 노드: ${visits.size}")

val components = ops.connectedComponents(ComponentOptions(edgeLabel = "KNOWS"))
println("연결 컴포넌트 수: ${components.size}")

val top10 = ops.pageRank(PageRankOptions(topK = 10))
top10.forEach { println("${it.vertex.label}: ${it.score}") }

// Virtual Thread 사용
val vtOps = ops.asVirtualThread()
val future = vtOps.pageRankAsync()
val scores = future.join()
```
