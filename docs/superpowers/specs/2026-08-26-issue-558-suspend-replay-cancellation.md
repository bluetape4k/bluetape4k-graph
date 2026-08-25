# #558 suspend replay cancellation checkpoint 및 output lifecycle TCK 설계

## 문제

#539의 suspend CSV/GraphML exporter는 blocking spool과 output을
`Dispatchers.IO`에서 실행하지만, replay를 하나의 blocking loop로 소비한다.
취소가 첫 record 뒤에 도착해도 다음 record로 넘어가기 전에 coroutine 상태를
확인하지 않아 대용량 output에서 취소 관찰 시간이 불필요하게 길어질 수 있다.
또한 output close가 cancellation 중 실패하면 coroutine context 전환이 원래
`CancellationException`을 덮을 수 있어 sink ownership과 suppressed 예외를
backend-independent TCK로 고정해야 한다.

## 결정

1. CSV/GraphML spool replay sequence에 record 경계 `CoroutineContext.ensureActive`
   checkpoint를 둔다. backend read와 기존 immutable spool snapshot 계약은
   변경하지 않는다.
2. suspend CSV는 vertex/edge writer를 명시적으로 보유하고
   `NonCancellable + Dispatchers.IO`에서 닫는다. GraphML은 `StaxGraphMlWriter`
   session을 직접 열고 record별로 기록해 같은 lifecycle 경계를 적용한다.
3. cleanup은 session/output/writer/spool을 모두 시도하며, primary failure가
   있으면 cleanup failure를 `suppressed`로 연결한다. primary가 없을 때만
   cleanup failure를 새 primary로 반환한다.
4. `OutputStreamSink(closeOutput = false)`는 flush만 하고 underlying stream을
   닫지 않으며, `true`일 때만 exporter가 stream을 닫는다는 계약을 CSV/GraphML
   fake sink TCK로 고정한다.

## 범위

- `SuspendCsvGraphBulkExporter`와 `SuspendGraphMlBulkExporter`의 replay/checkpoint
  및 cleanup
- CSV/GraphML suspend cancellation, caller-owned/owned sink, close failure TCK
- graph-io CSV/GraphML README EN/KO와 7-Tier review/lesson/WIP receipt

## 비범위

- sync exporter lifecycle 또는 `GraphIoRecordSpool` record format 변경
- backend cursor/bounded source capability와 transaction snapshot 의미
- cancellation을 강제하는 새로운 dispatcher 또는 thread interruption 정책

## 계약

- 큰 replay는 각 record 경계에서 취소를 관찰하며 후속 record를 계속 쓰지 않는다.
- source·sink·cancellation 중 primary 원인은 유지하고 cleanup failure만 suppressed
  된다.
- caller-owned output은 열린 상태로 남고 owned output은 정상·실패·취소 후 닫힌다.
- #539의 immutable stage-time 값과 기존 sync/virtual-thread 경로는 그대로다.

## 검증 기준

- TDD RED에서 checkpoint 부재로 후속 record가 계속 쓰이는 실패를 관찰한다.
- CSV/GraphML suspend targeted 및 전체 관련 테스트, Detekt, 금지 assertion scan,
  `git diff --check`를 통과한다.
- PR exact head, hosted CI/Examples terminal receipt, metadata를 read-back한다.
- hosted workflow-dispatch가 `BASE_SHA` 없이 image gate를 실패시키면 코드 회귀와
  분리해 receipt에 기록하고 PR merge는 보류한다.
