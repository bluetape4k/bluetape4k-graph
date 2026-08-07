# 핵심 모델

![핵심 추상화 지도](../../assets/architecture/core-abstraction-map.png)

`GraphElementId`는 비어 있지 않은 문자열을 감싼 값 클래스다. Long이나 드라이버 ID를 공통 형태로 바꾸지만, 애플리케이션은 그 값을 해석하지 말고 불투명한 식별자로 다뤄야 한다. 구현과 검증: [`GraphElementId.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-core/src/main/kotlin/io/bluetape4k/graph/model/GraphElementId.kt), [`GraphElementIdTest.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-core/src/test/kotlin/io/bluetape4k/graph/model/GraphElementIdTest.kt).

`GraphVertex(id, label, properties)`와 `GraphEdge(id, label, startId, endId, properties)`는 불변 스냅샷이다. 속성 값은 null일 수 있지만 실제로 저장할 수 있는 형식은 백엔드와 파일 형식에 따라 달라진다. 소스: [`GraphVertex.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-core/src/main/kotlin/io/bluetape4k/graph/model/GraphVertex.kt), [`GraphEdge.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-core/src/main/kotlin/io/bluetape4k/graph/model/GraphEdge.kt).

`GraphPath`는 `PathStep.VertexStep`과 `PathStep.EdgeStep`을 담는다. `vertices`, `edges`, `length`, `totalWeight`는 여기서 계산한다. 정점만 넘겨 만든 경로에는 간선이 저절로 생기지 않는다. [`GraphPath.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-core/src/main/kotlin/io/bluetape4k/graph/model/GraphPath.kt)와 [`GraphPathTest.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-core/src/test/kotlin/io/bluetape4k/graph/model/GraphPathTest.kt)를 함께 보자.

```kotlin
val id = GraphElementId.of("person:42")
val person = GraphVertex(id, "Person", mapOf("name" to "Ada"))
```

반환 객체를 살아 있는 엔티티처럼 수정하지 않는다. 변경은 repository 메서드로 기록하고, 가져오기 파일의 외부 ID와 백엔드 ID를 분리한다.

## 만들고 확인한다

```kotlin
TinkerGraphOperations().use { ops ->
    val a: GraphVertex = ops.createVertex("Person", mapOf("name" to "Alice"))
    val b: GraphVertex = ops.createVertex("Person", mapOf("name" to "Bob"))
    val e: GraphEdge = ops.createEdge(a.id, b.id, "KNOWS", mapOf("since" to 2024))
    val p: GraphPath? = ops.shortestPath(a.id, b.id, PathOptions(edgeLabel = "KNOWS", maxDepth = 1))
    check(e.startId == a.id && e.endId == b.id)
    check(p?.length == 1 && p.vertices.map { it.id } == listOf(a.id, b.id))
}
```

백엔드가 만든 ID는 비어 있지 않아야 하고, 간선 방향은 Alice에서 Bob으로 유지돼야 한다. 경로에는 정점 둘과 간선 하나가 나온다. `GraphSuspendOperations`에서도 같은 의미를 확인하되 `Flow` 반환은 소유한 코루틴 범위 안에서 소비한다.

## 잘못된 가정을 진단한다

`GraphElementId.of("")`는 백엔드 질의 전에 실패해야 한다. import 간선의 끝점을 못 찾았다고 ID 문자열을 해석하지 말고 외부-ID map을 확인한다.

```bash
./gradlew :bluetape4k-graph-core:test --tests '*GraphElementIdTest' --tests '*GraphPathTest'
```
