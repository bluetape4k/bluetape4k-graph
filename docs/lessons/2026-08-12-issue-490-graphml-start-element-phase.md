# Issue #490 GraphML START_ELEMENT 이전 phase 추적 교훈

## Context

Issue #485에서 `node`/`edge` START_ELEMENT 이후의 malformed XML은 마지막
record phase를 보존하도록 보정했지만, opening tag 자체가 깨지면 StAX가
`START_ELEMENT`를 만들지 못하고 whitespace `CHARACTERS` 이벤트만 남길 수 있다.
이 경우 기존 `currentPhase`만으로는 malformed edge도 `READ_VERTEX`로 보고된다.

## Decision or Finding

- `StaxGraphMlReader`가 이미 사용하는 non-closing input wrapper에 제한적인
  ASCII record-tag marker를 추가한다.
- marker는 `<node`/`<edge`의 phase와 태그 진행 상태만 보존하고, attribute·record ID·
  parser message·원문 payload는 저장하지 않는다.
- 완전한 record name 뒤의 name delimiter를 확인하고, malformed tag 안에서 다음
  `<`를 만나면 terminal marker를 유지하여 뒤따르는 `</graph>`의 `>`가 phase를
  덮어쓰지 않게 한다.
- parser 교체, 일반 buffering abstraction, raw parser message 분석은 범위 밖에
  둔다. 정상 XML에서는 `>` 또는 `/>`에서 marker를 폐기하고 기존 StAX phase를
  그대로 사용한다.

## Outcome

opening tag가 START_ELEMENT로 승격되지 않아도 malformed edge는 `READ_EDGE`를
보고한다. malformed node는 기존 `READ_VERTEX` fallback을 유지하며, sync importer,
suspend importer, streaming reader가 같은 `GraphIoFailure` phase와 redaction
계약을 공유한다.

## Miss or Surprise

실패 시점의 StAX reader event가 `START_ELEMENT`가 아니라 whitespace
`CHARACTERS`였으므로 `getLocalName()` 재조회로는 record name을 복구할 수 없었다.
parser exception message는 raw 입력을 포함할 수 있고 이 이슈의 redaction 범위 밖이므로
phase 판별 근거로 사용하지 않았다.

## Verification

- RED: malformed edge opening streaming 테스트가 `READ_VERTEX`를 반환하여 실패했다.
- GREEN: sync/suspend/streaming malformed edge opening 및 malformed node opening
  계약 테스트 통과.
- 기존 malformed record-body, source ownership, cancellation, buffer-boundary
  테스트를 포함한 `GraphMlStreamingReaderContractTest` 17개 통과.
- GraphML 모듈 전체 테스트 40개 통과.
- `:bluetape4k-graph-io-graphml:compileKotlin`과 `:bluetape4k-graph-io-graphml:detekt`
  통과.
- `git diff --check` 통과.

## Future Guidance

StAX가 parser event에서 record name을 제공하지 않는 입력을 보정할 때는 raw
exception message를 해석하거나 XML parser를 교체하지 않는다. bounded lexical
marker를 추가하더라도 secret-bearing attribute를 보존하지 말고, 정상 태그와
malformed 태그의 terminal transition을 함께 회귀 테스트한다.
