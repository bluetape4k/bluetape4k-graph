# CSV streaming failure 순서는 handoff 경계에서 고정해야 한다

## 배경

PR #628 exact-head CI run
[#34081704962](https://github.com/bluetape4k/bluetape4k-graph/actions/runs/34081704962)은
전체 결과가 성공이었지만 core test job의 첫 시도는
`CsvStreamingReaderContractTest.parse failure remains primary when owned close also fails`
에서 실패했다. retry evidence에는 기대한 `GraphIoReadException` 대신 owned source의
`IOException("csv-close-failure")`이 primary가 된 기록이 남았다.

## 원인

`CsvRecordParser`는 `Record`를 rendezvous channel로 전달하고 source를 닫았지만,
`CsvGraphRecordFlowReader`의 validation은 downstream `map`에서 수행했다. channel
handoff 직후 producer와 collector가 서로 다른 coroutine에서 진행할 수 있으므로,
record validation과 source close가 동시에 실패하면 어느 예외가 먼저 관찰되는지가
scheduler timing에 의존했다.

## 결정

- record-to-domain 변환을 parser의 source 소유권 경계 안에서 수행한다.
- 변환 실패는 parser callback failure로 운반하고, `use`가 callback wrapper에 붙인
  close failure를 원래 primary 예외의 suppressed 목록으로 이전한다.
- bulk importer가 사용하는 raw `Record` Flow overload와 public API/ABI는 유지한다.
- 정상 EOF의 close-only failure, cancellation 우선순위, bounded read-ahead 계약은
  기존 테스트로 함께 검증한다.

## 교훈

Rendezvous channel은 buffer 크기를 제한하지만 downstream 변환의 완료까지 보장하지
않는다. source close와 변환 failure의 순서가 계약이라면 변환을 producer의 ownership
scope 안에서 완료해야 한다. 또한 최종 workflow가 green이어도 retry evidence가
`success_after_retry`이면 최초 실패를 독립적인 결함 신호로 조사해야 한다.

관련: [#486](https://github.com/bluetape4k/bluetape4k-graph/issues/486),
[#629](https://github.com/bluetape4k/bluetape4k-graph/issues/629)
