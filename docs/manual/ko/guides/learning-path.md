# 학습 경로

## 1단계: 관계 하나를 만든다

[시작하기](../getting-started.md)를 TinkerGraph로 실행한다. 불투명한 ID, 방향이 있는 간선, 반환값이 스냅샷이라는 점을 익힌다. 생성된 ID와 이웃 방향을 관찰한다. 이웃이 안 나오면 `startId`/`endId`, `Direction`, label 조건을 확인한다. 이어서 [`GraphVertexTest.kt`](../../../../graph/graph-core/src/test/kotlin/io/bluetape4k/graph/model/GraphVertexTest.kt)를 읽는다.

## 2단계: 도메인 예제를 따라간다

[`CodeGraphSchema.kt`](../../../../examples/code-graph-examples/src/main/kotlin/io/bluetape4k/graph/examples/code/schema/CodeGraphSchema.kt)에서 모델을 본 뒤 [`AbstractCodeGraphTest.kt`](../../../../examples/code-graph-examples/src/test/kotlin/io/bluetape4k/graph/examples/code/AbstractCodeGraphTest.kt)의 쓰기·순회·검증 순서를 따라간다. 구체 백엔드 테스트 하나를 실행하고 ID와 질의 로그가 어떻게 다른지 본다.

## 3단계: 쓰기 의미를 확인한다

merge, batch, `transaction {}`를 실행한다. 중복 처리, 반환 순서, commit, rollback을 관찰한다. 중간 실패 전후의 개수를 비교해 부분 쓰기를 진단한다. 계약 지도는 [`GraphBatchOperationsTest.kt`](../../../../graph/graph-core/src/test/kotlin/io/bluetape4k/graph/repository/GraphBatchOperationsTest.kt)다.

## 4단계: 백엔드를 바꾼다

[선택 가이드](../backends/selection-guide.md)로 후보를 두 개까지 줄인 뒤 같은 예제를 양쪽에서 실행한다. 스키마, 속성 형식, 트랜잭션, 순회 차이를 기록한다. compile 통과만으로 의미가 같다고 판단하지 않는다.

## 5단계: 전송하고 운영한다

작은 자료를 [graph-io](../graph-io/formats.md)로 왕복하고 잘못되거나 잘린 입력을 넣어 본다. [운영 가이드](operations.md)의 지표와 복구 절차를 세운 다음 실제 작업 부하를 benchmark한다.
