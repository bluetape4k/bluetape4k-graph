# #561 Virtual Thread optional async surface 설계

## 목표

`graph-core`의 Virtual Thread facade가 동기 delegate의 선택 기능을
`CompletableFuture` 기반 API로 안전하게 노출하도록 한다. merge/upsert, schema,
transaction, chunked read/export를 같은 실행·예외·취소·소유권 계약으로 정렬하고,
지원하지 않는 backend가 성공한 척하지 않도록 capability와 future 결과를 함께
검증한다.

## 범위

- `GraphVirtualThreadMergeOperations`: `mergeVertexAsync`, `mergeEdgeAsync`
- `GraphVirtualThreadSchemaManagementOperations`: index/constraint 생성·삭제와
  metadata 조회
- `GraphVirtualThreadTransactionalOperations`: 전체 transaction block을 한
  virtual thread에서 실행
- `GraphVirtualThreadChunkedOperations`: vertex/edge chunk 조회를 future로
  materialize하되 chunk 경계 보존
- 통합 `VirtualThreadOperationsAdapter`와 focused adapter extension
- `GraphVirtualThreadOperations.capabilities()`와
  `delegateCapabilities()`의 분리
- TinkerGraph supported path, marker를 숨긴 unsupported decorator, 예외·취소·timeout
  회귀 TCK

기존 synchronous/coroutine API, backend query semantics, delegate의 lifecycle은
이 범위에서 변경하지 않는다.

## 실행·executor 계약

모든 adapter는 Bluetape4k `virtualFutureOf`/`virtualFutureOfNullable` helper를
사용한다. adapter가 별도 executor를 생성하거나 delegate를 소유하지 않는다.

| 표면 | 실행 경계 | 결과 경계 |
| --- | --- | --- |
| merge/schema | 동기 delegate 호출 하나 | delegate 결과 또는 원인 예외 |
| transaction | block 전체가 동일 virtual thread | backend commit/rollback 결과 |
| chunked | sequence 소비 전체가 동일 virtual thread | chunk 경계를 유지한 materialized list |

Completion-stage callback의 executor는 호출자 선택이며, adapter는 callback
affinity를 보장하지 않는다. transaction block 안에서 별도 async 작업을 만들거나
thread-local을 공유하는 것은 계약에 포함하지 않는다.

## capability 계약

- `capabilities()`는 현재 facade에서 호출 가능한 async surface를 보고한다.
- `delegateCapabilities()`는 감싼 synchronous delegate의 기존 전체 매핑을
  보존한다. 기존 delegate capability 조회가 필요한 호출자는 이 메서드를 사용한다.
- delegate가 `GraphMergeOperations`, `GraphSchemaManagementOperations`,
  `GraphTransactionalOperations`를 구현할 때만 각각 `MERGE`, `SCHEMA`,
  `TRANSACTION`을 surface에 추가한다.
- chunk API는 모든 `GraphOperations`에 존재하므로 `CHUNKED_READ`와
  `CHUNKED_EXPORT`를 노출한다. delegate가 `GraphBoundedChunkOperations`인 경우에만
  `BOUNDED_CHUNKED_READ`와 `BOUNDED_CHUNKED_EXPORT`를 추가한다.
- optional marker가 없는 delegate의 optional 호출은 즉시 성공하지 않고
  `UnsupportedOperationException`으로 완료된 future를 반환한다.
- focused adapter의 `GraphCapabilities.from` 결과도 자신이 감싼 표면만 광고한다.

## 예외·취소·timeout 계약

- synchronous delegate의 예외는 future exceptional completion의 원인으로 보존한다.
  `join()`에서만 표준 `CompletionException`으로 감싼다.
- `CompletableFuture.cancel(true)`는 future 취소 상태와 best-effort interrupt
  요청을 관찰할 수 있게 하지만 이미 실행 중인 JDBC/driver 작업의 중단을 보장하지
  않는다.
- `orTimeout`은 호출자 future의 timeout 상태를 보장하지만 backend statement
  취소나 connection close까지 대신하지 않는다.
- facade `close()`는 facade만 닫고 borrowed delegate를 닫지 않는다. chunk source가
  `AutoCloseable`이면 virtual-thread 소비가 끝난 뒤 adapter가 닫는다.
- chunk async 결과는 future 완료 시점에 전체 list를 materialize한다. streaming,
  조기 `take`, 명시적 cursor close가 필요하면 기존 synchronous close-aware cursor를
  사용한다.

## 호환성·마이그레이션

optional surface는 additive API다. 기존 `GraphVirtualThreadOperations`의 기본
CRUD/traversal/algorithm 메서드와 public delegate constructor는 유지한다.
기존 코드가 `capabilities()`를 delegate 정보로 사용했다면
`delegateCapabilities()`로 이동하고, 새 optional 호출 전에는 `capabilities()`로
surface를 확인한다. TinkerGraph의 unique constraint처럼 schema manager가
capability를 구현해도 개별 backend DDL이 unsupported일 수 있으며, 이 경우 기존
`UnsupportedOperationException` semantics를 유지한다.

## 검증 기준

1. TDD RED에서 optional method가 없음을 관찰한 뒤 production API를 추가한다.
2. TinkerGraph facade가 optional capability, merge, schema index, transaction
   thread affinity, chunk 경계를 검증한다.
3. marker를 숨긴 decorator가 optional capability를 광고하지 않고 unsupported
   future를 반환하는지 검증한다.
4. delegate 예외 원인, future cancellation, timeout을 검증한다.
5. graph-core 전체 test, compile, Detekt, 금지 assertion 검색, `git diff --check`를
   통과한다.
6. EN/KO README, public KDoc, 7-Tier review, lesson, WIP, PR receipt에 같은 계약과
   exact head를 기록한다.

## 비범위 및 후속 위험

- backend별 native async driver cancellation은 이 공통 facade가 보장하지 않는다.
- async chunk 결과의 heap boundedness는 보장하지 않는다. backend source bounded
  여부는 `BOUNDED_*` capability와 별도 TCK로 판단한다.
- callback executor 선택 API나 custom executor injection은 후속 설계로 남긴다.
