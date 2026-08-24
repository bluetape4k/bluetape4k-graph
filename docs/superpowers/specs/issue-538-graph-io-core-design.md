# #538 graph-io-core workflow·writer 계약 설계

## 문제와 범위

`GraphImportWorkflow.transition`은 state store에서 현재 상태를 읽고 허용
전이를 검증한 뒤 저장하는 세 단계를 별도 호출한다. 같은 job에 대한 두
workflow 인스턴스가 동시에 실행되면 둘 다 stale 상태를 읽고 마지막 저장이
앞선 전이의 결과를 덮어쓸 수 있다. `validate`도 같은 저장 경계를 사용하므로
동일한 원자성 계약에 포함한다.

`GraphIoBatchWriter`와 `SuspendGraphIoBatchWriter`는 importer 옵션을 거치지
않고 public 생성자로 직접 만들 수 있지만 `batchSize`를 검증하지 않는다.
0은 flush 조건을 만족하지 못하고 음수는 `ArrayList` 초기 용량에서 즉시
실패하므로, 옵션 경로와 같은 Bluetape `requirePositiveNumber` 계약을
생성자에 적용한다.

이번 변경은 graph-io-core의 workflow store, 두 writer, 회귀 테스트와 계약
문서만 대상으로 한다. 외부 durable store 구현이나 모듈별 importer의
배치 정책은 변경하지 않는다.

## 제안 설계

1. `GraphImportJobStateStore.update(jobId, transform)`를 추가한다. 기본 구현은
   store 인스턴스 monitor에서 `load → transform → save`를 묶어 동일 store를
   공유하는 workflow 인스턴스 간 원자성을 보장한다. 현재 in-memory store는
   이 경계를 사용하고, 향후 database/CAS store는 이 메서드를 native
   transaction/CAS로 override할 수 있다.
2. `GraphImportWorkflow.persist`는 `update` 안에서 현재 state와
   `ALLOWED_TRANSITIONS`를 다시 확인하고 새 report를 저장한다. 따라서
   validation 이전에 읽은 stale state로 뒤로 이동하거나 동일 job 전이를
   중복 성공할 수 없다.
3. 두 writer 생성자에서 `batchSize.requirePositiveNumber("batchSize")`를
   실행한다. 기존 `GraphImportOptions`와 예외 타입·메시지 계열을 맞추고,
   buffering/flush 동작은 변경하지 않는다.
4. 동시성 테스트는 한 저장 호출을 잠시 멈춘 custom store로 두 workflow
   인스턴스의 같은 job 전이를 겹치게 한다. 원자 경계가 있으면 한 호출만
   성공하고 두 번째는 최신 state를 읽어 `IllegalArgumentException`이 된다.
   writer 테스트는 sync/suspend 생성자의 0·음수 입력을
   `io.bluetape4k.assertions.assertFailsWith`로 검증한다.

## 실패·호환성 계약

- public workflow method와 writer method signature는 바꾸지 않는다.
- `GraphImportJobStateStore`의 새 default `update`는 기존 구현을 깨지 않으며,
  동일 store 인스턴스 내부의 JVM 원자성만 제공한다. 분산 프로세스 간 원자성은
  backend store가 `update`를 CAS/transaction으로 override해야 한다.
- transition 오류는 기존 `IllegalArgumentException`과 메시지 형식을
  유지한다. 동시 호출에서 허용되지 않은 두 번째 전이는 실패한다.
- batchSize는 양수만 허용한다. importer의 `GraphImportOptions` validation과
  직접 writer 생성 validation이 동일한 Bluetape helper를 사용한다.

## 수용 기준과 DoD

- stale workflow transition overwrite가 회귀 테스트에서 차단된다.
- sync/suspend writer가 0·음수 `batchSize`를 생성 시점에 거부한다.
- graph-io-core 전체 테스트, detekt, Kotlin compile, diff-check가 통과한다.
- 새 예외 검증은 `io.bluetape4k.assertions.assertFailsWith`만 사용한다.
- 독립 7-Tier review에서 P0/P1이 없고, 남은 P2/P3는 별도 GitHub issue로
  기록한다.

## 범위 밖

- 분산 durable state store의 실제 database transaction/CAS 구현
- writer buffer의 cross-thread safety 또는 importer 전체 backpressure
- graph-io 다른 format module의 batch policy
