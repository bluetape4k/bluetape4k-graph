# #546 예제 suspend teardown 레슨

## 문제

7개 예제의 suspend backend `@AfterAll` teardown이 `runSuspendIO` 호출을
`runCatching`으로 감싸 모든 예외를 logging-only 경로로 바꾸고 있었다. 이
패턴은 coroutine cancellation도 일반 graph drop 실패처럼 보이게 만들어
테스트 lifecycle의 취소 신호를 잃을 수 있다.

## 레슨

1. **CancellationException은 일반 예외보다 먼저 처리한다.**
   suspend cleanup에서 `CancellationException`을 먼저 catch하고 즉시
   rethrow해야 상위 coroutine과 JUnit lifecycle이 취소 상태를 보존한다.
2. **정리 순서는 `try`/`catch`/`finally`로 읽히게 만든다.**
   graph drop 실패는 `warn`으로 남기되, owned driver close는 성공·실패·취소
   모든 경로에서 `finally`로 시도한다. 이 slice에서는 close가 동기 API이므로
   별도 `NonCancellable` 경계를 추가하지 않았다.
3. **sync-only 패턴은 증거 없이 함께 바꾸지 않는다.**
   같은 파일의 동기 teardown `runCatching`은 이번 cancellation 계약의
   대상이 아니므로 그대로 두고, suspend wrapper만 좁게 수정했다.
4. **반복되는 예제 lifecycle은 동일한 계약으로 검증한다.**
   한 모듈만 통과한 결과를 전체 예제의 증거로 확장하지 말고, graph DB
   Testcontainers 충돌을 피하도록 일곱 모듈을 순차 실행해 각 report를
   확인한다.

## 후속 가드

- 새 suspend teardown은 `CancellationException`을 먼저 재전파하고 owned
  resource close를 `finally`에 둔다.
- cleanup을 `NonCancellable`로 확장해야 하는 경우에는 실제 suspend close
  API와 cancellation injection 회귀를 먼저 추가한다.
- sync-only teardown 변경은 별도 이슈와 실패 전파 근거를 갖춘 뒤 수행한다.
