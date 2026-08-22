# bluetape4k-graph-falkordb


실행 방식: **release test fixture 연계형**이다. 아래 테스트가 FalkorDB container, jfalkordb Driver, 그래프 이름, `ops`, 정리를 맡는다. snippet은 fixture 값이 준비된 뒤의 핵심 흐름이다.

## 실행 전 준비

FalkorDB는 Redis 형태로 운영되는 그래프 서비스이며 jfalkordb 0.8.0과 openCypher 일부를 쓴다. 해당 서비스를 운영하고 query 범위가 요구사항에 맞을 때 선택한다. Neo4j와 query, schema, 트랜잭션, 운영 방식이 같다고 가정하면 안 된다. 구현은 [FalkorDBGraphOperations.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-falkordb/src/main/kotlin/io/bluetape4k/graph/falkordb/FalkorDBGraphOperations.kt)다.

## 실행

```kotlin
dependencies {
    implementation(platform("io.github.bluetape4k:bluetape4k-dependencies:<ecosystem-version>"))
    implementation("io.github.bluetape4k:bluetape4k-graph-falkordb")
}
```

```kotlin
val driver = FalkorDB.driver("localhost", 6379)
val ops = FalkorDBGraphOperations(driver, graphName = "social")
val a = ops.createVertex("Person", mapOf("name" to "Alice"))
val b = ops.mergeVertex("Person", mapOf("email" to "b@example.com"), mapOf("name" to "Bob"))
ops.createEdge(a.id, b.id, "KNOWS")
check(ops.neighbors(a.id, NeighborOptions(edgeLabel = "KNOWS")).single().id == b.id)
ops.close()
driver.close()
```

## 기대 결과

예상 결과는 첫 query에서 그래프가 만들어지고 Bob이 이웃으로 조회되는 것이다.

## 동작과 자원

merge와 schema는 FalkorDB 전용 구현을 따른다. 0.6.0의 공통 suspend 트랜잭션 DSL은 명시적으로 지원하지 않는다. 여러 쓰기를 호출자 쪽에서 원자적인 것처럼 감싸지 말고, 멱등 단계로 설계하거나 트랜잭션 요구를 만족하는 구현을 고른다. 근거는 [FalkorDBGraphSuspendOperationsTest.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-falkordb/src/test/kotlin/io/bluetape4k/graph/falkordb/FalkorDBGraphSuspendOperationsTest.kt)다. Driver는 호출자가 닫는다.

## 운영 점검

- 서버/image 버전과 선택한 그래프/database를 기록한다.
- connection pool 대기와 query latency를 확인한다.
- 트랜잭션 rollback과 schema 지원을 따로 검증한다.
- operations를 먼저 닫고 호출자 소유 Driver/DataSource를 닫는다.

## 실패와 복구

증상: 공통 트랜잭션 API가 지원하지 않는 기능이라고 보고한다. 재시도하거나 원자성을 흉내 내지 말고 멱등 단계로 다시 설계하거나 필요한 트랜잭션 경계를 제공하는 구현을 선택한다.

```bash
./gradlew :bluetape4k-graph-falkordb:test --tests '*FalkorDBGraphOperationsTest' --tests '*FalkorDBGraphSuspendOperationsTest'
```

예상 결과는 CRUD가 통과하고 트랜잭션 테스트가 미지원 경로를 확인하는 것이다. 서버 준비, 연결·인증, pool, 그래프 이름, query 범위, index 순서로 본다. 미지원 결과를 일시적인 장애로 재시도하지 않는다.

## 완전한 release 예제

고정된 [FalkorDBGraphOperationsTest](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-falkordb/src/test/kotlin/io/bluetape4k/graph/falkordb/FalkorDBGraphOperationsTest.kt)가 fixture 값을 정의한 완전한 실행 예제다. 다음 명령으로 확인한다.

```bash
./gradlew :bluetape4k-graph-falkordb:test --tests '*FalkorDBGraphOperationsTest'
```

예상 결과는 fixture가 시작되고 검증이 통과하며 소유 자원이 문서에 적은 순서로 닫히는 것이다.

## 하지 않는 일과 관련 문서

[FalkorDB](../backends/falkordb.md), [구현 선택](../backends/selection-guide.md), [운영](../guides/operations.md)을 참고한다. 이 모듈은 FalkorDB를 설치하거나 숨은 트랜잭션 대안을 제공하지 않는다.

<!-- release-readme-diagrams:start -->
## 배포본 다이어그램 {#release-diagrams}

아래 그림은 `0.6.0` 배포본의 README 자산을 해당 배포 커밋에서 직접 불러옵니다. 이후 SNAPSHOT이 아니라 이 매뉴얼 버전의 구조와 실행 흐름을 보여 줍니다. 미리보기를 누르면 같은 배포 커밋의 SVG 원본이 열립니다.

### Bluetape4k Graph falkordb 아키텍처

[![Bluetape4k Graph falkordb 아키텍처](https://raw.githubusercontent.com/bluetape4k/bluetape4k-graph/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-falkordb-architecture-01.png)](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-falkordb-architecture-01.svg)

_배포본 README: [`graph/graph-falkordb/README.ko.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-falkordb/README.ko.md)_

<!-- release-readme-diagrams:end -->
