# 그래프 애플리케이션 테스트

검증은 의미론을 중심으로 층을 나눈다.

1. 모델과 도메인 순회는 TinkerGraph로 빠르게 돌린다.
2. 같은 도메인 검증을 후보 백엔드마다 실행한다.
3. 릴리스의 Testcontainers 환경에서 질의, 스키마, 트랜잭션, merge, batch를 검증한다.
4. 운영에 쓸 형식마다 graph-io 왕복과 실패 경로를 추가한다.

예제는 공통 추상 테스트와 구체 백엔드 생명주기를 분리한다. [`AbstractRecommendationTest.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/recommendation-examples/src/test/kotlin/io/bluetape4k/graph/examples/recommendation/AbstractRecommendationTest.kt)와 [`RecommendationBackendTests.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/recommendation-examples/src/test/kotlin/io/bluetape4k/graph/examples/recommendation/RecommendationBackendTests.kt)를 함께 보면 구조가 보인다.

ID의 모양을 단정하지 말고 존재와 동일성만 검증한다. 방향·깊이 제한, 중복 merge 키, 빈 batch와 실패 batch, rollback·취소, 미지원 스키마, 잘못된 import 레코드, 찾지 못한 외부 ID, 자원 종료를 시험한다.

컨테이너 통과는 로컬 근거다. 이미지·설정·재시도·시작 시간을 기록해야 재시도 뒤 통과한 결과가 생명주기 문제를 가리지 않는다.

## 실행하고 결과를 진단한다

```bash
./gradlew :bluetape4k-graph-core:test --tests '*GraphMergeOperationsTest' --tests '*GraphBatchOperationsTest'
./gradlew :bluetape4k-graph-tinkerpop:test --tests '*TinkerGraphTransactionTest'
./gradlew :bluetape4k-graph-neo4j:test --tests '*Neo4jGraphOperationsTest'
./gradlew :bluetape4k-graph-okio:test --tests '*NegativePathTest'
```

batch 반환 순서, 중복 merge 뒤 ID 하나, 강제 rollback 뒤 그대로인 개수, 컨테이너 CRUD 개수, OkIO 실패 뒤 보존된 파일이 기대 결과다. 안전하지 않은 label, 트랜잭션 예외, 멈춘 컨테이너, 잘린 gzip을 넣는다. 첫 실패 계층을 core 검증, repository capability, 서버 생명주기·질의, 파일 codec·보안 순서로 가른다. 컨테이너를 재실행해 통과시키기 전에 첫 오류와 시작 시간을 기록한다.
