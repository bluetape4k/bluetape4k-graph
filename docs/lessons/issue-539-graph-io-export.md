# #539 graph-io export lesson

## Context

CSV는 header union을 위해 전체 record를 list로 만들었고, GraphML은 key pre-scan과
payload에서 backend를 다시 읽었다. 대용량 입력에서는 heap pressure가 커지고 두 pass
사이의 변경이 header와 payload를 갈라놓을 수 있었다.

## Decision

backend transaction capability를 강제하는 대신 `GraphIoRecordSpool`을 추가했다.
chunk-aware backend의 입력을 UTF-8 binary temp file에 한 번 stage하고, header와
payload는 같은 spool을 replay한다. property 값은 기존 writer와 같은 `toString()`으로
정규화한다. sequence가 조기 종료될 수 있으므로 active replay input을 추적하고,
exporter cleanup은 `NonCancellable + Dispatchers.IO`에서 수행한다. 원래 source·sink·
cancellation 예외는 유지하고 cleanup 실패를 suppressed로 연결한다.

## Outcome

- CSV sync/suspend와 GraphML sync/suspend가 live second pass 없이 immutable staged
  불변 기준 데이터를 사용한다.
- caller-owned `OutputStreamSink(closeOutput = false)` ownership을 유지한다.
- core abandoned replay와 CSV/GraphML sync·suspend sink failure 회귀를 추가했다.
- full fresh test는 core 158, CSV 53, GraphML 46건이며 세 모듈 detekt와 diff check가
  통과했다.

## Misses and surprises

- 최초 review에서 정상 EOF의 `Sequence.use`만으로는 writer failure/cancellation 때
  input close를 보장하지 못한다는 P1을 발견했다. active input registry를 추가한 뒤
  재검토에서 P1을 닫았다.
- `close()`가 실패하면 원래 예외를 덮을 수 있었다. exporter별 `primaryFailure`와
  `closeSuppressing`을 명시해 원인 보존을 회귀로 고정했다.
- `find*ByLabelChunked`의 compatibility fallback은 backend가 전달 전에 전체 label을
  materialize할 수 있다. 따라서 exporter spool의 boundedness와 backend 조회
  boundedness는 같은 주장으로 합치면 안 된다.
- per-record 128 MiB buffer는 guard가 있어도 serialization 중 peak allocation을
  완전히 제한하지 않는다.

## Verification

```text
./gradlew :bluetape4k-graph-io-core:test \
  :bluetape4k-graph-io-csv:test \
  :bluetape4k-graph-io-graphml:test \
  --rerun-tasks --no-daemon --console=plain
158 + 53 + 46 tests passed

./gradlew :bluetape4k-graph-io-core:detekt \
  :bluetape4k-graph-io-csv:detekt \
  :bluetape4k-graph-io-graphml:detekt --no-daemon --console=plain
BUILD SUCCESSFUL
```

## Future guard

다음 변경은 [#556](https://github.com/bluetape4k/bluetape4k-graph/issues/556)의 backend별
bounded chunk/cursor와 기준 데이터 변경 TCK, [#557](https://github.com/bluetape4k/bluetape4k-graph/issues/557)의
record serialization peak memory 및 constructor fault cleanup, [#558](https://github.com/bluetape4k/bluetape4k-graph/issues/558)의
suspend output replay record 단위 cancellation checkpoint를 별도 issue로 다룬다. 새 replay sequence를
추가할 때는 반드시 abandoned consumer, caller-owned sink, primary/suppressed 예외를
함께 테스트한다.

## Reader-facing note

이 lesson은 #539의 구현·review 경계를 기록한다. #469와 #471의 종료된 선행 이슈를
재개하지 않으며, backend transaction isolation이나 publication/merge 절차를 이
이슈의 완료로 해석하지 않는다.
