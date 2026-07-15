# Neo4j와 Memgraph

두 모듈은 Neo4j 드라이버와 호환되는 Bolt·Cypher를 사용하므로 애플리케이션 코드를 상당 부분 공유할 수 있다. 그렇다고 같은 백엔드는 아니다. 지원 Cypher, 스키마 DDL, 배포 방식, 운영 지표가 다르다.

이미 Neo4j를 운영하거나 그 트랜잭션·스키마 의미론이 기준이라면 Neo4j를 고른다. [`Neo4jGraphOperations.kt`](../../../../graph/graph-neo4j/src/main/kotlin/io/bluetape4k/graph/neo4j/Neo4jGraphOperations.kt)를 읽고, 인접한 [`Neo4jGraphSuspendOperationsTest.kt`](../../../../graph/graph-neo4j/src/test/kotlin/io/bluetape4k/graph/neo4j/Neo4jGraphSuspendOperationsTest.kt)에서 batch, merge, schema, transaction을 확인한다.

Memgraph를 이미 배포했고 그 운영 모델이 작업 부하에 맞으면 Memgraph를 고른다. 구현은 [`MemgraphGraphOperations.kt`](../../../../graph/graph-memgraph/src/main/kotlin/io/bluetape4k/graph/memgraph/MemgraphGraphOperations.kt), 스키마 경계는 [`MemgraphGraphSchemaManager.kt`](../../../../graph/graph-memgraph/src/main/kotlin/io/bluetape4k/graph/memgraph/MemgraphGraphSchemaManager.kt)와 [`MemgraphGraphSchemaManagerTest.kt`](../../../../graph/graph-memgraph/src/test/kotlin/io/bluetape4k/graph/memgraph/MemgraphGraphSchemaManagerTest.kt)에 있다.

배포할 서버 계열로 컨테이너 테스트를 다시 돌리고 pool 포화, rollback, 질의 지연, 서버 로그를 본다. 한쪽에서 통과한 Cypher가 다른 쪽의 DDL과 예외 조건까지 보장하지 않는다.
