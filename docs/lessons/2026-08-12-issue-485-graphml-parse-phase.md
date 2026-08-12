# Issue #485 GraphML malformed XML phase 교훈

## Context

GraphML StAX reader가 XML 구조 오류를 안전한 `GraphIoFailure`로 변환하고
있었지만, `XMLStreamException`을 처리하는 공통 catch 경로가 항상
`READ_VERTEX`를 사용했다. 따라서 edge를 읽는 중 XML이 깨져도 운영 report와
streaming 예외가 vertex 단계로 잘못 표시됐다.

## Decision or Finding

- parser의 공통 예외 경로에는 마지막으로 진입한 record phase를 로컬 상태로
  보존한다.
- `node` 진입 전에는 `READ_VERTEX`, `edge` 진입 전에는 `READ_EDGE`를 기록하고,
  기존 safe message·line location·redaction 계약은 그대로 유지한다.
- sync importer, suspend importer, `GraphMlRecordFlowReader`는 같은 StAX sink/event
  결과를 사용하므로 각 공개 경계에서 phase를 별도로 재해석하지 않는다.
- 새 buffering abstraction, public API, parser 교체는 이 결함의 범위에 넣지 않는다.

## Outcome

malformed vertex XML은 `READ_VERTEX`, malformed edge XML은 `READ_EDGE`를 보고한다.
sync/suspend/streaming 경로가 동일한 phase semantics를 공유하고, raw XML·record ID는
기존처럼 public failure에 노출하지 않는다.

## Verification

- RED: malformed edge를 sync/suspend/streaming에서 검증하는 3개 테스트가 기존
  `READ_VERTEX` 결과로 실패했다.
- GREEN: GraphML 전체 테스트 35개 통과.
- `:bluetape4k-graph-io-graphml:compileKotlin` 통과.
- `:bluetape4k-graph-io-graphml:detekt` 통과.
- `git diff --check` 통과.

## Future Guidance

새 streaming parser의 parse failure를 추가할 때는 record phase를 parser entry
경계에서 먼저 고정하고, sync·suspend·Flow 경계가 같은 failure object를 소비하는지
각각 계약 테스트로 확인한다. 문서/예외 redaction을 수정하지 않고 phase만 보정하는
경우에도 malformed vertex와 edge를 모두 포함한 회귀 fixture를 유지한다.
