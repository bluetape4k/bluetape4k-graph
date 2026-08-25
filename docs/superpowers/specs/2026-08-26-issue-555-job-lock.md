# #555 GraphImportJobStateStore job별 lock 범위 최적화 설계

## 범위와 stacked 기준

- 이슈: [#555](https://github.com/bluetape4k/bluetape4k-graph/issues/555)
- 대상 모듈: `graph-io-core`
- 유형: Type B / concurrency refactoring·test
- 선행 PR: [#578](https://github.com/bluetape4k/bluetape4k-graph/pull/578)
- 선행 exact head: `26b41485d3107a99a555678de85fb455b1000504`
- 작업 branch: `fix/issue-555-state-store-job-lock-stacked`
- implementation commit: `030f7879`
- implementation/docs candidate head: `5260dc89f16af9503c5c9d1b4016c3c14ceab9ea`
- 범위: `InMemoryGraphImportJobStateStore`의 JVM-local 전체 monitor를 job별
  lock으로 좁히고 병렬성·직렬성·취소 안전성을 회귀 테스트와 문서로 고정

## 문제

기본 `GraphImportJobStateStore.update`는 같은 store 인스턴스 안에서
load·transform·검증·save를 원자적으로 수행해야 한다. 기존 in-memory
reference store는 `load`와 `save`를 모두 `@Synchronized`로 보호했고 기본
`update`도 store 전체 monitor를 사용해 서로 다른 job의 전이까지
head-of-line blocking으로 직렬화했다. job 간 독립성을 활용하는 import
작업에서는 불필요한 대기와 cancellation 시 lock lifecycle 위험이 남았다.

## 결정

1. report map을 `ConcurrentHashMap`으로 바꾸어 서로 다른 job의 값을 안전하게
   동시에 읽고 쓸 수 있게 한다.
2. job ID별 `ReentrantLock`을 참조 횟수와 함께 private registry에 보관한다.
   `load`, `save`, `update` 모두 동일한 job lock을 사용해 direct access와
   atomic transition의 경계를 일치시킨다.
3. lock 대기는 `lockInterruptibly()`로 수행하고, acquire 여부와 무관하게
   registry 참조를 감소시킨다. 마지막 참조가 끝나면 idle entry를 제거해
   transform 실패와 interrupted waiter가 lock entry를 누수시키지 않게 한다.
4. `InMemoryGraphImportJobStateStore.update`만 override한다. 다른 store의
   기본 JVM monitor 계약과 public API/ABI는 바꾸지 않으며, durable store는
   여전히 native transaction 또는 CAS로 process-wide 원자성을 제공해야 한다.
5. 정량 microbenchmark 대신 두 job의 overlap과 한 job의 serialization을
   latch 기반 deterministic test로 검증한다. scheduler·CI 환경에 종속된
   숫자형 benchmark는 이 slice의 범위 밖이며, 변경 효과는 “job 간 lock
   대기 제거, job 내부 순서 보존”으로 문서화한다.

## 수용 기준 매핑

| 기준 | 구현·검증 |
| --- | --- |
| 서로 다른 job 병렬성 | 첫 job transform을 latch로 유지한 동안 두 번째 job update가 완료되는 test |
| 같은 job 직렬성 | 두 번째 transform이 첫 번째 commit 전에는 진입하지 않고, 최신 state를 보고 stale transition을 거부하는 test |
| lock lifecycle | 참조 카운트 기반 idle registry 제거와 direct `load`/`save`/`update` 공통 경계 |
| cancellation/error safety | interruptible waiter가 종료된 뒤 같은 job update가 다시 성공하고, transform 예외는 기존 TCK에서 보존 검증 |
| Bluetape 품질 | `io.bluetape4k.assertions` matcher만 사용, 금지 assertion scan 통과 |
| 모듈 품질 | targeted 3/3, graph-io-core 154/154, Detekt, `git diff --check` |

## SPW gate

- SPW-01: live #555 요구사항과 #578 exact base 확인
- SPW-02: `GraphImportJobStateStore` default contract, immutable report, 기존
  Bluetape lock/test 패턴 대조
- SPW-03: production API/ABI를 보존하고 in-memory 구현 내부의 lock 범위만 수정
- SPW-04: concurrency targeted → graph-io-core full/Detekt → assertion/static scan 순서
- SPW-05: README EN/KO, 7-Tier review, lesson, WIP, PR receipt를 최종 exact head에 연결

## 범위 밖

- 실제 durable database의 multi-process lock, CAS, transaction 또는 partial-write rollback
- suspend state-store counterpart 추가
- 정량 throughput/latency benchmark 및 운영 튜닝
- 전체 train의 PR merge 또는 issue close
