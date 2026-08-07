# FalkorDB

![백엔드 선택 지도](../../assets/backends/backend-decision-map.png)

Redis 형태로 운영하는 서비스와 openCypher 일부가 시스템 경계에 맞으면 FalkorDB를 검토한다. 두 제품 모두 Cypher와 비슷한 질의를 받는다는 이유로 Neo4j 대체품처럼 취급하면 안 된다.

구현은 [`FalkorDBGraphOperations.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-falkordb/src/main/kotlin/io/bluetape4k/graph/falkordb/FalkorDBGraphOperations.kt), 스키마 처리는 [`FalkorDBGraphSchemaManager.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-falkordb/src/main/kotlin/io/bluetape4k/graph/falkordb/FalkorDBGraphSchemaManager.kt)에 있다. CRUD, merge, batch, schema는 [`FalkorDBGraphOperationsTest.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-falkordb/src/test/kotlin/io/bluetape4k/graph/falkordb/FalkorDBGraphOperationsTest.kt)와 인접 테스트가 컨테이너로 검증한다.

가장 먼저 확인할 이식성 경계는 트랜잭션이다. 0.6.0의 suspend 테스트는 지원하지 않는 repository DSL을 원자적인 것처럼 흉내 내지 않는다. [`FalkorDBGraphSuspendOperationsTest.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-falkordb/src/test/kotlin/io/bluetape4k/graph/falkordb/FalkorDBGraphSuspendOperationsTest.kt)를 읽고 여러 쓰기 단계의 실패 처리를 설계한다.

pool과 Redis 연결, 질의 지연, 메모리, 느린 질의, 인덱스 생성을 관찰한다. 릴리스 컨테이너로 재현한 다음 운영 이미지와 설정으로 다시 확인한다.

## 서버와 client 경계를 실행한다

```kotlin
dependencies {
    implementation(platform("io.github.bluetape4k:bluetape4k-dependencies:<ecosystem-version>"))
    implementation("io.github.bluetape4k:bluetape4k-graph-falkordb")
}
```

```bash
./gradlew :bluetape4k-graph-falkordb:test --tests '*FalkorDBGraphOperationsTest'
./gradlew :bluetape4k-graph-falkordb:test --tests '*FalkorDBGraphSuspendOperationsTest'
```

fixture에서 정점 둘과 간선 하나를 만든 뒤 `neighbors`를 호출한다. 대상 정점 하나가 나오고 schema 테스트에는 FalkorDB가 실제 제공하는 인덱스만 보여야 한다. 이어서 `suspendTransaction { ... }`을 실행하면 0.6.0은 repository DSL을 지원하지 않는다고 실패해야 한다. native 다중 문장 rollback으로 꾸미지 않는다.

## 실패를 진단한다

연결은 되는데 transaction 검증만 실패하면 client 쪽 read/write fallback을 추가하지 않는다. 작업을 검증된 idempotent 단계로 나누거나 원자적 경계를 제공하는 백엔드를 고른다. 서버 준비, 인증·네트워크, client pool, 지원 질의, 인덱스 순서로 확인한다. fixture가 만든 서버와 client는 fixture가 닫고, 외부 client는 호출자가 관리한다.
