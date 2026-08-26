# #561 Virtual Thread optional async surface lesson

## Context

기존 Virtual Thread facade는 session, CRUD, traversal, algorithm만
`CompletableFuture`로 제공했고 capability는 동기 delegate mapping을 그대로
반환했다. 따라서 merge/schema/transaction/chunked API의 실제 지원 여부와
`capabilities()` 결과가 분리되지 않았고, backend가 marker를 숨긴 decorator에서는
지원하지 않는 기능을 구분하기 어려웠다.

## Decision

optional 표면을 네 interface로 분리하고 통합 facade가 기존 sync marker를 기준으로
이를 조합하도록 했다. Bluetape4k `virtualFutureOf` helper로 동기 delegate 호출을
하나의 virtual-thread task에 넣고, `delegateCapabilities()`와
`capabilities()`를 분리해 delegate 정보와 실제 async surface를 각각 조회한다.

transaction block은 한 virtual thread에서 실행하며, chunk sequence는 같은 task에서
소비하고 chunk 경계를 유지한 materialized list를 반환한다. `AutoCloseable` source는
소비 후 닫고, facade close는 borrowed delegate를 닫지 않는다. unsupported optional
호출은 성공한 척하지 않고 exceptional future를 반환한다.

## Outcome

- TinkerGraph supported path가 merge/schema/transaction/chunked capability와
  transaction thread affinity를 검증한다.
- marker를 숨긴 decorator가 optional capability를 광고하지 않고
  `UnsupportedOperationException`을 반환한다.
- synchronous delegate exception의 원인, `cancel(true)`, `orTimeout` 관찰을
  Bluetape assertion TCK로 고정했다.
- graph-core 전체 378개 test, compile, Detekt, diff-check가 통과했다.
- EN/KO README와 public KDoc에 executor 선택, callback affinity, delegate ownership,
  backend cancellation, materialized chunk 제한, migration note를 기록했다.

## Misses and surprises

- `GraphCapabilities.from`는 통합 facade를 다시 optional marker로 해석하면 안 되므로
  `GraphVirtualThreadOperations`를 skip하고 adapter가 surface flag를 명시적으로
  추가해야 했다.
- `GraphSchemaManagementOperations`는 schema 표면을 뜻하지만 TinkerGraph unique
  constraint처럼 개별 DDL은 여전히 unsupported일 수 있다. capability와 method별
  backend support를 하나의 boolean으로 합치지 않는다.
- `CompletableFuture.cancel(true)`와 `orTimeout`은 future 상태를 바꾸지만 이미
  실행 중인 JDBC/driver 중단을 보장하지 않는다. backend-native cancellation은
  별도 계약으로 남긴다.
- chunk async 결과를 `List<List<...>>`로 반환하는 표면은 chunk 경계를 보존하지만
  bounded heap API가 아니다. source bounded marker와 result materialization을
  같은 주장으로 쓰지 않는다.

## Verification

```text
TDD RED: optional method 부재 실패 관찰
TDD GREEN: VirtualThreadOptionalSurfaceTddTest 5개 통과
./gradlew :bluetape4k-graph-core:test --no-daemon --rerun-tasks --console=plain
378 tests passed, BUILD SUCCESSFUL
./gradlew :bluetape4k-graph-core:compileKotlin --no-daemon --rerun-tasks --console=plain
BUILD SUCCESSFUL
./gradlew :bluetape4k-graph-core:detekt --no-daemon --rerun-tasks --console=plain
BUILD SUCCESSFUL
git diff --check
PASS
```

## Future guard

새 optional async method를 추가할 때는 sync marker, focused adapter, integrated
facade capability, unsupported decorator, delegate exception, cancellation/timeout,
source close TCK와 EN/KO KDoc를 같은 slice에 추가한다. callback executor를 custom으로
주입하거나 backend statement cancellation을 공통 facade가 보장하려면 별도 설계와
backend별 integration 증거를 먼저 만든다.

## Reader-facing note

이 lesson은 #561의 graph-core API surface와 TCK 경계를 기록한다. PR 생성·merge·issue
close는 별도 workflow gate이며, stacked train의 최종 일괄 승인 전에는 선행 PR을
독립 병합하지 않는다.
