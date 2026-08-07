# bluetape4k-graph-tinkerpop


실행 방식: **메모리에서 바로 실행**한다. `use` 안에서 `TinkerGraphOperations`를 만들고 닫으므로 서버, test fixture, Driver, DataSource가 필요 없다.

## 실행 전 준비

이 모듈은 공통 그래프 API를 내장형 TinkerGraph와 Gremlin에 연결한다. 단위 테스트, 학습, 알고리즘 기준선, 초기 domain model에 알맞다. 원격 지연, 영속성, cluster, 다른 제품의 트랜잭션을 검증하려는 경우에는 피한다. 구현은 [TinkerGraphOperations.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-tinkerpop/src/main/kotlin/io/bluetape4k/graph/tinkerpop/TinkerGraphOperations.kt)다.

## 실행

```kotlin
dependencies {
    implementation(platform("io.github.bluetape4k:bluetape4k-dependencies:<ecosystem-version>"))
    implementation("io.github.bluetape4k:bluetape4k-graph-tinkerpop")
}
```

```kotlin
import io.bluetape4k.graph.model.NeighborOptions
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations

TinkerGraphOperations().use { ops ->
    val a = ops.createVertex("Person", mapOf("name" to "Alice"))
    val b = ops.mergeVertex("Person", mapOf("email" to "b@example.com"), mapOf("name" to "Bob"))
    ops.createEdge(a.id, b.id, "KNOWS")
    check(ops.neighbors(a.id, NeighborOptions(edgeLabel = "KNOWS")).single().id == b.id)
}
```

## 기대 결과

예상 결과는 서버 없이 이웃 하나가 조회되는 것이다.

## 동작과 자원

트랜잭션 DSL은 메모리 snapshot을 만들고 실패 시 되돌린다. 원격 ACID 트랜잭션과 같은 의미가 아니다. [TinkerGraphTransactionTest.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-tinkerpop/src/test/kotlin/io/bluetape4k/graph/tinkerpop/TinkerGraphTransactionTest.kt)가 이 경계를 고정한다. schema 관리도 vendor DDL이 아니라 메모리 호환 계층이다. `use`가 생성한 operations만 닫는다.

## 운영 점검

- heap 사용량과 그래프 크기를 확인한다.
- 탐색 깊이와 소요 시간을 기록한다.
- 운영 그래프 구현으로 merge와 트랜잭션 검사를 다시 실행한다.
- 격리 테스트가 끝나면 그래프를 버린다.

## 실패와 복구

증상: 고의로 예외를 낸 뒤 rollback 검증이 실패한다. 객체를 닫고 새 메모리 그래프를 만든 다음 `TinkerGraphTransactionTest`를 다시 실행한다.

```bash
./gradlew :bluetape4k-graph-tinkerpop:test --tests '*TinkerGraphOperationsTest' --tests '*TinkerGraphTransactionTest'
```

예상 결과는 CRUD와 traversal이 통과하고, 고의로 낸 예외 뒤 snapshot이 복구되는 것이다. 다른 그래프에서 결과가 달라지면 property type, ID, schema, merge, 트랜잭션 차이를 확인한다. 장시간 실행할 때는 heap에 쌓인 그래프 크기도 본다.

## 완전한 release 예제

고정된 [TinkerGraphOperationsTest](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-tinkerpop/src/test/kotlin/io/bluetape4k/graph/tinkerpop/TinkerGraphOperationsTest.kt)가 실제로 실행되는 release 근거다. 다음 명령으로 확인한다.

```bash
./gradlew :bluetape4k-graph-tinkerpop:test --tests '*TinkerGraphOperationsTest'
```

예상 결과는 위 자원 소유권과 기능 경계를 검증하면서 release 테스트 또는 build가 끝나는 것이다.

## 하지 않는 일과 관련 문서

[TinkerPop](../backends/tinkerpop.md), [구현 선택](../backends/selection-guide.md), [성능 자료로 선택하기](../guides/benchmark-based-selection.md)를 참고한다. 이 모듈은 장애, 영속성, 원격 Gremlin, cluster를 흉내 내지 않는다.
