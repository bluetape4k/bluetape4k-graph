# Apache AGE

![백엔드 선택 지도](../../assets/backends/backend-decision-map.png)

그래프 데이터를 기존 PostgreSQL 운영 경계 안에 둬야 한다면 Apache AGE가 후보가 된다. 질의가 SQL 안의 Cypher 계층을 지나므로 JDBC 연결 상태, graph context, PostgreSQL 트랜잭션, AGE 자료형까지 함께 진단해야 한다.

동기·코루틴 구현은 [`AgeGraphOperations.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-age/src/main/kotlin/io/bluetape4k/graph/age/AgeGraphOperations.kt)와 [`AgeGraphSuspendOperations.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-age/src/main/kotlin/io/bluetape4k/graph/age/AgeGraphSuspendOperations.kt)에 있다. [`JdbcTransactionExtensions.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-age/src/main/kotlin/io/bluetape4k/graph/age/JdbcTransactionExtensions.kt)에서 트랜잭션 연결을 보고, [`AgeGraphSuspendOperationsTest.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-age/src/test/kotlin/io/bluetape4k/graph/age/AgeGraphSuspendOperationsTest.kt)에서 rollback과 취소를 확인한다.

공통 스키마 연산이 모두 AGE에 안전하게 대응한다고 가정하지 않는다. [`AgeGraphSchemaManager.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-age/src/main/kotlin/io/bluetape4k/graph/age/AgeGraphSchemaManager.kt)와 [`AgeGraphSchemaManagerTest.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-age/src/test/kotlin/io/bluetape4k/graph/age/AgeGraphSchemaManagerTest.kt)를 확인한다. 먼저 `apache/age:release_PG18_1.7.0`로 검증하고, 운영할 PostgreSQL·AGE 조합으로 반복한다.

연결 수, lock, SQL/Cypher 오류, 실행 계획, rollback 수를 관찰한다. pool 연결을 재사용한 뒤에만 실패한다면 도메인 질의를 바꾸기 전에 세션 초기화와 graph 선택부터 확인한다.

## fixture와 세션을 준비한다

```kotlin
dependencies {
    implementation(platform("io.github.bluetape4k:bluetape4k-dependencies:<ecosystem-version>"))
    implementation("io.github.bluetape4k:bluetape4k-graph-age")
}
```

```bash
./gradlew :bluetape4k-graph-age:test --tests '*AgeGraphOperationsTest'
./gradlew :bluetape4k-graph-age:test --tests '*AgeGraphSuspendOperationsTest'
```

fixture는 AGE가 포함된 PostgreSQL을 띄우고 graph를 만들며, Cypher-over-SQL을 실행하기 전에 연결을 초기화한다. 같은 경계에서 rollback을 확인한다.

```kotlin
val before = ops.countVertices("Person")
runCatching { ops.transaction { createVertex("Person", mapOf("email" to "rollback@example.com")); error("rollback") } }
check(ops.countVertices("Person") == before)
```

## 확인하고 문제를 해결한다

예외는 호출자에게 돌아오고 정점 수는 그대로여야 한다. `graph`가 없거나 `search_path`가 빠졌거나 pool에서 다시 꺼낸 JDBC 연결에 AGE 세션 초기화가 안 됐다면 도메인 검증보다 먼저 SQL/Cypher 이름 해석 오류가 난다. 새로 빌린 연결에서 재현해 세 조건을 차례로 확인한다. 지원하지 않는 schema DDL은 성공으로 바꾸지 않는다. fixture 소유 DataSource와 컨테이너는 fixture가 닫고, 호출자가 넘긴 DataSource는 호출자가 관리한다.
