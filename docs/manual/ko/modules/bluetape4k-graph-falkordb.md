# bluetape4k-graph-falkordb

## 선택 기준

FalkorDB는 Redis 형태로 운영되는 graph 서비스이며 jfalkordb와 openCypher 일부를 쓴다. 해당 서비스를 운영하고 query 범위가 요구사항에 맞을 때 선택한다. Neo4j와 query, schema, transaction, 운영 방식이 같다고 가정하면 안 된다. 구현은 [FalkorDBGraphOperations.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/graph/graph-falkordb/src/main/kotlin/io/bluetape4k/graph/falkordb/FalkorDBGraphOperations.kt)다.

## 의존성과 실행

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

예상 결과는 첫 query에서 graph가 만들어지고 Bob이 이웃으로 조회되는 것이다.

## 동작과 자원

merge와 schema는 FalkorDB 전용 구현을 따른다. 0.5.1의 공통 suspend transaction DSL은 명시적으로 지원하지 않는다. 여러 쓰기를 호출자 쪽에서 원자적인 것처럼 감싸지 말고, 멱등 단계로 설계하거나 transaction 요구를 만족하는 구현을 고른다. 근거는 [FalkorDBGraphSuspendOperationsTest.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/graph/graph-falkordb/src/test/kotlin/io/bluetape4k/graph/falkordb/FalkorDBGraphSuspendOperationsTest.kt)다. Driver는 호출자가 닫는다.

## 확인과 문제 해결

```bash
./gradlew :bluetape4k-graph-falkordb:test --tests '*FalkorDBGraphOperationsTest' --tests '*FalkorDBGraphSuspendOperationsTest'
```

예상 결과는 CRUD가 통과하고 transaction 테스트가 미지원 경로를 확인하는 것이다. 서버 준비, 연결·인증, pool, graph 이름, query 범위, index 순서로 본다. 미지원 결과를 일시적인 장애로 재시도하지 않는다.

## 관련 문서와 하지 않는 일

[FalkorDB](../backends/falkordb.md), [구현 선택](../backends/selection-guide.md), [운영](../guides/operations.md)을 참고한다. 이 모듈은 FalkorDB를 설치하거나 숨은 transaction 대안을 제공하지 않는다.
