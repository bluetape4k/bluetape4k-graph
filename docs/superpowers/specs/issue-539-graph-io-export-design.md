# #539 graph-io CSV/GraphML export bounded snapshot 설계

## 문제와 범위

CSV exporter는 모든 정점과 간선을 `List`에 materialize한 뒤 header와 payload를
작성한다. GraphML exporter는 이미 `exportChunkSize`로 chunk를 사용하지만
property-key pre-scan과 payload write에서 backend를 두 번 조회한다. 두 pass
사이에 그래프가 변경되면 header가 payload와 다른 관찰 결과를 설명할 수 있다.

이번 변경은 `graph-io/csv`와 `graph-io/graphml`의 sync/suspend exporter 및
공통 graph-io-core spool helper, 회귀 테스트, README/KDoc, 설계·계획·review·lesson만
대상으로 한다. backend query API, importer, virtual-thread public entry point와
외부 transaction 구현은 변경하지 않는다.

## 선택지와 결정

### 1. backend transaction 안에서 두 pass 실행

`GraphTransactionalOperations`/`GraphSuspendTransactionalOperations`가 있는
backend에서는 snapshot을 얻을 수 있지만, 모든 exporter 호출자가 해당 capability를
구현한다는 보장이 없다. capability가 없는 backend의 기존 export를 실패시키거나
best-effort로 되돌리면 계약이 분기된다.

### 2. header key만 저장하고 payload를 두 번째 live pass로 재조회

heap 사용량은 줄지만 GraphML의 동일 snapshot을 보장하지 못한다. #471의 bounded
reader 조회를 유지하면서도 #539 acceptance를 충족하지 못하므로 채택하지 않는다.

### 3. 공통 immutable disk spool (채택)

graph-io-core에 `GraphIoRecordSpool`을 추가한다. exporter는 label별
`find*ByLabelChunked`를 한 번만 소비해 정규화된 record(속성 값은 기존 writer가
사용하던 `toString()` 표현)를 두 개의 temporary binary file에 순차 기록하고
property key 집합만 작은 heap metadata로 유지한다. staging이 끝난 뒤 CSV/GraphML
header를 key 집합에서 만들고 같은 spool을 다시 읽어 payload를 작성한다.

chunk-aware backend가 실제로 bounded chunk를 제공한다는 전제에서 이 방식의
exporter-side 메모리 경계는 `O(exportChunkSize + distinctPropertyKeys)`이고, header와
payload는 staging 완료 시점의 동일 immutable snapshot이다. 호환성 list/Flow fallback은
exporter에 전달되기 전에 라벨 전체를 materialize할 수 있으므로 backend capability를
별도로 확인해야 한다. source 조회 실패나 cancellation이 발생하면 sink를 열기 전에
spool을 정리하므로 부분 output을 만들지 않는다.

## 컴포넌트 계약

### `GraphIoRecordSpool`

- `appendVertices`/`appendEdges`는 chunk 단위로만 호출하고, 호출 시 property 값을
  문자열 또는 null로 고정한다.
- `finish()`가 write stream을 닫은 뒤 `vertexRecords()`/`edgeRecords()` sequence를
  재생한다. replay는 한 번에 한 record만 heap에 두며, spool은 active replay input을
  추적해 조기 종료·writer 실패·취소 때도 닫는다.
- `vertexPropertyKeys`/`edgePropertyKeys`는 immutable copy로 노출한다.
- `close()`는 writer, replay stream, temporary file을 독립적으로 정리하며 실패를
  숨기지 않는다. exporter는 정상·예외·cancellation 모두에서 close를 호출하고,
  `closeSuppressing(primaryFailure)`로 원래 실패를 보존하면서 cleanup 실패를
  suppressed exception으로 연결한다.
- helper는 exporter 내부 공용 기반 계약이며 새로운 dependency를 추가하지 않는다.

### CSV sync/suspend

1. `resolveLabels` 후 vertex/edge chunk를 spool에 append한다.
2. `CsvRecordCodec`는 spool replay로 union header를 계산한다.
3. sink를 열고 같은 replay를 row writer에 전달한다.
4. suspend 경로의 blocking spool/file writer와 sink replay는 `Dispatchers.IO`에서
   실행하고, source Flow 소비는 caller coroutine context를 유지한다.

### GraphML sync/suspend

1. vertex/edge chunk를 한 번만 spool에 append하고 key 집합을 고정한다.
2. `StaxGraphMlWriter`의 existing sequence/session API에 spool sequence와 key
   집합을 전달한다.
3. header와 node/edge payload가 같은 spool snapshot을 사용하므로 backend의
   두 번째 live traversal이 없다.
4. suspend 경로는 output/session/replay를 `Dispatchers.IO`에서 실행하고,
   `NonCancellable` cleanup에서 spool과 output ownership을 정리한다.

## 실패·호환성 계약

- 기존 exporter public signature, `GraphExportOptions.exportChunkSize`, CSV/GraphML
  output 순서와 report count는 유지한다.
- spool은 내부 구현이 아니라 graph-io-core의 reader-facing support helper이므로
  Korean KDoc와 direct unit test를 제공한다. 기존 caller는 변경 없이 동작한다.
- property 값은 기존 CSV/GraphML writer와 동일하게 `toString()`으로 표현한다.
  임의 객체의 Java serialization을 요구하지 않는다.
- temporary file 생성·write·read·delete 실패는 정상 경로에서 숨기지 않는다. 작업 중
  source·sink·cancellation이 먼저 실패하면 그 원래 예외를 유지하고 cleanup 실패를
  suppressed exception으로 연결한다. caller-owned `OutputStreamSink(closeOutput = false)`는
  기존 ownership 계약대로 닫지 않는다.
- virtual-thread adapter는 exporter public entry를 그대로 위임하므로 별도 변경하지
  않는다.

## 수용 기준과 DoD

- CSV sync/suspend가 전체 record `List`를 만들지 않고 chunk→spool→replay를 사용한다.
- GraphML sync/suspend가 backend를 한 번만 읽고 header/payload가 같은 immutable
  snapshot에서 생성된다.
- empty graph, multi-label, property union, cancellation, abandoned replay input,
  caller-owned sink close와 source/write failure 회귀가 sync/suspend 모두에서 통과한다.
- cross-format round-trip, graph-io-core spool test, detekt, Kotlin compile과
  `git diff --check`가 통과한다.
- 신규 예외 검증은 `io.bluetape4k.assertions.assertFailsWith`를 사용한다.
- 독립 7-Tier review에서 P0/P1=0이며 남는 P2/P3는 후속 GitHub issue로 분리한다.

## 범위 밖

- backend별 transaction isolation level 또는 cross-process transaction 구현
- importer staging/maxEdgeBufferSize 정책
- virtual-thread API signature 변경
- Maven publication, PR 생성, merge, push
