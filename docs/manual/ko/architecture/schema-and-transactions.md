# 스키마와 쓰기·트랜잭션 경계

![핵심 추상화 지도](../../assets/architecture/core-abstraction-map.png)

`VertexLabel`과 `EdgeLabel`은 이름과 속성 정의를 재사용하는 Exposed 방식 선언이다. 도메인을 표현할 뿐, 실제 스키마 DDL은 `GraphSchemaManager`가 실행한다. [`VertexLabel.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-core/src/main/kotlin/io/bluetape4k/graph/schema/VertexLabel.kt), [`EdgeLabel.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-core/src/main/kotlin/io/bluetape4k/graph/schema/EdgeLabel.kt), [`CodeGraphSchema.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/code-graph-examples/src/main/kotlin/io/bluetape4k/graph/examples/code/schema/CodeGraphSchema.kt)를 차례로 읽으면 선언과 사용이 연결된다.

```kotlin
object Person : VertexLabel("Person") { val email = string("email") }
ops.schemaManager().createIndex(Person.label, Person.email.name)
```

스키마 기능도 capability다. 지원하지 않는 변경은 성공한 척하지 않고 예외를 던진다. 메타데이터 목록은 비어 있을 수 있다. 공통 계약은 [`GraphSchemaManager.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-core/src/main/kotlin/io/bluetape4k/graph/schema/GraphSchemaManager.kt)에 있다.

`mergeVertex`/`mergeEdge`는 upsert 의도를, `createVertices`/`createEdges`는 batch 의도를 나타낸다. 여러 단계가 자동으로 원자적이 되는 것은 아니다. 구현이 capability를 제공할 때만 `transaction {}` 또는 `suspendTransaction {}`를 쓴다. 정상 종료하면 commit하고 예외가 나면 rollback한다. 지원하지 않으면 자동 커밋 대신 실패한다. 근거: [`GraphTransactionScope.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-core/src/main/kotlin/io/bluetape4k/graph/repository/GraphTransactionScope.kt), [`GraphSuspendTransactionScope.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-core/src/main/kotlin/io/bluetape4k/graph/repository/GraphSuspendTransactionScope.kt).

운영에 넣기 전에 중복 merge 키, 빈 batch, 중간 실패, rollback, 취소, commit 전에 소비되는 `Flow`를 검증한다. [`Neo4jGraphSuspendOperationsTest.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-neo4j/src/test/kotlin/io/bluetape4k/graph/neo4j/Neo4jGraphSuspendOperationsTest.kt)가 실제 예를 보여 준다.

## 쓰기 경계를 차례로 실행한다

```kotlin
val people = ops.createVertices("Person", listOf(mapOf("email" to "a@x"), mapOf("email" to "b@x")))
val edge = ops.mergeEdge(people[0].id, people[1].id, "KNOWS", setProperties = mapOf("since" to 2024))
val path = ops.shortestPath(people[0].id, people[1].id, PathOptions(edgeLabel = "KNOWS", maxDepth = 1))
check(people.size == 2 && path?.edges?.single()?.id == edge.id)
```

기본 batch는 입력 순서를 지키지만 단건 메서드를 순서대로 부르므로 중간까지 반영될 수 있다. 운영 백엔드 override가 all-or-fail을 제공하는지는 해당 batch 테스트로 확인한다. 빈 입력은 백엔드를 부르지 않고 빈 목록을 돌려준다.

```kotlin
val before = ops.countVertices("Person")
runCatching { ops.transaction { createVertex("Person"); error("rollback") } }
check(ops.countVertices("Person") == before)
```

## 결과와 실패를 진단한다

schema는 index를 만들고 목록에서 확인한 뒤 삭제한다. 미지원 manager는 변경을 성공 처리하지 않고 예외를 던져야 한다. 순회에서 경로가 없으면 `shortestPath`는 null, `allPaths`는 빈 목록이다. 잘못된 깊이·방문 한도는 질의 전에 거부한다. `suspendTransaction` 취소는 rollback 뒤 취소를 다시 던져야 하며, 반환 `Flow`는 백엔드 테스트에 적힌 대로 commit 전에 소비된다.
