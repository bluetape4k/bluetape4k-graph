# bluetape4k-graph-neo4j


실행 방식: **release test fixture 연계형**이다. snippet의 `password`는 `NEO4J_PASSWORD`에서 읽는다. 아래 테스트가 container, Driver, `ops`, test data를 만들고 operations, Driver, container 순서로 닫는다.

## 실행 전 준비

Neo4j Java Driver, Bolt, Cypher, native session, schema, merge, traversal가 필요할 때 선택한다. PostgreSQL 안에 그래프를 두어야 하거나 내장형 그래프가 필요하면 다른 구현을 고른다. 핵심 구현은 [Neo4jGraphOperations.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/graph/graph-neo4j/src/main/kotlin/io/bluetape4k/graph/neo4j/Neo4jGraphOperations.kt)다.

## 실행

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

## 기대 결과

예상 결과는 Alice의 identity가 merge에서 유지되고 Bob이 이웃으로 조회되는 것이다.

## 동작과 자원

`transaction { }` 안에서 예외가 나면 Neo4j 트랜잭션이 rollback된다. schema는 [Neo4jGraphSchemaManager.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/graph/graph-neo4j/src/main/kotlin/io/bluetape4k/graph/neo4j/Neo4jGraphSchemaManager.kt)가 처리한다. ID는 `elementId()` 값이므로 숫자 ID처럼 다루면 안 된다. operations는 주입받은 Driver를 닫지 않는다.

## 운영 점검

- 서버/image 버전과 선택한 그래프/database를 기록한다.
- connection pool 대기와 query latency를 확인한다.
- 트랜잭션 rollback과 schema 지원을 따로 검증한다.
- operations를 먼저 닫고 호출자 소유 Driver/DataSource를 닫는다.

## 실패와 복구

증상: 인증·서비스 연결 오류와 Cypher·schema 오류가 섞여 보인다. 인증 정보와 network를 먼저 고친 뒤 query/index 상태를 확인하고, 일부 test data를 지운 다음 트랜잭션 검증을 다시 실행한다.

```bash
./gradlew :bluetape4k-graph-neo4j:test --tests '*Neo4jGraphOperationsTest' --tests '*Neo4jGraphMergeOperationsTest'
```

예상 결과는 Neo4j 5 container에서 CRUD, traversal, merge, rollback이 통과하는 것이다. 인증·연결 실패와 Cypher·schema·트랜잭션 실패를 나눠 본다. pool 대기, retry, query latency, server log, database 이름, index를 함께 기록한다.

## 완전한 release 예제

고정된 [Neo4jGraphOperationsTest](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/graph/graph-neo4j/src/test/kotlin/io/bluetape4k/graph/neo4j/Neo4jGraphOperationsTest.kt)가 fixture 값을 정의한 완전한 실행 예제다. 다음 명령으로 확인한다.

```bash
./gradlew :bluetape4k-graph-neo4j:test --tests '*Neo4jGraphOperationsTest'
```

예상 결과는 fixture가 시작되고 검증이 통과하며 소유 자원이 문서에 적은 순서로 닫히는 것이다.

## 하지 않는 일과 관련 문서

[Neo4j와 Memgraph](../backends/neo4j-and-memgraph.md), [테스트](../guides/testing.md), [운영](../guides/operations.md)을 참고한다. 이 모듈은 Neo4j를 설치하거나 주입받은 Driver를 소유하지 않으며, 같은 query가 다른 Bolt 서버에서도 같다고 보장하지 않는다.
