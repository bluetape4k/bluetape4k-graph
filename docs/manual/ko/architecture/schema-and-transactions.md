# 스키마와 쓰기·트랜잭션 경계

`VertexLabel`과 `EdgeLabel`은 이름과 속성 정의를 재사용하는 Exposed 방식 선언이다. 도메인을 표현할 뿐, 실제 스키마 DDL은 `GraphSchemaManager`가 실행한다. [`VertexLabel.kt`](../../../../graph/graph-core/src/main/kotlin/io/bluetape4k/graph/schema/VertexLabel.kt), [`EdgeLabel.kt`](../../../../graph/graph-core/src/main/kotlin/io/bluetape4k/graph/schema/EdgeLabel.kt), [`CodeGraphSchema.kt`](../../../../examples/code-graph-examples/src/main/kotlin/io/bluetape4k/graph/examples/code/schema/CodeGraphSchema.kt)를 차례로 읽으면 선언과 사용이 연결된다.

```kotlin
object Person : VertexLabel("Person") { val email = string("email") }
ops.schemaManager().createIndex(Person.label, Person.email.name)
```

스키마 기능도 capability다. 지원하지 않는 변경은 성공한 척하지 않고 예외를 던진다. 메타데이터 목록은 비어 있을 수 있다. 공통 계약은 [`GraphSchemaManager.kt`](../../../../graph/graph-core/src/main/kotlin/io/bluetape4k/graph/schema/GraphSchemaManager.kt)에 있다.

`mergeVertex`/`mergeEdge`는 upsert 의도를, `createVertices`/`createEdges`는 batch 의도를 나타낸다. 여러 단계가 자동으로 원자적이 되는 것은 아니다. 구현이 capability를 제공할 때만 `transaction {}` 또는 `suspendTransaction {}`를 쓴다. 정상 종료하면 commit하고 예외가 나면 rollback한다. 지원하지 않으면 자동 커밋 대신 실패한다. 근거: [`GraphTransactionScope.kt`](../../../../graph/graph-core/src/main/kotlin/io/bluetape4k/graph/repository/GraphTransactionScope.kt), [`GraphSuspendTransactionScope.kt`](../../../../graph/graph-core/src/main/kotlin/io/bluetape4k/graph/repository/GraphSuspendTransactionScope.kt).

운영에 넣기 전에 중복 merge 키, 빈 batch, 중간 실패, rollback, 취소, commit 전에 소비되는 `Flow`를 검증한다. [`Neo4jGraphSuspendOperationsTest.kt`](../../../../graph/graph-neo4j/src/test/kotlin/io/bluetape4k/graph/neo4j/Neo4jGraphSuspendOperationsTest.kt)가 실제 예를 보여 준다.
