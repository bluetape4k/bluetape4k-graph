# TinkerPop과 TinkerGraph

![백엔드 선택 지도](../../assets/backends/backend-decision-map.png)

0.6.0 모듈은 TinkerGraph를 프로세스 안에 띄우고 공통 repository 계약을 TinkerPop/Gremlin에 연결한다. 로컬 검증과 알고리즘 테스트는 빠르지만, 원격 서버의 지연·내구성·클러스터·트랜잭션을 재현하지는 않는다.

동기 코드는 [`TinkerGraphOperations.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-tinkerpop/src/main/kotlin/io/bluetape4k/graph/tinkerpop/TinkerGraphOperations.kt), 코루틴 연동은 [`TinkerGraphSuspendOperations.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-tinkerpop/src/main/kotlin/io/bluetape4k/graph/tinkerpop/TinkerGraphSuspendOperations.kt)에서 시작한다. CRUD와 순회는 [`TinkerGraphOperationsTest.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-tinkerpop/src/test/kotlin/io/bluetape4k/graph/tinkerpop/TinkerGraphOperationsTest.kt), commit과 rollback은 [`TinkerGraphTransactionTest.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-tinkerpop/src/test/kotlin/io/bluetape4k/graph/tinkerpop/TinkerGraphTransactionTest.kt)가 검증한다.

단위 테스트, 학습, 첫 모델링에는 좋은 선택이다. 다른 백엔드로 옮길 때는 merge, batch, 스키마, 트랜잭션, 속성 형식, 순회를 그 백엔드에서 다시 실행한다. 메모리 테스트 통과는 도메인 논리 근거이지 운영 준비 근거가 아니다.

## 실행한다

```kotlin
dependencies {
    implementation(platform("io.github.bluetape4k:bluetape4k-dependencies:<ecosystem-version>"))
    implementation("io.github.bluetape4k:bluetape4k-graph-tinkerpop")
}

TinkerGraphOperations().use { ops ->
    val a = ops.createVertex("Person", mapOf("name" to "Alice"))
    val b = ops.createVertex("Person", mapOf("name" to "Bob"))
    ops.createEdge(a.id, b.id, "KNOWS")
    check(ops.neighbors(a.id, NeighborOptions(edgeLabel = "KNOWS")).single().id == b.id)
}
```

```bash
./gradlew :bluetape4k-graph-tinkerpop:test --tests '*TinkerGraphTransactionTest'
```

## 확인하고 한계를 구분한다

나가는 방향의 이웃은 Bob 하나여야 하고, 일부러 예외를 넣은 트랜잭션은 snapshot으로 되돌아가야 한다. 컨테이너와 네트워크는 전혀 쓰지 않는다. 따라서 연결 끊김, 서버 동시성, 내구성, cluster 장애 전환, 원격 Gremlin은 검증되지 않는다. 다른 백엔드 결과가 다르면 TinkerGraph 결과를 도메인 기준으로 두고 속성 형식, schema/transaction capability, 질의 변환을 확인한다. `use`는 메모리 operations만 닫는다.
