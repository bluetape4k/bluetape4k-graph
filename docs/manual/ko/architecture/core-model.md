# 핵심 모델

`GraphElementId`는 비어 있지 않은 문자열을 감싼 값 클래스다. Long이나 드라이버 ID를 공통 형태로 바꾸지만, 애플리케이션은 그 값을 해석하지 말고 불투명한 식별자로 다뤄야 한다. 구현과 검증: [`GraphElementId.kt`](../../../../graph/graph-core/src/main/kotlin/io/bluetape4k/graph/model/GraphElementId.kt), [`GraphElementIdTest.kt`](../../../../graph/graph-core/src/test/kotlin/io/bluetape4k/graph/model/GraphElementIdTest.kt).

`GraphVertex(id, label, properties)`와 `GraphEdge(id, label, startId, endId, properties)`는 불변 스냅샷이다. 속성 값은 null일 수 있지만 실제로 저장할 수 있는 형식은 백엔드와 파일 형식에 따라 달라진다. 소스: [`GraphVertex.kt`](../../../../graph/graph-core/src/main/kotlin/io/bluetape4k/graph/model/GraphVertex.kt), [`GraphEdge.kt`](../../../../graph/graph-core/src/main/kotlin/io/bluetape4k/graph/model/GraphEdge.kt).

`GraphPath`는 `PathStep.VertexStep`과 `PathStep.EdgeStep`을 담는다. `vertices`, `edges`, `length`, `totalWeight`는 여기서 계산한다. 정점만 넘겨 만든 경로에는 간선이 저절로 생기지 않는다. [`GraphPath.kt`](../../../../graph/graph-core/src/main/kotlin/io/bluetape4k/graph/model/GraphPath.kt)와 [`GraphPathTest.kt`](../../../../graph/graph-core/src/test/kotlin/io/bluetape4k/graph/model/GraphPathTest.kt)를 함께 보자.

```kotlin
val id = GraphElementId.of("person:42")
val person = GraphVertex(id, "Person", mapOf("name" to "Ada"))
```

반환 객체를 살아 있는 엔티티처럼 수정하지 않는다. 변경은 repository 메서드로 기록하고, 가져오기 파일의 외부 ID와 백엔드 ID를 분리한다.
