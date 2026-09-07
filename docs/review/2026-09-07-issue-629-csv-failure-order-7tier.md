# Issue #629 CSV failure-order 7-Tier review

- 기준 base: `5aaa08a98f0e573bd1099b85408a179241cdd8df`
- 검토 head: `788371b130dac4d81187fbdafcf57310985454cd`
- 검토 범위: `git diff 5aaa08a9..788371b1`
- 판정: `COMMENT`
- provenance: inline exact-diff fallback

독립 native review lane 두 개가 제한 시간 안에 usable verdict를 반환하지 않아 이
문서는 독립 리뷰로 주장하지 않는다. 아래 결과는 exact diff에 대한 inline fallback
검토이며, hosted exact-head CI와 Full Nightly gate를 대체하지 않는다.

## Severity

| 등급 | 건수 | 결과 |
| --- | ---: | --- |
| P0 | 0 | 없음 |
| P1 | 0 | 없음 |
| P2 | 0 | 없음 |
| P3 | 0 | 없음 |

## Tier 1 — Compile, API, ABI

- `CsvRecordParser`는 `internal` class이며 새 generic overload도 내부 경계에만 있다
  (`CsvRecordParser.kt:25-38`).
- public `CsvGraphRecordFlowReader.readVertices/readEdges` signature는 변경하지 않았다
  (`CsvGraphRecordFlowReader.kt:29-43`).
- raw `Record` 소비자가 사용하는 기존 overload는 identity transform으로 유지한다
  (`CsvRecordParser.kt:27-31`).
- `:bluetape4k-graph-io-csv:build`가 compile, test, detekt, Kover를 포함해 통과했다.

결론: public API/ABI 변경 없음.

## Tier 2 — Behavior, compatibility

- vertex/edge 변환 결과와 validation 규칙은 기존 `toVertex`/`toEdge`를 그대로 사용한다
  (`CsvGraphRecordFlowReader.kt:45-84`).
- bulk importer 경로는 identity overload를 사용하므로 raw record 처리 계약을 유지한다.
- CSV 전체 74개 테스트가 통과해 sync, suspend, Virtual Thread, checkpoint, raw JSON
  round trip과 error policy의 회귀가 없다.

결론: 의도한 failure ordering 외 동작 변경 없음.

## Tier 3 — Failure, concurrency, cancellation

- 변환을 `trySendBlocking` 인자 평가 시점에 수행해 source가 닫히기 전에 validation
  failure를 확정한다 (`CsvRecordParser.kt:40-47`).
- `use`가 `CallbackFailure` wrapper에 부착한 close failure를 원래 변환 예외에
  순서대로 이전하고 원래 인스턴스를 재전파한다 (`CsvRecordParser.kt:83-101`).
- 회귀 테스트는 primary 인스턴스, suppressed 메시지 순서, close exactly once를 직접
  단언한다 (`CsvRecordParserFailureOrderTest.kt:20-42`).
- 기존 cancellation, take(1), close-only failure와 owned/caller-owned source 계약은
  `CsvStreamingReaderContractTest` 전체에서 통과했다.
- failure-order 관련 13개 테스트를 `--rerun-tasks`로 5회 실행해 65/65 통과했다.

결론: scheduler timing과 무관하게 primary/suppressed 순서가 결정적이다.

## Tier 4 — Security, privacy

- 새 예외 메시지나 source/payload 노출을 추가하지 않았다.
- 기존 safe failure가 raw `Person` 값을 노출하지 않는 단언을 유지한다
  (`CsvStreamingReaderContractTest.kt:42-58`).

결론: 정보 노출 회귀 없음.

## Tier 5 — Performance, resource lifecycle

- 새 buffer, executor, dependency를 추가하지 않았다.
- transform은 기존 record 한 건당 한 번 수행되며 channel 전송 전으로만 이동한다.
- `.buffer(0)`과 bounded read-ahead 검증을 유지하고 close exactly once를 직접 단언한다
  (`CsvRecordParser.kt:38-59`, `CsvStreamingReaderContractTest.kt:23-38`).

결론: 메모리 상한과 source lifecycle 계약 유지.

## Tier 6 — Bluetape4k, Kotlin, assertions patterns

- coroutine Flow와 `Dispatchers.IO`, `CancellationException` 우선순위를 기존 구조 안에서
  유지했다.
- 회귀 테스트는 `bluetape4k-assertions`의 `assertFailsWith`,
  `shouldBeSameInstanceAs`, `shouldBeEqualTo`를 사용한다
  (`CsvRecordParserFailureOrderTest.kt:3-5,31-42`).
- 새 dependency나 불필요한 public abstraction을 추가하지 않았다.

결론: `$bluetape-kotlin-patterns`와 assertions 지침 충족.

## Tier 7 — Tests, docs, CI

- RED: 새 transform contract 부재로 test compile이 실패함을 확인했다.
- GREEN: 회귀 1/1, CSV 전체 74/74, 반복 65/65, detekt, Kover,
  `git diff --check`가 통과했다.
- `CHANGELOG.md`, `WIP.md`, lesson 문서를 갱신했고 한국어 용어 audit finding은 0건이다.
- hosted exact-head CI와 Full Nightly는 PR 생성 후 실행하므로 현재 `PENDING`이다.

## Known gaps and merge gate

- 독립 reviewer verdict: `PENDING` — 두 native lane이 usable verdict 없이 hard-stall.
- hosted exact-head CI: `PENDING`.
- exact-head Full Nightly `scope=full`: `PENDING`.
- 로컬 SSH signature cryptographic verification: `PENDING` — commit에는 SSH signature
  block이 있으나 local `gpg.ssh.allowedSignersFile`이 설정되지 않았다. push 후 GitHub의
  verified read-back으로 확인한다.
- merge는 모든 pending gate와 live review/thread/mergeability를 재확인한 뒤 별도 최종
  승인을 받아야 한다.
