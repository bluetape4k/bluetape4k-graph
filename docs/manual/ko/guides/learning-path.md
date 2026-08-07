# 학습 경로

## 1단계: 관계 하나를 만든다

[시작하기](../getting-started.md)를 TinkerGraph로 실행한다. 불투명한 ID, 방향이 있는 간선, 반환값이 스냅샷이라는 점을 익힌다. 생성된 ID와 이웃 방향을 관찰한다. 이웃이 안 나오면 `startId`/`endId`, `Direction`, label 조건을 확인한다. 이어서 [`GraphVertexTest.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-core/src/test/kotlin/io/bluetape4k/graph/model/GraphVertexTest.kt)를 읽는다.

## 2단계: 도메인 예제를 따라간다

[`CodeGraphSchema.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/code-graph-examples/src/main/kotlin/io/bluetape4k/graph/examples/code/schema/CodeGraphSchema.kt)에서 모델을 본 뒤 [`AbstractCodeGraphTest.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/code-graph-examples/src/test/kotlin/io/bluetape4k/graph/examples/code/AbstractCodeGraphTest.kt)의 쓰기·순회·검증 순서를 따라간다. 구체 백엔드 테스트 하나를 실행하고 ID와 질의 로그가 어떻게 다른지 본다.

## 3단계: 쓰기 의미를 확인한다

merge, batch, `transaction {}`를 실행한다. 중복 처리, 반환 순서, commit, rollback을 관찰한다. 중간 실패 전후의 개수를 비교해 부분 쓰기를 진단한다. 계약 지도는 [`GraphBatchOperationsTest.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-core/src/test/kotlin/io/bluetape4k/graph/repository/GraphBatchOperationsTest.kt)다.

## 4단계: 백엔드를 바꾼다

[선택 가이드](../backends/selection-guide.md)로 후보를 두 개까지 줄인 뒤 같은 예제를 양쪽에서 실행한다. 스키마, 속성 형식, 트랜잭션, 순회 차이를 기록한다. compile 통과만으로 의미가 같다고 판단하지 않는다.

## 5단계: 전송하고 운영한다

작은 자료를 [graph-io](../graph-io/formats.md)로 왕복하고 잘못되거나 잘린 입력을 넣어 본다. [운영 가이드](operations.md)의 지표와 복구 절차를 세운 다음 실제 작업 부하를 benchmark한다.

## 명령으로 끝까지 따라가는 5단계

### 1단계 — 모델과 방향

Java 21을 준비하고 아래 테스트를 실행한다.

```bash
./gradlew :bluetape4k-graph-core:test --tests '*GraphElementIdTest' --tests '*GraphPathTest'
./gradlew :bluetape4k-graph-tinkerpop:test --tests '*TinkerGraphOperationsTest'
```

`GraphElementIdTest`는 ID를 해석하면 안 되는 이유를, `TinkerGraphOperationsTest`는 CRUD와 순회 결과를 보여 준다. 정점 둘, 방향을 보존한 간선, 정점·간선 수가 분명한 경로가 나와야 한다. 빈 ID와 반대 방향 이웃 조건을 넣는다. 입력 단계 예외는 모델 문제, 빈 이웃 목록은 자료 방향이나 option 문제다.

### 2단계 — 도메인 예제를 두 백엔드에서 실행한다

컨테이너 백엔드에는 Docker가 필요하다. 먼저 code-graph schema에서 label을 보고, 공통 테스트에서 실제 사용자 질의를 읽는다.

```bash
./gradlew :code-graph-examples:test --tests '*TinkerGraphCodeGraphTest'
./gradlew :code-graph-examples:test --tests '*Neo4jCodeGraphTest'
```

도메인 검증 조건과 경로·개수는 같고 ID와 백엔드 로그만 달라야 한다. Neo4j 컨테이너를 멈춰 연결 실패를 주입한다. TinkerGraph만 통과하고 Neo4j가 검증 전에 실패하면 픽스처·서버·Driver를 본다. 둘 다 검증까지 왔는데 결과가 다르면 속성·질의 의미를 비교한다.

### 3단계 — 쓰기 보장을 확인한다

```bash
./gradlew :bluetape4k-graph-core:test --tests '*GraphMergeOperationsTest' --tests '*GraphBatchOperationsTest' --tests '*GraphTransactionExtensionsTest'
./gradlew :bluetape4k-graph-neo4j:test --tests '*Neo4jGraphMergeOperationsTest' --tests '*Neo4jGraphSuspendOperationsTest'
```

중복 merge는 같은 ID 하나를 유지하고, batch는 입력 순서를 지키며, 예외·취소 뒤에는 rollback해야 한다. 빈 merge key를 넣어 입력 검증 실패를 만든다. 중간 실패 뒤 일부 행이 남는지는 순차 기본 구현과 백엔드 override의 사후 개수를 비교한다.

### 4단계 — schema와 백엔드를 결정한다

```bash
./gradlew :bluetape4k-graph-neo4j:test --tests '*Neo4jGraphSchemaManagerTest'
./gradlew :bluetape4k-graph-age:test --tests '*AgeGraphSchemaManagerTest'
```

공통 API가 숨기지 않는 DDL 차이를 보려고 각 schema manager를 연다. index 생성·목록·삭제 결과와 미지원 예외를 기록한다. AGE의 session/search-path 오류는 fixture 문제이고 `UnsupportedOperationException`은 capability 결과다.

### 5단계 — 전송과 보안을 검증한다

```bash
./gradlew :bluetape4k-graph-io-jackson3:test --tests '*Jackson3RoundTripTest' --tests '*Jackson3EdgeBufferOverflowTest'
./gradlew :bluetape4k-graph-okio:test --tests '*OkioRoundTripTest' --tests '*NegativePathTest'
```

왕복 개수는 같고, 이른 간선 초과는 메모리를 제한하며, 잘못된 DAEAD 문맥과 잘린 입력은 실패해야 한다. 원자적 쓰기 오류 뒤 기존 파일은 남아야 한다. `NegativePathTest`를 여는 이유는 소유권과 정리 규칙을 파일 시스템 검증 조건으로 확인할 수 있기 때문이다. 이 단계를 통과한 뒤 남은 후보에 맞는 벤치마크를 실행하고 환경을 함께 기록한다.
