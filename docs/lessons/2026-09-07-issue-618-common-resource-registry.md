# GraphPlugin과 공통 application resource registry의 소유권 경계

## 배경

`graph-ktor`는 `ApplicationStopped`를 직접 구독해 plugin이 생성한 graph operations를
정리했다. Bluetape4k Projects #1656과 PR #1668은 Ktor application이 소유하는 동기식
resource를 한 registry에서 LIFO로 닫고, late registration과 종료 실패를 구조화된
report로 남기는 `ApplicationResourceRegistry`를 제공한다. Graph의 caller-owned
resource 규칙과 #617에서 고정한 실패 action 재시도는 이 공통 계약을 도입한 뒤에도
유지해야 한다.

## 결정

`GraphPlugin`은 state를 확정한 직후 `installApplicationResourceLifecycle()`로 공통
registry를 설치하거나 재사용하고, plugin이 만든 close action이 있을 때만 등록한다.
`closeOnStop=false`인 caller-owned operations는 close action이 없으므로 registry에도
들어가지 않는다. Registry 연결이나 attribute 저장이 실패하면 이미 만든 state를
즉시 닫고 원래 실패를 다시 던진다.

여러 Graph close action은 registry에 각각 노출하지 않고 하나의 bounded resource
group으로 등록한다. Registry의 resource 사이 LIFO 규칙과 Graph 내부의 기존 등록 순서를
동시에 보존하고, concurrent direct close와 application shutdown이 action 단위로
엇갈리지 않게 하기 위해서다. Group close는 모든 action을 독립적으로 시도하고 실패한
action 수가 하나 이상이면 sanitized group exception을 던진다. 공통 report에는 group
실패 한 건이 남고, 각 action의 원래 cause와 이름은 기존 Graph warning logger가 보존한다.
원래 실패가 JVM `Error`이면 cause나 message를 전달하지 않는 fatal marker로 공통
registry에 알린다. Registry는 다른 resource를 계속 닫은 뒤 sanitized fatal marker를
던지고 report의 `fatal=true`를 보존한다. 명시적 `GraphPluginState.close()`는 기존처럼
모든 Throwable을 warning으로 격리하는 정책을 유지한다.

`GraphPluginState`는 제거하지 않는다. 공통 registry는 resource entry를 한 번만 claim하고
재시도하지 않지만, Graph state는 성공한 action을 건너뛰고 실패 action을 후속 명시적
`close()`에서 다시 시도하는 계약을 소유한다. 또한 application attribute로 제공하는
sync/suspend operations와 explicit-close handle도 기존 state의 책임이다.

종료된 registry에 늦게 등록하면 공통 계약에 따라 caller thread에서 group close가 즉시
실행된다. 이때도 Graph 내부 action 순서와 failure isolation은 동일하다.

## 결과

Ktor application 종료는 Bluetape4k 공통 lifecycle report에서 Graph resource group의
성공 또는 실패를 관찰할 수 있다. Graph가 직접 생성한 operations만 자동으로 닫히며,
caller-owned operations의 ownership은 바뀌지 않는다. 일부 action 실패는 나머지 action을
막지 않고 실패 action은 state에 남아 후속 명시적 close에서 재시도할 수 있다.

## 검증

- 구현 전 registry report의 `attempted`가 0인 RED를 확인했다.
- plugin 소유 sync/suspend action 두 개가 group entry 한 건으로 닫히는지 검증했다.
- caller-owned operations가 registry에 등록되거나 자동으로 닫히지 않는지 검증했다.
- 종료된 registry에 등록해도 Graph action 순서로 즉시 닫히는지 검증했다.
- 일부 close 실패가 registry의 `SHUTDOWN` failure 한 건으로 남고 다른 action은 닫히는지
  검증했다.
- Fatal Graph action이 이후 action을 막지 않고 common report의 `fatal=true`와 cause 없는
  marker로 보존되는지 검증했다.
- `:bluetape4k-graph-ktor:check`의 23개 테스트, Detekt와 Kover가 통과했다.
- Generated POM/module metadata가 `bluetape4k-ktor-core:2.1.0-SNAPSHOT`을 포함하고,
  POM validator가 1 POM, 125 dependencies와 1 Maven effective model을 검증했다.

## 제한과 향후 지침

동시에 직접 `GraphPluginState.close()`가 진행 중일 때 registry group close가 합쳐지면
후속 호출은 기존 #617 계약대로 선행 pass 완료를 기다리지 않는다. Registry report의
성공은 다른 caller가 이미 시작한 Graph cleanup 완료까지 보장하지 않는다. 완료 대기나
timeout이 필요하면 공통 동기식 registry에 숨은 blocking을 추가하지 말고 별도 lifecycle
계약으로 설계한다.

공통 report는 bounded group 단위이고 개별 Graph action cause는 Graph logger 단위다.
두 관측 경계를 하나로 합치려면 sanitized failure detail schema와 retry ownership을 먼저
공통 API에서 정의해야 한다.

## 추적

- Graph issue: [#618](https://github.com/bluetape4k/bluetape4k-graph/issues/618)
- Projects issue: [#1656](https://github.com/bluetape4k/bluetape4k-projects/issues/1656)
- Projects PR: [#1668](https://github.com/bluetape4k/bluetape4k-projects/pull/1668)
