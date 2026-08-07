# Neo4j와 Memgraph

![백엔드 선택 지도](../../assets/backends/backend-decision-map.png)

두 모듈은 Neo4j 드라이버와 호환되는 Bolt·Cypher를 사용하므로 애플리케이션 코드를 상당 부분 공유할 수 있다. 그렇다고 같은 백엔드는 아니다. 지원 Cypher, 스키마 DDL, 배포 방식, 운영 지표가 다르다.

이미 Neo4j를 운영하거나 그 트랜잭션·스키마 의미론이 기준이라면 Neo4j를 고른다. [`Neo4jGraphOperations.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-neo4j/src/main/kotlin/io/bluetape4k/graph/neo4j/Neo4jGraphOperations.kt)를 읽고, 인접한 [`Neo4jGraphSuspendOperationsTest.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-neo4j/src/test/kotlin/io/bluetape4k/graph/neo4j/Neo4jGraphSuspendOperationsTest.kt)에서 batch, merge, schema, transaction을 확인한다.

Memgraph를 이미 배포했고 그 운영 모델이 작업 부하에 맞으면 Memgraph를 고른다. 구현은 [`MemgraphGraphOperations.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-memgraph/src/main/kotlin/io/bluetape4k/graph/memgraph/MemgraphGraphOperations.kt), 스키마 경계는 [`MemgraphGraphSchemaManager.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-memgraph/src/main/kotlin/io/bluetape4k/graph/memgraph/MemgraphGraphSchemaManager.kt)와 [`MemgraphGraphSchemaManagerTest.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-memgraph/src/test/kotlin/io/bluetape4k/graph/memgraph/MemgraphGraphSchemaManagerTest.kt)에 있다.

배포할 서버 계열로 컨테이너 테스트를 다시 돌리고 pool 포화, rollback, 질의 지연, 서버 로그를 본다. 한쪽에서 통과한 Cypher가 다른 쪽의 DDL과 예외 조건까지 보장하지 않는다.

## 선택하고 실행한다

```kotlin
dependencies {
    implementation(platform("io.github.bluetape4k:bluetape4k-dependencies:<ecosystem-version>"))
    implementation("io.github.bluetape4k:bluetape4k-graph-neo4j") // 또는 ...-memgraph
}
```

같은 Driver 계열을 쓰더라도 fixture는 따로 실행한다. 그래야 한 컨테이너의 시작·종료가 다른 결과를 가리지 않는다.

```bash
./gradlew :bluetape4k-graph-neo4j:test --tests '*Neo4jGraphOperationsTest'
./gradlew :bluetape4k-graph-memgraph:test --tests '*MemgraphGraphOperationsTest'
```

fixture에서 얻은 `ops`로 두 번 merge한다.

```kotlin
val first = ops.mergeVertex("Person", mapOf("email" to "a@example.com"), mapOf("name" to "Alice"))
val second = ops.mergeVertex("Person", mapOf("email" to "a@example.com"), mapOf("name" to "A. Example"))
check(first.id == second.id)
check(ops.countVertices("Person") == 1L)
```

## 결과를 확인하고 문제를 가른다

정점은 하나이고 set 속성만 바뀌어야 한다. 이어서 `transaction { createVertex(...); error("rollback") }`을 실행하고 정점 수가 늘지 않았는지 본다. CRUD는 통과하는데 schema-manager 테스트만 실패하면 Driver 호환 문제가 아니라 서버별 DDL 차이다. 생성된 질의와 서버 버전을 비교한다. fixture가 만든 operations와 컨테이너는 fixture가 닫고, 외부에서 넘긴 Driver는 호출자가 닫는다.
