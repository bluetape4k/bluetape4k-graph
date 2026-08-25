# #553 workflow report payload 보존 설계

## 범위와 stacked 기준

- 이슈: [#553](https://github.com/bluetape4k/bluetape4k-graph/issues/553)
- 대상 모듈: `graph-io-core`
- 유형: Type C/Bug Fix
- 선행 PR: [#576](https://github.com/bluetape4k/bluetape4k-graph/pull/576)
- 선행 exact head: `112703d4752fa5dad6f25cef5a53328cd6712bfa`
- 현재 candidate: `88cc9676dfbbd48c7b700c51854f7380ee6fe07a`
- 범위: workflow 전이 때 기존 `GraphImportWorkflowReport` payload 보존

## 문제

선행 #538은 `GraphImportJobStateStore.update` 안에서 현재 state를 다시 읽고
허용 전이를 검증하도록 원자 경계를 만들었다. 그러나 `persist`가 매 전이마다
`GraphImportWorkflowReport(jobId, state)`를 새로 만들면 기존 `sources`, `elapsed`,
`checkpoint`가 기본값으로 초기화된다. 원자성은 유지되더라도 보고서의 누적
데이터가 사라지는 계약 결함이다.

## 결정

`stateStore.update` transform 안에서 전이를 검증한 뒤 다음 우선순위를 사용한다.

1. 기존 report가 있으면 `currentReport.copy(state = state)`를 반환한다.
2. 최초 report면 기존과 같은 `GraphImportWorkflowReport(manifest.jobId, state)`를
   생성한다.
3. jobId 검증과 저장은 #538의 `update` 경계에 맡긴다.

따라서 `sources`, `elapsed`, `checkpoint`는 전이와 독립적인 payload로 유지되고,
허용되지 않은 전이는 여전히 `IllegalArgumentException`으로 실패한다.

## 호환성·범위 경계

- public method, 생성자, `serialVersionUID`는 변경하지 않는다.
- `GraphImportJobStateStore.update`의 atomic·retry-safe transform 계약을 그대로
  사용한다. durable store의 native transaction/CAS 구현은 [#554](https://github.com/bluetape4k/bluetape4k-graph/issues/554) 범위다.
- 소스 검색 결과 `GraphImportWorkflow` production caller는 없고 core test만
  생성한다. suspend counterpart도 source에 없으므로 이번 이슈에서 N/A다.
- store 전체 monitor의 병렬성 개선은 [#555](https://github.com/bluetape4k/bluetape4k-graph/issues/555)로 분리한다.

## 수용 기준

- 기존 report의 `sources`, `elapsed`, `checkpoint`가 state 전이 후 그대로 남는다.
- 저장소를 다시 읽은 report도 반환 report와 동일한 payload를 가진다.
- 최초 report의 빈 payload와 기존 state 전이·동시성 회귀가 유지된다.
- 예외·의도 검증은 `io.bluetape4k.assertions` matcher를 사용한다.
- graph-io-core 전체 테스트, Detekt, 금지 assertion scan, `git diff --check`가
  통과한다.

## SPW gate

- SPW-01: live #553 요구사항과 #576 exact base 확인
- SPW-02: Kotlin immutable `copy`와 Bluetape assertions 패턴 확인
- SPW-03: 기존 `update` 원자 경계 안에서 검증·복사 수행
- SPW-04: RED payload 회귀 → GREEN targeted/full/Detekt 순서
- SPW-05: EN/KO README, review, lesson, PR receipt를 exact head에 연결

## 범위 밖

- durable database transaction/CAS store 구현
- suspend workflow API 추가
- store 전체 job lock 최적화
