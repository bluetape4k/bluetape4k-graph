# Plugin resource close 실패의 재시도 상태

## 배경

`GraphPluginCloseAction`과 `GraphPluginState`는 실제 종료 동작을 실행하기 전에
`closed=true`로 전환했다. 종료 동작이 실패해도 이후 호출은 이미 닫힌 것으로
판단해 열린 driver나 pool을 다시 정리할 수 없었다. 한 action의 실패를 로그로
남기고 다른 action을 계속 실행하는 기존 장점은 유지해야 했다.

## 결정

재시도 상태는 전체 plugin state가 아니라 각 `GraphPluginCloseAction`이 소유한다.
Action은 `OPEN`, `CLOSING`, `CLOSED` 상태를 원자적으로 전환한다. `OPEN`을 획득한
호출자 한 명만 종료 동작을 실행하며, 성공한 경우에만 `CLOSED`가 된다. 실패하면
`OPEN`으로 돌아가 원래 예외를 상위 close loop에 전달한다.

`GraphPluginState.close()`는 CAS guard로 전체 action pass를 하나만 실행한다. 현재
pass가 끝난 뒤 다음 호출은 action 목록을 다시 순회하고, 이미 성공한 action은 자체
상태가 건너뛰며 실패해 `OPEN`으로 돌아온 action만 재시도한다. 이 state-level guard는
영구적인 `closed` 표식이 아니라 pass의 in-flight 상태만 표현한다.

Action 단위 guard만으로는 서로 다른 action의 동시 실행을 막을 수 없다. 첫 호출이
첫 번째 action에서 대기하는 동안 두 번째 호출이 그 action을 건너뛰고 다음 action을
실행하면 등록된 종료 순서가 깨진다. 따라서 action 단위 성공/재시도 상태와 state 단위
pass 직렬화가 모두 필요하다. `closeGraphPluginActions`는 각 실패 원인과 action 이름을
경고로 남기고 다음 action을 계속 실행하는 기존 정책을 유지한다.

## 결과

일부 resource 종료가 실패해도 나머지 resource는 정리된다. 같은 state를 다시
닫으면 성공한 action을 중복 실행하지 않고 실패한 action만 재시도한다. 동시에 여러
호출자가 닫아도 전체 action pass는 하나만 실행되므로 등록된 종료 순서를 보존한다.

## 검증

- 구현 전 첫 close 실패 후 두 번째 close의 attempt 수가 `1`로 남는 RED를 확인했다.
- 구현 전 concurrent failure 뒤 재호출도 attempt 수가 `1`인 RED를 확인했다.
- 구현 후 retry action은 2회, 이미 성공한 action은 1회만 실행됨을 검증했다.
- Virtual Thread 두 개의 동시 close에서 최대 in-flight 수가 1임을 검증했다.
- action 단위 guard만 둔 중간 구현에서 두 번째 action이 첫 번째 action 완료 전에
  실행되는 RED(`expected 0, actual 1`)를 확인했다.
- state-level CAS guard를 추가한 뒤 동시 전체 pass 회귀 테스트가 통과했다.
- `:bluetape4k-graph-ktor:check`와 19개 module test가 통과했다.
- 예외 회귀는 `io.bluetape4k.assertions.assertFailsWith` 기존 사용 규칙을 유지한다.

## 향후 지침

Idempotent resource cleanup에서 실행 시도와 성공 완료를 같은 boolean으로 표현하지
않는다. 실패 후 재시도가 필요하면 action 단위 상태를 두고, 여러 action의 순서가
계약이면 전체 container pass도 별도 in-flight guard로 직렬화한다. 동시성 테스트는
latch에 timeout을 두어 회귀 실패 자체가 test hang으로 바뀌지 않게 한다.

## 추적

- GitHub issue: [#617](https://github.com/bluetape4k/bluetape4k-graph/issues/617)
