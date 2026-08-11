# Issue #313 streaming reader parity 교훈

## Context

graph-io CSV, Jackson2/3 NDJSON, GraphML, OkIO facade에 공통 `GraphRecordFlowReader`
계약을 적용했다. 목표는 전체 payload materialization 없이 record 순서를 유지하고,
`GraphImportOptions.batchSize`(백엔드 쓰기 플러시)와 reader streaming 축을 분리하며,
source 소유권·취소·실패 경계를 포맷 간에 동일하게 만드는 것이었다.

## Decision or Finding

- CSV, Jackson2/3, GraphML은 포맷별 parser를 유지하고 cold `Flow` reader를 추가했다.
  GraphML production importer는 StAX sink/event 경로로 전환해 전체 vertex/edge list를 만들지
  않으며 edge staging만 `maxEdgeBufferSize`로 제한한다.
- OkIO는 기존 `GraphIoOkioPaths.openSource(...).use`를 단일 close owner로 재사용한다.
  `ownsSource`/`ownsStream=true`와 `PathSource`는 library-owned, 기본 false는 caller-owned로
  유지하고, delegate에는 `closeInput=false`를 전달한다. CSV는 `{stem}_vertices.csv`와
  `{stem}_edges.csv`가 필요한 paired-path 계약이므로 stream-backed source는 명시적으로
  unsupported다.
- parse failure가 primary이면 source close failure는 suppressed로 남겨 원래 안전한
  `GraphIoReadException`을 덮지 않는다. raw JSON/XML payload와 외부 ID는 public failure에
  포함하지 않는다.
- 테스트 fixture의 XML declaration은 반드시 첫 바이트부터 시작해야 한다. multiline
  fixture는 `trimIndent()`보다 `trimMargin()`으로 declaration 앞 공백을 제거해야 StAX와
  OkIO bridge에서 동일하게 동작한다.

## Outcome

CSV/Jackson2/Jackson3/GraphML reader와 OkIO adapter가 같은 record order, cold collection,
ownership, cancellation 경계를 공유한다. generated 10,000-record fixture가 CSV·Jackson2·
Jackson3·GraphML·OkIO contract에 포함되며, 기존 duplicate/missing-endpoint 정책과
`GraphIoBatchWriter` batch flush 의미는 유지된다.

## Verification

- CSV·Jackson2·Jackson3 generated reader contract 테스트 통과.
- GraphML streaming contract 및 OkIO reader contract 통과. OkIO 신규 contract는 6 tests
  (순서, owned/caller-owned close, GraphML `take(1)`, CSV pair/unsupported, close suppressed)를
  검증한다.
- 전체 `:bluetape4k-graph-okio:test`: 110 tests 통과; `:bluetape4k-graph-okio:detekt` 통과.
- OkIO 구현 커밋: `ae46335`; `git diff --check`는 각 구현 단계에서 통과했다.

## Future Guidance

새 포맷 reader를 추가할 때 `GraphRecordFlowReader`의 cold/re-read, source ownership,
`CancellationException` 보존, safe failure, record order를 먼저 계약 테스트로 고정한다.
OkIO facade에는 포맷별 delegate를 직접 연결하되 paired-file 포맷의 stream 지원 여부를
추론하지 말고 명시적인 예외와 README locale pair를 함께 추가한다. `batchSize`를 reader
buffer 상한으로 설명하지 않으며, generated counter와 bounded edge queue를 별도로 검증한다.
