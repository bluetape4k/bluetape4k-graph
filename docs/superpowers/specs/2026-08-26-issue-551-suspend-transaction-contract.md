# #551 suspendTransaction 중첩 Flow 결과 계약 설계

## 문제와 범위

graph-core의 `GraphSuspendTransactionalOperations.suspendTransaction`은
backend transaction 안에서 suspend block을 실행하지만, backend마다 반환된
최상위 `Flow` materialization 구현이 따로 있었다. `Pair`, `Triple`, `Map`,
`Collection`, 배열 안에 `Flow`를 넣어 반환하면 transaction scope 밖에서 cursor나
driver resource를 참조할 수 있으므로 네 backend의 결과 계약을 하나로 고정한다.

대상 이슈는 [#551](https://github.com/bluetape4k/bluetape4k-graph/issues/551)이며,
적층 기준은 PR [#573](https://github.com/bluetape4k/bluetape4k-graph/pull/573)의
base exact head `186ea8af18192d8fe1e8024bc78cc80b7f235bc1`이다. #552의
`Statement.cancel()` driver stall 취소는 별도 범위로 남긴다.

## 결정

1. 허용하는 반환은 그대로 유지한다. 최상위 `Flow`만 commit 전에 `toList()`로
   수집하고 재수집 가능한 `Flow`로 반환한다.
2. 표준 컨테이너의 중첩 `Flow`는 허용하지 않는다. 공통
   `materializeSuspendTransactionResult`가 `Pair`, `Triple`, `Map`, `Collection`,
   배열을 재귀적으로 검사하고 발견 시 `IllegalArgumentException`을 던진다.
3. 중첩 `Flow`가 필요한 호출자는 transaction block 안에서 `toList()` 등으로
   materialize한 값을 반환한다. 오류는 backend commit 전에 발생해야 하며,
   backend의 기존 rollback 경계를 그대로 통과한다.
4. 임의 사용자 wrapper/data class의 내부 필드는 reflection으로 해석하지 않는다.
   따라서 wrapper 안에 `Flow`를 보관하는 경우는 호출자가 명시적으로
   materialize해야 하며, 이 제한을 public KDoc과 EN/KO README에 기록한다.

## API·호환성

- `GraphSuspendTransactionalOperations.suspendTransaction`의 시그니처와 반환
  타입은 변경하지 않는다.
- 네 backend의 중복 private materializer를 제거하고 graph-core의 public helper를
  공유한다. helper 추가는 backend 모듈 간 공통 구현을 위한 additive API이며,
  기존 호출자는 변경 없이 동작한다.
- transaction block 밖에서 수집되지 않은 표준 컨테이너 중첩 `Flow`를 반환하던
  호출자는 `toList()` 등으로 migration해야 한다.

## 검증 계획

- graph-core: 최상위 `Flow`, `Pair`, `Triple`, `Map`, `Collection`, 배열의 공통
  계약과 예외 메시지를 검증한다.
- AGE·Neo4j·Memgraph·TinkerPop: 중첩 `Flow` 반환 시
  `bluetape4k.assertions.assertFailsWith`와 rollback 결과를 검증한다.
- 모듈 전체 테스트는 Testcontainers backend를 AGE → Neo4j → Memgraph 순서로
  순차 실행하고, in-memory TinkerPop과 graph-core를 별도 실행한다.
- 다섯 모듈 `detekt`, 금지 assertion scan, `git diff --check`를 실행한다.

## 범위 밖

- `Statement.cancel()`과 driver 내부 `executeQuery`/`ResultSet.next()` stall의
  prompt cancellation은 [#552](https://github.com/bluetape4k/bluetape4k-graph/issues/552)
  에서 다룬다.
- Amazon Neptune backend feasibility는 기존 backlog 계약을 유지한다.

## SPW writer gate

- **SPW-01 — Audience and purpose: PASS.** graph-core/backend 유지보수자와
  reviewer가 nested Flow 결과 계약을 구현·검증할 수 있도록 범위를 고정했다.
- **SPW-02 — Artifact contract: PASS.** 문제, 결정, API·호환성, 검증 계획과
  범위 밖 항목을 포함한다.
- **SPW-03 — Korean technical register: PASS.** 설명은 한국어이고 code,
  command, API, issue token은 원문을 보존한다.
- **SPW-04 — Technical traceability: PASS.** #551, #552, PR #573 base와 네
  backend/helper 경계를 연결한다.
- **SPW-05 — Read-back: PENDING.** PR 생성 후 exact head와 hosted evidence를
  GitHub live metadata로 다시 대조한다.
