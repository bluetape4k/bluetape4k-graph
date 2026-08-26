# 이슈 #536 bounded chunk capability 교훈

## 배경

`CHUNKED_READ`와 `CHUNKED_EXPORT`가 repository chunk API의 존재만 표현하면서,
`find*ByLabel`이 반환한 전체 `List`를 먼저 만든 backend도 bounded 실행처럼
해석될 수 있었다. TinkerGraph는 traversal iterator를 chunk 경계에서 소비하지만
AGE, Neo4j, Memgraph, FalkorDB 동기 경로는 같은 source materialization을 증명하지
못했다.

## 결정

기존 `CHUNKED_*` API와 기본 list/Flow fallback은 호환성을 위해 유지하고,
`BOUNDED_CHUNKED_READ`와 `BOUNDED_CHUNKED_EXPORT`를 별도 capability로 추가했다.
`GraphBoundedChunkOperations` marker는 vertex와 edge를 전체 결과 materialization
없이 소비하는 구현에만 적용한다. 현재 reference 구현은 TinkerGraph sync/suspend이며,
virtual-thread facade는 delegate capability를 그대로 투영한다. 네 container 동기
backend는 API chunking만 광고하고 bounded flag는 광고하지 않는다.

GraphML 문서와 repository KDoc에서는 chunk 결과 형태와 source heap bound를 같은
뜻으로 쓰지 않도록 조건을 명시했다. exporter 실행 방식은 바꾸지 않아 기존
fallback 동작과 결과 순서를 보존했다.

## 결과와 검증

- graph-core capability 테스트 351개, graph-tinkerpop 테스트 114개,
  graph-io-graphml 테스트 44개가 통과했다.
- AGE, Neo4j, Memgraph, FalkorDB capability conformance가 각각 4개 테스트로
  통과했으며, Testcontainers는 AGE → Neo4j → Memgraph → FalkorDB 순서로 실행했다.
- `compileKotlin`, test compilation, 세 모듈 `detekt`, `git diff --check`와
  Korean terminology audit를 통과했다.
- 새 테스트는 `io.bluetape4k.assertions` 기반 assertion을 사용하고 금지된
  JUnit/Kotlin `assertThrows` 계열을 추가하지 않았다.
- `BOUNDED_CHUNKED_*` capability가 대응하는 `CHUNKED_*` API capability 없이
  생성되지 않도록 생성자 불변식과 `assertFailsWith` 회귀 테스트를 추가했다.
- TinkerGraph의 bounded helper는 첫 chunk만 요청할 때 source를 정확히 chunk 크기만
  소비하는 회귀 테스트를 추가했고, 전체 소비 시 traversal close callback도 확인했다.

## 독립 7-Tier review와 후속 이슈

이전 implementation head `d1fd2eac`에 대해 두 source-read-only reviewer가
P0/P1 없이 `PASS/WATCH`를 판정했고, 이번 stacked branch는 #547 exact head 위로
rebase한 뒤 같은 계약과 검증을 다시 통과했다. WATCH 항목은 현재 이슈를 차단하지
않는 다음 세 가지다.

- public chunk API 자체의 lazy source 소비를 직접 고정하는 회귀 테스트가 필요하다.
- `Sequence.take(1)`과 suspend Flow cancellation에서 cursor/traversal close를 보장할
  close-aware lifecycle 계약이 필요하다.
- 새 enum capability를 exhaustive `when`으로 소비하는 외부 코드의 source/binary
  호환성 정책과 release guidance가 필요하다.

첫 두 항목은 [이슈 #548](https://github.com/bluetape4k/bluetape4k-graph/issues/548),
enum compatibility 항목은 [이슈 #549](https://github.com/bluetape4k/bluetape4k-graph/issues/549)로
분리해 후속 처리한다.

## 놓친 점

첫 container 실행에서는 context-mode subprocess가 Colima 환경변수를 전달하지 않아
Docker client 탐색이 실패했다. `colima status`, `docker context show`, `docker info`
로 daemon 상태와 socket을 확인한 뒤 `DOCKER_HOST`와
`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE`를 명시해 같은 모듈을 순차 재실행했다.
첫 실패를 테스트 실패로 숨기지 않고 환경 경계로 분리해 기록해야 한다.

## 다음 guard

bounded marker를 새 backend에 추가할 때는 해당 chunk override가 실제로 전체 label
결과를 만들지 않는지 source read와 conformance로 함께 증명한다. `CHUNKED_*`만
확인하고 heap bound를 가정하는 exporter 호출자는 `BOUNDED_CHUNKED_*` capability를
먼저 확인해야 한다. backend별 paging/cursor 구현은 결과 순서와 cursor lifecycle을
별도 이슈로 검증한 뒤 추가한다. Kotlin `Sequence.take`의 조기 종료는 producer의
`finally`를 재개하지 않으므로, 동기 cursor의 조기 close 보장은 #548의 close-aware
API 이슈로 다룬다. `GraphCapability`를 exhaustive `when`으로 소비하는 코드는 #549의
compatibility policy가 정해질 때까지 unknown/`else` 분기를 유지해야 한다.
