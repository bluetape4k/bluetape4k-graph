# #476 graph-io 빈 label export 계약

## 결정

`GraphExportOptions`의 빈 vertex/edge label 집합은 전체 label export를
요청한다. exporter는 `GraphLabelDiscovery` 또는
`GraphSuspendLabelDiscovery`로 label을 먼저 조회하며, backend가 해당
capability를 제공하지 않으면 명시적 label을 요구한다.

## 이유

기존 exporter는 빈 집합을 직접 순회해 backend를 호출하지 않고도
`COMPLETED`와 0건 결과를 반환할 수 있었다. 이는 기본 옵션이 전체 export라는
문서 계약과 달랐고 데이터 유실을 성공으로 숨겼다.

## 검증

- sync/coroutine CSV, Jackson2, Jackson3, GraphML exporter가 공통 resolver를 사용한다.
- TinkerGraph sync/suspend facade가 label discovery를 제공한다.
- discovery 제공, 미제공 fail-fast, 명시적 label 우회 테스트를 추가했다.
- graph-io core resolver test 3개와 4개 format module test를 통과했다.

## 후속

Neo4j, Memgraph, AGE, FalkorDB가 기본 옵션 all-label export를 제공하려면
각 backend의 label discovery를 별도 capability로 구현해야 한다. 그 전까지는
명시적 label을 사용한다.
