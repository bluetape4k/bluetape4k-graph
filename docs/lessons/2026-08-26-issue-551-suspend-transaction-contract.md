# #551 suspendTransaction 중첩 Flow 결과 계약 lesson

## 상황

네 backend가 최상위 `Flow`를 각자 materialize하고 있었지만, generic `T`가
`Pair` 같은 carrier 안에 포함된 `Flow`까지 안전하게 재구성할 수는 없었다. 이
상태에서는 transaction scope가 종료된 뒤 cursor나 driver resource에 접근할 수
있다.

## 결정

graph-core에 공통 `materializeSuspendTransactionResult`를 두고 최상위 `Flow`는
commit 전에 `toList()`로 수집한다. `Pair`, `Triple`, `Map`, `Collection`, 배열
내부의 nested `Flow`는 fail-fast `IllegalArgumentException`으로 거부한다.
호출자가 nested 값을 필요로 하면 transaction block 안에서 `toList()` 등으로
materialize해야 한다.

임의 data class나 사용자 wrapper는 reflection으로 검사하지 않는다. 표준
컨테이너 범위 밖의 내부 `Flow`는 호출자 책임이라는 제한을 KDoc과 EN/KO README에
기록한다. 이 선택은 generic carrier의 임의 재구성보다 ABI를 예측 가능하게
유지한다.

## 검증에서 얻은 교훈

- `assertFailsWith` 내부의 suspend lambda는 기대 반환 타입 때문에 transaction
  결과를 `Unit`으로 추론할 수 있다. nested result 계약을 검증할 때는
  `Pair<String, Flow<*>>`처럼 generic 반환 타입을 명시해야 한다.
- backend별 private materializer를 고치는 것만으로는 계약 drift를 막을 수 없다.
  공통 helper와 core container matrix test를 함께 두고, 네 backend rollback을
  각각 확인해야 한다.
- `bluetape4k.assertions.assertFailsWith`를 사용하면 exception contract가
  repository의 assertion 규칙과 일치하고, 금지된 JUnit/Kotlin assertion이
  유입되지 않는다.

## 재현 명령과 결과

```bash
./gradlew :bluetape4k-graph-core:test --no-build-cache --no-daemon --console=plain
./gradlew :bluetape4k-graph-tinkerpop:test --no-build-cache --no-daemon --console=plain
./gradlew :bluetape4k-graph-neo4j:test --no-build-cache --no-daemon --console=plain
./gradlew :bluetape4k-graph-memgraph:test --no-build-cache --no-daemon --console=plain
./gradlew :bluetape4k-graph-age:test --no-build-cache --no-daemon --console=plain
```

결과는 core `355`, TinkerPop `119`, Neo4j `132`, Memgraph `124`, AGE `195`개
전체 테스트 통과이며 failures/errors/skipped는 모두 `0`이다. 다섯 모듈 Detekt,
금지 assertion scan, `git diff --check`도 통과했다. Testcontainers backend는
AGE → Neo4j → Memgraph 순서로 순차 실행했다.

## 남은 범위

driver 내부 stall에 `Statement.cancel()`을 연결하는 prompt cancellation은
[#552](https://github.com/bluetape4k/bluetape4k-graph/issues/552)에서 별도로
검증한다. 본 변경은 nested Flow escape를 표준 컨테이너 경계에서 차단하는 계약만
담당한다.

## SPW writer gate

- **SPW-01 — Audience and purpose: PASS.** 후속 maintainer가 nested Flow
  계약과 실패 원인을 재현할 수 있도록 작성했다.
- **SPW-02 — Evidence contract: PASS.** 결정, 제한, assertion/type inference
  lesson, 재현 명령과 결과를 포함한다.
- **SPW-03 — Korean register: PASS.** reader-facing 설명은 한국어이며 code,
  command, API, issue token은 원문을 보존한다.
- **SPW-04 — Traceability: PASS.** #551, #552, core helper와 네 backend
  테스트 경계를 연결한다.
- **SPW-05 — Read-back: PENDING.** 최종 PR exact head와 hosted 결과를 PR 생성
  후 다시 대조한다.
