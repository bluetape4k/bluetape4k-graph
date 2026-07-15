# bluetape4k-graph-neo4j

## 선택 기준

Neo4j Java Driver, Bolt, Cypher, native session, schema, merge, traversal가 필요할 때 선택한다. PostgreSQL 안에 graph를 두어야 하거나 내장형 graph가 필요하면 다른 구현을 고른다. 핵심 구현은 [Neo4jGraphOperations.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/graph/graph-neo4j/src/main/kotlin/io/bluetape4k/graph/neo4j/Neo4jGraphOperations.kt)다.

## 의존성과 실행

```kotlin
dependencies {
    implementation(platform("io.github.bluetape4k:bluetape4k-dependencies:<ecosystem-version>"))
    implementation("io.github.bluetape4k:bluetape4k-graph-neo4j")
}
```

```kotlin
val driver = GraphDatabase.driver("bolt://localhost:7687", AuthTokens.basic("neo4j", password))
val ops = Neo4jGraphOperations(driver, "neo4j")
val alice = ops.mergeVertex("Person", mapOf("email" to "a@example.com"), mapOf("name" to "Alice"))
val bob = ops.createVertex("Person", mapOf("name" to "Bob"))
ops.createEdge(alice.id, bob.id, "KNOWS")
check(ops.neighbors(alice.id, NeighborOptions(edgeLabel = "KNOWS")).single().id == bob.id)
ops.close()
driver.close()
```

예상 결과는 Alice의 identity가 merge에서 유지되고 Bob이 이웃으로 조회되는 것이다.

## 동작과 자원

`transaction { }` 안에서 예외가 나면 Neo4j transaction이 rollback된다. schema는 [Neo4jGraphSchemaManager.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/graph/graph-neo4j/src/main/kotlin/io/bluetape4k/graph/neo4j/Neo4jGraphSchemaManager.kt)가 처리한다. ID는 `elementId()` 값이므로 숫자 ID처럼 다루면 안 된다. operations는 주입받은 Driver를 닫지 않는다.

## 확인과 문제 해결

```bash
./gradlew :bluetape4k-graph-neo4j:test --tests '*Neo4jGraphOperationsTest' --tests '*Neo4jGraphMergeOperationsTest'
```

예상 결과는 Neo4j 5 container에서 CRUD, traversal, merge, rollback이 통과하는 것이다. 인증·연결 실패와 Cypher·schema·transaction 실패를 나눠 본다. pool 대기, retry, query latency, server log, database 이름, index를 함께 기록한다.

## 관련 문서와 하지 않는 일

[Neo4j와 Memgraph](../backends/neo4j-and-memgraph.md), [테스트](../guides/testing.md), [운영](../guides/operations.md)을 참고한다. 이 모듈은 Neo4j를 설치하거나 주입받은 Driver를 소유하지 않으며, 같은 query가 다른 Bolt 서버에서도 같다고 보장하지 않는다.
