# 동기·코루틴 API와 capability

![핵심 추상화 지도](../../assets/architecture/core-abstraction-map.png)

`GraphOperations`는 `GraphSession`, 정점·간선 repository, `GraphGenericRepository`를 합친다. `GraphGenericRepository`에는 순회와 알고리즘이 들어 있다. 코루틴 쪽은 같은 구성을 suspend 함수와 `Flow`로 제공한다. 소스: [`GraphOperations.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-core/src/main/kotlin/io/bluetape4k/graph/repository/GraphOperations.kt), [`GraphSuspendOperations.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-core/src/main/kotlin/io/bluetape4k/graph/repository/GraphSuspendOperations.kt), [`GraphGenericRepository.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-core/src/main/kotlin/io/bluetape4k/graph/repository/GraphGenericRepository.kt).

`GraphSession`은 그래프 생성·삭제·존재 확인을 맡는다. 다만 외부에서 주입한 Driver나 DataSource까지 `close()`가 소유한다는 뜻은 아니다. 자원 생명주기는 호출자나 프레임워크 컨테이너가 관리한다. 이 경계는 [`GraphSession.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-core/src/main/kotlin/io/bluetape4k/graph/repository/GraphSession.kt)에 명시돼 있다.

CRUD, merge/upsert, batch, 순회, 스키마, 트랜잭션은 서로 다른 capability다. 기본 batch 구현은 단건 메서드를 순서대로 호출할 수 있어서 중간 실패 전에 기록한 값이 남을 수 있다. [`GraphVertexRepository.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-core/src/main/kotlin/io/bluetape4k/graph/repository/GraphVertexRepository.kt), [`GraphEdgeRepository.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-core/src/main/kotlin/io/bluetape4k/graph/repository/GraphEdgeRepository.kt), [`GraphMergeOperationsTest.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-core/src/test/kotlin/io/bluetape4k/graph/repository/GraphMergeOperationsTest.kt)에서 경계를 확인한다.

호출 흐름이 blocking이면 동기 API나 virtual thread를 고른다. 취소와 `Flow` 소비를 코루틴 범위가 책임진다면 suspend API를 쓴다. 임의의 호출을 `runBlocking`으로 감싸 두 API를 섞지 않는다.

## 두 API를 같은 의미로 실행한다

```kotlin
fun sync(ops: GraphOperations): List<GraphVertex> {
    check(ops.graphExists("social"))
    return ops.findVerticesByLabel("Person")
}

suspend fun async(ops: GraphSuspendOperations): List<GraphVertex> {
    check(ops.graphExists("social"))
    return ops.findVerticesByLabel("Person").toList()
}
```

둘 다 session, vertex, edge, traversal, algorithm 계약을 포함한다. merge, schema, transaction은 별도 capability라서 제공된 extension으로 호출해야 한다. 구현체가 capability를 구현하지 않으면 `UnsupportedOperationException`이 난다.

```kotlin
val first = ops.mergeVertex("Person", mapOf("email" to "a@example.com"), mapOf("name" to "Alice"))
val second = ops.mergeVertex("Person", mapOf("email" to "a@example.com"), mapOf("name" to "Alice 2"))
check(first.id == second.id && ops.countVertices("Person") == 1L)
```

## 실패 유형을 구분한다

빈 `matchProperties`를 넣으면 `IllegalArgumentException`이 나야 한다. `GraphMergeOperations`가 없는 구현에 같은 호출을 하면 읽은 뒤 쓰는 fallback 없이 미지원 오류가 나야 한다. 전자는 잘못된 입력이고 후자는 백엔드 capability 부재다.
