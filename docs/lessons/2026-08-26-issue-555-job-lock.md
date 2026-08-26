# #555 GraphImportJobStateStore job별 lock 범위 최적화 lesson

## 상황

#554에서 `GraphImportJobStateStore.update`의 reusable TCK와 retry/failure
경계를 고정한 뒤, 기본 in-memory reference store가 여전히 store 전체
monitor로 서로 다른 job을 직렬화한다는 범위를 분리했다. 같은 job의 순서는
보존해야 하지만 독립 job까지 대기시키면 import 병렬성이 줄고, 대기 중 취소가
lock lifecycle을 흔들 수 있다.

## 결정

`InMemoryGraphImportJobStateStore`에 `ConcurrentHashMap`과 job ID별
`ReentrantLock` registry를 적용했다. `load`·`save`·`update`가 같은 key lock을
공유하고 registry는 참조 카운트가 0일 때 entry를 제거한다. `lockInterruptibly`
와 `finally` cleanup을 사용해 transform 예외와 interrupted waiter 모두 lock을
반납한다. 다른 구현의 기본 monitor와 durable transaction/CAS 책임은 그대로
남긴다.

## 검증

- 서로 다른 job의 첫 transform을 멈춘 동안 두 번째 job update가 완료된다.
- 같은 job의 두 번째 transform은 첫 commit 전 진입하지 않고 최신 state를
  보고 stale transition을 실패시킨다.
- 대기 중인 waiter를 interrupt한 뒤 같은 job의 후속 update가 성공한다.
- targeted concurrency 3/3, graph-io-core 154/154, Detekt, forbidden assertion
  scan, `git diff --check`가 통과했다.
- reader-facing KDoc와 README EN/KO에 JVM-local lock 범위, idle cleanup,
  durable adapter의 미검증 경계를 기록했다.
- candidate head `5260dc89f16af9503c5c9d1b4016c3c14ceab9ea`에서 hosted CI
  `32899855369`와 Examples `32899855380`이 모두 성공했고, docs-only lifecycle
  최종 head `419e1e4ccbe7063514cccabce4fb505efaf538ae`에서도 CI `32900966027`과
  Examples `32900966048`이 terminal PASS했다. PR #579의 exact base/head와
  `MERGEABLE`/`CLEAN` metadata를 read-back했다.

## 남은 가드

1. 실제 durable adapter의 process 간 contention과 transaction rollback은
   #554 testFixtures를 소비하는 별도 lane에서 검증한다.
2. 정량 benchmark는 scheduler/CI 변동성이 큰 별도 작업으로 남기고, 이 PR은
   deterministic overlap/serialization 증거만 제공한다.
3. exact final head hosted CI·Examples와 metadata/stacked base를 read-back한 뒤에도
   전체 train 마지막 승인 전에는 merge하지 않는다.
