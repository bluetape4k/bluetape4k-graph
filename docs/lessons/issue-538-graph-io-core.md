# #538 graph-io-core 작업 lesson

## 상황

`GraphImportWorkflow`가 현재 상태를 읽고 전이 검증과 저장을 별도 호출해
동일 job의 동시 workflow가 stale state를 덮어쓸 수 있었다. 직접 생성 가능한
`GraphIoBatchWriter`와 `SuspendGraphIoBatchWriter`도 importer options를 우회하면
비양수 `batchSize`를 받았다.

## 결정

기존 구현을 깨지 않는 `GraphImportJobStateStore.update(jobId, transform)` default
계약을 추가하고, 기본 구현은 동일 JVM store monitor 안에서
`load → transform → jobId 검증 → save`를 수행하도록 했다. durable store는 native
transaction/CAS로 override해야 하며 transform은 pure/retry-safe여야 한다.
두 writer는 공용 `requirePositiveNumber("batchSize")`를 생성자에서 호출하고,
새 예외 검증은 `io.bluetape4k.assertions.assertFailsWith`로 통일했다.

## 결과

- 실제 두 번째 task 시작을 보장하는 race 회귀에서 성공 1건/실패 1건을 확인
- targeted 11/11, graph-io-core 전체 142/142, detekt와 Kotlin compile 통과
- PR #575 exact head `941c822e40f670ae8d856fad893f0922ae5d8a0d` 위에서
  PR #576 exact head `3a9f8e52a07107d365a33e090722d78a10d5c5f0`을 검증
- README 영어/한국어, design/plan, public KDoc에 JVM-local 및 durable override 경계를 기록
- 독립 7-Tier review에서 P0/P1=0, merge gate PASS
- 비차단 후속 이슈 생성: [#553](https://github.com/bluetape4k/bluetape4k-graph/issues/553), [#554](https://github.com/bluetape4k/bluetape4k-graph/issues/554), [#555](https://github.com/bluetape4k/bluetape4k-graph/issues/555)

## 놓친 점과 보완

초기 구현 검토에서 상태 전이 때 기존 report의 `sources`, `elapsed`, `checkpoint`가
새 report 생성으로 초기화되는 dormant 동작을 발견했다. 현재 production caller가
없어 #538 blocker는 아니지만 #553으로 분리했다. 또한 durable CAS contention,
retry와 jobId mismatch를 공통 TCK로 고정하지 못해 #554로 분리했고, store 전체
monitor의 head-of-line blocking은 #555로 추적한다.

병렬 Gradle `--rerun-tasks`에서는 일시적인 Companion class loading과 test report
파일 lifecycle 오류가 관찰됐다. `cleanTest`와 순차 재실행으로 환경성 문제를
분리했으며, 앞으로 graph-io-core 검증은 shared build output을 동시에 건드리지
않도록 순차 receipt를 우선한다.

## 다음 작업 가드

1. #538 PR을 생성하고 hosted exact-head checks를 확인한 뒤 #553을 그 PR의
   exact head 위에 적층한다.
2. #554에서 durable store TCK와 `jobId` mismatch 저장 금지를 정립한다.
3. 성능 근거가 생길 때 #555에서 job별 lock 범위를 최적화한다.
4. graph-io 다음 우선순위는 CSV/GraphML 대용량 export 시점 일관성과 boundedness 이슈
   [#539](https://github.com/bluetape4k/bluetape4k-graph/issues/539)다.
