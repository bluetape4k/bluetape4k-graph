# Issue #312 native loader SPI 교훈

## 범위

이번 변경은 `graph-io-core`에 backend-native bulk loader의 공통 경계를
추가한다. 기존 `GraphBulkImporter`의 portable record loop는 변경하지 않고,
실제 Neo4j/Memgraph/AGE/FalkorDB adapter와 파일 staging·URI dereference·
Testcontainers 검증은 후속 이슈로 남겼다. TinkerPop/TinkerGraph도 서버가
소유한 native command/staging semantics가 없는 인메모리/reference 구현이므로
이 SPI 대상에서 제외하고 portable `GraphBulkImporter` 경로를 유지한다.

## 핵심 선택

- raw caller source `R`과 validator가 만든 typed artifact `V`를 분리했다.
  `loadValidated`에는 raw request/source를 전달하지 않고, one-shot validated
  handle과 동일 cancellation token만 전달한다.
- request/capabilities를 함께 받는 report factory가 count, transaction,
  failure-detail, cancellation invariant를 한곳에서 검증한다. progress/report
  postcondition 위반은 caller 입력 오류와 구분되는 `CONTRACT_VIOLATION`으로
  매핑한다.
- URI 접근은 default deny다. exact scheme/host/port allowlist와 cardinality·
  aggregate-size bound, redirect/private-network 정책, backend 재검증 flag를
  capabilities에 고정했다.
- cancellation, source close, loader close, validation rollback, diagnostic
  observer를 monotonic deadline과 virtual-thread bounded call로 감싼다.
  `CLOSED`는 실제 cleanup worker completion 이후에만 publish하며, close grace
  만료 뒤에는 load/take 종료 경로가 deferred cleanup owner를 인계한다.
- diagnostic은 고정 `native-bulk-load` label, backend, phase, elapsed, outcome,
  fixed code, bounded correlation ID만 노출한다. expired observer timeout은
  caller를 막지 않는 async 경로로 보내되 실제 worker completion 전에는
  in-flight를 해제하지 않고 pending TIMEOUT event를 한 번만 재시도한다.

## 검증

- RED: nativebulk 타입이 없을 때 targeted `compileTestKotlin` 실패를 확인했다.
- GREEN: targeted `io.bluetape4k.graph.io.nativebulk.*` 테스트 19개 통과.
- 전체 `:bluetape4k-graph-io-core:test`: 126개 통과.
- `:bluetape4k-graph-io-core:compileKotlin`: 성공.
- `git diff --check`: 성공.

## 후속 adapter 경계

후속 adapter는 validator 단계에서 canonical file/URI, approved staging root,
DNS rebinding 방지와 server-side origin/artifact revalidation을 다시 수행해야
한다. native driver가 cancellation·`takeOnce()`·`loadValidated()`·terminal
cleanup deadline을 관찰하지 못하면 `shutdownGuarantee = UNKNOWN` 및
`supported = false`로 선언한다. 이 core 이슈에서는 실제 backend/Testcontainers
실행을 하지 않았다.
