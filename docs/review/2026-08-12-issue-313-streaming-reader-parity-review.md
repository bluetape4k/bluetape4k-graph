# Issue #313 streaming reader parity 최종 리뷰

## 범위

CSV, Jackson2/3 NDJSON, GraphML StAX, OkIO facade의 `GraphRecordFlowReader`
계약과 기존 sync/suspend/virtual-thread importer의 경계, source ownership,
cancellation, bounded edge staging, safe failure를 Type-A 변경 기준으로 검토했다.

## 독립 리뷰 결과

| 관점 | 결과 | 핵심 확인 |
| --- | --- | --- |
| performance/stability | PASS | GraphML busy-spin 제거, `trySendBlocking` backpressure, edge overflow `StopImport` 즉시 중단 |
| security/API | PASS | public `GraphIoReadException.failure` redaction, raw XML/CSV payload·cause 비노출 |
| completion/verifier | PASS | 전체 테스트·compile·detekt·Dokka·diff 검증을 fresh 상태에서 재실행 |

통합 판정은 P0=0, P1=0이다. 기존 GraphML strict policy의 다중 failure 집계는
report 호환성을 위해 EOF까지 유지하고, memory/streaming terminal 경계인 edge buffer
overflow만 parser를 즉시 중단한다.

## 반영한 수정

- `GraphIoReadException`은 원본 `sourceName`, `recordId`, `columnName`, `elementName`,
  message를 보유하지 않고 `line`/`row`/`edge-buffer` 숫자 위치와 고정 message만 노출한다.
- GraphML invalid typed value와 missing endpoint message에서 raw value/endpoint를 제거했다.
- GraphML event handoff의 `Thread.yield()` busy-spin을 `trySendBlocking`으로 교체하고,
  downstream cancellation/closed channel을 예외로 전파한다.
- sync/suspend GraphML importer의 edge buffer overflow는 `StopImport`로 StAX parsing을
  중단하며 trailing input read-count 회귀를 고정했다.
- CSV `catch(Throwable)`를 `IOException`/`RuntimeException` parsing 경로로 좁혔다.
  `Error`와 EOF 후 close `IOException`은 malformed input으로 치환하지 않는다.
- Jackson KDoc short link와 OkIO `PathSource` link를 qualified reference로 보정했다.

## 검증 증거

- 전체 graph-io 회귀: core 129, CSV 42, Jackson2 15, Jackson3 17, GraphML 28,
  OkIO 111 — 총 342 tests 통과.
- 여섯 module `compileKotlin` 및 `detekt`: `BUILD SUCCESSFUL`.
- 여섯 module `dokkaGeneratePublicationHtml`: `BUILD SUCCESSFUL`, unresolved link 없음.
- `git diff --check`: 통과.
- 보안 회귀: core exception redaction, GraphML secret payload, CSV fatal `Error`와
  owned close failure 테스트 통과.
- ABI dump task는 repository에 없어 N/A로 판정했으며, 0.7.0 jar와 `javap`로 기존
  importer contract 및 additive reader/exception signature를 확인했다.

## 잔여 P2 및 후속 작업

- CSV/Jackson `take(1)` read-count와 suspend GraphML overflow의 trailing-byte parity는
  후속 보강 대상이다. 현재 GraphML/OkIO cancellation 및 GraphML sync overflow coverage는
  통과한다.
- malformed XML의 parse phase가 vertex로 고정된 기존 StAX failure 모델은 후속 개선 대상이다.

위 P2는 이번 issue의 공통 reader API, ownership, safe failure, bounded edge staging
수용 기준을 차단하지 않는다.
