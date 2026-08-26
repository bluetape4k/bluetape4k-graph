# #556 backend bounded chunk 및 기준 데이터 변경 TCK lesson

## 상황

#539에서 CSV/GraphML exporter의 두 번째 live 조회를 `GraphIoRecordSpool`로
제거했지만, backend가 chunk API를 override하지 않으면 compatibility list/Flow
fallback이 첫 chunk 전에 전체 label을 materialize할 수 있었다. 또한 첫 stage 뒤
backend가 바뀌어도 export output이 stage 시점 데이터로 고정되는지 네 exporter
경로에서 공통으로 관찰할 수 있는 TCK가 없었다.

## 결정

CSV·GraphML sync/suspend 테스트에 첫 chunk를 반환한 뒤 vertex/edge property를
`before`에서 `after`로 바꾸는 chunk-only fake를 추가했다. 테스트는 요청 chunk
크기 `1`, label별 한 번의 chunk 호출, output의 `before` 포함 및 `after` 부재를
확인한다. graph-core에는 sync list와 suspend Flow 기본 fallback이 첫 emission
전에 full label lookup을 한 번 수행한다는 회귀를 추가했다.

문서는 API chunking과 source bounded execution을 분리한다. TinkerGraph의
cursor/bounded capability는 유지하고 AGE·Neo4j·Memgraph·FalkorDB fallback은
bounded implementation으로 표시하지 않는다. 이 TCK는 exporter stage 기준 데이터
계약을 검증할 뿐 backend transaction snapshot을 보장하지 않는다.

## 검증

- graph-core `357/357`, graph-io-core `158/158`, CSV `55/55`, GraphML `48/48`
  전체 테스트가 통과했고 failures/errors/skipped는 모두 `0`이다.
- CSV/GraphML sync/suspend mutation TCK 네 개와 graph-core fallback eager
  materialization TCK 두 개가 모두 PASS했다.
- 세 대상 모듈 Detekt, 금지 assertion scan, `git diff --check`가 통과했다.
- `scripts/audit-korean-terms.mjs`는 checkout에 없어 실행하지 못했다.

## 남은 가드

1. 실제 AGE·Neo4j·Memgraph·FalkorDB adapter가 bounded cursor를 제공하는지는
   backend container conformance에서 별도로 확인한다.
2. fallback 전체 materialization의 peak memory와 transaction-consistent snapshot은
   이 slice의 수용 기준으로 확대하지 않는다.
3. PR exact head의 hosted CI·Examples와 metadata를 read-back한 뒤에도 전체 train은
   마지막 일괄 merge 승인 전까지 병합하지 않는다.
