# bluetape4k-graph-neo4j


실행 방식: **release test fixture 연계형**이다. snippet의 `password`는 `NEO4J_PASSWORD`에서 읽는다. 아래 테스트가 container, Driver, `ops`, test data를 만들고 operations, Driver, container 순서로 닫는다.

## 실행 전 준비

Neo4j Java Driver, Bolt, Cypher, native session, schema, merge, traversal가 필요할 때 선택한다. PostgreSQL 안에 그래프를 두어야 하거나 내장형 그래프가 필요하면 다른 구현을 고른다. 핵심 구현은 [Neo4jGraphOperations.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-neo4j/src/main/kotlin/io/bluetape4k/graph/neo4j/Neo4jGraphOperations.kt)다.

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

`transaction { }` 안에서 예외가 나면 Neo4j 트랜잭션이 rollback된다. schema는 [Neo4jGraphSchemaManager.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-neo4j/src/main/kotlin/io/bluetape4k/graph/neo4j/Neo4jGraphSchemaManager.kt)가 처리한다. ID는 `elementId()` 값이므로 숫자 ID처럼 다루면 안 된다. operations는 주입받은 Driver를 닫지 않는다.

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

고정된 [Neo4jGraphOperationsTest](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-neo4j/src/test/kotlin/io/bluetape4k/graph/neo4j/Neo4jGraphOperationsTest.kt)가 fixture 값을 정의한 완전한 실행 예제다. 다음 명령으로 확인한다.

```bash
./gradlew :bluetape4k-graph-neo4j:test --tests '*Neo4jGraphOperationsTest'
```

예상 결과는 fixture가 시작되고 검증이 통과하며 소유 자원이 문서에 적은 순서로 닫히는 것이다.

## 하지 않는 일과 관련 문서

[Neo4j와 Memgraph](../backends/neo4j-and-memgraph.md), [테스트](../guides/testing.md), [운영](../guides/operations.md)을 참고한다. 이 모듈은 Neo4j를 설치하거나 주입받은 Driver를 소유하지 않으며, 같은 query가 다른 Bolt 서버에서도 같다고 보장하지 않는다.

<!-- release-readme-diagrams:start -->
## 배포본 다이어그램 {#release-diagrams}

아래 그림은 `0.6.0` 배포본의 README 자산을 해당 배포 커밋에서 직접 불러옵니다. 이후 SNAPSHOT이 아니라 이 매뉴얼 버전의 구조와 실행 흐름을 보여 줍니다. 미리보기를 누르면 같은 배포 커밋의 SVG 원본이 열립니다.

### Bluetape4k Graph neo4j 아키텍처

[![Bluetape4k Graph neo4j 아키텍처](https://raw.githubusercontent.com/bluetape4k/bluetape4k-graph/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-neo4j-architecture-01.png)](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-neo4j-architecture-01.svg)

_배포본 README: [`graph/graph-neo4j/README.ko.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-neo4j/README.ko.md)_

### Reactive-Coroutine 다이어그램

[![Reactive-Coroutine 다이어그램](https://raw.githubusercontent.com/bluetape4k/bluetape4k-graph/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-neo4j-architecture-02.png)](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-neo4j-architecture-02.svg)

_배포본 README: [`graph/graph-neo4j/README.ko.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-neo4j/README.ko.md)_

### neighbors Cypher 다이어그램

[![neighbors Cypher 다이어그램](https://raw.githubusercontent.com/bluetape4k/bluetape4k-graph/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-neo4j-architecture-09.png)](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-neo4j-architecture-09.svg)

_배포본 README: [`graph/graph-neo4j/README.ko.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-neo4j/README.ko.md)_

### Neo4j 다이어그램

[![Neo4j 다이어그램](https://raw.githubusercontent.com/bluetape4k/bluetape4k-graph/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-neo4j-architecture-11.png)](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-neo4j-architecture-11.svg)

_배포본 README: [`graph/graph-neo4j/README.ko.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-neo4j/README.ko.md)_

### Bluetape4k Graph neo4j 아키텍처 12 다이어그램

[![Bluetape4k Graph neo4j 아키텍처 12 다이어그램](https://raw.githubusercontent.com/bluetape4k/bluetape4k-graph/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-neo4j-architecture-12.png)](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-neo4j-architecture-12.svg)

_배포본 README: [`graph/graph-neo4j/README.ko.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-neo4j/README.ko.md)_

### Neo4jGraphOperations 다이어그램

[![Neo4jGraphOperations 다이어그램](https://raw.githubusercontent.com/bluetape4k/bluetape4k-graph/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-neo4j-class-03.png)](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-neo4j-class-03.svg)

_배포본 README: [`graph/graph-neo4j/README.ko.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-neo4j/README.ko.md)_

### Neo4jCoroutineSession 다이어그램

[![Neo4jCoroutineSession 다이어그램](https://raw.githubusercontent.com/bluetape4k/bluetape4k-graph/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-neo4j-class-04.png)](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-neo4j-class-04.svg)

_배포본 README: [`graph/graph-neo4j/README.ko.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-neo4j/README.ko.md)_

### Neo4jRecordMapper 다이어그램

[![Neo4jRecordMapper 다이어그램](https://raw.githubusercontent.com/bluetape4k/bluetape4k-graph/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-neo4j-class-05.png)](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-neo4j-class-05.svg)

_배포본 README: [`graph/graph-neo4j/README.ko.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-neo4j/README.ko.md)_

### createVertex 다이어그램

[![createVertex 다이어그램](https://raw.githubusercontent.com/bluetape4k/bluetape4k-graph/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-neo4j-sequence-06.png)](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-neo4j-sequence-06.svg)

_배포본 README: [`graph/graph-neo4j/README.ko.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-neo4j/README.ko.md)_

### createEdge 다이어그램

[![createEdge 다이어그램](https://raw.githubusercontent.com/bluetape4k/bluetape4k-graph/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-neo4j-sequence-07.png)](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-neo4j-sequence-07.svg)

_배포본 README: [`graph/graph-neo4j/README.ko.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-neo4j/README.ko.md)_

### shortestPath 다이어그램

[![shortestPath 다이어그램](https://raw.githubusercontent.com/bluetape4k/bluetape4k-graph/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-neo4j-sequence-08.png)](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-neo4j-sequence-08.svg)

_배포본 README: [`graph/graph-neo4j/README.ko.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-neo4j/README.ko.md)_

### Publisher → Coroutine 다이어그램

[![Publisher → Coroutine 다이어그램](https://raw.githubusercontent.com/bluetape4k/bluetape4k-graph/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-neo4j-sequence-10.png)](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-neo4j-sequence-10.svg)

_배포본 README: [`graph/graph-neo4j/README.ko.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-neo4j/README.ko.md)_

<!-- release-readme-diagrams:end -->
