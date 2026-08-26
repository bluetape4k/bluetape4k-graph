# #548 TinkerGraph chunk lifecycle 레슨

## 문제

Kotlin `Sequence.take`는 producer의 `finally`를 재개하지 않으므로 traversal을
sequence builder의 `finally`에만 맡기면 public chunk API의 조기 소비에서 cursor가
열린 채 남을 수 있다. suspend Flow도 underlying iterator를 직접 닫지 않으면
`take(1)`, cancellation, mapper 예외가 동일한 lifecycle gap을 만든다.

## 결정

1. 기존 repository `Sequence` 반환 ABI는 유지하고 TinkerGraph에 additive
   `findVerticesByLabelChunkedCursor`/`findEdgesByLabelChunkedCursor`를 둔다.
2. cursor는 chunk 크기만큼만 traversal을 소비하고 `close()`로 active iterator를
   명시적으로 종료한다. 조기 종료 소비자는 cursor를 `use` 또는 `finally`로 닫는다.
3. suspend Flow는 cursor를 만들고 `finally`에서 닫는다. cancellation과 원래
   iterator/mapper 예외를 별도 catch로 삼키지 않아 상위 coroutine 신호를 보존한다.
4. remote driver backend는 이번 TinkerGraph lifecycle API를 재사용하지 않는다.
   AGE, Neo4j, Memgraph, FalkorDB와 graph-io는 conformance·consumer 영향만
   순차 확인하고 backend별 cursor 계약은 별도 이슈로 남긴다.

## 후속 가드

- 새로운 lazy `Sequence`가 AutoCloseable resource를 소유하면 producer `finally`
  만으로 early termination close를 주장하지 않는다.
- public API는 기존 반환 타입을 바꾸기 전에 additive close-aware cursor와 ABI
  증거를 우선 검토한다.
- Flow 기반 adapter는 `take`, cancellation, mapper/iterator exception 각각의
  close 회귀를 둔다.
