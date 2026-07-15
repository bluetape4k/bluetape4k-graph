# bluetape4k-graph-memgraph

## 선택 기준

Memgraph는 Neo4j Java Driver로 Bolt에 연결하지만 서버, Cypher 범위, schema DDL, 운영 특성이 다르다. Memgraph를 이미 운영하거나 메모리 중심 graph 처리가 요구사항에 맞을 때 선택한다. Neo4j 호환성 검증 수단으로 쓰면 안 된다. 구현은 [MemgraphGraphOperations.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/graph/graph-memgraph/src/main/kotlin/io/bluetape4k/graph/memgraph/MemgraphGraphOperations.kt)다.

## 의존성과 실행

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

예상 결과는 생성한 Bob이 Alice의 이웃으로 조회되는 것이다.

## 동작과 자원

transaction과 merge는 Memgraph 서버 버전에서 직접 확인해야 한다. schema는 [MemgraphGraphSchemaManager.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/graph/graph-memgraph/src/main/kotlin/io/bluetape4k/graph/memgraph/MemgraphGraphSchemaManager.kt)의 DDL만 따른다. Neo4j DDL을 그대로 복사하지 않는다. 주입한 Driver는 호출자가 닫는다.

## 확인과 문제 해결

```bash
./gradlew :bluetape4k-graph-memgraph:test --tests '*MemgraphGraphOperationsTest' --tests '*MemgraphGraphSchemaManagerTest'
```

예상 결과는 Memgraph container에서 CRUD와 Memgraph 전용 schema 검증이 통과하는 것이다. 연결·인증, database 선택, query 지원, schema 문법, transaction을 순서대로 나눠 확인한다. pool 대기, query latency, memory, server log도 함께 본다.

## 관련 문서와 하지 않는 일

[Neo4j와 Memgraph](../backends/neo4j-and-memgraph.md), [구현 선택](../backends/selection-guide.md), [실패와 취소](../guides/failure-and-cancellation.md)를 참고한다. 이 모듈은 Memgraph를 Neo4j의 일률적인 상위 집합으로 만들지 않는다.
