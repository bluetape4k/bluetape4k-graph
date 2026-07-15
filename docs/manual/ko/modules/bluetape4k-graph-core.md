# bluetape4k-graph-core

## 선택 기준

core는 `GraphVertex`, `GraphEdge`, `GraphPath`, 동기·virtual thread·coroutine repository, merge, schema DSL, transaction scope, 공통 algorithm을 정의한다. 공통 API로 애플리케이션을 작성하거나 새 graph 구현을 만들 때 선택한다. 저장 엔진이 필요하면 core만 넣지 말고 실제 graph 모듈을 고른다.

근거 API는 [GraphOperations.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/graph/graph-core/src/main/kotlin/io/bluetape4k/graph/repository/GraphOperations.kt)와 [GraphTraversalRepository.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/graph/graph-core/src/main/kotlin/io/bluetape4k/graph/repository/GraphTraversalRepository.kt)다.

## 의존성

```kotlin
dependencies {
    implementation(platform("io.github.bluetape4k:bluetape4k-dependencies:<ecosystem-version>"))
    implementation("io.github.bluetape4k:bluetape4k-graph-core")
}
```

## 핵심 API와 실행

실행 가능한 최소 예제에는 TinkerGraph 구현을 함께 쓴다.

```kotlin
TinkerGraphOperations().use { ops ->
    val alice = ops.createVertex("Person", mapOf("email" to "a@example.com"))
    val bob = ops.mergeVertex("Person", mapOf("email" to "b@example.com"), mapOf("name" to "Bob"))
    ops.createEdge(alice.id, bob.id, "KNOWS")
    check(ops.neighbors(alice.id, NeighborOptions(edgeLabel = "KNOWS")).single().id == bob.id)
}
```

예상 결과는 정점 2개, 방향 간선 1개, Alice의 이웃 1개다.

## 동작과 자원

공통 facade는 session, vertex, edge, traversal repository를 묶는다. `transaction { }`은 구현체가 `GraphTransactionalOperations`를 제공할 때만 동작한다. 지원하지 않는 기능을 원자적인 것처럼 흉내 내지 않는다. `GraphElementId`는 불투명 값이므로 숫자나 문자열 구조를 해석하지 않는다.

core는 서버 자원을 소유하지 않는다. operations, Driver, DataSource의 종료 책임은 실제 구현과 framework 설정이 정한다.

## 확인과 문제 해결

```bash
./gradlew :bluetape4k-graph-core:test --tests '*GraphMergeOperationsTest' --tests '*GraphTransactionExtensionsTest'
```

예상 결과는 merge helper와 transaction capability 검사가 통과하는 것이다. 특정 graph 모듈만 실패하면 그 구현의 query 변환과 transaction 경계를 확인한다. traversal 비용과 native algorithm 지원 여부도 구현마다 다르다.

## 관련 문서와 하지 않는 일

[core model](../architecture/core-model.md), [짝을 이루는 API](../architecture/paired-apis.md), [schema와 transaction](../architecture/schema-and-transactions.md)을 참고한다. core는 모든 graph 제품의 기능을 하나로 합치거나 데이터베이스를 설치하지 않는다.
