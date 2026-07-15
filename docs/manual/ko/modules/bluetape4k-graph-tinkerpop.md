# bluetape4k-graph-tinkerpop

## 선택 기준

이 모듈은 공통 graph API를 내장형 TinkerGraph와 Gremlin에 연결한다. 단위 테스트, 학습, algorithm 기준선, 초기 domain model에 알맞다. 원격 지연, 영속성, cluster, 다른 제품의 transaction을 검증하려는 경우에는 피한다. 구현은 [TinkerGraphOperations.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/graph/graph-tinkerpop/src/main/kotlin/io/bluetape4k/graph/tinkerpop/TinkerGraphOperations.kt)다.

## 의존성과 실행

```kotlin
dependencies {
    implementation(platform("io.github.bluetape4k:bluetape4k-dependencies:<ecosystem-version>"))
    implementation("io.github.bluetape4k:bluetape4k-graph-tinkerpop")
}
```

```kotlin
TinkerGraphOperations().use { ops ->
    val a = ops.createVertex("Person", mapOf("name" to "Alice"))
    val b = ops.mergeVertex("Person", mapOf("email" to "b@example.com"), mapOf("name" to "Bob"))
    ops.createEdge(a.id, b.id, "KNOWS")
    check(ops.neighbors(a.id, NeighborOptions(edgeLabel = "KNOWS")).single().id == b.id)
}
```

예상 결과는 서버 없이 이웃 하나가 조회되는 것이다.

## 동작과 자원

transaction DSL은 메모리 snapshot을 만들고 실패 시 되돌린다. 원격 ACID transaction과 같은 의미가 아니다. [TinkerGraphTransactionTest.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/graph/graph-tinkerpop/src/test/kotlin/io/bluetape4k/graph/tinkerpop/TinkerGraphTransactionTest.kt)가 이 경계를 고정한다. schema 관리도 vendor DDL이 아니라 메모리 호환 계층이다. `use`가 생성한 operations만 닫는다.

## 확인과 문제 해결

```bash
./gradlew :bluetape4k-graph-tinkerpop:test --tests '*TinkerGraphOperationsTest' --tests '*TinkerGraphTransactionTest'
```

예상 결과는 CRUD와 traversal이 통과하고, 고의로 낸 예외 뒤 snapshot이 복구되는 것이다. 다른 graph에서 결과가 달라지면 property type, ID, schema, merge, transaction 차이를 확인한다. 장시간 실행할 때는 heap에 쌓인 graph 크기도 본다.

## 관련 문서와 하지 않는 일

[TinkerPop](../backends/tinkerpop.md), [구현 선택](../backends/selection-guide.md), [성능 자료로 선택하기](../guides/benchmark-based-selection.md)를 참고한다. 이 모듈은 장애, 영속성, 원격 Gremlin, cluster를 흉내 내지 않는다.
