# graph-tinkerpop

Apache TinkerPop Gremlin 기반 `GraphOperations` / `GraphSuspendOperations` 구현 모듈.

> 🇺🇸 [English](README.md)

## 개요

TinkerGraph(in-memory JVM 그래프 DB)를 사용하여 `graph-core` 인터페이스를 구현한다.
외부 서버 없이 단독 실행 가능하므로 테스트, 프로토타이핑에 적합하다.

## 주요 클래스

| 클래스 | 설명 |
|--------|------|
| `TinkerGraphOperations` | 동기(blocking) 방식 구현 |
| `TinkerGraphSuspendOperations` | 코루틴(suspend + Flow) 방식 구현 |
| `GremlinRecordMapper` | TinkerPop Vertex/Edge/Path -> GraphVertex/GraphEdge/GraphPath 변환 |

## 의존성

```kotlin
dependencies {
    api(project(":graph-core"))
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

## 그래프 알고리즘

TinkerPop은 6개 알고리즘 모두 Gremlin 네이티브 순회로 구현 — JVM 폴백 불필요.

### 알고리즘 지원 매트릭스

| 알고리즘 | 구현 방식 |
|----------|-----------|
| `degreeCentrality` | Gremlin native (`g.V().bothE().count()`) |
| `bfs` | Gremlin native (`repeat().breadthFirst()`) |
| `dfs` | Gremlin native (`repeat().depthFirst()`) |
| `detectCycles` | Gremlin native (cycle-path 탐지) |
| `connectedComponents` | Gremlin native (`connectedComponent()` step) |
| `pageRank` | Gremlin native (`pageRank()` step) |

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
