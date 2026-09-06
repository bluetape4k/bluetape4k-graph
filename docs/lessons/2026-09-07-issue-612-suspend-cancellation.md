# Suspend graph-io 취소 경계

## Context

`SuspendGraphIoBatchWriter`가 backend suspend 호출의 모든 `Throwable`을
failure callback으로 전달하고 있었다. importer callback은 checkpoint를
`FAILED`로 저장하므로 정상적인 coroutine 취소도 재개 가능한 실패와 같은
상태로 오염될 수 있었다.

## Decision or Finding

`CancellationException`은 backend 오류·row-count mismatch보다 먼저 catch해
callback을 호출하지 않고 동일 인스턴스를 재전파한다. suspend importer는 취소
경계에서 checkpoint를 추가 저장하지 않고 claim만 best-effort로 해제한 뒤
기존 checkpoint를 보존한다. 일반 `RuntimeException`과 계약 위반 예외는 기존
failure callback과 suppressed-error 규칙을 유지한다.

## Outcome

CSV, Jackson2/3 NDJSON, GraphML suspend importer에서 취소가 `FAILED` phase나
failure boundary를 새로 기록하지 않는다. sync writer와 sync importer의 기존
실패 계약은 변경하지 않았다.

## Verification

- core `GraphIoBatchWriterTest`: 직접 `CancellationException` 경로와 실제
  coroutine `Job`의 vertex/edge 중간 취소, backend failure, row-count mismatch
  callback 회귀를 검증했다.
- CSV checkpoint lifecycle에서 실제 vertex/edge `Job` 취소가 마지막 안전 phase를
  보존하고 claim을 재사용 가능하게 하는지 검증했다. Jackson2/Jackson3/GraphML도
  직접 취소 경계에서 `DISCOVERED` phase와 null failure boundary를 검증했다.
- 관련 전체 테스트 및 detekt를 모듈별로 실행했다. CSV 전체 71건은
  `CsvStreamingReaderContractTest`의 기존 close/parse 경계 race가 병렬 실행에서
  간헐적으로 실패해 targeted 재실행과 별도 full-suite 결과를 기록한다.

## Future Guidance

coroutine 취소는 업무 실패가 아니므로 broad `catch (Throwable)`보다 먼저
처리하고, cancellation 중에는 checkpoint·retry·failure callback을 변경하지
않는다. 새 suspend importer는 동일한 `cancelled()` claim-release 경계를
사용하고 `io.bluetape4k.assertions.assertFailsWith`로 원래 취소 인스턴스를
검증한다.

## 추적

- GitHub issue: [#612](https://github.com/bluetape4k/bluetape4k-graph/issues/612)
- Stacked base: PR [#620](https://github.com/bluetape4k/bluetape4k-graph/pull/620)
