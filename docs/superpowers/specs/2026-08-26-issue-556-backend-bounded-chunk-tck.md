# #556 backend bounded chunk·기준 데이터 변경 TCK 설계

## 문제

`GraphExportOptions.exportChunkSize`와 `find*ByLabelChunked`는 chunk-shaped API
계약을 제공하지만, repository 기본 구현은 먼저 `find*ByLabel` 전체 결과를
materialize한 뒤 chunk로 나눈다. 따라서 CSV·GraphML exporter가 spool을 사용해도
backend source 자체가 bounded하다고 말할 수 없다.

또한 CSV header union과 GraphML key pre-scan을 위해 exporter가 입력을 한 번
stage하므로, 첫 chunk가 stage된 뒤 backend record 또는 property가 바뀌어도 출력은
stage 시점 snapshot이어야 한다.

## 결정

1. CSV·GraphML sync/suspend round-trip test에 mutation-aware chunk fake를 추가한다.
   첫 chunk를 exporter가 소비한 뒤 mutable property map을 `before`에서 `after`로
   바꾸고 빈 두 번째 chunk를 반환한다.
2. fake는 `find*ByLabelChunked` 요청을 `vertices:<size>`, `edges:<size>`로 기록하고
   full list/Flow API는 즉시 실패시킨다. 네 exporter가 label별 정확히 한 번만
   chunk API를 호출하고 `exportChunkSize`를 그대로 전달하는지 확인한다.
3. CSV 두 파일과 GraphML 한 파일의 출력에는 `before`만 존재하고 `after`는 없어야
   한다. 이는 backend transaction snapshot을 검증하는 것이 아니라 exporter가
   stage한 record의 값 보존 계약만 검증한다.
4. graph-core 기본 fallback test는 chunk sequence를 받기 전에 full label lookup이
   한 번 실행되는 사실을 기록한다. capability는 `CHUNKED_*`만 유지하고
   `BOUNDED_CHUNKED_*`를 광고하지 않는다.

## 범위와 비범위

- 범위: graph-io CSV/GraphML 네 exporter 경로, graph-core fallback contract test,
  root·core·format README, 7-Tier review와 lesson receipt.
- 비범위: backend driver/cursor 구현 변경, backend transaction isolation, 새로운
  public API, #469/#471 재개, per-record serialization peak memory(#557), suspend
  replay cancellation checkpoint(#558).

## capability matrix

| Backend | `CHUNKED_*` | `BOUNDED_CHUNKED_*` | 근거 |
| --- | --- | --- | --- |
| TinkerGraph | override + cursor | 광고 | native traversal iterator와 기존 capability conformance |
| AGE | 호환 API/fallback | 미광고 | container backend capability test |
| Neo4j | 호환 API/fallback | 미광고 | container backend capability test |
| Memgraph | 호환 API/fallback | 미광고 | container backend capability test |
| FalkorDB | 호환 API/fallback | 미광고 | container backend capability test |

`CHUNKED_*`는 반환 shape와 요청 크기만 설명한다. source heap bound가 필요한
호출자는 `BOUNDED_CHUNKED_READ`와 `BOUNDED_CHUNKED_EXPORT`를 확인해야 한다.

## 수용 기준 추적

| 기준 | 검증 |
| --- | --- |
| 네 exporter가 첫 stage 이후 재조회하지 않음 | 각 fake의 full lookup 실패 + chunk request `vertices:1`, `edges:1` |
| stage 시점 데이터 보존 | CSV vertex/edge 파일 및 GraphML output의 `before`/`after` assertion |
| chunk-aware 요청 계약 | 네 fake의 exact request list |
| fallback materialization 명시 | graph-core sync/suspend lookup-count test와 API README |
| 종료 이슈와 중복 없음 | #469/#471는 선행 list/chunk 범위로 source ledger에만 기록 |

## 실패 시 판정

- P0: exporter가 full lookup을 호출하거나 `after`가 출력에 섞임.
- P1: 네 경로 중 하나라도 chunk size/call count가 어긋나거나 fallback을 bounded라고
  문서화함.
- P2: backend별 실제 cursor conformance, serialization peak, cancellation checkpoint.
