# #558 suspend replay cancellation checkpoint 및 output lifecycle lesson

## 상황

#539의 suspend CSV/GraphML exporter는 disk spool을 사용했지만 replay 전체를 한
번의 blocking IO loop로 소비했다. job cancellation이 첫 record 뒤에 발생해도
후속 record를 모두 쓴 뒤에야 관찰될 수 있었고, output close failure가 coroutine
취소 예외를 덮을 수 있었다.

## 결정

spool replay sequence에 `CoroutineContext.ensureActive`를 record마다 적용해
bounded cancellation checkpoint를 만들었다. CSV writer와 GraphML session/output을
명시적으로 보유하고 `NonCancellable + Dispatchers.IO`에서 닫으며, cleanup 중
발생한 예외는 최초 source·sink·cancellation failure에 suppressed로 연결한다.
`OutputStreamSink(closeOutput = false)`는 flush 후 호출자에게 반환하고,
`closeOutput = true`만 exporter가 닫는다.

## 검증

- TDD RED에서 GraphML 후속 record 출력 실패를 재현했다.
- CSV/GraphML replay cancellation, caller-owned/owned sink, close failure TCK를
  통과했다.
- suspend CSV 10개, GraphML 9개 관련 테스트가 통과했다.
- 전체 module test/Detekt/forbidden assertion scan/diff-check와 hosted exact-head
  receipt는 PR 생성 후 갱신한다.

## 남은 가드

1. checkpoint는 blocking `write` 호출을 interrupt하지 않는다. 더 짧은 지연이
   필요하면 sink 자체의 interruptible API를 별도 이슈로 설계한다.
2. compatibility fallback의 source full materialization과 backend transaction
   snapshot은 여전히 별도 계약이다.
3. 전체 train은 exact base/head와 hosted terminal receipt를 read-back한 뒤에도
   마지막 일괄 merge 승인 전까지 병합하지 않는다.
