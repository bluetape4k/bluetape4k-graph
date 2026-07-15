# Apache AGE

그래프 데이터를 기존 PostgreSQL 운영 경계 안에 둬야 한다면 Apache AGE가 후보가 된다. 질의가 SQL 안의 Cypher 계층을 지나므로 JDBC 연결 상태, graph context, PostgreSQL 트랜잭션, AGE 자료형까지 함께 진단해야 한다.

동기·코루틴 구현은 [`AgeGraphOperations.kt`](../../../../graph/graph-age/src/main/kotlin/io/bluetape4k/graph/age/AgeGraphOperations.kt)와 [`AgeGraphSuspendOperations.kt`](../../../../graph/graph-age/src/main/kotlin/io/bluetape4k/graph/age/AgeGraphSuspendOperations.kt)에 있다. [`JdbcTransactionExtensions.kt`](../../../../graph/graph-age/src/main/kotlin/io/bluetape4k/graph/age/JdbcTransactionExtensions.kt)에서 트랜잭션 연결을 보고, [`AgeGraphSuspendOperationsTest.kt`](../../../../graph/graph-age/src/test/kotlin/io/bluetape4k/graph/age/AgeGraphSuspendOperationsTest.kt)에서 rollback과 취소를 확인한다.

공통 스키마 연산이 모두 AGE에 안전하게 대응한다고 가정하지 않는다. [`AgeGraphSchemaManager.kt`](../../../../graph/graph-age/src/main/kotlin/io/bluetape4k/graph/age/AgeGraphSchemaManager.kt)와 [`AgeGraphSchemaManagerTest.kt`](../../../../graph/graph-age/src/test/kotlin/io/bluetape4k/graph/age/AgeGraphSchemaManagerTest.kt)를 확인한다. 먼저 `apache/age:PG16_latest`로 검증하고, 운영할 PostgreSQL·AGE 조합으로 반복한다.

연결 수, lock, SQL/Cypher 오류, 실행 계획, rollback 수를 관찰한다. pool 연결을 재사용한 뒤에만 실패한다면 도메인 질의를 바꾸기 전에 세션 초기화와 graph 선택부터 확인한다.
