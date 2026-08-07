# bluetape4k-graph-memgraph


실행 방식: **release test fixture 연계형**이다. 아래 테스트가 정확한 Memgraph image를 시작하고 Driver와 `ops`를 만든 뒤 데이터를 넣고 operations, Driver, container 순서로 닫는다. endpoint와 인증 설정도 fixture가 제공한다.

## 실행 전 준비

Memgraph는 Neo4j Java Driver로 Bolt에 연결하지만 서버, Cypher 범위, schema DDL, 운영 특성이 다르다. Memgraph를 이미 운영하거나 메모리 중심 그래프 처리가 요구사항에 맞을 때 선택한다. Neo4j 호환성 검증 수단으로 쓰면 안 된다. 구현은 [MemgraphGraphOperations.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-memgraph/src/main/kotlin/io/bluetape4k/graph/memgraph/MemgraphGraphOperations.kt)다.

## 실행

```kotlin
dependencies {
    implementation(platform("io.github.bluetape4k:bluetape4k-dependencies:<ecosystem-version>"))
    implementation("io.github.bluetape4k:bluetape4k-graph-memgraph")
}
```

```kotlin
val driver = GraphDatabase.driver("bolt://localhost:7687", AuthTokens.none())
val ops = MemgraphGraphOperations(driver)
val a = ops.createVertex("Person", mapOf("name" to "Alice"))
val b = ops.mergeVertex("Person", mapOf("email" to "b@example.com"), mapOf("name" to "Bob"))
ops.createEdge(a.id, b.id, "KNOWS")
check(ops.neighbors(a.id, NeighborOptions(edgeLabel = "KNOWS")).single().id == b.id)
ops.close()
driver.close()
```

## 기대 결과

예상 결과는 생성한 Bob이 Alice의 이웃으로 조회되는 것이다.

## 동작과 자원

트랜잭션과 merge는 Memgraph 서버 버전에서 직접 확인해야 한다. schema는 [MemgraphGraphSchemaManager.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-memgraph/src/main/kotlin/io/bluetape4k/graph/memgraph/MemgraphGraphSchemaManager.kt)의 DDL만 따른다. Neo4j DDL을 그대로 복사하지 않는다. 주입한 Driver는 호출자가 닫는다.

## 운영 점검

- 서버/image 버전과 선택한 그래프/database를 기록한다.
- connection pool 대기와 query latency를 확인한다.
- 트랜잭션 rollback과 schema 지원을 따로 검증한다.
- operations를 먼저 닫고 호출자 소유 Driver/DataSource를 닫는다.

## 실패와 복구

증상: CRUD는 통과하지만 schema 또는 트랜잭션 검증이 실패한다. Memgraph 서버 버전과 생성 DDL을 비교하고 호환되지 않는 index와 test data를 정리한 뒤 Memgraph 전용 테스트를 다시 실행한다.

```bash
./gradlew :bluetape4k-graph-memgraph:test --tests '*MemgraphGraphOperationsTest' --tests '*MemgraphGraphSchemaManagerTest'
```

예상 결과는 Memgraph container에서 CRUD와 Memgraph 전용 schema 검증이 통과하는 것이다. 연결·인증, database 선택, query 지원, schema 문법, 트랜잭션을 순서대로 나눠 확인한다. pool 대기, query latency, memory, server log도 함께 본다.

## 완전한 release 예제

고정된 [MemgraphGraphOperationsTest](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-memgraph/src/test/kotlin/io/bluetape4k/graph/memgraph/MemgraphGraphOperationsTest.kt)가 fixture 값을 정의한 완전한 실행 예제다. 다음 명령으로 확인한다.

```bash
./gradlew :bluetape4k-graph-memgraph:test --tests '*MemgraphGraphOperationsTest'
```

예상 결과는 fixture가 시작되고 검증이 통과하며 소유 자원이 문서에 적은 순서로 닫히는 것이다.

## 하지 않는 일과 관련 문서

[Neo4j와 Memgraph](../backends/neo4j-and-memgraph.md), [구현 선택](../backends/selection-guide.md), [실패와 취소](../guides/failure-and-cancellation.md)를 참고한다. 이 모듈은 Memgraph를 Neo4j의 일률적인 상위 집합으로 만들지 않는다.
