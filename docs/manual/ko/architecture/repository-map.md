# 저장소 지도

이 저장소는 드라이버 하나에 모든 책임을 넣지 않는다. 실패가 난 계층을 먼저 찾으면 진단 범위가 크게 줄어든다.

| 영역 | 여기서 배울 내용 | 근거 |
|---|---|---|
| `graph/graph-core` | 모델, 저장소 계약, 스키마, 알고리즘 | [`GraphOperations.kt`](../../../../graph/graph-core/src/main/kotlin/io/bluetape4k/graph/repository/GraphOperations.kt) |
| `graph/graph-*` | 백엔드별 질의와 트랜잭션 의미 | [`Neo4jGraphOperations.kt`](../../../../graph/graph-neo4j/src/main/kotlin/io/bluetape4k/graph/neo4j/Neo4jGraphOperations.kt) |
| `graph-io/*` | 레코드, 형식, 대량 전송 | [`GraphBulkImporter.kt`](../../../../graph-io/core/src/main/kotlin/io/bluetape4k/graph/io/contract/GraphBulkImporter.kt) |
| `ktor`, `spring-boot` | 애플리케이션 생명주기 연동 | [`GraphPlugin.kt`](../../../../ktor/graph-ktor/src/main/kotlin/io/bluetape4k/graph/ktor/GraphPlugin.kt) |
| `examples` | 도메인 문제와 백엔드 공통 테스트 | [`AbstractCodeGraphTest.kt`](../../../../examples/code-graph-examples/src/test/kotlin/io/bluetape4k/graph/examples/code/AbstractCodeGraphTest.kt) |
| `benchmark` | 제한된 작업 부하의 측정 근거 | [`benchmark/README.md`](../../../../benchmark/README.md) |

<!-- diagram: repository learning map -->

공통 계약을 읽고, 연산 하나를 골라 구현과 테스트까지 따라간다. 예제 모듈은 배포 대상이 아니다. 설계와 검증 방법은 참고해도 예제의 배포 전제를 그대로 가져오면 안 된다.

장애가 나면 모델 검증, repository capability, 백엔드 질의/트랜잭션, 형식 codec, 애플리케이션 생명주기 중 어디서 시작됐는지 가른다. 그래야 한 드라이버의 증상을 공통 계약으로 오해하지 않는다.
