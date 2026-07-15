# FalkorDB

Redis 형태로 운영하는 서비스와 openCypher 일부가 시스템 경계에 맞으면 FalkorDB를 검토한다. 두 제품 모두 Cypher와 비슷한 질의를 받는다는 이유로 Neo4j 대체품처럼 취급하면 안 된다.

구현은 [`FalkorDBGraphOperations.kt`](../../../../graph/graph-falkordb/src/main/kotlin/io/bluetape4k/graph/falkordb/FalkorDBGraphOperations.kt), 스키마 처리는 [`FalkorDBGraphSchemaManager.kt`](../../../../graph/graph-falkordb/src/main/kotlin/io/bluetape4k/graph/falkordb/FalkorDBGraphSchemaManager.kt)에 있다. CRUD, merge, batch, schema는 [`FalkorDBGraphOperationsTest.kt`](../../../../graph/graph-falkordb/src/test/kotlin/io/bluetape4k/graph/falkordb/FalkorDBGraphOperationsTest.kt)와 인접 테스트가 컨테이너로 검증한다.

가장 먼저 확인할 이식성 경계는 트랜잭션이다. 0.5.1의 suspend 테스트는 지원하지 않는 repository DSL을 원자적인 것처럼 흉내 내지 않는다. [`FalkorDBGraphSuspendOperationsTest.kt`](../../../../graph/graph-falkordb/src/test/kotlin/io/bluetape4k/graph/falkordb/FalkorDBGraphSuspendOperationsTest.kt)를 읽고 여러 쓰기 단계의 실패 처리를 설계한다.

pool과 Redis 연결, 질의 지연, 메모리, 느린 질의, 인덱스 생성을 관찰한다. 릴리스 컨테이너로 재현한 다음 운영 이미지와 설정으로 다시 확인한다.
