# 시작하기

## 1. 생태계 버전 하나를 고른다

개별 graph 모듈이나 `bluetape4k-graph-bom`에 독립 버전을 붙이지 않는다. 소비자가 관리할 값은 `bluetape4k-dependencies` 버전이다.

```kotlin
dependencies {
    implementation(platform("io.github.bluetape4k:bluetape4k-dependencies:<ecosystem-version>"))
    implementation("io.github.bluetape4k:bluetape4k-graph-tinkerpop")
}
```

모듈 좌표에 버전이 없는 이유는 graph와 공통 Bluetape 라이브러리를 생태계 BOM이 함께 맞추기 때문이다. 릴리스 모듈 선언은 [`graph-tinkerpop/build.gradle.kts`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-tinkerpop/build.gradle.kts)에서 확인할 수 있다.

## 2. 로컬 그래프를 실행한다

```kotlin
TinkerGraphOperations().use { ops ->
    val alice = ops.createVertex("Person", mapOf("name" to "Alice"))
    val bob = ops.createVertex("Person", mapOf("name" to "Bob"))
    ops.createEdge(alice.id, bob.id, "KNOWS")
    check(ops.neighbors(alice.id).single().id == bob.id)
}
```

## 3. 결과를 보고 실패를 가른다

실행 뒤에는 생성된 ID와 간선 방향을 본다. ID를 숫자로 가정하면 안 된다. 이웃이 비어 있으면 백엔드를 의심하기 전에 방향, 간선 레이블 조건, `maxDepth`를 확인한다. 공통 조합은 [`GraphOperations.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-core/src/main/kotlin/io/bluetape4k/graph/repository/GraphOperations.kt), 구현과 검증은 [`TinkerGraphOperations.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-tinkerpop/src/main/kotlin/io/bluetape4k/graph/tinkerpop/TinkerGraphOperations.kt)와 [`TinkerGraphOperationsTest.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-tinkerpop/src/test/kotlin/io/bluetape4k/graph/tinkerpop/TinkerGraphOperationsTest.kt)에 있다.

트랜잭션 확장이 `UnsupportedOperationException`을 던지면 선택한 구현에 그 capability가 없는 것이다. 자동 커밋으로 조용히 바꾸지 않는다. 다음으로 [동기·코루틴 API](architecture/paired-apis.md)를 읽는다.
